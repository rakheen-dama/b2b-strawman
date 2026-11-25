"use client";

import { useCallback, useRef, useState } from "react";
import { Upload, FileText, CheckCircle2, AlertCircle } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { BankStatementResponse } from "@/lib/types";
import { SERVER_ACTION_FILE_MAX_LABEL, fileTooLargeError } from "@/lib/server-action-upload";

/** A refused upload whose statement ends on the same date as one already imported (FU-6b). */
interface DuplicateStatement {
  existingStatementId: string;
  existingFileName: string;
}

interface BankStatementUploadProps {
  accountId: string;
  onUploadComplete: (statement: BankStatementResponse) => void;
  /**
   * FU-6b: the statement a new upload replaces — the wizard's statement when the user uploads a
   * corrected file. The backend retires it (its matches released, a draft reconciliation moved to
   * the new statement) or refuses with the reason.
   */
  replacesStatementId?: string;
  uploadAction: (
    accountId: string,
    file: File,
    replacesStatementId?: string
  ) => Promise<{
    success: boolean;
    data?: BankStatementResponse;
    error?: string;
    duplicate?: DuplicateStatement;
  }>;
}

export function BankStatementUpload({
  accountId,
  onUploadComplete,
  replacesStatementId,
  uploadAction,
}: BankStatementUploadProps) {
  const [uploading, setUploading] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const [result, setResult] = useState<BankStatementResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // FU-6b: a refused duplicate waiting for the user's "Replace existing statement"
  const [pending, setPending] = useState<{ file: File; duplicate: DuplicateStatement } | null>(
    null
  );
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = useCallback(
    async (file: File, replaces: string | undefined) => {
      setError(null);
      setResult(null);
      setUploading(true);
      try {
        const response = await uploadAction(accountId, file, replaces);
        if (response.success && response.data) {
          setResult(response.data);
          onUploadComplete(response.data);
        } else if (response.duplicate && !replaces) {
          // FU-6b D-2: one upload replaces at most one statement. An upload that already replaces
          // one (a wizard re-upload, or a confirmed replace) never offers a second replace — its
          // refusal is shown as it stands.
          setPending({ file, duplicate: response.duplicate });
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

  const handleFileSelect = useCallback(
    (file: File) => {
      setFileName(file.name);
      // FR-3: a file over the server-action limit would never reach the action — refuse it here
      const tooLarge = fileTooLargeError(file.size);
      if (tooLarge) {
        setResult(null);
        setError(tooLarge);
        return;
      }
      void upload(file, replacesStatementId);
    },
    [upload, replacesStatementId]
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) handleFileSelect(file);
      e.target.value = ""; // Reset so the same file can be re-selected
    },
    [handleFileSelect]
  );

  const confirmReplace = useCallback(() => {
    if (!pending) return;
    const { file, duplicate } = pending;
    setPending(null);
    void upload(file, duplicate.existingStatementId);
  }, [pending, upload]);

  return (
    <Card data-testid="bank-statement-upload">
      <CardHeader>
        <CardTitle>Upload Bank Statement</CardTitle>
        <CardDescription>
          Upload a CSV bank statement file of up to {SERVER_ACTION_FILE_MAX_LABEL} to begin
          reconciliation
        </CardDescription>
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
          <div
            className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
            data-testid="upload-error"
          >
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

      <AlertDialog
        open={pending !== null}
        onOpenChange={(open) => {
          if (!open) setPending(null);
        }}
      >
        <AlertDialogContent data-testid="replace-statement-dialog">
          <AlertDialogHeader>
            <AlertDialogTitle>Replace existing statement?</AlertDialogTitle>
            <AlertDialogDescription>
              Statement {pending?.duplicate.existingFileName || "already imported"} of this trust
              account ends on the same date. Replacing it retires that statement: its matches are
              released so this statement can be matched, and a draft reconciliation that uses it
              moves to this statement. The replaced statement is kept for the audit trail.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel variant="plain">Cancel</AlertDialogCancel>
            <Button type="button" onClick={confirmReplace} data-testid="replace-statement-confirm">
              Replace existing statement
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
