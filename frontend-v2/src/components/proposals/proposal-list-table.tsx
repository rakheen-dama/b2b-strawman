"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { FileText, MoreHorizontal } from "lucide-react";
import type { ColumnDef } from "@tanstack/react-table";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

import type {
  ProposalResponse,
  ProposalStatus,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { ProposalStatusBadge } from "@/components/proposals/proposal-status-badge";
import { DataTableEmpty } from "@/components/ui/data-table-empty";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const STATUS_TABS: { label: string; value: ProposalStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Draft", value: "DRAFT" },
  { label: "Sent", value: "SENT" },
  { label: "Accepted", value: "ACCEPTED" },
  { label: "Declined", value: "DECLINED" },
  { label: "Expired", value: "EXPIRED" },
];

const FEE_MODEL_LABELS: Record<string, string> = {
  FIXED: "Fixed Fee",
  HOURLY: "Hourly",
  RETAINER: "Retainer",
};

function buildColumns(
  orgSlug: string,
  onNavigate: (path: string) => void,
): ColumnDef<ProposalResponse, unknown>[] {
  return [
    {
      accessorKey: "proposalNumber",
      header: "Number",
      cell: ({ row }) => (
        <span className="font-mono text-sm tabular-nums">
          {row.original.proposalNumber}
        </span>
      ),
      size: 120,
    },
    {
      accessorKey: "title",
      header: "Title",
      cell: ({ row }) => (
        <span className="text-sm font-medium text-slate-900">
          {row.original.title}
        </span>
      ),
    },
    {
      accessorKey: "feeModel",
      header: "Fee Model",
      cell: ({ row }) => (
        <span className="text-sm text-slate-600">
          {FEE_MODEL_LABELS[row.original.feeModel] ?? row.original.feeModel}
        </span>
      ),
      size: 120,
    },
    {
      id: "amount",
      header: () => <div className="text-right">Amount</div>,
      cell: ({ row }) => {
        const p = row.original;
        let display = "\u2014";
        if (
          p.feeModel === "FIXED" &&
          p.fixedFeeAmount != null &&
          p.fixedFeeCurrency
        ) {
          display = new Intl.NumberFormat("en-US", {
            style: "currency",
            currency: p.fixedFeeCurrency,
          }).format(p.fixedFeeAmount);
        } else if (
          p.feeModel === "RETAINER" &&
          p.retainerAmount != null &&
          p.retainerCurrency
        ) {
          display =
            new Intl.NumberFormat("en-US", {
              style: "currency",
              currency: p.retainerCurrency,
            }).format(p.retainerAmount) + "/mo";
        } else if (p.feeModel === "HOURLY") {
          display = p.hourlyRateNote ?? "Hourly";
        }
        return (
          <div className="text-right font-mono text-sm tabular-nums">
            {display}
          </div>
        );
      },
      size: 140,
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => <ProposalStatusBadge status={row.original.status} />,
      size: 110,
    },
    {
      accessorKey: "sentAt",
      header: "Sent Date",
      cell: ({ row }) => (
        <span className="text-sm text-slate-500">
          {row.original.sentAt ? formatDate(row.original.sentAt) : "\u2014"}
        </span>
      ),
      size: 130,
    },
    {
      id: "actions",
      cell: ({ row }) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={(e) => e.stopPropagation()}
            >
              <MoreHorizontal className="size-4" />
              <span className="sr-only">Actions</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem
              onClick={(e) => {
                e.stopPropagation();
                onNavigate(
                  `/org/${orgSlug}/proposals/${row.original.id}`,
                );
              }}
            >
              View proposal
            </DropdownMenuItem>
            {row.original.status === "DRAFT" && (
              <DropdownMenuItem
                onClick={(e) => {
                  e.stopPropagation();
                  onNavigate(
                    `/org/${orgSlug}/proposals/${row.original.id}/edit`,
                  );
                }}
              >
                Edit
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
      size: 50,
      enableSorting: false,
    },
  ];
}

interface ProposalListTableProps {
  proposals: ProposalResponse[];
  orgSlug: string;
  activeStatus?: ProposalStatus | "ALL";
}

export function ProposalListTable({
  proposals,
  orgSlug,
  activeStatus = "ALL",
}: ProposalListTableProps) {
  const router = useRouter();

  const filtered =
    activeStatus === "ALL"
      ? proposals
      : proposals.filter((p) => p.status === activeStatus);

  const counts: Record<string, number> = { ALL: proposals.length };
  for (const p of proposals) {
    counts[p.status] = (counts[p.status] ?? 0) + 1;
  }

  const columns = useMemo(
    () => buildColumns(orgSlug, (path) => router.push(path)),
    [orgSlug, router],
  );

  const table = useReactTable({
    data: filtered,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="space-y-4">
      {/* Status filter tabs */}
      <div className="flex gap-1 overflow-x-auto border-b border-slate-200">
        {STATUS_TABS.map((tab) => {
          const isActive = tab.value === activeStatus;
          const count = counts[tab.value] ?? 0;
          return (
            <button
              key={tab.value}
              onClick={() => {
                const base = `/org/${orgSlug}/proposals`;
                if (tab.value === "ALL") {
                  router.push(base);
                } else {
                  router.push(`${base}?status=${tab.value}`);
                }
              }}
              className={cn(
                "relative whitespace-nowrap px-3 pb-2.5 pt-1 text-sm font-medium transition-colors",
                isActive
                  ? "text-slate-900"
                  : "text-slate-500 hover:text-slate-700",
              )}
            >
              {tab.label}
              {count > 0 && (
                <span
                  className={cn(
                    "ml-1.5 inline-flex min-w-[18px] items-center justify-center rounded-full px-1 text-[10px] font-medium",
                    isActive
                      ? "bg-slate-200 text-slate-700"
                      : "bg-slate-100 text-slate-500",
                  )}
                >
                  {count}
                </span>
              )}
              {isActive && (
                <span className="absolute inset-x-0 -bottom-px h-0.5 bg-teal-600" />
              )}
            </button>
          );
        })}
      </div>

      {/* Table or empty state */}
      {filtered.length === 0 ? (
        <DataTableEmpty
          icon={<FileText />}
          title={
            activeStatus === "ALL"
              ? "No proposals yet"
              : `No ${activeStatus.toLowerCase()} proposals`
          }
          description={
            activeStatus === "ALL"
              ? "Create your first proposal to get started."
              : undefined
          }
        />
      ) : (
        <Table>
          <TableHeader className="sticky top-0 z-10 bg-white dark:bg-slate-950">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    style={{
                      width:
                        header.getSize() !== 150
                          ? header.getSize()
                          : undefined,
                    }}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow
                key={row.id}
                className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() =>
                  router.push(
                    `/org/${orgSlug}/proposals/${row.original.id}`,
                  )
                }
              >
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>
                    {flexRender(
                      cell.column.columnDef.cell,
                      cell.getContext(),
                    )}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
