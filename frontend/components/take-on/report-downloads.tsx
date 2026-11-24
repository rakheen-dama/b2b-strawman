"use client";

import { useState } from "react";
import useSWR from "swr";
import { Button } from "@b2mash/ui/button";
import {
  downloadTakeOnReport,
  getTakeOnReport,
  retryReportRender,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import type { TakeOnReportFiles, TakeOnReportFileState } from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";

/** §17.10 X3 — the report files while the drain renders them. */
export const REPORT_FILES_PREPARING = "Preparing the report files…";

/** §17.10 X3 — 409 REPORT_RENDER_FAILED. */
export const REPORT_FILES_FAILED =
  "The report files could not be produced. The report itself is saved and unchanged. Try again, or contact support if it fails again.";

const FORMATS = [
  { format: "pdf", label: "Download PDF" },
  { format: "csv", label: "Download CSV" },
] as const;

type Format = (typeof FORMATS)[number]["format"];

function anyRendering(files: TakeOnReportFiles | undefined) {
  return files?.pdf.status === "RENDERING" || files?.csv.status === "RENDERING";
}

function anyFailed(files: TakeOnReportFiles | undefined) {
  return files?.pdf.status === "FAILED" || files?.csv.status === "FAILED";
}

/** Saves the bytes exactly as stored, so the downloaded file's SHA-256 is the one on record. */
function saveBytes(fileName: string, contentType: string, base64: string) {
  if (typeof URL.createObjectURL !== "function") return;
  const chars = atob(base64);
  const bytes = new Uint8Array(chars.length);
  for (let i = 0; i < chars.length; i++) bytes[i] = chars.charCodeAt(i);
  const url = URL.createObjectURL(new Blob([bytes], { type: contentType }));
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoked a task later: some browsers drop a download whose URL is revoked synchronously.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/**
 * The take-on report's PDF and CSV (Phase 93 657D, §17.3.12): a download button per file when it
 * is ready; "Preparing the report files…" while the drain renders them (the state is re-read every
 * three seconds until none is rendering); the X3 failure text and "Try again" once the render gave
 * up. Used on the trust Results step and in the Trust Reports page's "Take-on reports" list.
 */
export function ReportDownloads({
  reportId,
  files: initialFiles,
}: {
  reportId: string;
  files?: TakeOnReportFiles;
}) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<Format | "retry" | null>(null);
  const report = useSWR(
    `take-on-report-files-${reportId}`,
    async () => {
      const result = await getTakeOnReport(reportId);
      if (!result.success || !result.data) {
        throw new Error(result.error ?? "The take-on report could not be loaded.");
      }
      return result.data.files;
    },
    {
      fallbackData: initialFiles,
      refreshInterval: (latest) => (anyRendering(latest) ? 3000 : 0),
    }
  );
  const files = report.data;

  async function download(format: Format) {
    setError(null);
    setBusy(format);
    try {
      const result = await downloadTakeOnReport(reportId, format);
      if (!result.success || !result.data) {
        setError(result.error ?? "The report file could not be downloaded. Try again.");
        if (result.code === "REPORT_RENDER_FAILED") await report.mutate();
        return;
      }
      if (result.data.status === "RENDERING") {
        await report.mutate();
        return;
      }
      saveBytes(result.data.fileName, result.data.contentType, result.data.base64);
    } finally {
      setBusy(null);
    }
  }

  async function retry() {
    setError(null);
    setBusy("retry");
    try {
      const result = await retryReportRender(reportId);
      if (!result.success) {
        setError(result.error ?? "The report files could not be prepared. Try again.");
        return;
      }
      await report.mutate(result.data?.files, { revalidate: false });
    } finally {
      setBusy(null);
    }
  }

  const state = (format: Format): TakeOnReportFileState | undefined => files?.[format];

  return (
    <div data-testid="take-on-report-downloads" className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        {FORMATS.filter(({ format }) => state(format)?.status === "READY").map(
          ({ format, label }) => (
            <Button
              key={format}
              type="button"
              variant="outline"
              size="sm"
              disabled={busy !== null}
              onClick={() => download(format)}
              data-testid={`take-on-report-download-${format}`}
            >
              {busy === format ? "Downloading…" : label}
            </Button>
          )
        )}
      </div>
      {anyRendering(files) && (
        <p
          role="status"
          data-testid="take-on-report-files-preparing"
          className="text-sm text-slate-600 dark:text-slate-300"
        >
          {REPORT_FILES_PREPARING}
        </p>
      )}
      {anyFailed(files) && (
        <div className="space-y-2">
          <p
            role="alert"
            data-testid="take-on-report-files-failed"
            className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          >
            {REPORT_FILES_FAILED}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy !== null}
            onClick={retry}
            data-testid="take-on-report-files-retry"
          >
            {busy === "retry" ? "Trying again…" : "Try again"}
          </Button>
        </div>
      )}
      {report.error && !files && (
        <TakeOnError
          message="The report files could not be loaded."
          testId="take-on-report-download-error"
        />
      )}
      {error && <TakeOnError message={error} testId="take-on-report-download-error" />}
    </div>
  );
}
