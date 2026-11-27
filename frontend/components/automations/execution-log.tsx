"use client";

import { useMemo, useState, useTransition } from "react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@b2mash/ui/button";
import { ExecutionStatusBadge } from "@/components/automations/execution-status-badge";
import {
  TriggerTypeBadge,
  triggerTypeFromEvent,
} from "@/components/automations/trigger-type-badge";
import { ExecutionDetail } from "@/components/automations/execution-detail";
import { computeDuration } from "@/lib/format";
import { RelativeDate } from "@/components/ui/relative-date";
import { Check, X, RefreshCw } from "lucide-react";
import { fetchExecutionsAction } from "@/app/(app)/org/[slug]/settings/connections/_tabs/automations-actions";
import type {
  AutomationExecutionResponse,
  ExecutionStatus,
  TriggerType,
  PaginatedResponse,
} from "@/lib/api/automations";

/**
 * The table columns. The detail Sheet opens from a button in the Rule cell (or, when the Rule column
 * is hidden, the Triggered At cell): DataTable has no row-click callback (only `rowHref`), so the
 * opener is a real button — the keyboard path the old clickable `<tr>` never had (DA-15).
 */
function executionColumns(
  showRule: boolean,
  onOpen: (execution: AutomationExecutionResponse) => void
) {
  return defineColumns<AutomationExecutionResponse>([
    ...(showRule
      ? defineColumns<AutomationExecutionResponse>([
          {
            id: "rule",
            enableHiding: false,
            meta: { label: "Rule", cellClassName: "whitespace-normal" },
            cell: ({ row }) => (
              <button
                type="button"
                className="text-left font-medium hover:underline"
                onClick={() => onOpen(row.original)}
              >
                {row.original.ruleName}
              </button>
            ),
          },
        ])
      : []),
    {
      id: "trigger",
      meta: { label: "Trigger" },
      cell: ({ row }) => (
        <TriggerTypeBadge
          triggerType={triggerTypeFromEvent(row.original.triggerEventType) as TriggerType}
        />
      ),
    },
    {
      id: "triggeredAt",
      meta: { label: "Triggered At", cellClassName: "text-muted-foreground text-sm" },
      cell: ({ row }) =>
        showRule ? (
          <RelativeDate iso={row.original.startedAt} />
        ) : (
          <button
            type="button"
            className="text-left hover:underline"
            onClick={() => onOpen(row.original)}
          >
            <RelativeDate iso={row.original.startedAt} />
          </button>
        ),
    },
    {
      id: "status",
      meta: { label: "Status" },
      cell: ({ row }) => <ExecutionStatusBadge status={row.original.status} />,
    },
    {
      id: "conditions",
      meta: { label: "Conditions" },
      cell: ({ row }) =>
        row.original.conditionsMet ? (
          <Check className="size-4 text-emerald-600" />
        ) : (
          <X className="size-4 text-red-500" />
        ),
    },
    {
      id: "actions",
      meta: { label: "Actions", cellClassName: "font-mono text-sm tabular-nums" },
      cell: ({ row }) => row.original.actionExecutions.length,
    },
    {
      id: "duration",
      meta: { label: "Duration", cellClassName: "font-mono text-sm tabular-nums" },
      cell: ({ row }) => computeDuration(row.original.startedAt, row.original.completedAt),
    },
  ]);
}

interface ExecutionLogProps {
  initialExecutions: PaginatedResponse<AutomationExecutionResponse>;
  ruleId?: string;
  rules?: { id: string; name: string }[];
}

