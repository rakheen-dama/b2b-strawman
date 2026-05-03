"use client";

import { useEffect, useState } from "react";
import { ChevronDown, Download, FileText, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  countAuditEventsAction,
  exportAuditCsvAction,
  exportAuditPdfAction,
} from "@/app/(app)/org/[slug]/settings/audit-log/actions";
import type { AuditEventFilter } from "@/lib/api/audit-events";

const PDF_CAP = 10_000;
const COUNT_DEBOUNCE_MS = 400;

export interface ExportDropdownProps {
  filter: AuditEventFilter;
}

export function ExportDropdown({ filter }: ExportDropdownProps) {
  const [busy, setBusy] = useState<"csv" | "pdf" | null>(null);
  const [count, setCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Debounced count fetch when the filter changes. Uses JSON.stringify as a
  // stable dep — filter is plain serialisable data and presets/inputs replace
  // it shallowly so deep compare is cheap.
  const filterKey = JSON.stringify(filter);
  useEffect(() => {
    let cancelled = false;
    const handle = setTimeout(async () => {
      try {
        const res = await countAuditEventsAction(filter);
        if (!cancelled && res.data != null) {
          setCount(res.data);
        }
      } catch {
        // Leave count null — PDF item stays enabled (graceful fallback).
      }
    }, COUNT_DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- filterKey is a stable JSON-string proxy for the filter object.
  }, [filterKey]);

  const pdfDisabled = count !== null && count > PDF_CAP;
  const pdfTooltip = pdfDisabled
    ? `Narrow the date range — PDF export limited to ${PDF_CAP.toLocaleString()} events.`
    : null;

  function downloadBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function handleCsv() {
    setBusy("csv");
    setError(null);
    try {
      const res = await exportAuditCsvAction(filter);
      if (res.data != null) {
        const blob = new Blob([res.data], { type: "text/csv" });
        downloadBlob(blob, `audit-log-${new Date().toISOString().slice(0, 10)}.csv`);
      } else {
        setError(res.error ?? "Failed to export CSV.");
      }
    } finally {
      setBusy(null);
    }
  }

  async function handlePdf() {
    setBusy("pdf");
    setError(null);
    try {
      const res = await exportAuditPdfAction(filter);
      if (res.data != null) {
        const byteCharacters = atob(res.data);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        const blob = new Blob([byteArray], { type: "application/pdf" });
        downloadBlob(blob, `audit-log-${new Date().toISOString().slice(0, 10)}.pdf`);
      } else {
        const detail = res.detail;
        const detailMsg =
          detail?.rowCount && detail?.cap
            ? ` (${detail.rowCount.toLocaleString()} rows; cap ${detail.cap.toLocaleString()})`
            : "";
        setError((res.error ?? "Failed to export PDF.") + detailMsg);
      }
    } finally {
      setBusy(null);
    }
  }

  return (
    <TooltipProvider>
      <div className="inline-flex items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="outline"
              size="sm"
              disabled={busy !== null}
              data-testid="export-dropdown-trigger"
            >
              {busy ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <Download className="mr-1.5 size-4" />
              )}
              Export
              <ChevronDown className="ml-1.5 size-3" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem
              data-testid="export-dropdown-csv"
              disabled={busy !== null}
              onSelect={(e) => {
                e.preventDefault();
                void handleCsv();
              }}
            >
              {busy === "csv" ? (
                <Loader2 className="mr-1.5 size-4 animate-spin" />
              ) : (
                <FileText className="mr-1.5 size-4" />
              )}
              Download CSV
            </DropdownMenuItem>
            {pdfDisabled ? (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="block">
                    <DropdownMenuItem
                      data-testid="export-dropdown-pdf"
                      disabled
                      onSelect={(e) => e.preventDefault()}
                    >
                      <FileText className="mr-1.5 size-4" />
                      Download PDF
                    </DropdownMenuItem>
                  </span>
                </TooltipTrigger>
                <TooltipContent>{pdfTooltip}</TooltipContent>
              </Tooltip>
            ) : (
              <DropdownMenuItem
                data-testid="export-dropdown-pdf"
                disabled={busy !== null}
                onSelect={(e) => {
                  e.preventDefault();
                  void handlePdf();
                }}
              >
                {busy === "pdf" ? (
                  <Loader2 className="mr-1.5 size-4 animate-spin" />
                ) : (
                  <FileText className="mr-1.5 size-4" />
                )}
                Download PDF
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
        {error && (
          <span className="text-xs text-red-600" role="alert">
            {error}
          </span>
        )}
      </div>
    </TooltipProvider>
  );
}
