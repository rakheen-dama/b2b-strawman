import type { NextConfig } from "next";

/**
 * Legacy URL → canonical URL redirects: ONE committed table (Phase 87 §12.7.4 / §12.10.5).
 *
 * Consumed by `next.config.ts` `redirects()` and asserted by
 * `__tests__/lib/legacy-redirects.test.ts`. APPEND-ONLY: each slice adds its own rows
 * (610D routes; 612A /profitability; 613D calendars — a `has` row must precede its plain
 * twin; 615E expenses; 616 settings). Rows stay for one release after Phase 87 ships.
 *
 * `permanent: true` → Next.js answers HTTP 308 (method-preserving). `:path*` matches zero
 * or more segments. The request's query string is carried over and merged UNDER a
 * destination's own query (destination keys win). Config redirects run before the auth
 * middleware (`proxy.ts`).
 *
 * Dependency-free on purpose: `next.config.ts` imports it by relative path (the `next` import
 * above is type-only and erased).
 *
 * Typing: `satisfies readonly Redirect[]`, not `as const` — a const `has` tuple is readonly,
 * which Next's mutable `Redirect.has: RouteHas[]` rejects (TS2322 in next.config.ts).
 */
type Redirect = Awaited<ReturnType<NonNullable<NextConfig["redirects"]>>>[number];

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
  // Phase 87 S6b (613D, ADR-339): the two calendars are one Diary.
  {
    source: "/org/:slug/calendar",
    destination: "/org/:slug/diary",
    permanent: true,
  },
  // The court calendar's Prescriptions deep link — must precede the plain /court-calendar row.
  {
    source: "/org/:slug/court-calendar",
    has: [{ type: "query", key: "tab", value: "prescription" }],
    destination: "/org/:slug/diary?types=PRESCRIPTION&view=list",
    permanent: true,
  },
  {
    source: "/org/:slug/court-calendar",
    destination: "/org/:slug/diary?types=COURT_DATE,PRESCRIPTION",
    permanent: true,
  },
  // Phase 87 S8 (615E, ADR-340): the standalone matter expenses page is deleted — one Disbursements tab.
  // The old /projects/:id/expenses URL chains through the 610D /projects/:path* row first.
  {
    source: "/org/:slug/matters/:id/expenses",
    destination: "/org/:slug/matters/:id?tab=disbursements",
    permanent: true,
  },
] satisfies readonly Redirect[];
