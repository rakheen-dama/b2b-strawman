/**
 * Legacy URL → canonical URL redirects: ONE committed table (Phase 87 §12.7.4 / §12.10.5).
 *
 * Consumed by `next.config.ts` `redirects()` and asserted by
 * `__tests__/lib/legacy-redirects.test.ts`. APPEND-ONLY: each slice adds its own rows
 * (610D routes; 612A /profitability; 613D calendars — a `has` row must precede its plain
 * twin; 615E expenses; 616 settings). Rows stay for one release after Phase 87 ships.
 *
 * `permanent: true` → Next.js answers HTTP 308 (method-preserving). `:path*` matches zero
 * or more segments and query strings are carried over. Config redirects run before the
 * auth middleware (`proxy.ts`).
 *
 * Dependency-free on purpose: `next.config.ts` imports it by relative path.
 *
 * Typing note: `as const` turns a future `has: [...]` array into a readonly tuple, which
 * Next's mutable `Redirect[]` rejects. The slice adding the first `has` row (613D) must
 * adapt the typing, e.g. `satisfies readonly Redirect[]` here plus a mutable copy of
 * `has` in `next.config.ts` `redirects()`.
 */
export const LEGACY_REDIRECTS = [
  // GAP-Doc-Drift-26: legacy bookmarks and QA scenario docs reference
  // /org/:slug/settings/team; the canonical route is /org/:slug/team.
  {
    source: "/org/:slug/settings/team",
    destination: "/org/:slug/team",
    permanent: true,
  },
  // Phase 87 S4 (610D, ADR-337): route segments renamed to the two nouns.
  {
    source: "/org/:slug/projects/:path*",
    destination: "/org/:slug/matters/:path*",
    permanent: true,
  },
  {
    source: "/org/:slug/customers/:path*",
    destination: "/org/:slug/clients/:path*",
    permanent: true,
  },
  // Phase 87 S6a (612A, ADR-339): Profitability's single entry is Firm › Reports.
  {
    source: "/org/:slug/profitability",
    destination: "/org/:slug/reports/profitability",
    permanent: true,
  },
] as const;
