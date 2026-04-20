package io.b2mash.b2b.b2bstrawman.portal.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Per-portal-contact opt-outs for the five notification channels (Epic 498A, ADR-258). Cadence is
 * firm-level (see {@code org_settings.portal_digest_cadence}); per-contact opt-out lives here in
 * the shared {@code portal} schema. Schema-qualified via {@link Table#schema()} because the global
 * {@code portal} schema sits outside the per-tenant Hibernate search_path.
 *
 * <p>All five booleans default to {@code true} on first write — a portal contact opts in to every
 * channel by default and may opt out granularly.
 */
@Entity
@Table(name = "portal_notification_preference", schema = "portal")
public class PortalNotificationPreference {

  @Id
  @Column(name = "portal_contact_id")
  private UUID portalContactId;

  @Column(name = "digest_enabled", nullable = false)
  private boolean digestEnabled;

  @Column(name = "trust_activity_enabled", nullable = false)
  private boolean trustActivityEnabled;

  @Column(name = "retainer_updates_enabled", nullable = false)
  private boolean retainerUpdatesEnabled;

  @Column(name = "deadline_reminders_enabled", nullable = false)
  private boolean deadlineRemindersEnabled;

  @Column(name = "action_required_enabled", nullable = false)
  private boolean actionRequiredEnabled;

  @Column(name = "last_updated_at", nullable = false)
  private Instant lastUpdatedAt;

  protected PortalNotificationPreference() {}

  /** Factory: produces a new preference row with all five toggles defaulted to {@code true}. */
  public static PortalNotificationPreference allEnabled(UUID portalContactId) {
    var pref = new PortalNotificationPreference();
    pref.portalContactId = portalContactId;
    pref.digestEnabled = true;
    pref.trustActivityEnabled = true;
    pref.retainerUpdatesEnabled = true;
    pref.deadlineRemindersEnabled = true;
    pref.actionRequiredEnabled = true;
    pref.lastUpdatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    return pref;
  }

  /** Applies all five toggles in a single call and stamps {@code lastUpdatedAt}. */
  public void update(
      boolean digestEnabled,
      boolean trustActivityEnabled,
      boolean retainerUpdatesEnabled,
      boolean deadlineRemindersEnabled,
      boolean actionRequiredEnabled) {
    this.digestEnabled = digestEnabled;
    this.trustActivityEnabled = trustActivityEnabled;
    this.retainerUpdatesEnabled = retainerUpdatesEnabled;
    this.deadlineRemindersEnabled = deadlineRemindersEnabled;
    this.actionRequiredEnabled = actionRequiredEnabled;
    this.lastUpdatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public UUID getPortalContactId() {
    return portalContactId;
  }

  public boolean isDigestEnabled() {
    return digestEnabled;
  }

  public boolean isTrustActivityEnabled() {
    return trustActivityEnabled;
  }

  public boolean isRetainerUpdatesEnabled() {
    return retainerUpdatesEnabled;
  }

  public boolean isDeadlineRemindersEnabled() {
    return deadlineRemindersEnabled;
  }

  public boolean isActionRequiredEnabled() {
    return actionRequiredEnabled;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }
}
