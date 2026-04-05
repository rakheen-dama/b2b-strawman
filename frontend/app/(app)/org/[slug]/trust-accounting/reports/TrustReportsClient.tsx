"use client";

import { useState } from "react";
import { Loader2, Download } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  executeReportAction,
  exportReportCsvAction,
  exportReportPdfAction,
} from "@/app/(app)/org/[slug]/reports/[reportSlug]/actions";

type ParameterType =
  | "date_range"
  | "as_of_date"
  | "client_date_range"
  | "interest_run"
  | "reconciliation"
  | "financial_year";

type OutputFormat = "PDF" | "CSV" | "EXCEL";

interface TrustReportsClientProps {
  reportSlug: string;
  reportName: string;
  parameterType: ParameterType;
  accountId: string | null;
}

function getDefaultDateStr(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function TrustReportsClient({
  reportSlug,
  reportName,
  parameterType,
  accountId,
}: TrustReportsClientProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [format, setFormat] = useState<OutputFormat>("PDF");

  // Parameter state
  const [dateFrom, setDateFrom] = useState(getDefaultDateStr());
  const [dateTo, setDateTo] = useState(getDefaultDateStr());
  const [asOfDate, setAsOfDate] = useState(getDefaultDateStr());
  const [customerId, setCustomerId] = useState("");
  const [interestRunId, setInterestRunId] = useState("");
  const [reconciliationId, setReconciliationId] = useState("");
  const [financialYearEnd, setFinancialYearEnd] = useState(getDefaultDateStr());

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (!newOpen) {
      setError(null);
    }
  }

  function buildParameters(): Record<string, unknown> {
    const params: Record<string, unknown> = {};

    if (accountId) {
      params.trust_account_id = accountId;
    }

    switch (parameterType) {
      case "date_range":
        params.date_from = dateFrom;
        params.date_to = dateTo;
        break;
      case "as_of_date":
        params.as_of_date = asOfDate;
        break;
      case "client_date_range":
        params.customer_id = customerId;
        params.date_from = dateFrom;
        params.date_to = dateTo;
        break;
      case "interest_run":
        params.interest_run_id = interestRunId;
        break;
      case "reconciliation":
        params.reconciliation_id = reconciliationId;
        break;
      case "financial_year":
        params.financial_year_end = financialYearEnd;
        break;
    }

    return params;
  }

  async function handleGenerate() {
    setError(null);
    setIsSubmitting(true);

    try {
      const parameters = buildParameters();

      if (format === "CSV") {
        const result = await exportReportCsvAction(reportSlug, parameters);
        if (result.error) {
          setError(result.error);
        } else if (result.data) {
          // Trigger download
          const blob = new Blob([result.data], { type: "text/csv" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `${reportSlug}.csv`;
          a.click();
          URL.revokeObjectURL(url);
          handleOpenChange(false);
        }
      } else if (format === "PDF") {
        const result = await exportReportPdfAction(reportSlug, parameters);
        if (result.error) {
          setError(result.error);
        } else if (result.data) {
          // result.data is base64 PDF
          const bytes = atob(result.data);
          const arr = new Uint8Array(bytes.length);
          for (let i = 0; i < bytes.length; i++) {
            arr[i] = bytes.charCodeAt(i);
          }
          const blob = new Blob([arr], { type: "application/pdf" });
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `${reportSlug}.pdf`;
          a.click();
          URL.revokeObjectURL(url);
          handleOpenChange(false);
        }
      } else {
        // EXCEL — execute report and get data (falls back to JSON view)
        const result = await executeReportAction(reportSlug, parameters, 0, 1000);
        if (result.error) {
          setError(result.error);
        } else {
          // For Excel, fall back to CSV export since backend may not support xlsx
          const csvResult = await exportReportCsvAction(reportSlug, parameters);
          if (csvResult.error) {
            setError(csvResult.error);
          } else if (csvResult.data) {
            const blob = new Blob([csvResult.data], { type: "text/csv" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `${reportSlug}.csv`;
            a.click();
            URL.revokeObjectURL(url);
            handleOpenChange(false);
          }
        }
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onClick={() => setOpen(true)}
        className="w-full"
      >
        <Download className="mr-1.5 size-4" />
        Generate
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{reportName}</DialogTitle>
            <DialogDescription>
              Configure parameters and select the output format.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {/* Parameter fields based on parameterType */}
            {(parameterType === "date_range" ||
              parameterType === "client_date_range") && (
              <>
                <div className="space-y-2">
                  <Label htmlFor={`${reportSlug}-date-from`}>Date From</Label>
                  <Input
                    id={`${reportSlug}-date-from`}
                    type="date"
                    value={dateFrom}
                    onChange={(e) => setDateFrom(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`${reportSlug}-date-to`}>Date To</Label>
                  <Input
                    id={`${reportSlug}-date-to`}
                    type="date"
                    value={dateTo}
                    onChange={(e) => setDateTo(e.target.value)}
                  />
                </div>
              </>
            )}

            {parameterType === "client_date_range" && (
              <div className="space-y-2">
                <Label htmlFor={`${reportSlug}-customer`}>Client ID</Label>
                <Input
                  id={`${reportSlug}-customer`}
                  placeholder="Client UUID"
                  value={customerId}
                  onChange={(e) => setCustomerId(e.target.value)}
                />
              </div>
            )}

            {parameterType === "as_of_date" && (
              <div className="space-y-2">
                <Label htmlFor={`${reportSlug}-as-of`}>As Of Date</Label>
                <Input
                  id={`${reportSlug}-as-of`}
                  type="date"
                  value={asOfDate}
                  onChange={(e) => setAsOfDate(e.target.value)}
                />
              </div>
            )}

            {parameterType === "interest_run" && (
              <div className="space-y-2">
                <Label htmlFor={`${reportSlug}-run-id`}>Interest Run ID</Label>
                <Input
                  id={`${reportSlug}-run-id`}
                  placeholder="Interest run UUID"
                  value={interestRunId}
                  onChange={(e) => setInterestRunId(e.target.value)}
                />
              </div>
            )}

            {parameterType === "reconciliation" && (
              <div className="space-y-2">
                <Label htmlFor={`${reportSlug}-recon-id`}>
                  Reconciliation ID
                </Label>
                <Input
                  id={`${reportSlug}-recon-id`}
                  placeholder="Reconciliation UUID"
                  value={reconciliationId}
                  onChange={(e) => setReconciliationId(e.target.value)}
                />
              </div>
            )}

            {parameterType === "financial_year" && (
              <div className="space-y-2">
                <Label htmlFor={`${reportSlug}-fye`}>
                  Financial Year End
                </Label>
                <Input
                  id={`${reportSlug}-fye`}
                  type="date"
                  value={financialYearEnd}
                  onChange={(e) => setFinancialYearEnd(e.target.value)}
                />
              </div>
            )}

            {/* Format selector */}
            <div className="space-y-2">
              <Label>Output Format</Label>
              <Select
                value={format}
                onValueChange={(v) => setFormat(v as OutputFormat)}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PDF">PDF</SelectItem>
                  <SelectItem value="CSV">CSV</SelectItem>
                  <SelectItem value="EXCEL">Excel</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => handleOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button onClick={handleGenerate} disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-1.5 size-4 animate-spin" />
                  Generating...
                </>
              ) : (
                "Generate Report"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
