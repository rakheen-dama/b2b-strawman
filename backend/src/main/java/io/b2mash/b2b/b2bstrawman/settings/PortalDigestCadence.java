package io.b2mash.b2b.b2bstrawman.settings;

/**
 * Firm-wide cadence for portal digest emails (Epic 498A, ADR-258). Default {@link #WEEKLY}. {@link
 * #OFF} disables the digest scheduler for the firm entirely. Per-contact opt-out lives in
 * portal_notification_preference.digest_enabled.
 */
public enum PortalDigestCadence {
  /** Weekly digest cadence. Default. */
  WEEKLY,

  /** Biweekly (every other week) digest cadence. */
  BIWEEKLY,

  /** Digest disabled firm-wide. */
  OFF
}
