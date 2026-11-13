"use client";

import { useMemo, useState, useTransition } from "react";
import { cn } from "@/lib/utils";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { HelpTip } from "@/components/help-tip";
import { UtilizationChart } from "@/components/profitability/utilization-chart";
import { formatCurrencySafe } from "@/lib/format";
import { getUtilization } from "@/app/(app)/org/[slug]/profitability/actions";
import type { UtilizationResponse, MemberUtilizationRecord } from "@/lib/types";
import { ChevronDown, ChevronRight } from "lucide-react";

interface UtilizationTableProps {
  initialData: UtilizationResponse;
  initialFrom: string;
  initialTo: string;
}

type SortField = "name" | "totalHours" | "billableHours" | "nonBillableHours" | "utilization";

const DATE_INPUT_CLASS = "border-input bg-card text-foreground rounded-md border px-2 py-1 text-sm";

function sortMembers(
  members: MemberUtilizationRecord[],
  field: SortField,
  dir: "asc" | "desc"
): MemberUtilizationRecord[] {
  return [...members].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case "name":
        cmp = a.memberName.localeCompare(b.memberName);
        break;
      case "totalHours":
        cmp = a.totalHours - b.totalHours;
        break;
      case "billableHours":
        cmp = a.billableHours - b.billableHours;
        break;
      case "nonBillableHours":
        cmp = a.nonBillableHours - b.nonBillableHours;
        break;
      case "utilization":
        cmp = a.utilizationPercent - b.utilizationPercent;
        break;
    }
    return dir === "asc" ? cmp : -cmp;
  });
}

const getRowId = (member: MemberUtilizationRecord) => member.memberId;

const hasBreakdown = (member: MemberUtilizationRecord) => (member.currencies?.length ?? 0) > 0;

function CurrencyBreakdown({ member }: { member: MemberUtilizationRecord }) {
  return (
    <div className="flex flex-wrap gap-4 py-1 pl-8 text-sm">
      {member.currencies.map((c) => (
        <div key={c.currency} className="flex items-center gap-2">
          <span className="text-foreground font-medium">{c.currency}:</span>
          <span className="text-muted-foreground">
            Billable {formatCurrencySafe(c.billableValue, c.currency)}
          </span>
          <span className="text-muted-foreground">|</span>
          <span className="text-muted-foreground">
            Cost {formatCurrencySafe(c.costValue, c.currency)}
          </span>
        </div>
      ))}
    </div>
  );
}

const renderBreakdown = (member: MemberUtilizationRecord) => <CurrencyBreakdown member={member} />;

const hours = (value: number) => <span className="tabular-nums">{value.toFixed(1)}h</span>;

// Module-level (stable across renders): per-row UI state (expanded) is read from `row`, never from
// closure state, so the cells (and the focused expander button) are not remounted on a toggle or a
// sort.
const columns = defineColumns<MemberUtilizationRecord>([
  {
    id: "expand",
    enableHiding: false,
    meta: { label: "", headerClassName: "w-8", cellClassName: "w-8" },
    header: () => null,
    cell: ({ row }) => {
      if (!row.getCanExpand()) return null;
      const expanded = row.getIsExpanded();
      return (
        <button
          type="button"
          onClick={row.getToggleExpandedHandler()}
          className="text-muted-foreground hover:text-foreground"
          aria-label={expanded ? "Collapse currency breakdown" : "Expand currency breakdown"}
        >
          {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        </button>
      );
    },
  },
  {
    id: "name",
    enableHiding: false,
    meta: { label: "Name", sortKey: "name", cellClassName: "font-medium" },
    cell: ({ row }) => row.original.memberName,
  },
  {
    id: "totalHours",
    meta: { label: "Total Hours", sortKey: "totalHours", align: "end" },
    cell: ({ row }) => hours(row.original.totalHours),
  },
  {
    id: "billableHours",
    meta: { label: "Billable", sortKey: "billableHours", align: "end" },
    cell: ({ row }) => hours(row.original.billableHours),
  },
  {
    id: "nonBillableHours",
    meta: { label: "Non-Billable", sortKey: "nonBillableHours", align: "end" },
    cell: ({ row }) => hours(row.original.nonBillableHours),
  },
  {
    id: "utilization",
    meta: { label: "Utilization %", sortKey: "utilization", headerClassName: "w-[200px]" },
    cell: ({ row }) => {
      const percent = row.original.utilizationPercent;
      return (
        <div className="flex items-center gap-2">
          <div className="bg-muted relative h-2 w-full overflow-hidden rounded-full">
            <div
              className={cn(
                "h-full rounded-full transition-all",
                percent >= 70 ? "bg-success" : percent >= 40 ? "bg-warning" : "bg-muted-foreground"
              )}
              style={{ width: `${Math.min(percent, 100)}%` }}
            />
          </div>
          <span className="text-muted-foreground w-12 text-right text-sm tabular-nums">
            {percent.toFixed(1)}%
          </span>
        </div>
      );
    },
  },
]);

export function UtilizationTable({ initialData, initialFrom, initialTo }: UtilizationTableProps) {
  const [data, setData] = useState<UtilizationResponse>(initialData);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [sortField, setSortField] = useState<SortField>("utilization");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("desc");
    }
  }

  function handleDateChange(newFrom: string, newTo: string) {
    setFrom(newFrom);
    setTo(newTo);
    startTransition(async () => {
      const result = await getUtilization(newFrom, newTo);
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.error ?? "Failed to load utilization data.");
      }
    });
  }

  // A new array only when the rows or the sort change: `data` identity is navigation for the kit.
  const sorted = useMemo(
    () => sortMembers(data.members, sortField, sortDir),
    [data.members, sortField, sortDir]
  );

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle className="flex items-center gap-2">
            Team Utilization
            <HelpTip code="dashboard.utilisation" />
          </CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="util-from" className="text-muted-foreground text-sm">
              From
            </label>
            <input
              id="util-from"
              type="date"
              value={from}
              onChange={(e) => handleDateChange(e.target.value, to)}
              className={DATE_INPUT_CLASS}
            />
            <label htmlFor="util-to" className="text-muted-foreground text-sm">
              To
            </label>
            <input
              id="util-to"
              type="date"
              value={to}
              onChange={(e) => handleDateChange(from, e.target.value)}
              className={DATE_INPUT_CLASS}
            />
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <UtilizationChart members={data.members} />
        {isPending && <div className="text-muted-foreground mb-4 text-sm">Loading...</div>}
        {error && <div className="text-destructive mb-4 text-sm">{error}</div>}
        <DataTable
          columns={columns}
          data={sorted}
          getRowId={getRowId}
          sort={{ key: sortField, dir: sortDir, onSort: toggleSort }}
          getRowCanExpand={hasBreakdown}
          renderExpandedRow={renderBreakdown}
          empty={
            <p className="text-muted-foreground py-8 text-center text-sm">
              No utilization data for this period
            </p>
          }
          columnVisibility={false}
          stickyHeader={false}
          frame={false}
          pageSize={null}
        />
      </CardContent>
    </Card>
  );
}
