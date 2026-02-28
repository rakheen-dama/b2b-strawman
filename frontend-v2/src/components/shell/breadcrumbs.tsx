"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";

const SEGMENT_LABELS: Record<string, string> = {
  dashboard: "Dashboard",
  "my-work": "My Work",
  projects: "Projects",
  customers: "Customers",
  invoices: "Invoices",
  retainers: "Retainers",
  documents: "Documents",
  schedules: "Schedules",
  profitability: "Profitability",
  reports: "Reports",
  team: "Team",
  settings: "Settings",
  billing: "Billing",
  compliance: "Compliance",
  notifications: "Notifications",
};

/** Segments that contain dynamic child routes (e.g. /projects/[id]) */
const PARENT_SEGMENT_FALLBACKS: Record<string, string> = {
  projects: "Project",
  customers: "Customer",
  invoices: "Invoice",
  retainers: "Retainer",
  schedules: "Schedule",
  documents: "Document",
};

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value,
  );
}

interface BreadcrumbsProps {
  slug: string;
}

export function Breadcrumbs({ slug }: BreadcrumbsProps) {
  const pathname = usePathname();

  const prefix = `/org/${slug}/`;
  const relativePath = pathname.startsWith(prefix)
    ? pathname.slice(prefix.length)
    : "";
  const segments = relativePath.split("/").filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      <Link
        href={`/org/${slug}/dashboard`}
        className="text-slate-500 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        {slug}
      </Link>

      {segments.map((segment, index) => {
        const isLast = index === segments.length - 1;
        const parentSegment = index > 0 ? segments[index - 1] : undefined;
        const label =
          SEGMENT_LABELS[segment] ??
          (isUuid(segment) && parentSegment
            ? (PARENT_SEGMENT_FALLBACKS[parentSegment] ?? segment)
            : segment);

        const href = `/org/${slug}/${segments.slice(0, index + 1).join("/")}`;

        return (
          <span key={href} className="flex items-center gap-1">
            <ChevronRight className="h-3.5 w-3.5 text-slate-300 dark:text-slate-600" />
            {isLast ? (
              <span className="font-medium text-slate-900 dark:text-white">
                {label}
              </span>
            ) : (
              <Link
                href={href}
                className="text-slate-500 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
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
