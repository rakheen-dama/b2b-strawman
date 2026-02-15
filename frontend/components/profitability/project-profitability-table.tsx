"use client";

import { useState, useTransition } from "react";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
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
import { ArrowUpDown } from "lucide-react";

interface ProjectProfitabilityTableProps {
  initialData: OrgProfitabilityResponse;
  initialFrom: string;
  initialTo: string;
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
  projects: ProjectProfitabilitySummary[],
  field: SortField,
  dir: "asc" | "desc",
): ProjectProfitabilitySummary[] {
  return [...projects].sort((a, b) => {
    let cmp = 0;
    switch (field) {
      case "projectName":
        cmp = a.projectName.localeCompare(b.projectName);
        break;
      case "customerName":
        cmp = (a.customerName ?? "").localeCompare(b.customerName ?? "");
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

export function ProjectProfitabilityTable({
  initialData,
  initialFrom,
  initialTo,
}: ProjectProfitabilityTableProps) {
  const [data, setData] = useState<OrgProfitabilityResponse>(initialData);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [sortField, setSortField] = useState<SortField>("margin");
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
      const result = await getOrgProfitability(newFrom, newTo);
      if (result.data) {
        setData(result.data);
        setError(null);
      } else {
        setError(result.error ?? "Failed to load profitability data.");
      }
    });
  }

  const sorted = sortProjects(data.projects, sortField, sortDir);

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle>Project Profitability</CardTitle>
          <div className="flex items-center gap-2">
            <label
              htmlFor="prof-from"
              className="text-sm text-slate-600 dark:text-slate-400"
            >
              From
            </label>
            <input
              id="prof-from"
              type="date"
              value={from}
              onChange={(e) => handleDateChange(e.target.value, to)}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            />
            <label
              htmlFor="prof-to"
              className="text-sm text-slate-600 dark:text-slate-400"
            >
              To
            </label>
            <input
              id="prof-to"
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
            No project profitability data for this period
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>
                  <button
                    onClick={() => toggleSort("projectName")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Project
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
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
                    onClick={() => toggleSort("billableHours")}
                    className="inline-flex items-center gap-1 hover:text-slate-900 dark:hover:text-slate-100"
                  >
                    Billable Hours
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
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
              {sorted.map((project) => (
                <TableRow key={`${project.projectId}-${project.currency}`}>
                  <TableCell className="font-medium">
                    {project.projectName}
                  </TableCell>
                  <TableCell>
                    {project.customerName ? (
                      project.customerName
                    ) : (
                      <span className="text-slate-400">&mdash;</span>
                    )}
                  </TableCell>
                  <TableCell>{project.currency}</TableCell>
                  <TableCell className="text-right">
                    {project.billableHours.toFixed(1)}h
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(project.billableValue, project.currency)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCurrencySafe(project.costValue, project.currency)}
                  </TableCell>
                  <TableCell className="text-right">
                    {project.margin != null ? (
                      <span
                        className={cn(
                          project.margin >= 0
                            ? "text-green-600 dark:text-green-400"
                            : "text-red-600 dark:text-red-400",
                        )}
                      >
                        {formatCurrency(project.margin, project.currency)}
                      </span>
                    ) : (
                      <Badge variant="neutral">N/A</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
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
                      <Badge variant="neutral">N/A</Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}
