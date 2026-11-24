"use client";

import { useState } from "react";
import useSWR from "swr";
import { Button } from "@b2mash/ui/button";
import { StatCard } from "@b2mash/ui/stat-card";
import { Progress } from "@/components/ui/progress";
import {
  createPortalContacts,
  downloadTakeOnResult,
  fetchTakeOnBatch,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { formatDateTime } from "@/lib/format";
import type { TakeOnBatch, TakeOnPortalContactsResult } from "@/lib/types";
import { saveTakeOnFile, TakeOnError } from "./take-on-feedback";

/** How often the wizard re-reads a committing batch. */
export const COMMIT_POLL_MS = 2000;

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** Rows the commit has finished, and the rows it will process (X3 "Committing… {n} of {m}"). */
export function commitProgress(batch: TakeOnBatch): { done: number; of: number } {
  const { created, skipped, failed, valid } = batch.counts;
  return { done: created + skipped + failed, of: valid };
}

/**
 * Re-reads the batch every COMMIT_POLL_MS while it is COMMITTING and hands each answer to
 * `onBatchChange`; stops once the status changes (completed, or returned to VALIDATED).
 */
export function useTakeOnCommitProgress(
  batch: TakeOnBatch,
  onBatchChange: (batch: TakeOnBatch) => void
) {
  return useSWR(
    batch.status === "COMMITTING" ? `take-on-commit-progress-${batch.id}` : null,
    async () => {
      const result = await fetchTakeOnBatch(batch.id);
      if (!result.success || !result.data) {
        throw new Error(result.error ?? "The take-on progress could not be read.");
      }
      return result.data;
    },
    { refreshInterval: COMMIT_POLL_MS, revalidateOnFocus: false, onSuccess: onBatchChange }
  );
}

function Figure({ n }: { n: number }) {
  return <span className="font-mono tabular-nums">{n.toLocaleString("en-ZA")}</span>;
}

interface ResultsStepProps {
  batch: TakeOnBatch;
  onBatchChange: (batch: TakeOnBatch) => void;
}

/**
 * Commit progress and outcome (Phase 93 655A): "Committing… {n} of {m}" while the background
 * drain runs, then the counts, the result report and — for clients — portal access, which creates
 * portal contacts and sends no email. Every control follows the batch's allowedActions.
 */
export function ResultsStep({ batch, onBatchChange }: ResultsStepProps) {
  const allowed = new Set(batch.allowedActions);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [portalBusy, setPortalBusy] = useState(false);
  const [portalError, setPortalError] = useState<string | null>(null);
  const [portal, setPortal] = useState<TakeOnPortalContactsResult | null>(null);

  if (batch.status === "COMMITTING") {
    const { done, of } = commitProgress(batch);
    return (
      <div className="space-y-3" data-testid="take-on-results">
        <p
          role="status"
          className="text-sm font-medium text-slate-900 dark:text-slate-100"
          data-testid="take-on-commit-progress"
        >
          Committing… {done} of {of}
        </p>
        <Progress
          value={of > 0 ? Math.min(100, (done / of) * 100) : 0}
          aria-label="Take-on commit progress"
          className="max-w-md"
        />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          The commit runs in the background. You can leave this page and come back.
        </p>
      </div>
    );
  }

  if (batch.status !== "COMPLETED") {
    return (
      <p className="text-sm text-slate-600 dark:text-slate-400" data-testid="take-on-results">
        Nothing has been committed yet.
      </p>
    );
  }

  const counts = batch.counts;
  // Clients and matters skip only duplicates; the balance datasets also skip nil balances.
  const skippedLabel =
    batch.dataset === "CLIENTS" || batch.dataset === "MATTERS" ? "Duplicate — skipped" : "Skipped";

  async function download() {
    setDownloadError(null);
    setDownloading(true);
    try {
      const result = await downloadTakeOnResult(batch.id);
      if (!result.success || !result.data) {
        setDownloadError(result.error ?? "The result report could not be downloaded. Try again.");
        return;
      }
      saveTakeOnFile(result.data.fileName, result.data.content, { bom: true });
    } finally {
      setDownloading(false);
    }
  }

  async function createPortalAccess() {
    setPortalError(null);
    setPortalBusy(true);
    try {
      const result = await createPortalContacts(batch.id);
      if (!result.success || !result.data) {
        setPortalError(result.error ?? "Portal access could not be created.");
        return;
      }
      setPortal(result.data);
      onBatchChange(result.data.batch);
    } finally {
      setPortalBusy(false);
    }
  }

  return (
    <div className="space-y-6" data-testid="take-on-results">
      <div className="space-y-1">
        <h3 className="text-lg font-medium text-slate-950 dark:text-slate-50">Take-on committed</h3>
        {batch.completedAt && (
          <p
            className="text-sm text-slate-600 dark:text-slate-400"
            data-testid="take-on-completed-at"
          >
            Completed {formatDateTime(batch.completedAt)}
          </p>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-3" data-testid="take-on-result-cards">
        <StatCard
          label="Created"
          value={<Figure n={counts.created} />}
          data-testid="take-on-result-created"
        />
        <StatCard
          label={skippedLabel}
          value={<Figure n={counts.skipped} />}
          data-testid="take-on-result-skipped"
        />
        <StatCard
          label="Failed"
          value={<Figure n={counts.failed} />}
          data-testid="take-on-result-failed"
        />
      </div>

      {counts.failed > 0 && (
        <p
          className="text-sm text-slate-700 dark:text-slate-300"
          data-testid="take-on-result-failed-note"
        >
          {plural(counts.failed, "row was", "rows were")} not committed. The result report and the
          Validation step give the reason for each.
        </p>
      )}

      {allowed.has("DOWNLOAD_RESULT") && (
        <div className="space-y-2">
          <Button
            type="button"
            variant="outline"
            disabled={downloading}
            onClick={download}
            data-testid="take-on-result-download"
          >
            {downloading ? "Downloading…" : "Download the result report"}
          </Button>
          {downloadError && (
            <TakeOnError message={downloadError} testId="take-on-result-download-error" />
          )}
        </div>
      )}

      {allowed.has("CREATE_PORTAL_ACCESS") && (
        <section
          className="space-y-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800"
          data-testid="take-on-portal"
        >
          <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">Portal access</h4>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Creates a portal contact for each client this take-on created, with the client&apos;s
            email address. No email is sent: invite clients to the portal from the client&apos;s
            page when you are ready.
          </p>
          <Button
            type="button"
            variant="soft"
            disabled={portalBusy}
            onClick={createPortalAccess}
            data-testid="take-on-portal-access"
          >
            {portalBusy ? "Creating portal access…" : "Create portal access"}
          </Button>
          {portal && (
            <p
              role="status"
              className="text-sm text-slate-800 dark:text-slate-200"
              data-testid="take-on-portal-result"
            >
              {plural(portal.createdCount, "portal contact", "portal contacts")} created
              {portal.skippedCount > 0 ? ` · ${portal.skippedCount} already had access` : ""}
            </p>
          )}
          {portalError && <TakeOnError message={portalError} testId="take-on-portal-error" />}
        </section>
      )}
    </div>
  );
}
