package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Phase 93 / 653A (C-15): every {@link TakeOnBatch} transition, legal and illegal, as a plain unit
 * test — {@code COMMITTING} / {@code COMPLETED} are not reachable through the 653A endpoints.
 */
class TakeOnBatchTest {

  private static final UUID MEMBER = UUID.randomUUID();
  private static final String HASH = "a".repeat(64);
  private static final TakeOnBatch.Counts COUNTS = new TakeOnBatch.Counts(3, 2, 1, 0, 0);

  // --- builders for each status ---

  private static TakeOnBatch uploaded(TakeOnDataset dataset) {
    UUID account = dataset.requiresTrustAccount() ? UUID.randomUUID() : null;
    return new TakeOnBatch(dataset, account, "Batch", MEMBER);
  }

  private static TakeOnBatch uploaded() {
    return uploaded(TakeOnDataset.CLIENTS);
  }

  private static TakeOnBatch mapped() {
    var batch = uploaded();
    batch.markMapped();
    return batch;
  }

  private static TakeOnBatch validated() {
    var batch = mapped();
    batch.markValidated(HASH, COUNTS, Map.of("total", 3));
    return batch;
  }

  private static TakeOnBatch committing() {
    var batch = validated();
    batch.claimCommit(HASH, TakeOnSignOff.notRequired(HASH), MEMBER);
    return batch;
  }

  private static TakeOnBatch completed() {
    var batch = committing();
    batch.complete(2, 1, 0);
    return batch;
  }

  private static TakeOnBatch cancelled() {
    var batch = uploaded();
    batch.cancel(MEMBER);
    return batch;
  }

  private static TakeOnBatch inStatus(TakeOnBatchStatus status) {
    return switch (status) {
      case UPLOADED -> uploaded();
      case MAPPED -> mapped();
      case VALIDATED -> validated();
      case COMMITTING -> committing();
      case COMPLETED -> completed();
      case CANCELLED -> cancelled();
    };
  }

  private static TakeOnSignOff liveSignOff(String hash) {
    return new TakeOnSignOff(
        UUID.randomUUID(),
        TakeOnSignOffKind.FICA_ATTESTATION,
        MEMBER,
        "Alice",
        "Owner",
        "FICA-ATTEST-v1",
        "I attest.",
        hash,
        null);
  }

  // --- construction ---

