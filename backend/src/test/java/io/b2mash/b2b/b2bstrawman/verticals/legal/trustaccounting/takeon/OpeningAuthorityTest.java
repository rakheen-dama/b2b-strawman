package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Phase 93 / 657A: {@link OpeningAuthority#forBatch} — the only production way to the authority
 * {@code TrustPostingService.recordOpening} needs — refuses everything but a trust-position batch
 * with its cutover date and that batch's live trust sign-off bound to its current validation hash.
 * Plain unit test over in-memory entities (the batch id is set as JPA would on save).
 */
class OpeningAuthorityTest {

  private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID MAKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SIGNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final LocalDate CUTOVER = LocalDate.of(2026, 7, 31);
  private static final String HASH = "a".repeat(64);
  private static final String REFUSED_SIGN_OFF = "Opening entries need the batch's live trust";

  @Test
  void liveTrustSignOffOfTheBatch_givesTheAuthority_signerAndTimeFromTheSignOff() {
    var batch = validatedBatch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);

    var authority = OpeningAuthority.forBatch(batch, signOff);

    assertThat(authority.batchId()).isEqualTo(batch.getId());
    assertThat(authority.cutoverDate()).isEqualTo(CUTOVER);
    assertThat(authority.signedBy()).isEqualTo(SIGNER);
    assertThat(authority.signedAt()).isEqualTo(signOff.getSignedAt());
  }

  @Test
  void refused_whenTheBatchIsNotATrustPosition() {
    var batch = validatedBatch(TakeOnDataset.CLIENTS, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("only by a trust position take-on with a cutover date");
  }

  @Test
  void refused_whenTheBatchHasNoCutoverDate() {
    var batch = batch(TakeOnDataset.TRUST_POSITION, null);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("only by a trust position take-on with a cutover date");
  }

  @Test
  void refused_whenTheSignOffIsTheFicaAttestation() {
    var batch = validatedBatch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.FICA_ATTESTATION, HASH);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(REFUSED_SIGN_OFF);
  }

  @Test
  void refused_whenTheSignOffWasWithdrawn() {
    var batch = validatedBatch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);
    signOff.revoke(TakeOnSignOff.RevokedReason.REVOKED);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(REFUSED_SIGN_OFF);
  }

  @Test
  void refused_whenTheSignOffIsAnotherBatchs() {
    var batch = validatedBatch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(UUID.randomUUID(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(REFUSED_SIGN_OFF);
  }

  @Test
  void refused_whenTheBatchChangedSinceTheSignOff() {
    var batch = validatedBatch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, "b".repeat(64));

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The take-on batch changed since it was signed off");
  }

  @Test
  void refused_whenTheBatchWasNeverValidated() {
    var batch = batch(TakeOnDataset.TRUST_POSITION, CUTOVER);
    var signOff = signOff(batch.getId(), TakeOnSignOffKind.TRUST_SIGN_OFF, HASH);

    assertThatThrownBy(() -> OpeningAuthority.forBatch(batch, signOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The take-on batch changed since it was signed off");
  }

  // --- helpers ---

  private static TakeOnBatch batch(TakeOnDataset dataset, LocalDate cutover) {
    var batch =
        new TakeOnBatch(
            dataset, dataset == TakeOnDataset.TRUST_POSITION ? ACCOUNT : null, "Take-on", MAKER);
    ReflectionTestUtils.setField(batch, "id", UUID.randomUUID());
    if (cutover != null) {
      batch.setCutoverDate(cutover);
    }
    return batch;
  }

  /** A batch mapped and validated at {@link #HASH} — what a sign-off binds to. */
  private static TakeOnBatch validatedBatch(TakeOnDataset dataset, LocalDate cutover) {
    var batch = batch(dataset, cutover);
    batch.markMapped();
    batch.markValidated(HASH, new TakeOnBatch.Counts(1, 1, 0, 0, 0), Map.of());
    return batch;
  }

  private static TakeOnSignOff signOff(UUID batchId, TakeOnSignOffKind kind, String hash) {
    return new TakeOnSignOff(
        batchId, kind, SIGNER, "Signer", "Partner", "v1", "I confirm.", hash, null);
  }
}
