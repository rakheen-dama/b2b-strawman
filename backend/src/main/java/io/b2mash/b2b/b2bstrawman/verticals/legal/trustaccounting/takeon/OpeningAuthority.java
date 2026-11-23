package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The authority under which a trust take-on posts its opening entries (Phase 93 657A, ADR-386,
 * ADR-391): the batch, its cutover date and the live partner/director sign-off (Rule 54.19). {@code
 * TrustPostingService.recordOpening} posts a take-on row only under one, and stamps the signer and
 * the sign-off time on it as its approver (FD-25).
 *
 * <p>Built only by {@link #forBatch} from a live {@code TRUST_SIGN_OFF} of a trust-position batch —
 * the constructor is package-private (tests use {@code OpeningAuthorities} in the same package),
 * and {@code TakeOnEntryPointsArchTest} allows only {@code ..takeon..} to call {@code forBatch} and
 * {@code recordOpening}. First built from a real sign-off by the commit in 657C.
 */
public final class OpeningAuthority {

  private final UUID batchId;
  private final LocalDate cutoverDate;
  private final UUID signedBy;
  private final Instant signedAt;

  OpeningAuthority(UUID batchId, LocalDate cutoverDate, UUID signedBy, Instant signedAt) {
    this.batchId = Objects.requireNonNull(batchId, "batchId");
    this.cutoverDate = Objects.requireNonNull(cutoverDate, "cutoverDate");
    this.signedBy = Objects.requireNonNull(signedBy, "signedBy");
    this.signedAt = Objects.requireNonNull(signedAt, "signedAt");
  }

  /**
   * The authority of {@code batch}'s live trust sign-off: a trust-position batch with its cutover
   * date, and a live {@code TRUST_SIGN_OFF} of that batch bound to the batch's current validation
   * hash. Anything else is a programming error (IllegalStateException) — the commit re-checks the
   * sign-off under the batch lock before it gets here (657C).
   */
  public static OpeningAuthority forBatch(TakeOnBatch batch, TakeOnSignOff signOff) {
    Objects.requireNonNull(batch, "batch");
    Objects.requireNonNull(signOff, "signOff");
    if (batch.getDataset() != TakeOnDataset.TRUST_POSITION || batch.getCutoverDate() == null) {
      throw new IllegalStateException(
          "Opening entries are posted only by a trust position take-on with a cutover date");
    }
    if (signOff.getKind() != TakeOnSignOffKind.TRUST_SIGN_OFF
        || !signOff.isLive()
        || !batch.getId().equals(signOff.getBatchId())) {
      throw new IllegalStateException(
          "Opening entries need the batch's live trust sign-off (batch " + batch.getId() + ")");
    }
    if (!Objects.equals(batch.getValidationHash(), signOff.getValidationHash())) {
      throw new IllegalStateException("The take-on batch changed since it was signed off");
    }
    return new OpeningAuthority(
        batch.getId(), batch.getCutoverDate(), signOff.getMemberId(), signOff.getSignedAt());
  }

  public UUID batchId() {
    return batchId;
  }

  public LocalDate cutoverDate() {
    return cutoverDate;
  }

  public UUID signedBy() {
    return signedBy;
  }

  public Instant signedAt() {
    return signedAt;
  }
}
