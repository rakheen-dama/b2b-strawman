"use client";

import { useState } from "react";
import { FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { generateStatementPdf } from "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions";

interface PrintStatementButtonProps {
  accountId: string;
  customerId: string;
}

export function PrintStatementButton({ accountId, customerId }: PrintStatementButtonProps) {
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handlePrint() {
    if (!dateFrom || !dateTo) {
      setError("Please select both start and end dates");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const base64Pdf = await generateStatementPdf(accountId, customerId, dateFrom, dateTo);

      // Convert base64 to blob and trigger download
      const byteChars = atob(base64Pdf);
      const byteNumbers = new Array(byteChars.length);
      for (let i = 0; i < byteChars.length; i++) {
        byteNumbers[i] = byteChars.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);

      const link = document.createElement("a");
      link.href = url;
      link.download = `statement-${customerId}-${dateFrom}-to-${dateTo}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch {
      setError("Failed to generate statement. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-wrap items-end gap-3" data-testid="print-statement">
      <div className="flex flex-col gap-1">
        <label
          htmlFor="statement-from"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          From
        </label>
        <Input
          id="statement-from"
          type="date"
          value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          className="w-40"
          data-testid="statement-date-from"
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="statement-to"
          className="text-sm font-medium text-slate-600 dark:text-slate-400"
        >
          To
        </label>
        <Input
          id="statement-to"
          type="date"
          value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
          className="w-40"
          data-testid="statement-date-to"
        />
      </div>

      <Button
        onClick={handlePrint}
        disabled={loading}
        variant="outline"
        size="sm"
        data-testid="print-statement-btn"
      >
        <FileText className="mr-1 size-4" />
        {loading ? "Generating..." : "Print Statement"}
      </Button>

      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </div>
  );
}
