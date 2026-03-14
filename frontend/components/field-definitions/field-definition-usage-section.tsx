"use client";

import { ChevronRight } from "lucide-react";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import type { FieldUsageInfo } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";

interface FieldUsageSectionProps {
  fieldUsage: FieldUsageInfo;
  usageOpen: boolean;
  onUsageOpenChange: (open: boolean) => void;
  totalUsageCount: number;
}

export function FieldUsageSection({
  fieldUsage,
  usageOpen,
  onUsageOpenChange,
  totalUsageCount,
}: FieldUsageSectionProps) {
  return (
    <>
      <Collapsible open={usageOpen} onOpenChange={onUsageOpenChange}>
        <CollapsibleTrigger className="flex w-full items-center gap-2 rounded-md border border-slate-200 p-3 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800">
          <ChevronRight
            className={`size-4 shrink-0 text-slate-400 transition-transform ${usageOpen ? "rotate-90" : ""}`}
          />
          <span>
            Used in {fieldUsage.templates.length}{" "}
            {fieldUsage.templates.length === 1 ? "template" : "templates"}
            , {fieldUsage.clauses.length}{" "}
            {fieldUsage.clauses.length === 1 ? "clause" : "clauses"}
          </span>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <div className="mt-2 space-y-2 rounded-md border border-slate-200 p-3 dark:border-slate-700">
            {fieldUsage.templates.length > 0 && (
              <div>
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Templates
                </p>
                <ul className="space-y-1">
                  {fieldUsage.templates.map((t) => (
                    <li
                      key={t.id}
                      className="text-sm text-slate-600 dark:text-slate-300"
                    >
                      {t.name}
                      <span className="ml-1.5 text-xs text-slate-400">
                        ({t.category.replace(/_/g, " ").toLowerCase()})
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {fieldUsage.clauses.length > 0 && (
              <div>
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Clauses
                </p>
                <ul className="space-y-1">
                  {fieldUsage.clauses.map((c) => (
                    <li
                      key={c.id}
                      className="text-sm text-slate-600 dark:text-slate-300"
                    >
                      {c.title}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {fieldUsage.templates.length === 0 &&
              fieldUsage.clauses.length === 0 && (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  This field is not referenced by any templates or clauses.
                </p>
              )}
          </div>
        </CollapsibleContent>
      </Collapsible>

      {/* Warning when field is used and about to be deactivated */}
      {totalUsageCount > 0 && (
        <p className="text-sm text-amber-600 dark:text-amber-400">
          Warning: This field is used in {fieldUsage.templates.length}{" "}
          {fieldUsage.templates.length === 1 ? "template" : "templates"}{" "}
          and {fieldUsage.clauses.length}{" "}
          {fieldUsage.clauses.length === 1 ? "clause" : "clauses"}
          . Changes may affect generated documents.
        </p>
      )}
    </>
  );
}
