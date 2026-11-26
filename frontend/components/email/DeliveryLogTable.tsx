"use client";

import { useEffect, useState } from "react";
import { getDeliveryLog } from "@/lib/actions/email";
import type { EmailDeliveryLogEntry, EmailDeliveryStatus } from "@/lib/api/email";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { DataTable } from "@/components/data-table/data-table";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { SettingsSection } from "@/components/settings/settings-section";
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
  return date.toLocaleString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const getEntryId = (entry: EmailDeliveryLogEntry) => entry.id;

// F-665E-B2: the kit's cells are nowrap, so a long recipient, template name, message id or error
// widened the table to 1045 px. Those columns wrap (wrap-anywhere for the unbroken email addresses
// and ids) so the table fits its section; the date stays on one line as before.
const WRAP = "whitespace-normal";
const WRAP_ANYWHERE = "wrap-anywhere whitespace-normal";

const COLUMNS = defineColumns<EmailDeliveryLogEntry>([
  {
    id: "date",
    meta: { label: "Date", cellClassName: "text-sm whitespace-nowrap" },
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
  {
    id: "recipient",
    meta: { label: "Recipient", cellClassName: `text-sm ${WRAP_ANYWHERE}` },
    cell: ({ row }) => row.original.recipientEmail,
  },
  {
    id: "template",
    meta: { label: "Template", cellClassName: `text-sm ${WRAP}` },
    cell: ({ row }) => row.original.templateName,
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
    id: "providerMessageId",
    meta: {
      label: "Provider Message ID",
      headerClassName: WRAP,
      cellClassName: `font-mono text-xs ${WRAP_ANYWHERE}`,
    },
    cell: ({ row }) => row.original.providerMessageId ?? "\u2014",
  },
  {
    id: "error",
    meta: { label: "Error", cellClassName: `text-destructive text-sm ${WRAP}` },
    cell: ({ row }) => row.original.errorMessage ?? "\u2014",
  },
]);

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
    return () => {
      cancelled = true;
    };
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
    <SettingsSection>
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
            <span className="text-muted-foreground text-sm">to</span>
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
        {error && <p className="text-destructive text-sm">{error}</p>}

        {/* Table */}
        <DataTable
          columns={COLUMNS}
          data={entries}
          getRowId={getEntryId}
          loading={isLoading}
          columnVisibility={false}
          pageSize={null}
          stickyHeader={false}
          frame={false}
          empty={
            <p className="text-muted-foreground py-8 text-center text-sm">
              No delivery log entries found.
            </p>
          }
        />

        {/* Pagination */}
        {pageInfo.totalPages > 1 && (
          <div className="flex items-center justify-between">
            <p className="text-muted-foreground text-sm">
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
    </SettingsSection>
  );
}
