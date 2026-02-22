package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetainerAgreementEntityTest {

  // --- RetainerAgreement tests ---

  private RetainerAgreement buildAgreement() {
    return new RetainerAgreement(
        UUID.randomUUID(),
        "Monthly Retainer",
        RetainerType.HOUR_BANK,
        RetainerFrequency.MONTHLY,
        LocalDate.of(2026, 1, 1),
        null,
        new BigDecimal("40.00"),
        new BigDecimal("20000.00"),
        RolloverPolicy.FORFEIT,
        null,
        null,
        UUID.randomUUID());
  }

  @Test
  void create_statusIsActive() {
    var agreement = buildAgreement();
    assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.ACTIVE);
  }

  @Test
  void create_rolloverPolicyDefaultsToForfeit_whenNull() {
    var agreement =
        new RetainerAgreement(
            UUID.randomUUID(),
            "Monthly Retainer",
            RetainerType.HOUR_BANK,
            RetainerFrequency.MONTHLY,
            LocalDate.of(2026, 1, 1),
            null,
            new BigDecimal("40.00"),
            new BigDecimal("20000.00"),
            null, // null rolloverPolicy
            null,
            null,
            UUID.randomUUID());
    assertThat(agreement.getRolloverPolicy()).isEqualTo(RolloverPolicy.FORFEIT);
  }

  @Test
  void pause_fromActive_setsStatusToPaused() {
    var agreement = buildAgreement();

    agreement.pause();

    assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.PAUSED);
  }

  @Test
  void pause_fromNonActive_throws() {
    var agreement = buildAgreement();
    agreement.pause(); // now PAUSED

    assertThatThrownBy(agreement::pause)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only active retainers can be paused");
  }

  @Test
  void resume_fromPaused_setsStatusToActive() {
    var agreement = buildAgreement();
    agreement.pause();

    agreement.resume();

    assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.ACTIVE);
  }

  @Test
  void resume_fromNonPaused_throws() {
    var agreement = buildAgreement(); // starts ACTIVE

    assertThatThrownBy(agreement::resume)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only paused retainers can be resumed");
  }

  @Test
  void terminate_fromActive_setsStatusToTerminated() {
    var agreement = buildAgreement();

    agreement.terminate();

    assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.TERMINATED);
  }

  @Test
  void terminate_fromPaused_setsStatusToTerminated() {
    var agreement = buildAgreement();
    agreement.pause();

    agreement.terminate();

    assertThat(agreement.getStatus()).isEqualTo(RetainerStatus.TERMINATED);
  }

  @Test
  void terminate_alreadyTerminated_throws() {
    var agreement = buildAgreement();
    agreement.terminate();

    assertThatThrownBy(agreement::terminate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Retainer is already terminated");
  }

  @Test
  void updateTerms_setsAllFields() {
    var agreement = buildAgreement();
    var endDate = LocalDate.of(2026, 12, 31);

    agreement.updateTerms(
        "Updated Name",
        new BigDecimal("50.00"),
        new BigDecimal("25000.00"),
        RolloverPolicy.CARRY_FORWARD,
        new BigDecimal("10.00"),
        endDate,
        "Updated notes");

    assertThat(agreement.getName()).isEqualTo("Updated Name");
    assertThat(agreement.getAllocatedHours()).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(agreement.getPeriodFee()).isEqualByComparingTo(new BigDecimal("25000.00"));
    assertThat(agreement.getRolloverPolicy()).isEqualTo(RolloverPolicy.CARRY_FORWARD);
    assertThat(agreement.getRolloverCapHours()).isEqualByComparingTo(new BigDecimal("10.00"));
    assertThat(agreement.getEndDate()).isEqualTo(endDate);
    assertThat(agreement.getNotes()).isEqualTo("Updated notes");
    assertThat(agreement.getUpdatedAt()).isNotNull();
  }

  // --- RetainerPeriod tests ---

  private RetainerPeriod buildPeriod() {
    return new RetainerPeriod(
        UUID.randomUUID(),
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 4, 1),
        new BigDecimal("40.00"),
        new BigDecimal("40.00"),
        BigDecimal.ZERO);
  }

  @Test
  void updateConsumption_recalculatesRemainingHours() {
    var period = buildPeriod(); // allocatedHours = 40

    period.updateConsumption(new BigDecimal("32.50"));

    assertThat(period.getConsumedHours()).isEqualByComparingTo(new BigDecimal("32.50"));
    assertThat(period.getRemainingHours()).isEqualByComparingTo(new BigDecimal("7.50"));
  }

  @Test
  void close_setsStatusAndFields() {
    var period = buildPeriod();
    UUID invoiceId = UUID.randomUUID();
    UUID closedBy = UUID.randomUUID();

    period.close(invoiceId, closedBy, new BigDecimal("5.00"), BigDecimal.ZERO);

    assertThat(period.getStatus()).isEqualTo(PeriodStatus.CLOSED);
    assertThat(period.getInvoiceId()).isEqualTo(invoiceId);
    assertThat(period.getClosedBy()).isEqualTo(closedBy);
    assertThat(period.getClosedAt()).isNotNull();
    assertThat(period.getOverageHours()).isEqualByComparingTo(new BigDecimal("5.00"));
    assertThat(period.getRolloverHoursOut()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
