package io.b2mash.b2b.b2bstrawman.template;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

  private static String formatCurrency(Object value, Locale locale) {
    try {
      BigDecimal amount = new BigDecimal(String.valueOf(value));
      // NumberFormat is not thread-safe, so create a new instance each time
      NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
      return HtmlUtils.htmlEscape(currencyFormat.format(amount));
    } catch (NumberFormatException e) {
      return HtmlUtils.htmlEscape(String.valueOf(value));
    }
  }

  private static String formatDate(Object value) {
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
      return HtmlUtils.htmlEscape(String.valueOf(value));
    }
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
