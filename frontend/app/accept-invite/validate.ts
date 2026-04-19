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
 *   http://evil.example/?next=http://KC_HOST/realms/REALM/login-actions/
 * which contains the prefix but does not start with it.
 *
 * The KC origin and realm are env-driven via `NEXT_PUBLIC_KEYCLOAK_URL` and
 * `NEXT_PUBLIC_KEYCLOAK_REALM` so the same code works in staging/prod without
 * a source change.
 */

const DEFAULT_KEYCLOAK_URL = "http://localhost:8180";
const DEFAULT_KEYCLOAK_REALM = "docteams";

function getKeycloakRealmBase(): string {
  const kcUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL || DEFAULT_KEYCLOAK_URL;
  const realm = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || DEFAULT_KEYCLOAK_REALM;
  return `${kcUrl.replace(/\/$/, "")}/realms/${realm}`;
}

/**
 * KC 26.5 emits two distinct invite URL shapes we need to accept:
 *   1. /login-actions/action-token?key=…   (email-verification / reset-credential flows)
 *   2. /protocol/openid-connect/registrations?client_id=…   (the invite-user
 *      endpoint used by the org-invite flow — discovered in QA 2026-04-17
 *      after PR #1059 only whitelisted the first shape).
 */
export function getAllowedKcUrlPrefixes(): readonly string[] {
  const base = getKeycloakRealmBase();
  return [`${base}/login-actions/`, `${base}/protocol/openid-connect/registrations?`];
}

export function isAllowedKcUrl(candidate: string | null | undefined): candidate is string {
  if (!candidate) return false;
  // Reject URLs with embedded NULs or control characters.
  if (/[\u0000-\u001F\u007F]/.test(candidate)) return false;
  return getAllowedKcUrlPrefixes().some((prefix) => candidate.startsWith(prefix));
}
