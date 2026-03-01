"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  FileText,
  MoreHorizontal,
  ChevronUp,
  ChevronDown,
  ChevronsUpDown,
  Search,
} from "lucide-react";
import type { ColumnDef, SortingState } from "@tanstack/react-table";
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";

import type {
  ProposalResponse,
  ProposalStatus,
  FeeModel,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { ProposalStatusBadge } from "@/components/proposals/proposal-status-badge";
import { DataTableEmpty } from "@/components/ui/data-table-empty";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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

function getNumericAmount(p: ProposalResponse): number {
  if (p.feeModel === "FIXED" && p.fixedFeeAmount != null) {
    return p.fixedFeeAmount;
  }
  if (p.feeModel === "RETAINER" && p.retainerAmount != null) {
    return p.retainerAmount;
  }
  return 0;
}

function SortIndicator({ column }: { column: { getIsSorted: () => false | "asc" | "desc" } }) {
  const sorted = column.getIsSorted();
  if (sorted === "asc") return <ChevronUp className="ml-1 inline size-3.5" />;
  if (sorted === "desc") return <ChevronDown className="ml-1 inline size-3.5" />;
  return <ChevronsUpDown className="ml-1 inline size-3.5 opacity-50" />;
}

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
      enableSorting: false,
    },
    {
      accessorKey: "title",
      header: "Title",
      cell: ({ row }) => (
        <span className="text-sm font-medium text-slate-900">
          {row.original.title}
        </span>
      ),
      enableSorting: false,
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
      enableSorting: false,
    },
    {
      id: "amount",
      accessorFn: (row) => getNumericAmount(row),
      header: ({ column }) => (
        <button
          type="button"
          className="flex items-center justify-end w-full text-right"
          onClick={column.getToggleSortingHandler()}
        >
          Amount
          <SortIndicator column={column} />
        </button>
      ),
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
      header: ({ column }) => (
        <button
          type="button"
          className="flex items-center"
          onClick={column.getToggleSortingHandler()}
        >
          Status
          <SortIndicator column={column} />
        </button>
      ),
      cell: ({ row }) => <ProposalStatusBadge status={row.original.status} />,
      size: 110,
    },
    {
      accessorKey: "sentAt",
      header: ({ column }) => (
        <button
          type="button"
          className="flex items-center"
          onClick={column.getToggleSortingHandler()}
        >
          Sent Date
          <SortIndicator column={column} />
        </button>
      ),
      cell: ({ row }) => (
        <span className="text-sm text-slate-500">
          {row.original.sentAt ? formatDate(row.original.sentAt) : "\u2014"}
        </span>
      ),
      size: 130,
      sortingFn: (rowA, rowB) => {
        const a = rowA.original.sentAt ?? "";
        const b = rowB.original.sentAt ?? "";
        return a.localeCompare(b);
      },
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
  /** Hide the status navigation tabs (used when embedded in another page, e.g. customer detail) */
  embeddedMode?: boolean;
}

export function ProposalListTable({
  proposals,
  orgSlug,
  activeStatus = "ALL",
  embeddedMode = false,
}: ProposalListTableProps) {
  const router = useRouter();
  const [searchQuery, setSearchQuery] = useState("");
  const [feeModelFilter, setFeeModelFilter] = useState<FeeModel | "ALL">("ALL");
  const [sorting, setSorting] = useState<SortingState>([
    { id: "sentAt", desc: true },
  ]);

  const filtered = useMemo(() => {
    let result =
      activeStatus === "ALL"
        ? proposals
        : proposals.filter((p) => p.status === activeStatus);

    if (feeModelFilter !== "ALL") {
      result = result.filter((p) => p.feeModel === feeModelFilter);
    }

    if (searchQuery.trim()) {
      const q = searchQuery.trim().toLowerCase();
      result = result.filter((p) => p.title.toLowerCase().includes(q));
    }

    return result;
  }, [proposals, activeStatus, feeModelFilter, searchQuery]);

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
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const hasActiveFilters =
    searchQuery.trim() !== "" || feeModelFilter !== "ALL";

  return (
    <div className="space-y-4">
      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative">
          <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="Search proposals..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="h-9 w-[200px] pl-8 text-sm"
          />
        </div>
        <Select
          value={feeModelFilter}
          onValueChange={(v) => setFeeModelFilter(v as FeeModel | "ALL")}
        >
          <SelectTrigger className="h-9 w-[150px] text-sm">
            <SelectValue placeholder="Fee Model" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Fee Models</SelectItem>
            <SelectItem value="FIXED">Fixed Fee</SelectItem>
            <SelectItem value="HOURLY">Hourly</SelectItem>
            <SelectItem value="RETAINER">Retainer</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* Status filter tabs (hidden in embedded mode to avoid navigating away) */}
      {!embeddedMode && (
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
      )}

      {/* Table or empty state */}
      {filtered.length === 0 ? (
        <DataTableEmpty
          icon={<FileText />}
          title={
            hasActiveFilters
              ? "No proposals match your filters."
              : activeStatus === "ALL"
                ? "No proposals yet"
                : `No ${activeStatus.toLowerCase()} proposals`
          }
          description={
            hasActiveFilters
              ? "Try adjusting your search or filter criteria."
              : activeStatus === "ALL"
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
