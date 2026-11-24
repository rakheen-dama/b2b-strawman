package io.b2mash.b2b.b2bstrawman.takeon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The render attempts of one take-on report's files (Phase 93 657D, §17.3.12, R-18): each failure
 * counts one attempt; the {@value #MAX_ATTEMPTS}th sets {@code failed_at}, after which neither the
 * drain nor the sweep tries again until "Try again" ({@link #reset}) — a permanently failing render
 * is never re-enqueued every minute forever.
 */
@Entity
@Table(name = "take_on_report_renders")
public class TakeOnReportRender {

  /** Failed attempts before the render is given up ({@code failed_at} set). */
  public static final int MAX_ATTEMPTS = 5;

  /** {@code last_error_code} length. */
  static final int ERROR_CODE_LENGTH = 50;

  @Id
  @Column(name = "report_id", nullable = false, updatable = false)
  private UUID reportId;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error_code", length = ERROR_CODE_LENGTH)
  private String lastErrorCode;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  protected TakeOnReportRender() {}

  public TakeOnReportRender(UUID reportId) {
    this.reportId = Objects.requireNonNull(reportId, "reportId");
  }

  /**
   * One failed attempt with {@code code} (truncated to 50) at {@code at}; returns {@code true} when
   * it is the attempt that gives the render up ({@code failed_at} set now).
   */
  public boolean recordFailure(String code, Instant at) {
    if (failedAt != null) {
      return false;
    }
    attempts++;
    lastErrorCode =
        code == null
            ? null
            : code.length() > ERROR_CODE_LENGTH ? code.substring(0, ERROR_CODE_LENGTH) : code;
    lastAttemptAt = at;
    if (attempts >= MAX_ATTEMPTS) {
      failedAt = at;
      return true;
    }
    return false;
  }

  /** "Try again": a fresh cycle of {@value #MAX_ATTEMPTS} attempts. */
  public void reset() {
    attempts = 0;
    failedAt = null;
    lastErrorCode = null;
  }

  public boolean isFailed() {
    return failedAt != null;
  }

  public UUID getReportId() {
    return reportId;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }

  public Instant getFailedAt() {
    return failedAt;
  }
}
