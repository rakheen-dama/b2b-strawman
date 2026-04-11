"use client";

import { useCallback, useRef, useState } from "react";
import { Upload, FileText, CheckCircle2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import type { BankStatementResponse } from "@/lib/types";

interface BankStatementUploadProps {
  accountId: string;
  onUploadComplete: (statement: BankStatementResponse) => void;
  uploadAction: (
    accountId: string,
    file: File
  ) => Promise<{
    success: boolean;
    data?: BankStatementResponse;
    error?: string;
  }>;
}

export function BankStatementUpload({
  accountId,
  onUploadComplete,
  uploadAction,
}: BankStatementUploadProps) {
  const [uploading, setUploading] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const [result, setResult] = useState<BankStatementResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileSelect = useCallback(
    async (file: File) => {
      setFileName(file.name);
      setError(null);
      setResult(null);
      setUploading(true);

      try {
        const response = await uploadAction(accountId, file);
        if (response.success && response.data) {
          setResult(response.data);
          onUploadComplete(response.data);
        } else {
          setError(response.error ?? "Upload failed");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to upload statement");
      } finally {
        setUploading(false);
      }
    },
    [accountId, onUploadComplete, uploadAction]
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFileSelect(file);
      e.target.value = ""; // Reset so the same file can be re-selected
    },
    [handleFileSelect]
  );

  return (
    <Card data-testid="bank-statement-upload">
      <CardHeader>
        <CardTitle>Upload Bank Statement</CardTitle>
        <CardDescription>Upload a CSV bank statement file to begin reconciliation</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center gap-3">
          <input
            ref={inputRef}
            type="file"
            accept=".csv"
            onChange={handleInputChange}
            className="hidden"
            data-testid="file-input"
          />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => inputRef.current?.click()}
            disabled={uploading}
          >
            <Upload className="mr-1.5 size-3.5" />
            {uploading ? "Uploading..." : fileName ? "Choose Different File" : "Choose CSV File"}
          </Button>
          {fileName && !uploading && (
            <span className="flex items-center gap-1.5 text-sm text-slate-600 dark:text-slate-400">
              <FileText className="size-4" />
              {fileName}
            </span>
          )}
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            <AlertCircle className="size-4 shrink-0" />
            {error}
          </div>
        )}

        {result && (
          <div
            className="rounded-md border border-green-200 bg-green-50 p-4 dark:border-green-900 dark:bg-green-950"
            data-testid="upload-result"
          >
            <div className="flex items-center gap-2 text-sm font-medium text-green-700 dark:text-green-300">
              <CheckCircle2 className="size-4" />
              Statement imported successfully
            </div>
            <div className="mt-2 grid grid-cols-2 gap-2 text-sm text-green-600 dark:text-green-400">
              <span>Lines imported: {result.lineCount}</span>
              <span>File: {result.fileName}</span>
              <span>
                Period: {result.periodStart} to {result.periodEnd}
              </span>
              <span>Format: {result.format}</span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
