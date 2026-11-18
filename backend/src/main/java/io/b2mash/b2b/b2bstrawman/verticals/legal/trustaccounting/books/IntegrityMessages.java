package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How trust figures and dates are written in sentences for the firm — integrity flags, refusals,
 * the monthly list and the trust reports (extracted from {@link TrustIntegrityService} in 650A so
 * the monthly list and the report queries write the same words).
 */
public final class IntegrityMessages {

  private static final Locale TRUST_LOCALE = VariableFormatter.resolveLocale("ZAR");

  /** How a date is written in a sentence ("31 March 2026"). */
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  /** How a month is written in a sentence ("March 2026"). */
  private static final DateTimeFormatter MONTH_YEAR_FORMAT =
      DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

  private IntegrityMessages() {}

  /** A rand amount in the house en-ZA format ("R10 055,21"; G89-F40). */
  public static String rand(BigDecimal amount) {
    return VariableFormatter.formatCurrencyText(amount, TRUST_LOCALE);
  }

  /** "31 March 2026". */
  public static String date(LocalDate date) {
    return date.format(DATE_FORMAT);
  }

  /** "March 2026". */
  public static String monthYear(LocalDate date) {
    return date.format(MONTH_YEAR_FORMAT);
  }

  /** An unidentified deposit's status in words (never the raw value). */
  public static String statusWords(String status) {
    return switch (status) {
      case "HELD" -> "held";
      case "ALLOCATED" -> "allocated to a client";
      case "PAID_TO_FUND" -> "paid to the Fund";
      case "REVERSED" -> "reversed";
      default -> "resolved";
    };
  }
}
