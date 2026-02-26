"use client";

import { useEffect, useState } from "react";
import { getDeliveryLog } from "@/lib/actions/email";
import type {
  EmailDeliveryLogEntry,
  EmailDeliveryStatus,
} from "@/lib/api/email";
import { Badge } from "@/components/ui/badge";
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
import { ChevronLeft, ChevronRight, X } from "lucide-react";

function getStatusVariant(
  status: EmailDeliveryStatus
): "success" | "warning" | "destructive" | "neutral" {
  switch (status) {
    case "DELIVERED":
      return "success";
    case "BOUNCED":
      return "warning";
    case "FAILED":
      return "destructive";
    case "RATE_LIMITED":
      return "warning";
    case "SENT":
    default:
      return "neutral";
  }
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface PageInfo {
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export function DeliveryLogTable() {
  const [entries, setEntries] = useState<EmailDeliveryLogEntry[]>([]);
  const [pageInfo, setPageInfo] = useState<PageInfo>({
    totalElements: 0,
    totalPages: 0,
    size: 20,
    number: 0,
  });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const [currentPage, setCurrentPage] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setError(null);

      const result = await getDeliveryLog({
        status: statusFilter !== "ALL" ? (statusFilter as EmailDeliveryStatus) : undefined,
        from: fromDate || undefined,
        to: toDate || undefined,
        page: currentPage,
        size: 20,
      });

      if (cancelled) return;

      if (result.success && result.data) {
        setEntries(result.data.content);
        setPageInfo(result.data.page);
      } else {
        setError(result.error ?? "Failed to load delivery log.");
        setEntries([]);
      }

      setIsLoading(false);
    }

    load();
    return () => { cancelled = true; };
  }, [statusFilter, fromDate, toDate, currentPage]);

  const hasFilters = statusFilter !== "ALL" || fromDate !== "" || toDate !== "";

  function handleStatusChange(value: string) {
    setStatusFilter(value);
    setCurrentPage(0);
  }

  function handleFromDateChange(value: string) {
    setFromDate(value);
    setCurrentPage(0);
  }

  function handleToDateChange(value: string) {
    setToDate(value);
    setCurrentPage(0);
  }

  function clearFilters() {
    setStatusFilter("ALL");
    setFromDate("");
    setToDate("");
    setCurrentPage(0);
  }

  return (
    <div className="space-y-4">
      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3">
        <Select value={statusFilter} onValueChange={handleStatusChange}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="All statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All</SelectItem>
            <SelectItem value="SENT">Sent</SelectItem>
            <SelectItem value="DELIVERED">Delivered</SelectItem>
            <SelectItem value="BOUNCED">Bounced</SelectItem>
            <SelectItem value="FAILED">Failed</SelectItem>
            <SelectItem value="RATE_LIMITED">Rate Limited</SelectItem>
          </SelectContent>
        </Select>

        <div className="flex items-center gap-2">
          <Input
            type="date"
            value={fromDate}
            onChange={(e) => handleFromDateChange(e.target.value)}
            className="w-[160px]"
            aria-label="From date"
          />
          <span className="text-sm text-slate-400">to</span>
          <Input
            type="date"
            value={toDate}
            onChange={(e) => handleToDateChange(e.target.value)}
            className="w-[160px]"
            aria-label="To date"
          />
        </div>

        {hasFilters && (
          <Button variant="ghost" size="sm" onClick={clearFilters}>
            <X className="mr-1 size-4" />
            Clear filters
          </Button>
        )}
      </div>

      {/* Error State */}
      {error && (
        <p className="text-sm text-destructive">{error}</p>
      )}

      {/* Table */}
      <div className="rounded-lg border border-slate-200 dark:border-slate-800">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Date</TableHead>
              <TableHead>Recipient</TableHead>
              <TableHead>Template</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Provider Message ID</TableHead>
              <TableHead>Error</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-slate-500">
                  Loading...
                </TableCell>
              </TableRow>
            ) : entries.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-slate-500">
                  No delivery log entries found.
                </TableCell>
              </TableRow>
            ) : (
              entries.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="whitespace-nowrap text-sm">
                    {formatDate(entry.createdAt)}
                  </TableCell>
                  <TableCell className="text-sm">{entry.recipientEmail}</TableCell>
                  <TableCell className="text-sm">{entry.templateName}</TableCell>
                  <TableCell>
                    <Badge variant={getStatusVariant(entry.status)}>
                      {entry.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-mono text-xs">
                    {entry.providerMessageId ?? "\u2014"}
                  </TableCell>
                  <TableCell className="text-sm text-destructive">
                    {entry.errorMessage ?? "\u2014"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {pageInfo.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-slate-500">
            Page {pageInfo.number + 1} of {pageInfo.totalPages} ({pageInfo.totalElements} total)
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pageInfo.number === 0 || isLoading}
              onClick={() => setCurrentPage(pageInfo.number - 1)}
            >
              <ChevronLeft className="mr-1 size-4" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={pageInfo.number >= pageInfo.totalPages - 1 || isLoading}
              onClick={() => setCurrentPage(pageInfo.number + 1)}
            >
              Next
              <ChevronRight className="ml-1 size-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
