package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NameTokenResolverTest {

  private final NameTokenResolver resolver = new NameTokenResolver();

  // Helper: create a Customer with only the name field meaningful.
  private Customer customerWithName(String name) {
    return new Customer(name, null, null, null, null, UUID.randomUUID());
  }

  @Test
  void allTokensReplacedInCombinedPattern() {
    String pattern = "Bookkeeping - {customer} - {month} {year} ({period_start} to {period_end})";
    Customer customer = customerWithName("Acme Corp");
    LocalDate refDate = LocalDate.of(2026, 3, 1);
    LocalDate periodStart = LocalDate.of(2026, 3, 1);
    LocalDate periodEnd = LocalDate.of(2026, 3, 31);

    String result = resolver.resolveNameTokens(pattern, customer, refDate, periodStart, periodEnd);

    assertThat(result).isEqualTo("Bookkeeping - Acme Corp - March 2026 (2026-03-01 to 2026-03-31)");
  }

  @Test
  void customerToken_replacedWithCustomerName() {
    String pattern = "Project for {customer}";
    Customer customer = customerWithName("Beta Ltd");
    String result = resolver.resolveNameTokens(pattern, customer, null, null, null);
    assertThat(result).isEqualTo("Project for Beta Ltd");
  }

  @Test
  void monthToken_replacedWithFullEnglishMonthName() {
    String pattern = "Report for {month}";
    LocalDate refDate = LocalDate.of(2026, 3, 15);
    String result = resolver.resolveNameTokens(pattern, null, refDate, null, null);
    assertThat(result).isEqualTo("Report for March");
  }

  @Test
  void monthShortToken_replacedWith3LetterAbbreviation() {
    String pattern = "Report {month_short}";
    LocalDate refDate = LocalDate.of(2026, 3, 15);
    String result = resolver.resolveNameTokens(pattern, null, refDate, null, null);
    assertThat(result).isEqualTo("Report Mar");
  }

  @Test
  void yearToken_replacedWith4DigitYear() {
    String pattern = "Annual {year}";
    LocalDate refDate = LocalDate.of(2026, 1, 1);
    String result = resolver.resolveNameTokens(pattern, null, refDate, null, null);
    assertThat(result).isEqualTo("Annual 2026");
  }

  @Test
  void nullCustomer_leavesCustomerTokenUnreplaced() {
    String pattern = "Project for {customer}";
    String result = resolver.resolveNameTokens(pattern, null, null, null, null);
    assertThat(result).isEqualTo("Project for {customer}");
  }

  @Test
  void nullReferenceDate_leavesDateTokensUnreplaced() {
    String pattern = "{month} {month_short} {year}";
    String result = resolver.resolveNameTokens(pattern, null, null, null, null);
    assertThat(result).isEqualTo("{month} {month_short} {year}");
  }

  @Test
  void nullPeriodDates_leavesPeriodTokensUnreplaced() {
    String pattern = "{period_start} to {period_end}";
    String result = resolver.resolveNameTokens(pattern, null, null, null, null);
    assertThat(result).isEqualTo("{period_start} to {period_end}");
  }

  @Test
  void bothMonthAndMonthShort_resolvedCorrectly() {
    String pattern = "{month_short} ({month}) {year}";
    LocalDate refDate = LocalDate.of(2026, 3, 15);
    String result = resolver.resolveNameTokens(pattern, null, refDate, null, null);
    assertThat(result).isEqualTo("Mar (March) 2026");
  }

  @Test
  void nullPattern_throwsNullPointerException() {
    assertThatThrownBy(() -> resolver.resolveNameTokens(null, null, null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("pattern must not be null");
  }
}
