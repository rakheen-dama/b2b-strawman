"use client";

import { cn } from "@/lib/utils";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReportExecutionResponse, ColumnDefinition } from "@/lib/api/reports";

interface ReportResultsProps {
  response: ReportExecutionResponse | null;
  isLoading: boolean;
  onPageChange: (page: number) => void;
}

function formatLabel(key: string): string {
  return key
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/^./, (c) => c.toUpperCase());
}

function formatCellValue(value: unknown, column: ColumnDefinition): string {
  if (value == null) {
    return "\u2014";
  }

  switch (column.type) {
    case "decimal":
    case "currency":
      return Number(value).toFixed(2);
    case "integer":
      return String(value);
    case "date":
      return String(value);
    default:
      return String(value);
  }
}

function isNumericType(type: string): boolean {
  return type === "decimal" || type === "currency" || type === "integer";
}

export function ReportResults({
  response,
  isLoading,
  onPageChange,
}: ReportResultsProps) {
  if (isLoading && !response) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i}>
              <CardContent className="pt-6">
                <Skeleton className="mb-2 h-4 w-20" />
                <Skeleton className="h-8 w-24" />
              </CardContent>
            </Card>
          ))}
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!response) {
    return null;
  }

  const summaryEntries = Object.entries(response.summary);
  const { pagination } = response;

  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      {summaryEntries.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {summaryEntries.map(([key, value]) => (
            <Card key={key}>
              <CardContent className="pt-6">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  {formatLabel(key)}
                </p>
                <p className="mt-1 font-mono text-2xl tabular-nums text-slate-900 dark:text-slate-100">
                  {value != null ? String(value) : "\u2014"}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Data Table */}
      <Card>
        <CardContent className="pt-6">
          {response.rows.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">
              No data found for the selected parameters.
            </p>
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    {response.columns.map((col) => (
                      <TableHead
                        key={col.key}
                        className={cn(isNumericType(col.type) && "text-right")}
                      >
                        {col.label}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {response.rows.map((row, rowIdx) => (
                    <TableRow key={rowIdx}>
                      {response.columns.map((col) => (
                        <TableCell
                          key={col.key}
                          className={cn(isNumericType(col.type) && "text-right")}
                        >
                          {formatCellValue(row[col.key], col)}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {/* Pagination */}
              {pagination.totalPages > 0 && (
                <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4 dark:border-slate-800">
                  <p className="text-sm text-slate-600 dark:text-slate-400">
                    Page {pagination.page + 1} of {pagination.totalPages}
                  </p>
                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pagination.page === 0}
                      onClick={() => onPageChange(pagination.page - 1)}
                    >
                      <ChevronLeft className="mr-1 size-4" />
                      Previous
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pagination.page + 1 >= pagination.totalPages}
                      onClick={() => onPageChange(pagination.page + 1)}
                    >
                      Next
                      <ChevronRight className="ml-1 size-4" />
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
