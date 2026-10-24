package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Objects;

/**
 * Customer-portal settings group (Wave 3.3 embeddable refactor). Holds the firm-wide portal digest
 * cadence and the last-successful-digest timestamp.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). Both mapped columns are nullable, so Hibernate materialises a
 * NULL group when both are null; {@link OrgSettings#getPortal()} lazily re-creates it.
 *
 * <p>Phase 88 U13 / ADR-350: the per-event document-notification allowlist ({@code
 * portalNotificationDocTypes}) was removed — the client is emailed on explicit intent instead. Its
 * column {@code portal_notification_doc_types} (NOT NULL, JSONB DEFAULT in {@code
 * V1__baseline.sql}) remains in the table, unmapped and unused (A15); dropping it is a later
 * cleanup migration.
 *
 * <p>Field-level setters and the digest domain mutators ({@link #markDigestSent}/{@link
 * #clearDigestLastSent}) cannot reach the owning entity's timestamp; {@code OrgSettings.updatedAt}
 * is refreshed uniformly by the entity's {@code @PreUpdate} callback on every dirty flush,
 * preserving the pre-refactor contract where every mutator bumped it.
 */
@Embeddable
public class PortalSettings {

  /**
   * Firm-wide cadence (Epic 498A, ADR-258) controlling how often the portal digest scheduler emails
   * active portal contacts. Null-safe default = {@link PortalDigestCadence#WEEKLY}; use {@link
   * #getEffectivePortalDigestCadence()} to read.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "portal_digest_cadence", length = 12)
  private PortalDigestCadence portalDigestCadence;

  /**
   * Timestamp of the most recent successful portal digest send for this tenant (Epic 498B, Phase
   * 68). Consumed by {@code PortalDigestScheduler} for the {@link PortalDigestCadence#BIWEEKLY}
   * skip-window (12 days). WEEKLY ignores this column; OFF never runs. Null until the first
   * successful send.
   */
  @Column(name = "digest_last_sent_at")
  private Instant digestLastSentAt;

  protected PortalSettings() {}

  public PortalDigestCadence getPortalDigestCadence() {
    return portalDigestCadence;
  }

  /**
   * Returns the effective portal digest cadence, falling back to {@link PortalDigestCadence#WEEKLY}
   * when unset.
   */
  public PortalDigestCadence getEffectivePortalDigestCadence() {
    return portalDigestCadence != null ? portalDigestCadence : PortalDigestCadence.WEEKLY;
  }

  public void setPortalDigestCadence(PortalDigestCadence cadence) {
    this.portalDigestCadence = cadence;
  }

  public Instant getDigestLastSentAt() {
    return digestLastSentAt;
  }

  /** Stamps the last successful digest send timestamp (Epic 498B). */
  public void markDigestSent(Instant sentAt) {
    this.digestLastSentAt = Objects.requireNonNull(sentAt, "sentAt must not be null");
  }

  /**
   * Clears the last successful digest send timestamp (Epic 498B). Intended for test-reset paths and
   * administrative cadence resets.
   */
  public void clearDigestLastSent() {
    this.digestLastSentAt = null;
  }
}
