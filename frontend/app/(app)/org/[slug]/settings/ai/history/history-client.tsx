"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { ChevronLeft, ChevronRight, Activity } from "lucide-react";
import type { AiExecutionListItem } from "@/lib/api/ai";

interface ExecutionHistoryClientProps {
  slug: string;
  executions: AiExecutionListItem[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
  currentSkillId?: string;
  currentStatus?: string;
}

function getStatusVariant(status: string) {
  switch (status) {
    case "COMPLETED":
      return "success" as const;
    case "FAILED":
      return "destructive" as const;
    case "IN_PROGRESS":
      return "warning" as const;
    default:
      return "neutral" as const;
  }
}

function formatCostZAR(costCents: number): string {
  return `R ${(costCents / 100).toFixed(2)}`;
}

function formatDuration(ms: number | null): string {
  if (ms === null) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatSkillId(skillId: string): string {
  return skillId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function ExecutionHistoryClient({
  slug,
  executions,
  currentPage,
  totalPages,
  totalElements,
  currentSkillId,
  currentStatus,
}: ExecutionHistoryClientProps) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  function navigate(page: number) {
    const params = new URLSearchParams();
    if (currentSkillId) params.set("skillId", currentSkillId);
    if (currentStatus) params.set("status", currentStatus);
    if (page > 0) params.set("page", String(page));
    const qs = params.toString();
    startTransition(() => {
      router.push(`/org/${slug}/settings/ai/history${qs ? `?${qs}` : ""}`);
    });
  }

  if (executions.length === 0 && currentPage === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 px-6 py-12 text-center dark:border-slate-800">
        <Activity className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
        <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
          No executions yet
        </h3>
        <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">
          AI skill executions will appear here once skills are run. Configure your AI profile and
          execute a skill to get started.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Skill</TableHead>
              <TableHead>Target</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Cost</TableHead>
              <TableHead className="text-right">Tokens</TableHead>
              <TableHead className="text-right">Duration</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {executions.map((exec) => (
              <TableRow key={exec.id} className={isPending ? "opacity-60" : undefined}>
                <TableCell className="font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                  {new Date(exec.createdAt).toLocaleDateString()}
                </TableCell>
                <TableCell className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {formatSkillId(exec.skillId)}
                </TableCell>
                <TableCell className="text-xs text-slate-500 dark:text-slate-400">
                  {exec.entityType}
                </TableCell>
                <TableCell>
                  <Badge variant={getStatusVariant(exec.status)}>{exec.status}</Badge>
                </TableCell>
                <TableCell className="text-right font-mono text-xs tabular-nums">
                  {formatCostZAR(exec.costCents)}
                </TableCell>
                <TableCell className="text-right font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                  {(exec.inputTokens + exec.outputTokens).toLocaleString()}
                </TableCell>
                <TableCell className="text-right font-mono text-xs text-slate-600 tabular-nums dark:text-slate-400">
                  {formatDuration(exec.durationMs)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {totalElements} execution{totalElements !== 1 ? "s" : ""} total
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon-sm"
              aria-label="Previous page"
              disabled={currentPage === 0 || isPending}
              onClick={() => navigate(currentPage - 1)}
            >
              <ChevronLeft className="size-4" />
            </Button>
            <span className="text-xs text-slate-600 dark:text-slate-400">
              Page {currentPage + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="icon-sm"
              aria-label="Next page"
              disabled={currentPage >= totalPages - 1 || isPending}
              onClick={() => navigate(currentPage + 1)}
            >
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
