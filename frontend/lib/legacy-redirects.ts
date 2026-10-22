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
  // Phase 87 S9 (616A, ADR-341): the Firm family — eight former pages are ?tab= tabs of /settings/firm.
  {
    source: "/org/:slug/settings/general",
    destination: "/org/:slug/settings/firm?tab=general",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/features",
    destination: "/org/:slug/settings/firm?tab=features",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/roles",
    destination: "/org/:slug/settings/firm?tab=roles",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/audit-log",
    destination: "/org/:slug/settings/firm?tab=audit-trail",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/data-protection",
    destination: "/org/:slug/settings/firm?tab=data-protection",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/data-protection/requests",
    destination: "/org/:slug/settings/firm?tab=data-requests",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/compliance",
    destination: "/org/:slug/settings/firm?tab=compliance",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/billing",
    destination: "/org/:slug/settings/firm?tab=subscription",
    permanent: true,
  },
  // Phase 87 S9 (616B, ADR-341): the Matters family — seven former pages are ?tab= tabs of
  // /settings/matters; the template, checklist and request-template editors move under it.
  {
    source: "/org/:slug/settings/project-templates",
    destination: "/org/:slug/settings/matters?tab=templates",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/project-naming",
    destination: "/org/:slug/settings/matters?tab=naming",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/custom-fields",
    destination: "/org/:slug/settings/matters?tab=custom-fields",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/tags",
    destination: "/org/:slug/settings/matters?tab=tags",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/checklists",
    destination: "/org/:slug/settings/matters?tab=checklists",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/request-templates",
    destination: "/org/:slug/settings/matters?tab=request-templates",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/request-settings",
    destination: "/org/:slug/settings/matters?tab=request-settings",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/project-templates/:id",
    destination: "/org/:slug/settings/matters/templates/:id",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/checklists/new",
    destination: "/org/:slug/settings/matters/checklists/new",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/checklists/:id",
    destination: "/org/:slug/settings/matters/checklists/:id",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/checklists/:id/edit",
    destination: "/org/:slug/settings/matters/checklists/:id/edit",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/request-templates/new",
    destination: "/org/:slug/settings/matters/request-templates/new",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/request-templates/:id",
    destination: "/org/:slug/settings/matters/request-templates/:id",
    permanent: true,
  },
  // Phase 87 S9 (616C, ADR-341): the Documents family — four former pages are ?tab= tabs of
  // /settings/documents; the document-template editors move under it.
  {
    source: "/org/:slug/settings/templates",
    destination: "/org/:slug/settings/documents?tab=templates",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/clauses",
    destination: "/org/:slug/settings/documents?tab=clauses",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/acceptance",
    destination: "/org/:slug/settings/documents?tab=acceptance",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/email",
    destination: "/org/:slug/settings/documents?tab=email",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/templates/new",
    destination: "/org/:slug/settings/documents/templates/new",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/templates/:id/edit",
    destination: "/org/:slug/settings/documents/templates/:id/edit",
    permanent: true,
  },
  // Phase 87 S9 (616C, ADR-341): the Fees & Trust family — six former pages are ?tab= tabs of
  // /settings/fees (rates → Tariffs, time-tracking → Time recording, batch-billing → Fee runs).
  {
    source: "/org/:slug/settings/rates",
    destination: "/org/:slug/settings/fees?tab=tariffs",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/tax",
    destination: "/org/:slug/settings/fees?tab=tax",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/time-tracking",
    destination: "/org/:slug/settings/fees?tab=time-recording",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/batch-billing",
    destination: "/org/:slug/settings/fees?tab=fee-runs",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/collections",
    destination: "/org/:slug/settings/fees?tab=collections",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/trust-accounting",
    destination: "/org/:slug/settings/fees?tab=trust-accounting",
    permanent: true,
  },
  // Phase 87 S9 (616D, ADR-341): the Connections family — seven former pages are six ?tab= tabs of
  // /settings/connections (integrations and integrations/xero both → xero); the Xero sync log, the
  // AI execution history and the automation execution log move under it.
  {
    source: "/org/:slug/settings/integrations",
    destination: "/org/:slug/settings/connections?tab=xero",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/integrations/xero",
    destination: "/org/:slug/settings/connections?tab=xero",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/integrations/mcp",
    destination: "/org/:slug/settings/connections?tab=mcp",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/ai",
    destination: "/org/:slug/settings/connections?tab=ai",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/notifications",
    destination: "/org/:slug/settings/connections?tab=notifications",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/packs",
    destination: "/org/:slug/settings/connections?tab=packs",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/automations",
    destination: "/org/:slug/settings/connections?tab=automations",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/integrations/xero/sync-log",
    destination: "/org/:slug/settings/connections/xero/sync-log",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/ai/history",
    destination: "/org/:slug/settings/connections/ai/history",
    permanent: true,
  },
  {
    source: "/org/:slug/settings/automations/executions",
    destination: "/org/:slug/settings/connections/automations/executions",
    permanent: true,
  },
] satisfies readonly Redirect[];
