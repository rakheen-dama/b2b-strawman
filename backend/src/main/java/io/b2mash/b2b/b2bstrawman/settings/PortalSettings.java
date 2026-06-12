package io.b2mash.b2b.b2bstrawman.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Customer-portal settings group (Wave 3.3 embeddable refactor). Holds the firm-wide portal digest
 * cadence, the retainer-consumption member-display privacy mode, the last-successful-digest
 * timestamp, and the per-event document-notification allowlist.
 *
 * <p>Persisted inline on the {@code org_settings} table via {@code @Embedded} +
 * {@code @AttributeOverride} on {@link OrgSettings}. Column names, types, and nullability are
 * UNCHANGED from when these fields lived directly on the entity (zero schema change — see {@code
 * OrgSettingsSchemaSnapshotTest}). Note {@code portal_notification_doc_types} is NOT NULL (V117
 * JSONB DEFAULT); the constructor seeds the canonical default so fresh entities persist it.
 *
 * <p>Field-level setters and the digest domain mutators ({@link #markDigestSent}/{@link
 * #clearDigestLastSent}) cannot reach the owning entity's timestamp; {@code OrgSettings.updatedAt}
 * is refreshed uniformly by the entity's {@code @PreUpdate} callback on every dirty flush,
 * preserving the pre-refactor contract where every mutator bumped it.
 */
@Embeddable
public class PortalSettings {

  /**
   * Privacy toggle (ADR-255, Epic 496A) controlling how firm-member names appear on the customer
   * portal's retainer consumption list. Null-safe default = {@link
   * PortalRetainerMemberDisplay#FIRST_NAME_ROLE}; use {@link
   * #getEffectivePortalRetainerMemberDisplay()} to read.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "portal_retainer_member_display", length = 20)
  private PortalRetainerMemberDisplay portalRetainerMemberDisplay;

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

  /**
   * Per-tenant allowlist (GAP-L-72, slice 23) of generated-document template names whose {@code
   * DocumentGeneratedEvent} should trigger an immediate portal-contact email via {@code
   * PortalDocumentNotificationHandler}. Mirrors the JSONB-list shape of {@code
   * OrgSettings.enabledModules}. Default (set by Flyway V117) is {@code ["matter-closure-letter",
   * "statement-of-account"]}: the two portal-visible artefacts that warrant a per-event email on
   * top of the weekly digest. An empty list disables per-event sends without affecting the weekly
   * digest.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "portal_notification_doc_types", columnDefinition = "jsonb")
  private List<String> portalNotificationDocTypes = new ArrayList<>();

  protected PortalSettings() {}

  /**
   * Seeds the canonical {@link OrgSettings#DEFAULT_PORTAL_NOTIFICATION_DOC_TYPES} so newly
   * provisioned tenants persist the canonical list rather than an empty array (OBS-2107 follow-up).
   * Invoked from the {@link OrgSettings} constructor.
   */
  static PortalSettings withDefaults() {
    var portal = new PortalSettings();
    portal.portalNotificationDocTypes =
        new ArrayList<>(OrgSettings.DEFAULT_PORTAL_NOTIFICATION_DOC_TYPES);
    return portal;
  }

  public PortalRetainerMemberDisplay getPortalRetainerMemberDisplay() {
    return portalRetainerMemberDisplay;
  }

  /**
   * Returns the effective portal-retainer member-display mode, falling back to {@link
   * PortalRetainerMemberDisplay#FIRST_NAME_ROLE} when unset.
   */
  public PortalRetainerMemberDisplay getEffectivePortalRetainerMemberDisplay() {
    return portalRetainerMemberDisplay != null
        ? portalRetainerMemberDisplay
        : PortalRetainerMemberDisplay.FIRST_NAME_ROLE;
  }

  public void setPortalRetainerMemberDisplay(PortalRetainerMemberDisplay mode) {
    this.portalRetainerMemberDisplay = mode;
  }

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

  /**
   * Returns an immutable view of the portal-notification document-type allowlist (GAP-L-72). The
   * default JSONB seed ({@code ["matter-closure-letter", "statement-of-account"]}) is applied at
   * the database layer via Flyway V117 — Java-side defaulting only kicks in when the column is
   * absent (i.e. on a fresh entity not yet flushed from the DB).
   */
  public List<String> getPortalNotificationDocTypes() {
    return portalNotificationDocTypes != null ? List.copyOf(portalNotificationDocTypes) : List.of();
  }

  public void setPortalNotificationDocTypes(List<String> portalNotificationDocTypes) {
    this.portalNotificationDocTypes =
        portalNotificationDocTypes != null
            ? new ArrayList<>(portalNotificationDocTypes)
            : new ArrayList<>();
  }
}
