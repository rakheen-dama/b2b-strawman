"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { cn } from "@/lib/utils";

const FILTER_CHIPS = [
  { key: "ACTIVE", label: "Active" },
  { key: "COMPLETED", label: "Completed" },
  { key: "ARCHIVED", label: "Archived" },
  { key: "ALL", label: "All" },
] as const;

interface ProjectStatusFilterProps {
  slug: string;
}

export function ProjectStatusFilter({ slug }: ProjectStatusFilterProps) {
  const searchParams = useSearchParams();
  const currentStatus = searchParams.get("status") ?? "ACTIVE";

  return (
    <div className="flex items-center gap-2" data-testid="project-status-filter">
      {FILTER_CHIPS.map((chip) => {
        const isActive = currentStatus === chip.key;
        const params = new URLSearchParams(searchParams.toString());
        if (chip.key === "ACTIVE") {
          params.delete("status");
        } else {
          params.set("status", chip.key);
        }
        // Preserve other params like view
        const queryString = params.toString();
        const href = `/org/${slug}/projects${queryString ? `?${queryString}` : ""}`;

        return (
          <Link
            key={chip.key}
            href={href}
            className={cn(
              "rounded-full px-3 py-1 text-sm font-medium transition-colors",
              isActive
                ? "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900"
                : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700",
            )}
          >
            {chip.label}
          </Link>
        );
      })}
    </div>
  );
}
