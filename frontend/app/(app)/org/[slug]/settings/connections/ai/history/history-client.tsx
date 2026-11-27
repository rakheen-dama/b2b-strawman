"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Activity } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { EmptyState } from "@/components/empty-state";
import type { AiExecutionListItem } from "@/lib/api/ai";
import { formatCurrencyFromCents, formatDateInSA } from "@/lib/format";

interface ExecutionHistoryClientProps {
  slug: string;
  executions: AiExecutionListItem[];
  /** Zero-based, as in the URL's `?page=`. */
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

function formatDuration(ms: number | null): string {
  if (ms === null) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatSkillId(skillId: string): string {
  return skillId.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

// No handlers or render-time state in the cells, so the columns are a module constant.
const COLUMNS = defineColumns<AiExecutionListItem>([
  {
    id: "date",
    meta: {
      label: "Date",
      cellClassName: "text-muted-foreground font-mono text-xs tabular-nums",
    },
    cell: ({ row }) => formatDateInSA(row.original.createdAt),
  },
  {
    id: "skill",
    meta: { label: "Skill", cellClassName: "text-foreground text-sm font-medium" },
    cell: ({ row }) => formatSkillId(row.original.skillId),
  },
  {
    id: "target",
    meta: { label: "Target", cellClassName: "text-muted-foreground text-xs" },
    cell: ({ row }) => row.original.entityType,
  },
  {
    id: "status",
    meta: { label: "Status" },
    cell: ({ row }) => (
      <StatusBadgeCell
        label={row.original.status}
        variant={getStatusVariant(row.original.status)}
      />
    ),
  },
  {
    id: "cost",
    meta: { label: "Cost", align: "end", cellClassName: "font-mono text-xs tabular-nums" },
    cell: ({ row }) => formatCurrencyFromCents(row.original.costCents),
  },
  {
    id: "tokens",
    meta: {
      label: "Tokens",
      align: "end",
      cellClassName: "text-muted-foreground font-mono text-xs tabular-nums",
    },
    cell: ({ row }) => (row.original.inputTokens + row.original.outputTokens).toLocaleString(),
  },
  {
    id: "duration",
    meta: {
      label: "Duration",
      align: "end",
      cellClassName: "text-muted-foreground font-mono text-xs tabular-nums",
    },
    cell: ({ row }) => formatDuration(row.original.durationMs),
  },
]);

/**
 * AI execution history (665F): server-paged DataTable, callback-owned (ADR-395) — the pager calls
 * `navigate`, which keeps today's `router.push` inside `startTransition`.
 */
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
      router.push(`/org/${slug}/settings/connections/ai/history${qs ? `?${qs}` : ""}`);
    });
  }

  return (
    // The rows dim while a page change is pending, as before (the kit pager has no pending prop).
    <div aria-busy={isPending || undefined} className={isPending ? "opacity-60" : undefined}>
      <DataTable
        columns={COLUMNS}
        data={executions}
        getRowId={(exec) => exec.id}
        pagination={{ pageIndex: currentPage, pageCount: totalPages, onPageChange: navigate }}
        stickyHeader={false}
        footer={
          totalPages > 1 ? (
            <p className="text-muted-foreground text-xs">
              {totalElements} execution{totalElements !== 1 ? "s" : ""} total
            </p>
          ) : undefined
        }
        empty={
          <EmptyState
            variant="default"
            icon={Activity}
            title="No executions yet"
            description="AI skill executions will appear here once skills are run. Configure your AI profile and execute a skill to get started."
          />
        }
      />
    </div>
  );
}
