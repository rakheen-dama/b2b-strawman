package io.b2mash.b2b.b2bstrawman.template;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Type-aware value formatter for template variable rendering. Formats currency, date, and number
 * values based on type hints from {@link VariableMetadataRegistry}.
 */
public final class VariableFormatter {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  private VariableFormatter() {}

  private static final Locale ZA_LOCALE = Locale.of("en", "ZA");

  /** The only {@code data:} form an "image" value may take: base64 PNG/JPEG (626D, R-1). */
  private static final Pattern DATA_IMAGE_URI =
      Pattern.compile("^data:image/(png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$");

  /**
   * Formats a value based on its type hint and locale.
   *
   * @param value the raw value to format
   * @param typeHint the type hint ("currency", "date", "number", or null for default)
   * @param locale the locale for currency/number formatting; falls back to ZA if null
   * @return HTML-escaped formatted string, or empty string for null values
   */
  public static String format(Object value, String typeHint, Locale locale) {
    if (value == null) return "";
    if (typeHint == null) return HtmlUtils.htmlEscape(String.valueOf(value));

    Locale effectiveLocale = locale != null ? locale : ZA_LOCALE;
    return switch (typeHint) {
      case "currency" -> formatCurrency(value, effectiveLocale);
      case "date" -> formatDate(value);
      case "number" -> formatNumber(value, effectiveLocale);
      case "image" -> formatImage(value);
      default -> HtmlUtils.htmlEscape(String.valueOf(value));
    };
  }

  /**
   * Formats a value based on its type hint using the default ZA locale.
   *
   * @param value the raw value to format
   * @param typeHint the type hint ("currency", "date", "number", or null for default)
   * @return HTML-escaped formatted string, or empty string for null values
   */
  public static String format(Object value, String typeHint) {
    return format(value, typeHint, ZA_LOCALE);
  }

  /**
   * Resolves a currency code to the appropriate locale for formatting.
   *
   * @param currencyCode ISO 4217 currency code (e.g., "ZAR", "USD", "GBP", "EUR")
   * @return the locale for formatting; defaults to en-ZA if code is null or unrecognized
   */
  public static Locale resolveLocale(String currencyCode) {
    if (currencyCode == null) return ZA_LOCALE;
    return switch (currencyCode) {
      case "ZAR" -> ZA_LOCALE;
      case "USD" -> Locale.US;
      case "GBP" -> Locale.UK;
      case "EUR" -> Locale.GERMANY;
      default -> ZA_LOCALE;
    };
  }

  /**
   * Formats a currency amount as <b>plain text</b> (not HTML-escaped) for non-HTML sinks such as
   * the activity feed, whose strings React renders verbatim. Same locale/NumberFormat path as the
   * document renderer's {@code format(value, "currency", locale)}: en-ZA gives {@code R50 000,00}
   * with the JDK's U+00A0 grouping separator.
   *
   * @param value the raw amount (BigDecimal, Number or numeric String)
   * @param locale the formatting locale; falls back to ZA if null
   * @return the formatted amount, {@code String.valueOf(value)} if it is not numeric, or {@code ""}
   *     for null
   */
  public static String formatCurrencyText(Object value, Locale locale) {
    if (value == null) return "";
    try {
      BigDecimal amount = new BigDecimal(String.valueOf(value));
      // NumberFormat is not thread-safe, so create a new instance each time
      NumberFormat currencyFormat =
          NumberFormat.getCurrencyInstance(locale != null ? locale : ZA_LOCALE);
      return currencyFormat.format(amount);
    } catch (NumberFormatException e) {
      return String.valueOf(value);
    }
  }

  private static String formatCurrency(Object value, Locale locale) {
    return HtmlUtils.htmlEscape(formatCurrencyText(value, locale));
  }

  /**
   * Formats a date as <b>plain text</b> (not HTML-escaped) for non-HTML sinks such as the activity
   * feed. Same {@code "d MMMM yyyy"} pattern as the document renderer's {@code format(value,
   * "date")}, which wraps this method: a {@link LocalDate}, an ISO date ({@code 2026-03-08}) or an
   * ISO instant ({@code 2026-03-08T14:30:00Z}, taken in UTC) gives {@code 8 March 2026}.
   *
   * @param value the raw date (LocalDate, ISO date String or ISO instant String)
   * @return the formatted date, {@code String.valueOf(value)} if it is not parsable, or {@code ""}
   *     for null
   */
  public static String formatDateText(Object value) {
    if (value == null) return "";
    if (value instanceof LocalDate date) return date.format(DATE_FORMAT);
    try {
      String str = String.valueOf(value);
      if (str.contains("T")) {
        Instant instant = Instant.parse(str);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.format(DATE_FORMAT);
      } else {
        LocalDate date = LocalDate.parse(str);
        return date.format(DATE_FORMAT);
      }
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  /**
   * Document variant: {@link #formatDateText} HTML-escaped (a formatted date escapes to itself).
   */
  private static String formatDate(Object value) {
    return HtmlUtils.htmlEscape(formatDateText(value));
  }

  /**
   * Renders an {@code <img>} element for the "image" type hint (letterhead logo, LZKC-007). The
   * value is expected to be server-generated ({@code org.logoUrl} from {@code
   * TemplateContextHelper.buildOrgContext}): an {@link InlineLogo} (626D — rendered as its lazily
   * downloaded PNG/JPEG {@code data:} URI, or its presigned URL when the bytes are unavailable) or
   * a presigned http(s) URL string. Whatever the source, the result must be a strict PNG/JPEG
   * base64 {@code data:} URI or an http(s) URL; anything else renders as an empty string so a
   * missing/odd value degrades to "no logo" instead of broken markup. The value is
   * HTML-attribute-escaped; sizing comes from the {@code letterhead-logo} class in
   * document-default.css.
   */
  private static String formatImage(Object value) {
    String url = (value instanceof InlineLogo logo ? logo.src() : String.valueOf(value)).trim();
    if (!(url.startsWith("https://")
        || url.startsWith("http://")
        || DATA_IMAGE_URI.matcher(url).matches())) {
      return "";
    }
    return "<img class=\"letterhead-logo\" src=\""
        + HtmlUtils.htmlEscape(url)
        + "\" alt=\"Logo\"/>";
  }

  private static String formatNumber(Object value, Locale locale) {
    try {
      BigDecimal num = new BigDecimal(String.valueOf(value));
      return HtmlUtils.htmlEscape(NumberFormat.getInstance(locale).format(num));
    } catch (NumberFormatException e) {
      return HtmlUtils.htmlEscape(String.valueOf(value));
    }
  }
}
