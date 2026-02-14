"use client";

import { Fragment, useState, useTransition } from "react";
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
import { formatCurrencySafe } from "@/lib/format";
import { getUtilization } from "@/app/(app)/org/[slug]/profitability/actions";
import type {
  UtilizationResponse,
  MemberUtilizationRecord,
} from "@/lib/types";
import { ArrowUpDown, ChevronDown, ChevronRight } from "lucide-react";

interface UtilizationTableProps {
  initialData: UtilizationResponse;
  initialFrom: string;
  initialTo: string;
}

type SortField =
  | "name"
  | "totalHours"
  | "billableHours"
  | "nonBillableHours"
  | "utilization";

function sortMembers(
  members: MemberUtilizationRecord[],
  field: SortField,
  dir: "asc" | "desc",
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

export function UtilizationTable({
  initialData,
  initialFrom,
  initialTo,
}: UtilizationTableProps) {
  const [data, setData] = useState<UtilizationResponse>(initialData);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [sortField, setSortField] = useState<SortField>("utilization");
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

  function toggleExpand(memberId: string) {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(memberId)) {
        next.delete(memberId);
      } else {
        next.add(memberId);
      }
      return next;
    });
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

  const sorted = sortMembers(data.members, sortField, sortDir);

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <CardTitle>Team Utilization</CardTitle>
          <div className="flex items-center gap-2">
            <label htmlFor="util-from" className="text-sm text-olive-600 dark:text-olive-400">
              From
            </label>
            <input
              id="util-from"
              type="date"
              value={from}
              onChange={(e) => handleDateChange(e.target.value, to)}
              className="rounded-md border border-olive-300 bg-white px-2 py-1 text-sm text-olive-900 dark:border-olive-700 dark:bg-olive-950 dark:text-olive-100"
            />
            <label htmlFor="util-to" className="text-sm text-olive-600 dark:text-olive-400">
              To
            </label>
            <input
              id="util-to"
              type="date"
              value={to}
              onChange={(e) => handleDateChange(from, e.target.value)}
              className="rounded-md border border-olive-300 bg-white px-2 py-1 text-sm text-olive-900 dark:border-olive-700 dark:bg-olive-950 dark:text-olive-100"
            />
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {isPending && (
          <div className="mb-4 text-sm text-olive-500">Loading...</div>
        )}
        {error && (
          <div className="mb-4 text-sm text-red-600 dark:text-red-400">
            {error}
          </div>
        )}
        {sorted.length === 0 ? (
          <p className="py-8 text-center text-sm text-olive-500">
            No utilization data for this period
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8" />
                <TableHead>
                  <button
                    onClick={() => toggleSort("name")}
                    className="inline-flex items-center gap-1 hover:text-olive-900 dark:hover:text-olive-100"
                  >
                    Name
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("totalHours")}
                    className="inline-flex items-center gap-1 hover:text-olive-900 dark:hover:text-olive-100"
                  >
                    Total Hours
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("billableHours")}
                    className="inline-flex items-center gap-1 hover:text-olive-900 dark:hover:text-olive-100"
                  >
                    Billable
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="text-right">
                  <button
                    onClick={() => toggleSort("nonBillableHours")}
                    className="inline-flex items-center gap-1 hover:text-olive-900 dark:hover:text-olive-100"
                  >
                    Non-Billable
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
                <TableHead className="w-[200px]">
                  <button
                    onClick={() => toggleSort("utilization")}
                    className="inline-flex items-center gap-1 hover:text-olive-900 dark:hover:text-olive-100"
                  >
                    Utilization %
                    <ArrowUpDown className="size-3" />
                  </button>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((member) => {
                const hasBreakdown =
                  member.currencies && member.currencies.length > 0;
                const isExpanded = expandedRows.has(member.memberId);
                const percent = member.utilizationPercent;

                return (
                  <Fragment key={member.memberId}>
                    <TableRow>
                      <TableCell className="w-8">
                        {hasBreakdown && (
                          <button
                            onClick={() => toggleExpand(member.memberId)}
                            className="text-olive-500 hover:text-olive-700 dark:hover:text-olive-300"
                            aria-label={
                              isExpanded
                                ? "Collapse currency breakdown"
                                : "Expand currency breakdown"
                            }
                          >
                            {isExpanded ? (
                              <ChevronDown className="size-4" />
                            ) : (
                              <ChevronRight className="size-4" />
                            )}
                          </button>
                        )}
                      </TableCell>
                      <TableCell className="font-medium">
                        {member.memberName}
                      </TableCell>
                      <TableCell className="text-right">
                        {member.totalHours.toFixed(1)}h
                      </TableCell>
                      <TableCell className="text-right">
                        {member.billableHours.toFixed(1)}h
                      </TableCell>
                      <TableCell className="text-right">
                        {member.nonBillableHours.toFixed(1)}h
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <div className="relative h-2 w-full overflow-hidden rounded-full bg-olive-100 dark:bg-olive-800">
                            <div
                              className={cn(
                                "h-full rounded-full transition-all",
                                percent >= 70
                                  ? "bg-green-500"
                                  : percent >= 40
                                    ? "bg-amber-500"
                                    : "bg-olive-400",
                              )}
                              style={{
                                width: `${Math.min(percent, 100)}%`,
                              }}
                            />
                          </div>
                          <span className="w-12 text-right text-sm text-olive-600 dark:text-olive-400">
                            {percent.toFixed(1)}%
                          </span>
                        </div>
                      </TableCell>
                    </TableRow>
                    {hasBreakdown && isExpanded && (
                      <TableRow
                        key={`${member.memberId}-breakdown`}
                        className="bg-olive-50 dark:bg-olive-900/50"
                      >
                        <TableCell />
                        <TableCell colSpan={5}>
                          <div className="flex flex-wrap gap-4 py-1 text-sm">
                            {member.currencies.map((c) => (
                              <div
                                key={c.currency}
                                className="flex items-center gap-2"
                              >
                                <span className="font-medium text-olive-700 dark:text-olive-300">
                                  {c.currency}:
                                </span>
                                <span className="text-olive-600 dark:text-olive-400">
                                  Billable{" "}
                                  {formatCurrencySafe(
                                    c.billableValue,
                                    c.currency,
                                  )}
                                </span>
                                <span className="text-olive-500">|</span>
                                <span className="text-olive-600 dark:text-olive-400">
                                  Cost{" "}
                                  {formatCurrencySafe(c.costValue, c.currency)}
                                </span>
                              </div>
                            ))}
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
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
