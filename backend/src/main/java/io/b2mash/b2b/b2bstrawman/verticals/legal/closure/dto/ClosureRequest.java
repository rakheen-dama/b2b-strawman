package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body payload for {@code POST /api/matters/{projectId}/closure/close} (Phase 67, Epic 489B).
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
 */
public record ClosureRequest(
    @NotNull(message = "reason is required") ClosureReason reason,
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes,
    boolean generateClosureLetter,
    Boolean generateStatementOfAccount,
    boolean override,
    @Size(max = 5000, message = "overrideJustification must not exceed 5000 characters")
        String overrideJustification) {

  /** Returns true only when the boxed flag is explicitly true; null/false → false. */
  public boolean isGenerateStatementOfAccount() {
    return Boolean.TRUE.equals(generateStatementOfAccount);
  }
}
