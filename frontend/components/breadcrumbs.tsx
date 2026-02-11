"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ChevronRight } from "lucide-react";

const SEGMENT_LABELS: Record<string, string> = {
  dashboard: "Dashboard",
  projects: "Projects",
  team: "Team",
  settings: "Settings",
  billing: "Billing",
};

interface BreadcrumbsProps {
  slug: string;
}

export function Breadcrumbs({ slug }: BreadcrumbsProps) {
  const pathname = usePathname();

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
        className="text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-white transition-colors"
      >
        {slug}
      </Link>

      {segments.map((segment, index) => {
        const isLast = index === segments.length - 1;
        const label = SEGMENT_LABELS[segment] ?? segment;

        // Build the href for intermediate segments
        const href = `/org/${slug}/${segments.slice(0, index + 1).join("/")}`;

        return (
          <span key={href} className="flex items-center gap-1">
            <ChevronRight className="h-3.5 w-3.5 text-olive-400" />
            {isLast ? (
              <span className="font-medium text-olive-900 dark:text-white">
                {label}
              </span>
            ) : (
              <Link
                href={href}
                className="text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-white transition-colors"
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
