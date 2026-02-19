package io.b2mash.b2b.b2bstrawman.projecttemplate;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves name token patterns for project templates and recurring schedules. Uses simple
 * String.replace() substitution — no template engine. If a parameter is null, its token is left
 * unreplaced in the output string.
 */
@Component
public class NameTokenResolver {

  /**
   * Resolves tokens in a name pattern.
   *
   * <p>Supported tokens: {customer}, {month}, {month_short}, {year}, {period_start}, {period_end}
   *
   * @param pattern the name pattern with optional tokens, e.g. "Bookkeeping - {customer} - {month}
   *     {year}"
   * @param customer the customer whose name replaces {customer}; null leaves the token unreplaced
   * @param referenceDate date used for {month}, {month_short}, {year}; null leaves those tokens
   *     unreplaced
   * @param periodStart replaces {period_start} in ISO format; null leaves it unreplaced
   * @param periodEnd replaces {period_end} in ISO format; null leaves it unreplaced
   * @return the resolved name string
   */
  public String resolveNameTokens(
      String pattern,
      Customer customer,
      LocalDate referenceDate,
      LocalDate periodStart,
      LocalDate periodEnd) {
    Objects.requireNonNull(pattern, "pattern must not be null");
    String result = pattern;
    if (customer != null) {
      result = result.replace("{customer}", customer.getName());
    }
    if (referenceDate != null) {
      // Replace {month_short} before {month} for defensive ordering
      result =
          result.replace(
              "{month_short}",
              referenceDate.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
      result =
          result.replace(
              "{month}", referenceDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
      result = result.replace("{year}", String.valueOf(referenceDate.getYear()));
    }
    if (periodStart != null) {
      result =
          result.replace("{period_start}", periodStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
    if (periodEnd != null) {
      result = result.replace("{period_end}", periodEnd.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
    return result;
  }
}
