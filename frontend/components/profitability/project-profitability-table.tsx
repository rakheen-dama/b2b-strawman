"use client";

import { useMemo, useState, useTransition } from "react";
import { cn } from "@/lib/utils";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { HelpTip } from "@/components/help-tip";
import { ProfitabilityRankingChart } from "@/components/profitability/profitability-ranking-chart";
import {
  exportProfitabilityCsvAction,
  getOrgProfitability,
} from "@/app/(app)/org/[slug]/profitability/actions";
import type { OrgProfitabilityResponse, MatterProfitabilitySummary } from "@/lib/types";
import { Download } from "lucide-react";

interface ProjectProfitabilityTableProps {
  initialData: OrgProfitabilityResponse;
  initialFrom: string;
  initialTo: string;
  /** Org default currency for the margin ranking; `null` → no ranking chart (table unchanged). */
  defaultCurrency: string | null;
}

type SortField =
  | "projectName"
  | "customerName"
  | "billableHours"
  | "billableValue"
  | "costValue"
  | "margin"
  | "marginPercent";

function sortProjects(
  projects: MatterProfitabilitySummary[],
  field: SortField,
  dir: "asc" | "desc"
): MatterProfitabilitySummary[] {
  return [...projects].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case "projectName":
        cmp = a.matterName.localeCompare(b.matterName);
        break;
      case "customerName":
        cmp = (a.clientName ?? "").localeCompare(b.clientName ?? "");
        break;
      case "billableHours":
        cmp = a.billableHours - b.billableHours;
        break;
      case "billableValue":
        cmp = a.billableValue - b.billableValue;
        break;
      case "costValue":
        cmp = (a.costValue ?? 0) - (b.costValue ?? 0);
        break;
      case "margin":
        cmp = (a.margin ?? 0) - (b.margin ?? 0);
        break;
      case "marginPercent":
        cmp = (a.marginPercent ?? 0) - (b.marginPercent ?? 0);
        break;
    }
    return dir === "asc" ? cmp : -cmp;
  });
}

const DATE_INPUT_CLASS = "border-input bg-card text-foreground rounded-md border px-2 py-1 text-sm";

/** A matter appears once per currency. */
const getRowId = (matter: MatterProfitabilitySummary) => `${matter.matterId}-${matter.currency}`;

const signClass = (value: number) =>
  value >= 0 ? "text-success-foreground" : "text-danger-foreground";

const notAvailable = <Badge variant="neutral">N/A</Badge>;

/**
 * Money in the body font, not the kit's mono default: same typography as before the DataTable
 * migration and as the Client Profitability table below, and mono figures widen this nowrap table
 * enough (~33px at 1440) to cut off Margin (643C OBS D1).
 */
const MONEY_CLASS = "font-sans";

