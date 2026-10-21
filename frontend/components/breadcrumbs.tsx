"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";

const SEGMENT_LABELS: Record<string, string> = {
  // Core nav
  dashboard: "Dashboard",
  matters: "Matters",
  team: "Team",
  settings: "Settings",
  // Settings sub-segments
  billing: "Billing",
  notifications: "Notifications",
  rates: "Rates & Currency",
  tax: "Tax",
  "time-tracking": "Time Tracking",
  collections: "Collections",
  "custom-fields": "Custom Fields",
  tags: "Tags",
  templates: "Templates",
  clauses: "Clauses",
  checklists: "Checklists",
  acceptance: "Document Acceptance",
  compliance: "Compliance",
  "project-templates": "Matter Templates",
  "project-naming": "Matter Naming",
  "request-templates": "Request Templates",
  "request-settings": "Request Settings",
  "batch-billing": "Batch Billing",
  email: "Email",
  automations: "Automations",
  roles: "Roles & Permissions",
  integrations: "Integrations",
  // Additional segments
  clients: "Clients",
  "my-work": "My Work",
  // Vertical module pages
  "trust-accounting": "Trust Accounting",
  "conflict-check": "Conflict Check",
  "client-ledgers": "Client Ledgers",
  invoices: "Fee notes",
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
  reviews: "Reviews",
  timesheet: "Timesheet",
  "information-requests": "Information requests",
};

/** Segments that contain dynamic child routes (e.g. /matters/[id]) */
const PARENT_SEGMENT_FALLBACKS: Record<string, string> = {
  matters: "Matter",
  settings: "Settings",
  clients: "Client",
  invoices: "Fee note",
  "client-ledgers": "Client Ledger",
};

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

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
  "settings/templates/[id]",
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
        const label =
          SEGMENT_LABELS[segment] ??
          (isUuid(segment) && parentSegment
            ? (PARENT_SEGMENT_FALLBACKS[parentSegment] ?? segment)
            : segment);

        // Build the href for intermediate segments
        const prefixSegments = segments.slice(0, index + 1);
        const href = `/org/${slug}/${prefixSegments.join("/")}`;

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
