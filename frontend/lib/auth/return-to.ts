/**
 * Return-to capture + open-redirect allowlist guard.
 *
 * This module is the single chokepoint for validating any `returnTo` value
 * before it is reflected into a redirect URL or persisted to storage. It is
 * deliberately framework-light (pure functions + a tiny sessionStorage helper)
 * so it can run on both the server (middleware, server actions) and the client
 * (sign-in CTA, dashboard read-back). Do NOT add `server-only` here.
 */

/** sessionStorage key used to round-trip the post-login destination. */
export const RETURN_TO_STORAGE_KEY = "kazi.returnTo";

/** The default safe destination when a return-to is missing or invalid. */
const DEFAULT_RETURN_TO = "/dashboard";

/**
 * Allowlisted app path prefixes. Anything not matching one of these (after the
 * structural checks below) falls back to {@link DEFAULT_RETURN_TO}.
 */
const ALLOWED_PREFIXES = ["/dashboard", "/org/", "/platform-admin", "/create-org"];

/**
 * Extract the `pathname + search` from a request-like object. Accepts either a
 * middleware request (`{ nextUrl: URL }`) or a plain `{ pathname, search }`
 * (e.g. from `window.location`).
 */
export function captureReturnTo(
  req: { nextUrl: URL } | { pathname: string; search: string }
): string {
  if ("nextUrl" in req) {
    return `${req.nextUrl.pathname}${req.nextUrl.search}`;
  }
  return `${req.pathname}${req.search ?? ""}`;
}

/**
 * The single open-redirect chokepoint. Validates a raw `returnTo` value and
 * returns either the same value (when safe) or {@link DEFAULT_RETURN_TO}.
 *
 * Enforces, in order:
 * - non-empty string
 * - starts with exactly one `/` (rejects `//host` and `/\host` scheme-relative)
 * - no embedded scheme / control characters (rejects `javascript:`, `data:`,
 *   `http:`, `https:`, backslashes, whitespace)
 * - matches one of the {@link ALLOWED_PREFIXES} app path prefixes
 *
 * Never reflect unvalidated input — always route through this function.
 */
export function safeReturnTo(raw: string | null | undefined): string {
  if (!raw || typeof raw !== "string") {
    return DEFAULT_RETURN_TO;
  }

  // Must be a single-slash path-absolute reference. Reject protocol-relative
  // (`//evil`) and backslash-trick (`/\evil`, which some browsers normalise to
  // `//evil`) up front.
  if (!raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) {
    return DEFAULT_RETURN_TO;
  }

  // Reject anything carrying a scheme, control characters, whitespace, or
  // backslashes anywhere in the value.
  if (/[\\\s]/.test(raw) || /^[a-z][a-z0-9+.-]*:/i.test(raw) || raw.includes(":")) {
    return DEFAULT_RETURN_TO;
  }

  // Must target an allowlisted app surface, with boundary-aware matching so a
  // lookalike like `/dashboard-malicious` or `/create-orgx` can't slip through
  // a bare `startsWith` (partial-prefix bypass).
  const matchesAllowlist = ALLOWED_PREFIXES.some((prefix) => {
    // Prefixes ending with "/" are subtree-only by construction (e.g. "/org/").
    if (prefix.endsWith("/")) return raw.startsWith(prefix);
    // Otherwise allow exact match, a nested path, or a query on the same route.
    return raw === prefix || raw.startsWith(`${prefix}/`) || raw.startsWith(`${prefix}?`);
  });
  if (!matchesAllowlist) {
    return DEFAULT_RETURN_TO;
  }

  return raw;
}

/**
 * Persist a validated return-to destination to sessionStorage. No-op on the
 * server. Always re-validates before writing.
 */
export function persistReturnTo(raw: string | null | undefined): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(RETURN_TO_STORAGE_KEY, safeReturnTo(raw));
  } catch {
    // sessionStorage may be unavailable (private mode / SSR) — ignore.
  }
}

/**
 * Read + clear the persisted return-to destination, re-validating it through
 * {@link safeReturnTo}. Returns `null` on the server or when nothing was
 * stored. The stored key is always cleared (read-once semantics).
 */
export function consumeReturnTo(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(RETURN_TO_STORAGE_KEY);
    window.sessionStorage.removeItem(RETURN_TO_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    return safeReturnTo(raw);
  } catch {
    return null;
  }
}
