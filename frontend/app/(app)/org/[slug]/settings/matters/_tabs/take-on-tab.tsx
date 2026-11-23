import Link from "next/link";
import { ArrowLeft, FileSpreadsheet } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { api, ApiError } from "@/lib/api";
import { EmptyState } from "@/components/empty-state";
import { BatchHistoryTable } from "@/components/take-on/batch-history-table";
import { DatasetPicker } from "@/components/take-on/dataset-picker";
import { TakeOnWizard } from "@/components/take-on/take-on-wizard";
import { getSettingsFamily, settingsHref } from "@/lib/settings-nav";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";
import type {
  TakeOnBatch,
  TakeOnBatchSummary,
  TakeOnDatasetCatalogue,
  TakeOnPage,
} from "@/lib/types";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const TAB = getSettingsFamily("matters").tabs.find((t) => t.id === "take-on");

function Notice({ children, testId }: { children: React.ReactNode; testId: string }) {
  return (
    <p
      role="status"
      data-testid={testId}
      className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
    >
      {children}
    </p>
  );
}

async function loadBatch(batchId: string): Promise<TakeOnBatch | null> {
  try {
    return await api.get<TakeOnBatch>(`/api/take-on/batches/${batchId}`);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 403)) {
      return null;
    }
    throw error;
  }
}

/**
 * Matters › Take-on (Phase 93 653A, architecture §17.8.2). The family page has already gated
 * MANAGE_TAKE_ON; each dataset's own capability is checked by the backend. `?batch={id}` renders
 * the wizard inside the tab, so the settings route count stays 17 (ADR-341).
 */
export async function renderTakeOnTab({ slug, searchParams }: SettingsTabProps) {
  const rawBatch = searchParams.batch;
  const batchId = Array.isArray(rawBatch) ? rawBatch[0] : rawBatch;

  let datasets: TakeOnDatasetCatalogue[] = [];
  let history: TakeOnBatchSummary[] = [];
  let loadNotice: string | null = null;
  try {
    const [catalogue, page] = await Promise.all([
      api.get<TakeOnDatasetCatalogue[]>("/api/take-on/datasets"),
      api.get<TakeOnPage<TakeOnBatchSummary>>("/api/take-on/batches?page=0&size=20"),
    ]);
    datasets = catalogue;
    history = page.content;
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    loadNotice =
      error.status === 403
        ? "You don't have access to take-on."
        : "Take-on could not be loaded. Try again.";
  }

  let batchNotice: string | null = null;
  if (batchId !== undefined && !loadNotice) {
    const batch = UUID.test(batchId) ? await loadBatch(batchId) : null;
    const dataset = batch ? datasets.find((d) => d.dataset === batch.dataset) : undefined;
    if (batch && dataset) {
      return (
        <div className="space-y-6">
          <Link
            href={settingsHref(slug, "matters", "take-on")}
            className="inline-flex items-center gap-1.5 text-sm text-slate-600 hover:text-slate-950 dark:text-slate-400 dark:hover:text-slate-100"
            data-testid="take-on-back"
          >
            <ArrowLeft className="size-4" />
            Back to take-on
          </Link>
          <TakeOnWizard key={batch.id} batch={batch} dataset={dataset} />
        </div>
      );
    }
    batchNotice = "That take-on batch is not available.";
  }

  return (
    <div className="space-y-8" data-testid="take-on-tab">
      <PageHeader title="Take-on" description={TAB?.description} data-testid="take-on-header" />
      {loadNotice && <Notice testId="take-on-load-notice">{loadNotice}</Notice>}
      {batchNotice && <Notice testId="take-on-batch-notice">{batchNotice}</Notice>}
      {!loadNotice && (
        <>
          <DatasetPicker slug={slug} datasets={datasets} />
          <section className="space-y-3">
            <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
              Take-on history
            </h2>
            <BatchHistoryTable
              slug={slug}
              batches={history}
              empty={
                <EmptyState
                  icon={FileSpreadsheet}
                  variant="compact"
                  title="No take-on batches yet"
                  description="Start with clients: download the template, fill it from your previous system and upload it."
                />
              }
            />
          </section>
        </>
      )}
    </div>
  );
}
