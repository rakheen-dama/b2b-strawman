package io.b2mash.b2b.b2bstrawman.takeon.reader;

import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Types one raw cell text (§17.3.2 "Generic row checks", FD-17). Static and pure: no Spring, no
 * logging. A blank input is {@code ok} with a {@code null} value — whether a field is required is
 * the caller's check. Every refusal carries a stable {@code errorCode} and a user-facing message.
 */
public final class TakeOnValueParser {

  public static final String AMOUNT_INVALID = "AMOUNT_INVALID";
  public static final String AMOUNT_AMBIGUOUS = "AMOUNT_AMBIGUOUS";
  public static final String AMOUNT_TOO_PRECISE = "AMOUNT_TOO_PRECISE";
  public static final String AMOUNT_TOO_LARGE = "AMOUNT_TOO_LARGE";
  public static final String DATE_INVALID = "DATE_INVALID";
  public static final String NUMERIC_IN_DATE = "NUMERIC_IN_DATE";
  public static final String ENUM_INVALID = "ENUM_INVALID";
  public static final String BOOLEAN_INVALID = "BOOLEAN_INVALID";
  public static final String TEXT_TOO_LONG = "TEXT_TOO_LONG";
  public static final String EMAIL_INVALID = "EMAIL_INVALID";

  /** Excel's error tokens; a cell holding one is a row error (C-8). */
  public static final Set<String> EXCEL_ERROR_TOKENS =
      Set.of(
          "#REF!", "#VALUE!", "#DIV/0!", "#N/A", "#NAME?", "#NULL!", "#NUM!", "#SPILL!", "#CALC!");

  private static final int MAX_INTEGER_DIGITS = 13; // numeric(15,2)

  /** Length caps applied before any pattern runs (no regex over unbounded cell text). */
  private static final int MAX_AMOUNT_LENGTH = 40;

