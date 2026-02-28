package io.b2mash.b2b.b2bstrawman.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpenseTest {

  private Expense createExpense() {
    return new Expense(
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDate.of(2026, 2, 15),
        "Court filing fee",
        new BigDecimal("150.00"),
        "ZAR",
        ExpenseCategory.FILING_FEE);
  }

  @Test
  void newExpense_defaultsBillableTrue() {
    Expense expense = createExpense();

    assertThat(expense.isBillable()).isTrue();
    assertThat(expense.getCreatedAt()).isNotNull();
    assertThat(expense.getUpdatedAt()).isNotNull();
  }

  // --- getBillingStatus tests ---

  @Test
  void getBillingStatus_whenBilled_returnsBilled() {
    Expense expense = createExpense();
    expense.markBilled(UUID.randomUUID());

    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.BILLED);
  }

  @Test
  void getBillingStatus_whenBillableFalse_returnsNonBillable() {
    Expense expense = createExpense();
    expense.writeOff();

    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.NON_BILLABLE);
  }

  @Test
  void getBillingStatus_whenBillableAndNoInvoice_returnsUnbilled() {
    Expense expense = createExpense();

    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.UNBILLED);
  }

  // --- getBillableAmount (no-arg) tests ---

  @Test
  void getBillableAmount_withMarkupAndBillable_returnsAmountWithMarkup() {
    Expense expense = createExpense();
    expense.setMarkupPercent(new BigDecimal("10.00"));

    BigDecimal result = expense.getBillableAmount();

    // 150.00 * (1 + 10/100) = 150.00 * 1.10 = 165.00
    assertThat(result).isEqualByComparingTo(new BigDecimal("165.00"));
  }

  @Test
  void getBillableAmount_withoutMarkupAndBillable_returnsRawAmount() {
    Expense expense = createExpense();
    // markupPercent is null by default

    BigDecimal result = expense.getBillableAmount();

    assertThat(result).isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void getBillableAmount_whenNonBillable_returnsZero() {
    Expense expense = createExpense();
    expense.setMarkupPercent(new BigDecimal("10.00"));
    expense.writeOff();

    BigDecimal result = expense.getBillableAmount();

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- computeBillableAmount (with org default) tests ---

  @Test
  void computeBillableAmount_usesPerExpenseMarkup() {
    Expense expense = createExpense();
    expense.setMarkupPercent(new BigDecimal("10.00"));

    BigDecimal result = expense.computeBillableAmount(new BigDecimal("20.00"));

    // Per-expense markup (10%) takes precedence over org default (20%)
    // 150.00 * (1 + 10/100) = 150.00 * 1.10 = 165.00
    assertThat(result).isEqualByComparingTo(new BigDecimal("165.00"));
  }

  @Test
  void computeBillableAmount_fallsBackToOrgDefault() {
    Expense expense = createExpense();
    // markupPercent is null, so org default applies

    BigDecimal result = expense.computeBillableAmount(new BigDecimal("15.00"));

    // 150.00 * (1 + 15/100) = 150.00 * 1.15 = 172.50
    assertThat(result).isEqualByComparingTo(new BigDecimal("172.50"));
  }

  @Test
  void computeBillableAmount_zeroWhenNonBillable() {
    Expense expense = createExpense();
    expense.writeOff();

    BigDecimal result = expense.computeBillableAmount(new BigDecimal("10.00"));

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- writeOff / restore tests ---

  @Test
  void writeOff_fromUnbilled_succeeds() {
    Expense expense = createExpense();

    expense.writeOff();

    assertThat(expense.isBillable()).isFalse();
    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.NON_BILLABLE);
  }

  @Test
  void writeOff_whenBilled_throws() {
    Expense expense = createExpense();
    expense.markBilled(UUID.randomUUID());

    assertThatThrownBy(expense::writeOff)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("billed");
  }

  @Test
  void restore_fromNonBillable_succeeds() {
    Expense expense = createExpense();
    expense.writeOff();

    expense.restore();

    assertThat(expense.isBillable()).isTrue();
    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.UNBILLED);
  }

  @Test
  void restore_whenAlreadyBillable_throws() {
    Expense expense = createExpense();

    assertThatThrownBy(expense::restore)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already billable");
  }

  // --- update tests ---

  @Test
  void update_whenBilled_throws() {
    Expense expense = createExpense();
    expense.markBilled(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                expense.update(
                    LocalDate.of(2026, 3, 1),
                    "Updated description",
                    new BigDecimal("200.00"),
                    "ZAR",
                    ExpenseCategory.TRAVEL,
                    null,
                    null,
                    null,
                    true,
                    null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("billed");
  }

  // --- markBilled / unbill tests ---

  @Test
  void markBilled_fromUnbilled_succeeds() {
    Expense expense = createExpense();
    UUID invoiceId = UUID.randomUUID();

    expense.markBilled(invoiceId);

    assertThat(expense.getInvoiceId()).isEqualTo(invoiceId);
    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.BILLED);
  }

  @Test
  void markBilled_withNullInvoiceId_throws() {
    Expense expense = createExpense();

    assertThatThrownBy(() -> expense.markBilled(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invoice ID must not be null");
  }

  @Test
  void markBilled_whenAlreadyBilled_throws() {
    Expense expense = createExpense();
    expense.markBilled(UUID.randomUUID());

    assertThatThrownBy(() -> expense.markBilled(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already billed");
  }

  @Test
  void markBilled_whenNonBillable_throws() {
    Expense expense = createExpense();
    expense.writeOff();

    assertThatThrownBy(() -> expense.markBilled(UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-billable");
  }

  @Test
  void unbill_fromBilled_succeeds() {
    Expense expense = createExpense();
    expense.markBilled(UUID.randomUUID());

    expense.unbill();

    assertThat(expense.getInvoiceId()).isNull();
    assertThat(expense.getBillingStatus()).isEqualTo(ExpenseBillingStatus.UNBILLED);
  }

  @Test
  void unbill_whenNotBilled_throws() {
    Expense expense = createExpense();

    assertThatThrownBy(expense::unbill)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not billed");
  }

  // --- category enum test ---

  @Test
  void getCategory_returnsExpenseCategoryEnum() {
    Expense expense = createExpense();

    assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.FILING_FEE);
  }
}
