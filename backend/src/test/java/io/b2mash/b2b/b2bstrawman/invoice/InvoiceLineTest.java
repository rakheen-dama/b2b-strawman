package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceLineTest {

  private InvoiceLine buildLine() {
    return new InvoiceLine(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Test line",
        new BigDecimal("2.0000"),
        new BigDecimal("100.00"),
        0);
  }

  @Test
  void constructor_defaultsLineTypeToTime_andCalculatesAmount() {
    var line = buildLine();

    assertThat(line.getLineType()).isEqualTo(InvoiceLineType.TIME);
    assertThat(line.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    assertThat(line.getExpenseId()).isNull();
    assertThat(line.getCreatedAt()).isNotNull();
    assertThat(line.getUpdatedAt()).isNotNull();
  }

  @Test
  void setLineType_changesLineType() {
    var line = buildLine();

    line.setLineType(InvoiceLineType.EXPENSE);

    assertThat(line.getLineType()).isEqualTo(InvoiceLineType.EXPENSE);
  }

  @Test
  void setExpenseId_setsAndGetsExpenseId() {
    var line = buildLine();
    var expenseId = UUID.randomUUID();

    line.setExpenseId(expenseId);

    assertThat(line.getExpenseId()).isEqualTo(expenseId);
  }
}
