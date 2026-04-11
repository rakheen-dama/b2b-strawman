"use client";

import { useState, useTransition } from "react";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { ExecutionStatusBadge } from "@/components/automations/execution-status-badge";
import { TriggerTypeBadge } from "@/components/automations/trigger-type-badge";
import { ExecutionDetail } from "@/components/automations/execution-detail";
import { computeDuration } from "@/lib/format";
import { RelativeDate } from "@/components/ui/relative-date";
import { Check, X, RefreshCw } from "lucide-react";
import { fetchExecutionsAction } from "@/app/(app)/org/[slug]/settings/automations/actions";
import type {
  AutomationExecutionResponse,
  ExecutionStatus,
  TriggerType,
  PaginatedResponse,
} from "@/lib/api/automations";

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

  function handleRowClick(execution: AutomationExecutionResponse) {
    setSelectedExecution(execution);
    setDetailOpen(true);
  }

  const items = executions.content;
  const totalPages = executions.page.totalPages;

  return (
    <div className="space-y-4">
      {/* Filters */}
      <div className="flex items-center gap-3">
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
        <Button variant="outline" size="sm" onClick={() => handleRefresh()} disabled={isPending}>
          <RefreshCw className={`mr-1 size-4 ${isPending ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {/* Table */}
      {items.length === 0 ? (
        <p className="py-8 text-center text-sm text-slate-500 italic dark:text-slate-400">
          No executions found.
        </p>
      ) : (
        <div className="rounded-md border border-slate-200 dark:border-slate-700">
          <Table>
            <TableHeader>
              <TableRow>
                {!ruleId && <TableHead>Rule</TableHead>}
                <TableHead>Trigger</TableHead>
                <TableHead>Triggered At</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Conditions</TableHead>
                <TableHead>Actions</TableHead>
                <TableHead>Duration</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((execution) => (
                <TableRow
                  key={execution.id}
                  className="cursor-pointer"
                  onClick={() => handleRowClick(execution)}
                >
                  {!ruleId && <TableCell className="font-medium">{execution.ruleName}</TableCell>}
                  <TableCell>
                    <TriggerTypeBadge triggerType={execution.triggerEventType as TriggerType} />
                  </TableCell>
                  <TableCell className="text-sm text-slate-600 dark:text-slate-400">
                    <RelativeDate iso={execution.startedAt} />
                  </TableCell>
                  <TableCell>
                    <ExecutionStatusBadge status={execution.status} />
                  </TableCell>
                  <TableCell>
                    {execution.conditionsMet ? (
                      <Check className="size-4 text-emerald-600" />
                    ) : (
                      <X className="size-4 text-red-500" />
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-sm tabular-nums">
                    {execution.actionExecutions.length}
                  </TableCell>
                  <TableCell className="font-mono text-sm tabular-nums">
                    {computeDuration(execution.startedAt, execution.completedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-slate-500 dark:text-slate-400">
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
