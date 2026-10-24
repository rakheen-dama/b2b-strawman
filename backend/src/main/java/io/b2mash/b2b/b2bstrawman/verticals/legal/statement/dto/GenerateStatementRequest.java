package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/matters/{matterId}/statements} (architecture §67.4.3). The
 * optional {@code templateId} lets a caller select a non-default Statement of Account template; if
 * null the system template (slug {@code statement-of-account}) is resolved by the service.
 *
 * <p>Phase 88 U13 / ADR-350 client-notification intent: {@code shareWithClient} saves the statement
 * portal-visible and {@code notifyClient} additionally emails the client's portal contact (and
 * implies share). Both default to {@code false} — the statement is saved {@code INTERNAL} and no
 * email is sent unless the firm explicitly asks. Boxed {@link Boolean} so clients that omit the
 * fields are accepted (Jackson 3 strict mode rejects missing primitives in records); {@code null}
 * is treated as {@code false}.
 */
public record GenerateStatementRequest(
    @NotNull LocalDate periodStart,
    @NotNull LocalDate periodEnd,
    UUID templateId,
    Boolean shareWithClient,
    Boolean notifyClient) {

  /** Cross-field: the statement period must not run backwards. */
  @AssertTrue(message = "periodEnd must be on or after periodStart")
  boolean isValidPeriodRange() {
    return periodStart != null && periodEnd != null && !periodEnd.isBefore(periodStart);
  }

  /** ADR-350: email the client only on explicit intent; {@code null} → {@code false}. */
  public boolean isNotifyClient() {
    return Boolean.TRUE.equals(notifyClient);
  }

  /**
   * ADR-350: save the statement portal-visible. {@code notifyClient} implies share; {@code null} →
   * {@code false}.
   */
  public boolean isShareWithClient() {
    return Boolean.TRUE.equals(shareWithClient) || isNotifyClient();
  }
}
