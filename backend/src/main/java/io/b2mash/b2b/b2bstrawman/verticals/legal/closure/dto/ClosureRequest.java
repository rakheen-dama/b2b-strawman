package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body payload for {@code POST /api/matters/{matterId}/closure/close} (Phase 67, Epic 489B).
 *
 * <p>Semantic validation — override flag, justification length (&gt;=20 non-whitespace chars when
 * override=true) — happens in {@code MatterClosureService}.
 *
 * <p>{@code generateStatementOfAccount} (GAP-L-93): when {@code true}, {@code MatterClosureService}
 * inline-invokes {@code StatementService.generate(...)} with the matter's open-date → today default
 * period. Best-effort — failure to render the SoA does not roll back the close (mirrors the {@code
 * generateClosureLetter} REQUIRES_NEW guarantee). Boxed {@link Boolean} so older clients that don't
 * send the field are accepted (Jackson 3 strict-mode rejects missing primitives in records); a
 * {@code null} value is treated as {@code false}.
 *
 * <p>Phase 88 U13 / ADR-350 client-notification intent, applied to BOTH the closure letter and the
 * closure Statement of Account: {@code shareWithClient} saves them portal-visible and {@code
 * notifyClient} additionally emails the client's portal contact (and implies share). Both default
 * to {@code false} — the documents are saved {@code INTERNAL} and no email is sent. Boxed for the
 * same Jackson reason as {@code generateStatementOfAccount}.
 */
public record ClosureRequest(
    @NotNull(message = "reason is required") ClosureReason reason,
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes,
    boolean generateClosureLetter,
    Boolean generateStatementOfAccount,
    boolean override,
    @Size(max = 5000, message = "overrideJustification must not exceed 5000 characters")
        String overrideJustification,
    Boolean shareWithClient,
    Boolean notifyClient) {

  /** Returns true only when the boxed flag is explicitly true; null/false → false. */
  public boolean isGenerateStatementOfAccount() {
    return Boolean.TRUE.equals(generateStatementOfAccount);
  }

  /** ADR-350: email the client only on explicit intent; {@code null} → {@code false}. */
  public boolean isNotifyClient() {
    return Boolean.TRUE.equals(notifyClient);
  }

  /**
   * ADR-350: save the closure letter + SoA portal-visible. {@code notifyClient} implies share;
   * {@code null} → {@code false}.
   */
  public boolean isShareWithClient() {
    return Boolean.TRUE.equals(shareWithClient) || isNotifyClient();
  }
}
