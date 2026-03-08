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

  /**
   * Formats a value based on its type hint.
   *
   * @param value the raw value to format
   * @param typeHint the type hint ("currency", "date", "number", or null for default)
   * @return HTML-escaped formatted string, or empty string for null values
   */
  public static String format(Object value, String typeHint) {
    if (value == null) return "";
    if (typeHint == null) return HtmlUtils.htmlEscape(String.valueOf(value));

    return switch (typeHint) {
      case "currency" -> formatCurrency(value);
      case "date" -> formatDate(value);
      case "number" -> formatNumber(value);
      default -> HtmlUtils.htmlEscape(String.valueOf(value));
    };
  }

  // TODO: Support multi-currency via OrgSettings.defaultCurrency / invoice.currency (ADR-041)
  private static final Locale ZA_LOCALE = Locale.of("en", "ZA");

  private static String formatCurrency(Object value) {
    try {
      BigDecimal amount = new BigDecimal(String.valueOf(value));
      // NumberFormat is not thread-safe, so create a new instance each time
      // Default to South African Rand formatting (ZAR / "R" prefix)
      NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(ZA_LOCALE);
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

  private static String formatNumber(Object value) {
    try {
      BigDecimal num = new BigDecimal(String.valueOf(value));
      return HtmlUtils.htmlEscape(NumberFormat.getInstance(ZA_LOCALE).format(num));
    } catch (NumberFormatException e) {
      return HtmlUtils.htmlEscape(String.valueOf(value));
    }
  }
}