  @Test
  void newBatch_isUploadedWithZeroedPositionDefaults() {
    var batch = uploaded();
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.UPLOADED);
    assertThat(batch.getCreatedAt()).isNotNull().isEqualTo(batch.getUpdatedAt());
    assertThat(batch.getFundLedgerBalance()).isEqualByComparingTo("0");
    assertThat(batch.getInvestedTotal()).isEqualByComparingTo("0");
    assertThat(batch.isMidMonthAcknowledged()).isFalse();
  }

  // --- markMapped ---

  @Test
  void uploaded_toMapped() {
    var batch = uploaded();
    batch.markMapped();
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.MAPPED);
  }

  @Test
  void mapped_toMapped_isANoOp() {
    var batch = mapped();
    var before = batch.getUpdatedAt();
    batch.markMapped();
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.MAPPED);
    assertThat(batch.getUpdatedAt()).isEqualTo(before);
  }

  @Test
  void validated_toMapped_whenMappingChanges() {
    var batch = validated();
    batch.markMapped();
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.MAPPED);
  }

  // --- returnToUploaded ---

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"UPLOADED", "MAPPED", "VALIDATED"})
  void openBatch_returnsToUploaded(TakeOnBatchStatus status) {
    var batch = inStatus(status);
    batch.returnToUploaded();
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.UPLOADED);
  }

  // --- markValidated / requireValidatable ---

  @Test
  void mapped_toValidated_recordsHashCountsAndSummary() {
    var batch = mapped();
    batch.markValidated(HASH, new TakeOnBatch.Counts(10, 7, 3, 2, 1), Map.of("rows", 10));
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
    assertThat(batch.getValidationHash()).isEqualTo(HASH);
    assertThat(batch.getValidatedAt()).isNotNull();
    assertThat(batch.getTotalRows()).isEqualTo(10);
    assertThat(batch.getValidRows()).isEqualTo(7);
    assertThat(batch.getErrorRows()).isEqualTo(3);
    assertThat(batch.getDuplicateRows()).isEqualTo(2);
    assertThat(batch.getBlockCount()).isEqualTo(1);
    assertThat(batch.getSummary()).containsEntry("rows", 10);
  }

  @Test
  void validated_toValidated_onRevalidation() {
    var batch = validated();
    var newHash = "b".repeat(64);
    batch.markValidated(newHash, COUNTS, null);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
    assertThat(batch.getValidationHash()).isEqualTo(newHash);
  }

  @Test
  void uploaded_cannotBeValidated() {
    var batch = uploaded();
    assertThatThrownBy(() -> batch.markValidated(HASH, COUNTS, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only a mapped or validated take-on batch can be validated.");
  }

  @ParameterizedTest
  @EnumSource(
      value = TakeOnDataset.class,
      names = {"TRUST_POSITION", "DEBTOR_BALANCES"})
  void trustAndDebtorBatches_needACutoverDateToValidate(TakeOnDataset dataset) {
    var batch = uploaded(dataset);
    batch.markMapped();
    assertThat(batch.missingCutoverDate()).isTrue();
    batch.requireValidatable(); // the state alone is fine: the date is the dry run's own check
    assertThatThrownBy(() -> batch.markValidated(HASH, COUNTS, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Enter the cutover date before validating.");

    batch.setCutoverDate(LocalDate.of(2026, 10, 31));
    assertThat(batch.missingCutoverDate()).isFalse();
    batch.markValidated(HASH, COUNTS, null);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
  }

  // --- setCutoverDate ---

  @Test
  void changingTheCutoverDate_onAValidatedBatch_returnsItToMapped() {
    var batch = uploaded(TakeOnDataset.DEBTOR_BALANCES);
    batch.setCutoverDate(LocalDate.of(2026, 10, 31));
    batch.markMapped();
    batch.markValidated(HASH, COUNTS, null);

    batch.setCutoverDate(LocalDate.of(2026, 10, 31));
    assertThat(batch.getStatus())
        .as("same date is not a change")
        .isEqualTo(TakeOnBatchStatus.VALIDATED);

    batch.setCutoverDate(LocalDate.of(2026, 9, 30));
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.MAPPED);
    assertThat(batch.getCutoverDate()).isEqualTo(LocalDate.of(2026, 9, 30));
  }

  // --- claimCommit ---

  @Test
  void validated_toCommitting_withMatchingHashAndLiveSignOff() {
    var batch = validated();
    var requester = UUID.randomUUID();
    batch.claimCommit(HASH, liveSignOff(HASH), requester);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.COMMITTING);
    assertThat(batch.getCommitRequestedBy()).isEqualTo(requester);
    assertThat(batch.getCommitStartedAt()).isNotNull();
  }

  @Test
  void claimCommit_refusesAHashThatDiffersFromTheBatch() {
    var batch = validated();
    var other = "c".repeat(64);
    assertThatThrownBy(() -> batch.claimCommit(other, liveSignOff(other), MEMBER))
        .isInstanceOf(IllegalStateException.class);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
  }

  @Test
  void claimCommit_refusesASignOffOnAnotherHash() {
    var batch = validated();
    assertThatThrownBy(() -> batch.claimCommit(HASH, liveSignOff("d".repeat(64)), MEMBER))
        .isInstanceOf(IllegalStateException.class);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
  }

  @Test
  void claimCommit_refusesARevokedSignOff() {
    var batch = validated();
    var signOff = liveSignOff(HASH);
    signOff.revoke(TakeOnSignOff.RevokedReason.REVOKED);
    assertThatThrownBy(() -> batch.claimCommit(HASH, signOff, MEMBER))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"UPLOADED", "MAPPED", "COMMITTING", "COMPLETED", "CANCELLED"})
  void claimCommit_onlyFromValidated(TakeOnBatchStatus status) {
    var batch = inStatus(status);
    assertThatThrownBy(() -> batch.claimCommit(HASH, TakeOnSignOff.notRequired(HASH), MEMBER))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void notRequired_carriesOnlyTheHashAndIsLive() {
    var stand = TakeOnSignOff.notRequired(HASH);
    assertThat(stand.getValidationHash()).isEqualTo(HASH);
    assertThat(stand.isLive()).isTrue();
    assertThat(stand.getId()).isNull();
    assertThat(stand.getBatchId()).isNull();
    assertThat(stand.getKind()).isNull();
  }

  // --- complete / returnToValidated ---

  @Test
  void committing_toCompleted() {
    var batch = committing();
    batch.complete(5, 2, 1);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.COMPLETED);
    assertThat(batch.getCreatedRows()).isEqualTo(5);
    assertThat(batch.getSkippedRows()).isEqualTo(2);
    assertThat(batch.getFailedRows()).isEqualTo(1);
    assertThat(batch.getCompletedAt()).isNotNull();
  }

  @Test
  void committing_toValidated_withTheTruncatedError() {
    var batch = committing();
    batch.returnToValidated("x".repeat(1500));
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.VALIDATED);
    assertThat(batch.getLastError()).hasSize(1000);
  }

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"UPLOADED", "MAPPED", "VALIDATED", "COMPLETED", "CANCELLED"})
  void completeAndReturnToValidated_onlyFromCommitting(TakeOnBatchStatus status) {
    assertThatThrownBy(() -> inStatus(status).complete(1, 0, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> inStatus(status).returnToValidated("boom"))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- cancel ---

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"UPLOADED", "MAPPED", "VALIDATED"})
  void openBatch_canBeCancelled(TakeOnBatchStatus status) {
    var batch = inStatus(status);
    batch.cancel(MEMBER);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.CANCELLED);
    assertThat(batch.getCancelledBy()).isEqualTo(MEMBER);
    assertThat(batch.getCancelledAt()).isNotNull();
  }

  @Test
  void purgeJob_cancelsWithoutAMember() {
    var batch = mapped();
    batch.cancel(null);
    assertThat(batch.getStatus()).isEqualTo(TakeOnBatchStatus.CANCELLED);
    assertThat(batch.getCancelledBy()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"COMMITTING", "COMPLETED", "CANCELLED"})
  void closedOrCommittingBatch_cannotBeCancelled(TakeOnBatchStatus status) {
    assertThatThrownBy(() -> inStatus(status).cancel(MEMBER))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- terminal statuses refuse every transition ---

  @ParameterizedTest
  @EnumSource(
      value = TakeOnBatchStatus.class,
      names = {"COMPLETED", "CANCELLED"})
  void terminalBatch_refusesEveryTransition(TakeOnBatchStatus status) {
    List<Consumer<TakeOnBatch>> transitions =
        List.of(
            TakeOnBatch::markMapped,
            TakeOnBatch::returnToUploaded,
            TakeOnBatch::requireValidatable,
            b -> b.markValidated(HASH, COUNTS, null),
            b -> b.setCutoverDate(LocalDate.of(2026, 1, 31)),
            b -> b.claimCommit(HASH, TakeOnSignOff.notRequired(HASH), MEMBER),
            b -> b.complete(1, 0, 0),
            b -> b.returnToValidated("x"),
            b -> b.cancel(MEMBER));
    for (var transition : transitions) {
      var batch = inStatus(status);
      assertThatThrownBy(() -> transition.accept(batch)).isInstanceOf(IllegalStateException.class);
      assertThat(batch.getStatus()).isEqualTo(status);
    }
  }

  @Test
  void committingBatch_refusesMappingAndSourceChanges() {
    assertThatThrownBy(() -> committing().markMapped()).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> committing().returnToUploaded())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> committing().setCutoverDate(LocalDate.of(2026, 1, 31)))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- sign-off revoke ---

  @Test
  void signOff_revokesOnce() {
    var signOff = liveSignOff(HASH);
    signOff.revoke(TakeOnSignOff.RevokedReason.REVALIDATED);
    assertThat(signOff.isLive()).isFalse();
    assertThat(signOff.getRevokedReason()).isEqualTo(TakeOnSignOff.RevokedReason.REVALIDATED);
    assertThatThrownBy(() -> signOff.revoke(TakeOnSignOff.RevokedReason.CANCELLED))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- rows ---

  @Test
  void row_resetForValidation_refusesATerminalRow() throws Exception {
    var row = newRow();
    var status = TakeOnRow.class.getDeclaredField("status");
    status.setAccessible(true);
    status.set(row, TakeOnRowStatus.CREATED); // only a commit (655A) sets a terminal status
    assertThatThrownBy(row::resetForValidation).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void row_applyValidation_refusesABlockWithoutAnError() {
    var row = newRow();
    assertThatThrownBy(
            () -> row.applyValidation(Map.of(), "row:2", null, List.of(), List.of(), true, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void row_applyValidation_errorIffErrors_andResetClearsIt() {
    var row = newRow();
    row.applyValidation(
        Map.of("name", "A"),
        "row:2",
        "email:a@example.test",
        List.of(Map.of("code", "REQUIRED", "field", "email", "message", "Email is required.")),
        List.of(),
        true,
        null);
    assertThat(row.getStatus()).isEqualTo(TakeOnRowStatus.ERROR);
    assertThat(row.isBlock()).isTrue();

    row.resetForValidation();
    assertThat(row.getStatus()).isEqualTo(TakeOnRowStatus.PENDING);
    assertThat(row.getErrors()).isEmpty();
    assertThat(row.isBlock()).isFalse();
    assertThat(row.getDedupKey()).isNull();

    row.applyValidation(Map.of("name", "A"), "row:2", null, List.of(), List.of(), false, null);
    assertThat(row.getStatus()).isEqualTo(TakeOnRowStatus.VALIDATED);

    row.addError("DUPLICATE_IN_FILE", "email", "Duplicate of row 14 in this file");
    assertThat(row.getStatus()).isEqualTo(TakeOnRowStatus.ERROR);
    assertThat(row.getErrors()).hasSize(1);
  }

  private static TakeOnRow newRow() {
    return new TakeOnRow(
        UUID.randomUUID(),
        UUID.randomUUID(),
        TakeOnDataset.CLIENTS,
        TakeOnSection.MAIN,
        2,
        Map.of("Name", "A"));
  }
}
