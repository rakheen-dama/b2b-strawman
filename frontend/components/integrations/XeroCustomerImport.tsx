"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Upload, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { importXeroCustomersAction } from "@/app/(app)/org/[slug]/settings/integrations/xero/actions";
import type { XeroCustomerImportResult } from "@/lib/types";

interface XeroCustomerImportProps {
  slug: string;
}

export function XeroCustomerImport({ slug }: XeroCustomerImportProps) {
  const [isImporting, setIsImporting] = useState(false);
  const [importResult, setImportResult] = useState<XeroCustomerImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleImport() {
    setIsImporting(true);
    setError(null);
    try {
      const result = await importXeroCustomersAction(slug);
      if (result.success && result.data) {
        setImportResult(result.data);
        toast.success(
          `Imported ${result.data.created} customers from Xero.`
        );
      } else {
        setError(result.error ?? "Failed to import customers.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsImporting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
            <Upload className="size-5 text-slate-600 dark:text-slate-400" />
          </div>
          <div>
            <CardTitle className="font-display text-lg">Customer Import</CardTitle>
            <CardDescription>
              Import your existing Xero contacts as Kazi customers.
            </CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <p className="text-destructive text-sm" role="alert">
            {error}
          </p>
        )}

        {importResult ? (
          <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950">
            <div className="flex items-center gap-2 mb-3">
              <CheckCircle2 className="size-5 text-teal-600 dark:text-teal-400" />
              <span className="font-medium text-teal-800 dark:text-teal-200">
                Import Complete
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div className="text-slate-600 dark:text-slate-400">Created</div>
              <div className="font-mono tabular-nums text-slate-900 dark:text-slate-100">
                {importResult.created}
              </div>
              <div className="text-slate-600 dark:text-slate-400">Duplicates skipped</div>
              <div className="font-mono tabular-nums text-slate-900 dark:text-slate-100">
                {importResult.skippedDuplicate}
              </div>
              <div className="text-slate-600 dark:text-slate-400">Skipped (no email)</div>
              <div className="font-mono tabular-nums text-slate-900 dark:text-slate-100">
                {importResult.skippedNoEmail}
              </div>
              <div className="text-slate-600 dark:text-slate-400">Total processed</div>
              <div className="font-mono tabular-nums font-medium text-slate-900 dark:text-slate-100">
                {importResult.total}
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-slate-600 dark:text-slate-400">
              This will import all contacts from your connected Xero organization as
              customers in Kazi. Duplicates will be skipped automatically.
            </p>
            <Button onClick={handleImport} disabled={isImporting}>
              {isImporting ? (
                <>
                  <Upload className="mr-2 size-4 animate-pulse" />
                  Importing...
                </>
              ) : (
                <>
                  <Upload className="mr-2 size-4" />
                  Import Customers from Xero
                </>
              )}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
