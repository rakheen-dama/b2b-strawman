"use client";

import { useLayoutEffect, useMemo, useRef, useState, useTransition } from "react";
import Link from "next/link";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { Switch } from "@/components/ui/switch";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { toggleRuleAction } from "@/app/(app)/org/[slug]/settings/connections/_tabs/automations-actions";
import { toast } from "sonner";
import { RelativeDate } from "@/components/ui/relative-date";
import { Zap } from "lucide-react";
import type { AutomationRuleResponse } from "@/lib/api/automations";

/** A table row: the rule plus the render-time switch state (so the columns never re-memo). */
interface RuleRow {
  rule: AutomationRuleResponse;
  enabled: boolean;
  disabled: boolean;
}

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

  // TanStack renders each `cell` as a component: the column array keeps its identity so a toggle
  // does not remount the Switch. (Focus is still lost while the toggle is pending, because the
  // switch is disabled then — pre-existing, F-665F-A1.) Cells reach the handler through a ref
  // refreshed after each render (F-665A-4); checked/disabled travel in the rows (DA-21).
  const actions = useRef({ toggle: handleToggle });
  useLayoutEffect(() => {
    actions.current = { toggle: handleToggle };
  });

  const columns = useMemo(
    () =>
      defineColumns<RuleRow>([
        {
          id: "name",
          enableHiding: false,
          // K-114: the name/description cell wraps instead of widening the table.
          meta: { label: "Name", cellClassName: "whitespace-normal max-w-[28rem]" },
          cell: ({ row }) => (
            <>
              <p className="text-foreground font-medium">{row.original.rule.name}</p>
              {row.original.rule.description && (
                <p className="text-muted-foreground text-xs">{row.original.rule.description}</p>
              )}
            </>
          ),
        },
        {
          id: "trigger",
          meta: { label: "Trigger" },
          cell: ({ row }) => <TriggerTypeBadge triggerType={row.original.rule.triggerType} />,
        },
        {
          id: "enabled",
          meta: { label: "Enabled" },
          cell: ({ row }) => (
            <Switch
              checked={row.original.enabled}
              size="sm"
              onCheckedChange={() =>
                actions.current.toggle(row.original.rule.id, row.original.enabled)
              }
              disabled={row.original.disabled}
              aria-label={`Toggle ${row.original.rule.name}`}
            />
          ),
        },
        {
          id: "updatedAt",
          meta: { label: "Last Updated" },
          cell: ({ row }) => (
            <span className="text-muted-foreground text-sm">
              {row.original.rule.updatedAt ? (
                <RelativeDate iso={row.original.rule.updatedAt} />
              ) : (
                "Never"
              )}
            </span>
          ),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions" },
          cell: ({ row }) => (
            <span className="text-muted-foreground font-mono text-sm tabular-nums">
              {row.original.rule.actions.length}
            </span>
          ),
        },
      ]),
    []
  );

  const rows = useMemo(
    () =>
      rules.map((rule) => ({
        rule,
        enabled: optimisticToggles[rule.id] ?? rule.enabled,
        disabled: !canManage || isPending,
      })),
    [rules, optimisticToggles, canManage, isPending]
  );

  return (
    <div className="space-y-6">
      <DataTable
        columns={columns}
        data={rows}
        getRowId={(row) => row.rule.id}
        rowTestId="automation-row"
        pageSize={null}
        columnVisibility={false}
        stickyHeader={false}
        empty={
          <EmptyState
            variant="default"
            icon={Zap}
            title="No automation rules yet"
            description="Automation rules are installed with your practice packs and will appear here."
          />
        }
      />
      {/* DA-20: this second link stays (dropping it is an IA call, backlog). Empty list → none. */}
      {canManage && rules.length > 0 && (
        <div className="flex justify-end">
          <Link
            href={`/org/${slug}/settings/connections/automations/executions`}
            className="text-sm text-(--button-accent) underline-offset-4 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
          >
            View Execution Log &rarr;
          </Link>
        </div>
      )}
    </div>
  );
}