const columns = defineColumns<MatterProfitabilitySummary>([
  {
    id: "projectName",
    enableHiding: false,
    meta: { label: "Matter", sortKey: "projectName", cellClassName: "font-medium" },
    cell: ({ row }) => row.original.matterName,
  },
  {
    id: "customerName",
    meta: { label: "Client", sortKey: "customerName" },
    cell: ({ row }) => row.original.clientName || <EmptyCell />,
  },
  {
    id: "currency",
    meta: { label: "Currency" },
    cell: ({ row }) => row.original.currency,
  },
  {
    id: "billableHours",
    meta: { label: "Billable Hours", sortKey: "billableHours", align: "end" },
    cell: ({ row }) => (
      <span className="tabular-nums">{row.original.billableHours.toFixed(1)}h</span>
    ),
  },
  {
    id: "billableValue",
    meta: { label: "Revenue", sortKey: "billableValue", align: "end" },
    cell: ({ row }) => (
      <MoneyCell
        amount={row.original.billableValue}
        currency={row.original.currency}
        className={MONEY_CLASS}
      />
    ),
  },
  {
    id: "costValue",
    meta: { label: "Cost", sortKey: "costValue", align: "end" },
    // A missing cost reads "N/A" (no cost rate), not the "—" placeholder of a missing client.
    cell: ({ row }) =>
      row.original.costValue != null ? (
        <MoneyCell
          amount={row.original.costValue}
          currency={row.original.currency}
          className={MONEY_CLASS}
        />
      ) : (
        "N/A"
      ),
  },
  {
    id: "margin",
    meta: { label: "Margin", sortKey: "margin", align: "end" },
    cell: ({ row }) =>
      row.original.margin != null ? (
        <MoneyCell
          amount={row.original.margin}
          currency={row.original.currency}
          className={cn(MONEY_CLASS, signClass(row.original.margin))}
        />
      ) : (
        notAvailable
      ),
  },
  {
    id: "marginPercent",
    meta: { label: "Margin %", sortKey: "marginPercent", align: "end" },
    cell: ({ row }) =>
      row.original.marginPercent != null ? (
        <span className={cn("tabular-nums", signClass(row.original.marginPercent))}>
          {row.original.marginPercent.toFixed(1)}%
        </span>
      ) : (
        notAvailable
      ),
  },
]);

export function ProjectProfitabilityTable({
  initialData,
  initialFrom,
  initialTo,
  defaultCurrency,
}: ProjectProfitabilityTableProps) {
  const [data, setData] = useState<OrgProfitabilityResponse>(initialData);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [sortField, setSortField] = useState<SortField>("margin");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();
  const [isExporting, setIsExporting] = useState(false);

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
      const result = await getOrgProfitability(newFrom, newTo);
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.error ?? "Failed to load profitability data.");
      }
    });
  }

  async function handleExportCsv() {
    setIsExporting(true);
    setError(null);
    try {
      const result = await exportProfitabilityCsvAction(from, to);
      if (result.data != null) {
        const blob = new Blob([result.data], { type: "text/csv" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        // Same naming rule as the backend's Content-Disposition filename.
        a.download = from && to ? `profitability-${from}-to-${to}.csv` : "profitability.csv";
        a.click();
        URL.revokeObjectURL(url);
      } else {
        setError(result.error ?? "Failed to export CSV.");
      }
    } finally {
      setIsExporting(false);
    }
  }

  // A new array only when the rows or the sort change: `data` identity is navigation for the kit.
  const sorted = useMemo(
    () => sortProjects(data.matters, sortField, sortDir),
    [data.matters, sortField, sortDir]
  );

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle className="flex items-center gap-2">
            Matter Profitability
            <HelpTip code="dashboard.profitability" />
          </CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="prof-from" className="text-muted-foreground text-sm">
              From
            </label>
            <input
              id="prof-from"
              type="date"
              value={from}
              onChange={(e) => handleDateChange(e.target.value, to)}
              className={DATE_INPUT_CLASS}
            />
            <label htmlFor="prof-to" className="text-muted-foreground text-sm">
              To
            </label>
            <input
              id="prof-to"
              type="date"
              value={to}
              onChange={(e) => handleDateChange(from, e.target.value)}
              className={DATE_INPUT_CLASS}
            />
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportCsv}
              disabled={isExporting}
              data-testid="profitability-export-csv"
            >
              <Download className="mr-1.5 size-4" />
              {isExporting ? "Exporting…" : "Export CSV"}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <ProfitabilityRankingChart matters={data.matters} defaultCurrency={defaultCurrency} />
        {isPending && <div className="text-muted-foreground mb-4 text-sm">Loading...</div>}
        {error && (
          <div role="alert" className="text-destructive mb-4 text-sm">
            {error}
          </div>
        )}
        <DataTable
          columns={columns}
          data={sorted}
          getRowId={getRowId}
          sort={{ key: sortField, dir: sortDir, onSort: toggleSort }}
          empty={
            <p className="text-muted-foreground py-8 text-center text-sm">
              No matter profitability data for this period
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