export function ExecutionLog({ initialExecutions, ruleId, rules }: ExecutionLogProps) {
  const [executions, setExecutions] =
    useState<PaginatedResponse<AutomationExecutionResponse>>(initialExecutions);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [ruleFilter, setRuleFilter] = useState<string>(ruleId ?? "all");
  const [selectedExecution, setSelectedExecution] = useState<AutomationExecutionResponse | null>(
    null
  );
  const [detailOpen, setDetailOpen] = useState(false);
  const [isPending, startTransition] = useTransition();
  const [currentPage, setCurrentPage] = useState(0);

  function handleRefresh(page?: number) {
    const targetPage = page ?? currentPage;
    startTransition(async () => {
      try {
        const result = await fetchExecutionsAction({
          ruleId: ruleFilter !== "all" ? ruleFilter : undefined,
          status: statusFilter !== "all" ? (statusFilter as ExecutionStatus) : undefined,
          page: targetPage,
          size: 20,
        });
        setExecutions(result);
        setCurrentPage(targetPage);
      } catch {
        // Non-fatal
      }
    });
  }

  function handleStatusChange(value: string) {
    setStatusFilter(value);
    setCurrentPage(0);
    startTransition(async () => {
      try {
        const result = await fetchExecutionsAction({
          ruleId: ruleFilter !== "all" ? ruleFilter : undefined,
          status: value !== "all" ? (value as ExecutionStatus) : undefined,
          page: 0,
          size: 20,
        });
        setExecutions(result);
        setCurrentPage(0);
      } catch {
        // Non-fatal
      }
    });
  }

  function handleRuleChange(value: string) {
    setRuleFilter(value);
    setCurrentPage(0);
    startTransition(async () => {
      try {
        const result = await fetchExecutionsAction({
          ruleId: value !== "all" ? value : undefined,
          status: statusFilter !== "all" ? (statusFilter as ExecutionStatus) : undefined,
          page: 0,
          size: 20,
        });
        setExecutions(result);
        setCurrentPage(0);
      } catch {
        // Non-fatal
      }
    });
  }

  // Memoised once per `ruleId`: TanStack renders each cell as a component, so a new column array
  // would remount every cell. The opener only calls state setters, which are stable.
  const columns = useMemo(
    () =>
      executionColumns(!ruleId, (execution) => {
        setSelectedExecution(execution);
        setDetailOpen(true);
      }),
    [ruleId]
  );

  const items = executions.content;
  const totalPages = executions.page.totalPages;

  const filters = (
    <>
      {!ruleId && rules && rules.length > 0 && (
        <Select value={ruleFilter} onValueChange={handleRuleChange}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder="All Rules" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Rules</SelectItem>
            {rules.map((r) => (
              <SelectItem key={r.id} value={r.id}>
                {r.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
      <Select value={statusFilter} onValueChange={handleStatusChange}>
        <SelectTrigger className="w-[160px]">
          <SelectValue placeholder="All Statuses" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Statuses</SelectItem>
          <SelectItem value="TRIGGERED">Running</SelectItem>
          <SelectItem value="ACTIONS_COMPLETED">Completed</SelectItem>
          <SelectItem value="ACTIONS_FAILED">Failed</SelectItem>
          <SelectItem value="CONDITIONS_NOT_MET">Skipped</SelectItem>
        </SelectContent>
      </Select>
    </>
  );

  return (
    <div className="space-y-4">
      {/* Client-mode rows of the current server page; filters/Refresh in the toolbar. */}
      <DataTable
        columns={columns}
        data={items}
        getRowId={(execution) => execution.id}
        toolbar={{
          filters,
          actions: (
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleRefresh()}
              disabled={isPending}
            >
              <RefreshCw className={`mr-1 size-4 ${isPending ? "animate-spin" : ""}`} />
              Refresh
            </Button>
          ),
        }}
        pageSize={null}
        columnVisibility={false}
        stickyHeader={false}
        empty={
          <p className="text-muted-foreground py-8 text-center text-sm italic">
            No executions found.
          </p>
        }
      />

      {/* Pagination — kept hand-rolled: the kit pager has no slot for "({total} total)"
          (F-665E-12 kit gap; COPY-FREEZE). */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-muted-foreground text-sm">
            Page {currentPage + 1} of {totalPages} ({executions.page.totalElements} total)
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage === 0 || isPending}
              onClick={() => handleRefresh(currentPage - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage >= totalPages - 1 || isPending}
              onClick={() => handleRefresh(currentPage + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Detail Sheet */}
      <ExecutionDetail
        execution={selectedExecution}
        open={detailOpen}
        onOpenChange={setDetailOpen}
      />
    </div>
  );
}
