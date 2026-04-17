/**
 * Shared allow-list validator for the Keycloak invite bounce page.
 *
 * The `/accept-invite` and `/accept-invite/continue` routes accept a `kcUrl`
 * query parameter whose value is the original Keycloak invite action URL. To
 * prevent open-redirect abuse, the URL MUST begin with an allow-listed prefix
 * (KC's login-actions path on the configured KC origin).
 *
 * IMPORTANT: use `startsWith` — never `includes`/`indexOf` — because an
 * attacker could otherwise craft a URL like
 *   http://evil.example/?next=http://localhost:8180/realms/docteams/login-actions/
 * which contains the prefix but does not start with it.
 */

const ALLOWED_KC_URL_PREFIXES: readonly string[] = [
  // Local dev Keycloak — the only origin used in this QA cycle.
  "http://localhost:8180/realms/docteams/login-actions/",
  // Future: add staging/prod KC origins here when the bounce page ships out of
  // local dev. Keep this list explicit — no wildcards, no regex.
];

export function isAllowedKcUrl(candidate: string | null | undefined): candidate is string {
  if (!candidate) return false;
  // Reject URLs with embedded NULs or control characters.
  if (/[\u0000-\u001F\u007F]/.test(candidate)) return false;
  return ALLOWED_KC_URL_PREFIXES.some((prefix) => candidate.startsWith(prefix));
}
