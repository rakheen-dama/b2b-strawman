package io.b2mash.b2b.b2bstrawman.timeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TimeEntry billing domain methods (markBilled, markUnbilled, isBilled, isLocked).
 */
class TimeEntryBillingMethodsTest {

  private static final UUID TASK_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID INVOICE_ID = UUID.randomUUID();

  @Test
  void markBilled_happyPath_setsInvoiceIdAndUpdatesTimestamp() {
    var entry = createBillableEntry();
    Instant beforeMark = entry.getUpdatedAt();

    entry.markBilled(INVOICE_ID);

    assertThat(entry.getInvoiceId()).isEqualTo(INVOICE_ID);
    assertThat(entry.isBilled()).isTrue();
    assertThat(entry.isLocked()).isTrue();
    assertThat(entry.getUpdatedAt()).isAfterOrEqualTo(beforeMark);
  }

  @Test
  void markBilled_onNonBillableEntry_throwsInvalidStateException() {
    var entry = createNonBillableEntry();

    assertThatThrownBy(() -> entry.markBilled(INVOICE_ID))
        .isInstanceOf(InvalidStateException.class)
        .satisfies(
            ex -> {
              var ise = (InvalidStateException) ex;
              assertThat(ise.getBody().getDetail()).contains("non-billable");
            });
  }

  @Test
  void markBilled_onAlreadyBilledEntry_throwsInvalidStateException() {
    var entry = createBillableEntry();
    entry.markBilled(INVOICE_ID);

    UUID anotherInvoiceId = UUID.randomUUID();
    assertThatThrownBy(() -> entry.markBilled(anotherInvoiceId))
        .isInstanceOf(InvalidStateException.class)
        .satisfies(
            ex -> {
              var ise = (InvalidStateException) ex;
              assertThat(ise.getBody().getDetail()).contains("already billed");
            });
  }

  @Test
  void markBilled_onAlreadyBilledEntry_doesNotLeakInvoiceId() {
    var entry = createBillableEntry();
    entry.markBilled(INVOICE_ID);

    UUID anotherInvoiceId = UUID.randomUUID();
    assertThatThrownBy(() -> entry.markBilled(anotherInvoiceId))
        .isInstanceOf(InvalidStateException.class)
        .satisfies(
            ex -> {
              var ise = (InvalidStateException) ex;
              assertThat(ise.getBody().getDetail()).doesNotContain(INVOICE_ID.toString());
            });
  }

  @Test
  void markUnbilled_clearsInvoiceReferenceAndUpdatesTimestamp() {
    var entry = createBillableEntry();
    entry.markBilled(INVOICE_ID);
    assertThat(entry.isBilled()).isTrue();

    Instant beforeUnbill = entry.getUpdatedAt();
    entry.markUnbilled();

    assertThat(entry.getInvoiceId()).isNull();
    assertThat(entry.isBilled()).isFalse();
    assertThat(entry.isLocked()).isFalse();
    assertThat(entry.getUpdatedAt()).isAfterOrEqualTo(beforeUnbill);
  }

  @Test
  void isLocked_returnsTrueWhenBilled() {
    var entry = createBillableEntry();
    assertThat(entry.isLocked()).isFalse();

    entry.markBilled(INVOICE_ID);
    assertThat(entry.isLocked()).isTrue();
  }

  @Test
  void isBilled_returnsFalseForNewEntry() {
    var entry = createBillableEntry();
    assertThat(entry.isBilled()).isFalse();
  }

  // --- helpers ---

  private TimeEntry createBillableEntry() {
    return new TimeEntry(
        TASK_ID, MEMBER_ID, LocalDate.of(2024, 6, 15), 60, true, null, "Test work");
  }

  private TimeEntry createNonBillableEntry() {
    return new TimeEntry(
        TASK_ID, MEMBER_ID, LocalDate.of(2024, 6, 15), 30, false, null, "Internal work");
  }
}
