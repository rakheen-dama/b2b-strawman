/**
 * Shape checks for the audit-trail filter params the backend types strictly (625D, R-6). A value
 * the backend cannot parse is a 400 that would fail the whole Firm settings page, so the loader
 * drops it and the Entity ID input refuses to commit it.
 */

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** `actorId` / `entityId` are `java.util.UUID` on the backend. */
export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}

// `from` / `to` are `java.time.Instant`: date, time with seconds, optional fraction, Z or offset.
const ISO_INSTANT_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(?:Z|[+-](?:0\d|1[0-8]):[0-5]\d)$/;

/** An ISO-8601 instant with a real calendar date and time ("2026-02-30T…" is rejected). */
export function isIsoInstant(value: string): boolean {
  const m = ISO_INSTANT_PATTERN.exec(value);
  if (!m) return false;
  const [year, month, day, hour, minute, second] = m.slice(1, 7).map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day &&
    hour < 24 &&
    minute < 60 &&
    second < 60
  );
}
