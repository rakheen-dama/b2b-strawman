"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { useBreadcrumbLabels } from "@/components/breadcrumb-label-provider";

export const SEGMENT_LABELS: Record<string, string> = {
  // Core nav. Labels follow the sidebar words, sentence case (Phase 88 628B, D11).
  dashboard: "Home",
  matters: "Matters",
  team: "Team",
  settings: "Settings",
  // Settings families (Phase 87 S9, ADR-341; 616A–616D). `matters` is labelled above.
  firm: "Firm",
  documents: "Documents",
  fees: "Fees & Trust",
  connections: "Connections",
  // Kept settings sub-route segments (§12.7.1). Labels for deleted settings routes are pruned —
  // components/__tests__/breadcrumbs-routes.test.ts fails on a label with no segment on disk, and
  // on a segment on disk with no label.
  templates: "Templates",
  checklists: "Checklists",
  "request-templates": "Request templates",
  // The Xero sync-log sub-route (the Connections tab itself is labelled "Integrations").
  xero: "Xero",
  "sync-log": "Sync log",
  history: "History",
  automations: "Automations",
  executions: "Executions",
  // Shared action segments (matters/new, billing-runs/new, settings/…/new, …/[id]/edit).
  new: "New",
  edit: "Edit",
  // Other live segments (/notifications, /invoices/collections, /compliance).
  notifications: "Notifications",
  // "Debtors" in the sidebar and the page h1; the Fees & Trust settings tab stays "Collections".
  collections: "Debtors",
  compliance: "FICA & compliance",
  requests: "Data requests",
  // Additional segments
  clients: "Clients",
  "my-work": "My work",
  // Vertical module pages
  "trust-accounting": "Trust accounting",
  "conflict-check": "Conflict check",
  "client-ledgers": "Client ledgers",
  transactions: "Transactions",
  reconciliation: "Reconciliation",
  interest: "Interest",
  investments: "Investments",
  invoices: "Fee notes",
  "billing-runs": "Fee runs",
  // Phase 87 S6a (612A): nav-zone pages
  unbilled: "Unbilled work",
  reports: "Reports",
  profitability: "Profitability",
  // Phase 87 S6b (613D): one Diary replaces /calendar + /court-calendar.
  diary: "Diary",
  legal: "Legal",
  "adverse-parties": "Adverse parties",
  disbursements: "Disbursements",
  tariffs: "Tariffs",
  ai: "AI",
  reviews: "AI reviews",
  timesheet: "Timesheet",
  "information-requests": "Information requests",
};

/**
 * Label for a dynamic segment (an id, or a slug such as /reports/[reportSlug]) by its parent
 * segment, used when the page publishes no entity name (BreadcrumbLabelProvider). The routes test
 * requires an entry for the parent of every [param] route on disk.
 */
export const PARENT_SEGMENT_FALLBACKS: Record<string, string> = {
  matters: "Matter",
  settings: "Settings",
  clients: "Client",
  invoices: "Fee note",
  "billing-runs": "Fee run",
  "client-ledgers": "Client ledger",
  disbursements: "Disbursement",
  requests: "Data request",
  "information-requests": "Information request",
  reports: "Report",
  checklists: "Checklist template",
  "request-templates": "Request template",
  templates: "Template",
};

/**
 * Intermediate prefixes (relative to /org/[slug]/) that have child routes but no
 * page.tsx of their own, in route-pattern form ([id] = dynamic segment). Linking
 * them would 404 (and Next would prefetch the 404). Kept in sync with the
 * filesystem by components/__tests__/breadcrumbs-routes.test.ts.
 */
export const NON_ROUTABLE_PREFIXES: readonly string[] = [
  "ai",
  "information-requests",
  "legal",
  // Phase 87 S9 (ADR-341): Matters, Documents and Connections settings sub-routes have no list page
  // of their own — the list is a ?tab= of their family page (616B–616D).
  "settings/connections/ai",
  "settings/connections/automations",
  "settings/connections/xero",
  "settings/documents/templates",
  "settings/documents/templates/[id]",
  "settings/matters/checklists",
  "settings/matters/request-templates",
  "settings/matters/templates",
];

function matchesRoutePattern(segments: string[], pattern: string): boolean {
  const parts = pattern.split("/");
  return (
    parts.length === segments.length &&
    parts.every((part, i) => part.startsWith("[") || part === segments[i])
  );
}

/** Whether the crumb for this prefix should link (i.e. a page exists there). */
export function isLinkableBreadcrumbPrefix(segments: string[]): boolean {
  return !NON_ROUTABLE_PREFIXES.some((pattern) => matchesRoutePattern(segments, pattern));
}

interface BreadcrumbsProps {
  slug: string;
  orgName?: string | null;
}

export function Breadcrumbs({ slug, orgName }: BreadcrumbsProps) {
  const pathname = usePathname();
  const publishedLabels = useBreadcrumbLabels();

  // Strip the /org/[slug]/ prefix to get the relative path segments
  const prefix = `/org/${slug}/`;
  const relativePath = pathname.startsWith(prefix) ? pathname.slice(prefix.length) : "";
  const segments = relativePath.split("/").filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      {/* Org name as root link (falls back to slug) */}
      <Link
        href={`/org/${slug}/dashboard`}
        className="text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        {orgName ?? slug}
      </Link>

      {segments.map((segment, index) => {
        const isLast = index === segments.length - 1;
        const parentSegment = index > 0 ? segments[index - 1] : undefined;

        // Build the href for intermediate segments
        const prefixSegments = segments.slice(0, index + 1);
        const href = `/org/${slug}/${prefixSegments.join("/")}`;

        // The entity name the page published for this exact href wins; then the segment's label;
        // then, for a dynamic segment (id or slug), its parent's singular. Every segment on disk is
        // covered by one of the two maps (breadcrumbs-routes.test.ts), so the raw segment is a
        // last resort for unknown paths only.
        const label =
          publishedLabels.get(href) ??
          SEGMENT_LABELS[segment] ??
          (parentSegment ? PARENT_SEGMENT_FALLBACKS[parentSegment] : undefined) ??
          segment;

        return (
          <span key={href} className="flex items-center gap-1">
            <ChevronRight className="h-3.5 w-3.5 text-slate-400" />
            {isLast ? (
              <span className="font-medium text-slate-900 dark:text-white">{label}</span>
            ) : !isLinkableBreadcrumbPrefix(prefixSegments) ? (
              <span className="text-slate-500 dark:text-slate-400">{label}</span>
            ) : (
              <Link
                href={href}
                className="text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
              >
                {label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