  private static final int MAX_DATE_LENGTH = 40;
  private static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern NUMERIC = Pattern.compile("^\\d+(\\.\\d+)?$");
  private static final Pattern POINT_GROUPED = Pattern.compile("^\\d{1,3}(,\\d{3})+$");
  private static final Pattern COMMA_GROUPED = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");
  private static final Pattern ONE_GROUP_OF_THREE_POINT = Pattern.compile("^\\d{1,3},\\d{3}$");
  private static final Pattern ONE_GROUP_OF_THREE_COMMA = Pattern.compile("^\\d{1,3}\\.\\d{3}$");
  private static final DateTimeFormatter ISO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ENGLISH)
          .withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]++@[^@\\s.]++(\\.[^@\\s.]++)+$");

  private TakeOnValueParser() {}

  /** A typed value, or the reason the text could not be typed. */
  public record Parsed<T>(T value, String errorCode, String message) {
    public boolean ok() {
      return errorCode == null;
    }

    public static <T> Parsed<T> of(T value) {
      return new Parsed<>(value, null, null);
    }

    public static <T> Parsed<T> error(String code, String message) {
      return new Parsed<>(null, code, message);
    }
  }

  // --- amounts ---

  /**
   * An amount in rand: {@code R}/{@code ZAR}, spaces and non-breaking spaces stripped; negative
   * when wrapped in {@code ( )}, with a leading or trailing {@code -}, or a {@code DR} suffix
   * ({@code CR} is positive). Under {@code COMMA}, {@code .} and spaces group and {@code ,} is the
   * decimal mark; under {@code POINT} the reverse. A lone group of three after the grouping mark
   * ({@code 1,234} under POINT, {@code 1.234} under COMMA) is ambiguous — an error, never a guess.
   */
  public static Parsed<BigDecimal> amount(String raw, DecimalSeparator separator) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    if (raw.length() > MAX_AMOUNT_LENGTH) {
      return Parsed.error(AMOUNT_INVALID, "This value is too long to be an amount.");
    }
    String s = raw.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2009', ' ').strip();
    boolean negative = false;
    String upper = s.toUpperCase(Locale.ROOT);
    if (upper.endsWith("DR")) {
      negative = true;
      s = s.substring(0, s.length() - 2).strip();
    } else if (upper.endsWith("CR")) {
      s = s.substring(0, s.length() - 2).strip();
    }
    if (s.startsWith("(") && s.endsWith(")") && s.length() >= 2) {
      negative = true;
      s = s.substring(1, s.length() - 1).strip();
    }
    if (s.startsWith("-")) {
      negative = true;
      s = s.substring(1).strip();
    } else if (s.startsWith("+")) {
      s = s.substring(1).strip();
    }
    String upperRest = s.toUpperCase(Locale.ROOT);
    if (upperRest.startsWith("ZAR")) {
      s = s.substring(3).strip();
    } else if (upperRest.startsWith("R")) {
      s = s.substring(1).strip();
    }
    if (s.startsWith("-")) {
      negative = true;
      s = s.substring(1).strip();
    }
    if (s.endsWith("-")) {
      negative = true;
      s = s.substring(0, s.length() - 1).strip();
    }
    s = s.replaceAll("\\s+", "");
    if (!digitsAndSeparatorsOnly(s)) {
      return invalidAmount(raw);
    }

    String plain = separator == DecimalSeparator.COMMA ? normaliseComma(s) : normalisePoint(s);
    if (plain == null) {
      return invalidAmount(raw);
    }
    if (plain.startsWith("!")) {
      return Parsed.error(
          AMOUNT_AMBIGUOUS,
          "\"%s\" is ambiguous: it could use %s for thousands or for decimals. Check the decimal"
                  .formatted(
                      raw.strip(), separator == DecimalSeparator.COMMA ? "a point" : "a comma")
              + " separator chosen for this file.");
    }
    int dot = plain.indexOf('.');
    String integerPart = dot < 0 ? plain : plain.substring(0, dot);
    String fraction = dot < 0 ? "" : plain.substring(dot + 1);
    if (fraction.length() > 2) {
      return Parsed.error(
          AMOUNT_TOO_PRECISE, "\"%s\" has more than two decimals.".formatted(raw.strip()));
    }
    String significant = integerPart.replaceFirst("^0+", "");
    if (significant.length() > MAX_INTEGER_DIGITS) {
      return Parsed.error(AMOUNT_TOO_LARGE, "\"%s\" is too large.".formatted(raw.strip()));
    }
    var value =
        new BigDecimal((integerPart.isEmpty() ? "0" : integerPart) + "." + fraction + "0")
            .setScale(2, RoundingMode.UNNECESSARY);
    return Parsed.of(negative ? value.negate() : value);
  }

  /** POINT: {@code .} decimal, {@code ,} grouping. Returns "!" when ambiguous, null if invalid. */
  private static String normalisePoint(String s) {
    if (count(s, '.') > 1) {
      return null;
    }
    int dot = s.indexOf('.');
    String integerPart = dot < 0 ? s : s.substring(0, dot);
    String fraction = dot < 0 ? "" : s.substring(dot + 1);
    if (fraction.indexOf(',') >= 0) {
      return null;
    }
    if (integerPart.indexOf(',') >= 0) {
      if (dot < 0 && ONE_GROUP_OF_THREE_POINT.matcher(integerPart).matches()) {
        return "!";
      }
      if (!POINT_GROUPED.matcher(integerPart).matches()) {
        return null;
      }
      integerPart = integerPart.replace(",", "");
    }
    return dot < 0 ? integerPart : integerPart + "." + fraction;
  }

  /** COMMA: {@code ,} decimal, {@code .} grouping. Returns "!" when ambiguous, null if invalid. */
  private static String normaliseComma(String s) {
    if (count(s, ',') > 1) {
      return null;
    }
    int comma = s.indexOf(',');
    String integerPart = comma < 0 ? s : s.substring(0, comma);
    String fraction = comma < 0 ? "" : s.substring(comma + 1);
    if (fraction.indexOf('.') >= 0) {
      return null;
    }
    if (integerPart.indexOf('.') >= 0) {
      if (comma < 0 && ONE_GROUP_OF_THREE_COMMA.matcher(integerPart).matches()) {
        return "!";
      }
      if (!COMMA_GROUPED.matcher(integerPart).matches()) {
        return null;
      }
      integerPart = integerPart.replace(".", "");
    }
    return comma < 0 ? integerPart : integerPart + "." + fraction;
  }

  /** Linear scan: only digits, {@code .} and {@code ,}, with at least one digit. */
  private static boolean digitsAndSeparatorsOnly(String s) {
    boolean digit = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= '0' && c <= '9') {
        digit = true;
      } else if (c != '.' && c != ',') {
        return false;
      }
    }
    return digit;
  }

  private static Parsed<BigDecimal> invalidAmount(String raw) {
    return Parsed.error(AMOUNT_INVALID, "\"%s\" is not an amount.".formatted(raw.strip()));
  }

  private static int count(String s, char c) {
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }

  // --- dates ---

  /**
   * A date: ISO {@code yyyy-MM-dd} always (what XLSX date cells become), else {@code dateFormat}
   * (strict). A purely numeric value is refused — never read as an Excel serial (FD-17).
   */
  public static Parsed<LocalDate> date(String raw, String dateFormat) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    String s = raw.strip();
    if (s.length() > MAX_DATE_LENGTH) {
      return Parsed.error(DATE_INVALID, "This value is too long to be a date.");
    }
    if (NUMERIC.matcher(s).matches()) {
      return Parsed.error(
          NUMERIC_IN_DATE, "Format this column as a date in Excel, or as text dd/mm/yyyy");
    }
    try {
      return Parsed.of(LocalDate.parse(s, ISO));
    } catch (DateTimeParseException ignored) {
      // fall through to the source's format
    }
    String format = dateFormat == null ? TakeOnParseOptions.DEFAULT_DATE_FORMAT : dateFormat;
    try {
      var formatter =
          new DateTimeFormatterBuilder()
              .parseCaseInsensitive()
              .appendPattern(format.replace('y', 'u'))
              .toFormatter(Locale.ENGLISH)
              .withResolverStyle(ResolverStyle.STRICT);
      return Parsed.of(LocalDate.parse(s, formatter));
    } catch (DateTimeParseException | IllegalArgumentException e) {
      return Parsed.error(
          DATE_INVALID,
          "\"%s\" is not a date in the format %s.".formatted(s, format.toLowerCase(Locale.ROOT)));
    }
  }

  // --- enums ---

  /**
   * An enum name: matched case-insensitively with spaces and hyphens read as underscores, else by a
   * documented alias (e.g. {@code S86(4)} → {@code CLIENT_INSTRUCTION}). Returns the enum name.
   */
  public static Parsed<String> enumValue(
      String raw, List<String> names, Map<String, String> aliases) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    String key = normaliseEnum(raw);
    for (String name : names) {
      if (normaliseEnum(name).equals(key)) {
        return Parsed.of(name);
      }
    }
    if (aliases != null) {
      for (var alias : aliases.entrySet()) {
        if (normaliseEnum(alias.getKey()).equals(key)) {
          return Parsed.of(alias.getValue());
        }
      }
    }
    String labels = names.stream().map(TakeOnValueParser::label).collect(Collectors.joining(", "));
    return Parsed.error(ENUM_INVALID, "%s is not one of: %s".formatted(raw.strip(), labels));
  }

  private static String normaliseEnum(String value) {
    return value.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
  }

  /** {@code CLIENT_INSTRUCTION} → {@code Client instruction} (no raw enum on screen). */
  private static String label(String name) {
    String words = name.toLowerCase(Locale.ROOT).replace('_', ' ');
    return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  // --- booleans, text, email ---

  /** {@code true/false/yes/no/y/n/1/0}, case-insensitive. */
  public static Parsed<Boolean> bool(String raw) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    return switch (raw.strip().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "y", "1" -> Parsed.of(Boolean.TRUE);
      case "false", "no", "n", "0" -> Parsed.of(Boolean.FALSE);
      default -> Parsed.error(BOOLEAN_INVALID, "\"%s\" is not yes or no.".formatted(raw.strip()));
    };
  }

  /** Trimmed text no longer than {@code maxLength}. */
  public static Parsed<String> text(String raw, int maxLength) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    String s = raw.strip();
    if (s.length() > maxLength) {
      return Parsed.error(
          TEXT_TOO_LONG, "This value is longer than %d characters.".formatted(maxLength));
    }
    return Parsed.of(s);
  }

  /** One {@code @}, no spaces, a dot in the domain. Returned trimmed, case kept. */
  public static Parsed<String> email(String raw) {
    if (raw == null || raw.isBlank()) {
      return Parsed.of(null);
    }
    String s = raw.strip();
    if (s.length() > MAX_EMAIL_LENGTH) {
      return Parsed.error(EMAIL_INVALID, "This email address is longer than 254 characters.");
    }
    int at = s.indexOf('@');
    if (!EMAIL.matcher(s).matches()
        || s.endsWith(".")
        || at + 1 >= s.length()
        || s.charAt(at + 1) == '.') {
      return Parsed.error(EMAIL_INVALID, "\"%s\" is not a valid email address.".formatted(s));
    }
    return Parsed.of(s);
  }

  /** The Excel error token a cell holds ({@code #REF!}, {@code #N/A} …), if any. */
  public static Optional<String> excelErrorCode(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String s = raw.strip().toUpperCase(Locale.ROOT);
    return EXCEL_ERROR_TOKENS.contains(s) ? Optional.of(s) : Optional.empty();
  }
}
