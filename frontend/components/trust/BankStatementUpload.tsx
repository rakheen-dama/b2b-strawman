"use client";

import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Loader2, Upload, FileText } from "lucide-react";
import { uploadBankStatement } from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import type { BankStatement } from "@/lib/types";

interface BankStatementUploadProps {
  accountId: string;
  onUploadComplete: (statement: BankStatement) => void;
}

export function BankStatementUpload({
  accountId,
  onUploadComplete,
}: BankStatementUploadProps) {
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BankStatement | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0] ?? null;
    setFile(selected);
    setError(null);
    setResult(null);
  }

  async function handleUpload() {
    if (!file) return;

    setError(null);
    setIsUploading(true);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const statement = await uploadBankStatement(accountId, formData);
      setResult(statement);
      onUploadComplete(statement);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to upload bank statement",
      );
    } finally {
      setIsUploading(false);
    }
  }

  return (
    <div className="space-y-4" data-testid="bank-statement-upload">
      <div className="flex items-center gap-3">
        <div className="relative">
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            onChange={handleFileChange}
            className="absolute inset-0 cursor-pointer opacity-0"
            data-testid="file-input"
          />
          <Button type="button" variant="outline" className="pointer-events-none">
            <FileText className="mr-1.5 size-4" />
            {file ? file.name : "Choose CSV file"}
          </Button>
        </div>

        <Button
          onClick={handleUpload}
          disabled={!file || isUploading}
          data-testid="upload-btn"
        >
          {isUploading ? (
            <>
              <Loader2 className="mr-1.5 size-4 animate-spin" />
              Uploading...
            </>
          ) : (
            <>
              <Upload className="mr-1.5 size-4" />
              Upload
            </>
          )}
        </Button>
      </div>

      {error && (
        <p className="text-sm text-destructive" data-testid="upload-error">
          {error}
        </p>
      )}

      {result && (
        <Card data-testid="upload-result">
          <CardContent className="pt-4">
            <h4 className="mb-2 font-medium text-slate-950 dark:text-slate-50">
              Import Successful
            </h4>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <dt className="text-slate-500 dark:text-slate-400">Lines</dt>
              <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                {result.lineCount}
              </dd>
              <dt className="text-slate-500 dark:text-slate-400">File</dt>
              <dd className="text-slate-950 dark:text-slate-50">
                {result.fileName}
              </dd>
              <dt className="text-slate-500 dark:text-slate-400">Period</dt>
              <dd className="text-slate-950 dark:text-slate-50">
                {result.periodStart} to {result.periodEnd}
              </dd>
              <dt className="text-slate-500 dark:text-slate-400">
                Opening Balance
              </dt>
              <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                {result.openingBalance.toLocaleString()}
              </dd>
              <dt className="text-slate-500 dark:text-slate-400">
                Closing Balance
              </dt>
              <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                {result.closingBalance.toLocaleString()}
              </dd>
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
