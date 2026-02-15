"use client";

import { Fragment, useMemo, useState, useTransition } from "react";
import { cn } from "@/lib/utils";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { formatCurrency, formatCurrencySafe } from "@/lib/format";
import { getOrgProfitability } from "@/app/(app)/org/[slug]/profitability/actions";
import type {
  OrgProfitabilityResponse,
  ProjectProfitabilitySummary,
} from "@/lib/types";
import { ArrowUpDown, ChevronDown, ChevronRight } from "lucide-react";

interface CustomerProfitabilitySectionProps {
  initialData: OrgProfitabilityResponse;
  initialFrom: string;
  initialTo: string;
}

interface CustomerAggregate {
  customerName: string;
  currency: string;
  billableHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
  projects: ProjectProfitabilitySummary[];
}

function aggregateByCustomer(
  projects: ProjectProfitabilitySummary[],
): CustomerAggregate[] {
  const map = new Map<string, CustomerAggregate>();

  for (const p of projects) {
    const name = p.customerName ?? "Unassigned";
    const key = `${name}::${p.currency}`;

    if (!map.has(key)) {
      map.set(key, {
        customerName: name,
        currency: p.currency,
        billableHours: 0,
        billableValue: 0,
        costValue: null,
        margin: null,
        marginPercent: null,
        projects: [],
      });
    }

    const agg = map.get(key)!;
    agg.billableHours += p.billableHours;
    agg.billableValue += p.billableValue;
    agg.projects.push(p);

    if (p.costValue != null) {
      agg.costValue = (agg.costValue ?? 0) + p.costValue;
    }
  }

  // Compute margin and marginPercent for each aggregate
  for (const agg of map.values()) {
    if (agg.costValue != null) {
      agg.margin = agg.billableValue - agg.costValue;
      agg.marginPercent =
        agg.billableValue > 0
          ? (agg.margin / agg.billableValue) * 100
          : 0;
    }
  }

  return Array.from(map.values());
}

type SortField =
  | "customerName"
  | "billableValue"
  | "costValue"
  | "margin"
  | "marginPercent";

function sortCustomers(
  customers: CustomerAggregate[],
  field: SortField,
  dir: "asc" | "desc",
): CustomerAggregate[] {
  return [...customers].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case "customerName":
        cmp = a.customerName.localeCompare(b.customerName);
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

export function CustomerProfitabilitySection({
  initialData,
  initialFrom,
  initialTo,
}: CustomerProfitabilitySectionProps) {
  const [data, setData] = useState<OrgProfitabilityResponse>(initialData);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [sortField, setSortField] = useState<SortField>("billableValue");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
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

  function toggleExpand(key: string) {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
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
        setError(result.error ?? "Failed to load customer profitability data.");
      }
    });
  }

  const aggregated = useMemo(() => aggregateByCustomer(data.projects), [data]);
  const sorted = useMemo(
    () => sortCustomers(aggregated, sortField, sortDir),
    [aggregated, sortField, sortDir],
  );

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle>Customer Profitability</CardTitle>
          <div className="flex items-center gap-2">
            <label
              htmlFor="cust-prof-from"
              className="text-sm text-slate-600 dark:text-slate-400"
            >
              From
            </label>
            <input
              id="cust-prof-from"
              type="date"
              value={from}
              onChange={(e) => handleDateChange(e.target.value, to)}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            />
            <label
              htmlFor="cust-prof-to"
              className="text-sm text-slate-600 dark:text-slate-400"
            >
              To
            </label>
            <input
              id="cust-prof-to"
              type="date"
              value={to}
              onChange={(e) => handleDateChange(from, e.target.value)}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            />
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {isPending && (
          <div className="mb-4 text-sm text-slate-500">Loading...</div>
        )}
        {error && (
          <div className="mb-4 text-sm text-red-600 dark:text-red-400">
            {error}
          </div>
        )}
        {sorted.length === 0 ? (
          <p className="py-8 text-center text-sm text-slate-500">
            No customer profitability data for this period
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8" />
                <TableHead>
                  <button
                    onClick={() => toggleSort("customerName")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Customer
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead>Currency</TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("billableValue")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Revenue
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("costValue")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Cost
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("margin")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Margin
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("marginPercent")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Margin %
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((customer) => {
                const key = `${customer.customerName}::${customer.currency}`;
                const isExpanded = expandedRows.has(key);
                return (
                  <Fragment key={key}>
                    <TableRow
                      className="cursor-pointer"
                      onClick={() => toggleExpand(key)}
                    >
                      <TableCell className="w-8 px-2">
                        {isExpanded ? (
                          <ChevronDown className="size-4 text-slate-500" />
                        ) : (
                          <ChevronRight className="size-4 text-slate-500" />
                        )}
                      </TableCell>
                      <TableCell className="font-medium">
                        {customer.customerName}
                      </TableCell>
                      <TableCell>{customer.currency}</TableCell>
                      <TableCell className="text-right">
                        {formatCurrency(
                          customer.billableValue,
                          customer.currency,
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        {formatCurrencySafe(
                          customer.costValue,
                          customer.currency,
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        {customer.margin != null ? (
                          <span
                            className={cn(
                              customer.margin >= 0
                                ? "text-green-600 dark:text-green-400"
                                : "text-red-600 dark:text-red-400",
                            )}
                          >
                            {formatCurrency(
                              customer.margin,
                              customer.currency,
                            )}
                          </span>
                        ) : (
                          <span className="text-slate-400">&mdash;</span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        {customer.marginPercent != null ? (
                          <span
                            className={cn(
                              customer.marginPercent >= 0
                                ? "text-green-600 dark:text-green-400"
                                : "text-red-600 dark:text-red-400",
                            )}
                          >
                            {customer.marginPercent.toFixed(1)}%
                          </span>
                        ) : (
                          <span className="text-slate-400">&mdash;</span>
                        )}
                      </TableCell>
                    </TableRow>
                    {isExpanded &&
                      customer.projects.map((project) => (
                        <TableRow
                          key={`${key}::${project.projectId}`}
                          className="bg-slate-50 dark:bg-slate-900/30"
                        >
                          <TableCell />
                          <TableCell className="pl-8 text-sm text-slate-600 dark:text-slate-400">
                            {project.projectName}
                          </TableCell>
                          <TableCell className="text-sm text-slate-500">
                            {project.currency}
                          </TableCell>
                          <TableCell className="text-right text-sm">
                            {formatCurrency(
                              project.billableValue,
                              project.currency,
                            )}
                          </TableCell>
                          <TableCell className="text-right text-sm">
                            {formatCurrencySafe(
                              project.costValue,
                              project.currency,
                            )}
                          </TableCell>
                          <TableCell className="text-right text-sm">
                            {project.margin != null ? (
                              <span
                                className={cn(
                                  project.margin >= 0
                                    ? "text-green-600 dark:text-green-400"
                                    : "text-red-600 dark:text-red-400",
                                )}
                              >
                                {formatCurrency(
                                  project.margin,
                                  project.currency,
                                )}
                              </span>
                            ) : (
                              <span className="text-slate-400">&mdash;</span>
                            )}
                          </TableCell>
                          <TableCell className="text-right text-sm">
                            {project.marginPercent != null ? (
                              <span
                                className={cn(
                                  project.marginPercent >= 0
                                    ? "text-green-600 dark:text-green-400"
                                    : "text-red-600 dark:text-red-400",
                                )}
                              >
                                {project.marginPercent.toFixed(1)}%
                              </span>
                            ) : (
                              <span className="text-slate-400">&mdash;</span>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                  </Fragment>
                );
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
