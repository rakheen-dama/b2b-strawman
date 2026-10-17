"use client";

import { useState, useTransition } from "react";
import Link from "next/link";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@/components/ui/table";
import { Switch } from "@/components/ui/switch";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { toggleRuleAction } from "@/app/(app)/org/[slug]/settings/automations/actions";
import { toast } from "sonner";
import { RelativeDate } from "@/components/ui/relative-date";
import { Zap } from "lucide-react";
import type { AutomationRuleResponse } from "@/lib/api/automations";

interface RuleListProps {
  slug: string;
  rules: AutomationRuleResponse[];
  canManage: boolean;
}

/**
 * Always-on automation rules list (ADR-334): rules are installed by packs and can only be
 * enabled or disabled here — the rule editor and template gallery were removed in Phase 86.
 */
export function RuleList({ slug, rules, canManage }: RuleListProps) {
  const [isPending, startTransition] = useTransition();
  const [optimisticToggles, setOptimisticToggles] = useState<Record<string, boolean>>({});

  function handleToggle(ruleId: string, currentEnabled: boolean) {
    // Optimistic update
    setOptimisticToggles((prev) => ({ ...prev, [ruleId]: !currentEnabled }));

    startTransition(async () => {
      const result = await toggleRuleAction(slug, ruleId);
      if (result.success) {
        toast.success("Rule toggled successfully");
        // Clear optimistic state — server re-render will provide fresh data
        setOptimisticToggles((prev) => {
          const next = { ...prev };
          delete next[ruleId];
          return next;
        });
      } else {
        // Revert optimistic state
        setOptimisticToggles((prev) => {
          const next = { ...prev };
          delete next[ruleId];
          return next;
        });
        toast.error(result.error ?? "Failed to toggle rule");
      }
    });
  }

  return (
    <div className="space-y-6">
      {/* Rules Table or Empty State */}
      {rules.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <Zap className="size-12 text-slate-300 dark:text-slate-700" />
          <h2 className="font-display mt-4 text-lg text-slate-900 dark:text-slate-100">
            No automation rules yet
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Automation rules are installed with your practice packs and will appear here.
          </p>
        </div>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Trigger</TableHead>
                <TableHead>Enabled</TableHead>
                <TableHead>Last Updated</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.map((rule) => (
                <TableRow key={rule.id} data-testid="automation-row">
                  <TableCell>
                    <p className="font-medium text-slate-950 dark:text-slate-50">{rule.name}</p>
                    {rule.description && (
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {rule.description}
                      </p>
                    )}
                  </TableCell>
                  <TableCell>
                    <TriggerTypeBadge triggerType={rule.triggerType} />
                  </TableCell>
                  <TableCell>
                    <Switch
                      checked={optimisticToggles[rule.id] ?? rule.enabled}
                      size="sm"
                      onCheckedChange={() =>
                        handleToggle(rule.id, optimisticToggles[rule.id] ?? rule.enabled)
                      }
                      disabled={!canManage || isPending}
                      aria-label={`Toggle ${rule.name}`}
                    />
                  </TableCell>
                  <TableCell>
                    <span className="text-sm text-slate-600 dark:text-slate-400">
                      {rule.updatedAt ? <RelativeDate iso={rule.updatedAt} /> : "Never"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <span className="font-mono text-sm text-slate-600 tabular-nums dark:text-slate-400">
                      {rule.actions.length}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {canManage && (
            <div className="flex justify-end">
              <Link
                href={`/org/${slug}/settings/automations/executions`}
                className="text-sm text-teal-600 underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
              >
                View Execution Log &rarr;
              </Link>
            </div>
          )}
        </>
      )}
    </div>
  );
}
