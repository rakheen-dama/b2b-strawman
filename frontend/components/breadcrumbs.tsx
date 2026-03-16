"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { useTerminology } from "@/lib/terminology";

const SEGMENT_LABELS: Record<string, string> = {
  // Core nav
  dashboard: "Dashboard",
  projects: "Projects",
  team: "Team",
  settings: "Settings",
  resources: "Resources",
  utilization: "Utilization",
  // Settings sub-segments
  billing: "Billing",
  notifications: "Notifications",
  rates: "Rates & Currency",
  tax: "Tax",
  "time-tracking": "Time Tracking",
  "custom-fields": "Custom Fields",
  tags: "Tags",
  templates: "Templates",
  clauses: "Clauses",
  checklists: "Checklists",
  acceptance: "Document Acceptance",
  compliance: "Compliance",
  "project-templates": "Project Templates",
  "project-naming": "Project Naming",
  "request-templates": "Request Templates",
  "request-settings": "Request Settings",
  "batch-billing": "Batch Billing",
  capacity: "Capacity",
  email: "Email",
  automations: "Automations",
  roles: "Roles & Permissions",
  integrations: "Integrations",
  // Additional segments
  "my-work": "My Work",
  schedules: "Recurring Schedules",
  retainers: "Retainers",
};

/** Segments that contain dynamic child routes (e.g. /projects/[id]) */
const PARENT_SEGMENT_FALLBACKS: Record<string, string> = {
  projects: "Project",
  settings: "Settings",
  customers: "Customer",
  invoices: "Invoice",
};

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

interface BreadcrumbsProps {
  slug: string;
}

export function Breadcrumbs({ slug }: BreadcrumbsProps) {
  const pathname = usePathname();
  const { t } = useTerminology();

  // Strip the /org/[slug]/ prefix to get the relative path segments
  const prefix = `/org/${slug}/`;
  const relativePath = pathname.startsWith(prefix)
    ? pathname.slice(prefix.length)
    : "";
  const segments = relativePath.split("/").filter(Boolean);

  if (segments.length === 0) return null;

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      {/* Org slug as root link */}
      <Link
        href={`/org/${slug}/dashboard`}
        className="text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition-colors"
      >
        {slug}
      </Link>

      {segments.map((segment, index) => {
        const isLast = index === segments.length - 1;
        const parentSegment = index > 0 ? segments[index - 1] : undefined;
        const rawLabel =
          SEGMENT_LABELS[segment] ??
          (isUuid(segment) && parentSegment
            ? PARENT_SEGMENT_FALLBACKS[parentSegment] ?? segment
            : segment);
        const label = t(rawLabel);

        // Build the href for intermediate segments
        const href = `/org/${slug}/${segments.slice(0, index + 1).join("/")}`;

        return (
          <span key={href} className="flex items-center gap-1">
            <ChevronRight className="h-3.5 w-3.5 text-slate-400" />
            {isLast ? (
              <span className="font-medium text-slate-900 dark:text-white">
                {label}
              </span>
            ) : (
              <Link
                href={href}
                className="text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition-colors"
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
