"use client";

import { useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cancelBatch } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import {
  TAKE_ON_BATCH_STATUS_LABEL,
  TAKE_ON_DATASET_LABEL,
  TAKE_ON_SECTION_LABEL,
  takeOnKebab,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnSource,
} from "@/lib/types";
import { MappingStep } from "./mapping-step";
import { SheetPicker } from "./sheet-picker";
import { TakeOnError } from "./take-on-feedback";
import { TakeOnFilesPanel } from "./upload-step";
import { ValidationStep } from "./validation-step";

export type TakeOnStep = "upload" | "sheet" | "mapping" | "validation";

const STEPS: { id: TakeOnStep; label: string }[] = [
  { id: "upload", label: "Upload" },
  { id: "sheet", label: "Sheet" },
  { id: "mapping", label: "Mapping" },
  { id: "validation", label: "Validation" },
];

const needsSheetChoice = (src: TakeOnSource) =>
  src.fileFormat === "XLSX" && src.sheetNames.length > 1;

const firstUnmapped = (batch: TakeOnBatch) => batch.sources.find((s) => !s.mapped);

/** Where a batch resumes: no file → Upload; an unmapped file → Sheet (multi-sheet XLSX) or Mapping; mapped → Validation. */
export function takeOnStepFor(batch: TakeOnBatch): TakeOnStep {
  if (batch.sources.length === 0) return "upload";
  if (batch.status === "MAPPED" || batch.status === "VALIDATED") return "validation";
  const unmapped = firstUnmapped(batch);
  if (!unmapped) return "validation";
  return needsSheetChoice(unmapped) ? "sheet" : "mapping";
}

interface TakeOnWizardProps {
  batch: TakeOnBatch;
  dataset: TakeOnDatasetCatalogue;
}

/**
 * The take-on wizard inside Settings → Matters → Take-on (`?tab=take-on&batch={id}`, Phase 93
 * 653A): Upload → Sheet → Mapping → Validation. It ends at the dry run — there is no commit control
 * (Change #7, 655A). Every control follows the batch's allowedActions.
 */
export function TakeOnWizard({ batch: initialBatch, dataset }: TakeOnWizardProps) {
  const [batch, setBatch] = useState(initialBatch);
  const [step, setStep] = useState<TakeOnStep>(() => takeOnStepFor(initialBatch));
  const [activeSourceId, setActiveSourceId] = useState<string | null>(
    () => (firstUnmapped(initialBatch) ?? initialBatch.sources[0])?.id ?? null
  );

  const activeSource =
    batch.sources.find((s) => s.id === activeSourceId) ?? firstUnmapped(batch) ?? batch.sources[0];
  const cancelled = batch.status === "CANCELLED";
  const hasSources = batch.sources.length > 0;

  function changeBatch(next: TakeOnBatch) {
    setBatch(next);
    if (!next.sources.some((s) => s.id === activeSourceId)) {
      setActiveSourceId((firstUnmapped(next) ?? next.sources[0])?.id ?? null);
    }
  }

  function goToSourceWork(next: TakeOnBatch) {
    const unmapped = firstUnmapped(next);
    if (!unmapped) {
      setStep("validation");
      return;
    }
    setActiveSourceId(unmapped.id);
    setStep(needsSheetChoice(unmapped) ? "sheet" : "mapping");
  }

  const reachable = (id: TakeOnStep) => id === "upload" || hasSources;
  const sectionFields = (src: TakeOnSource) =>
    dataset.sections.find((s) => s.section === src.section)?.fields ?? [];

  return (
    <div className="space-y-8" data-testid="take-on-wizard">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-1">
          <h2 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            {batch.name ?? `${TAKE_ON_DATASET_LABEL[batch.dataset]} take-on`}
          </h2>
          <div className="flex flex-wrap items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
            <span>{TAKE_ON_DATASET_LABEL[batch.dataset]}</span>
            <Badge variant={cancelled ? "neutral" : "outline"} data-testid="take-on-batch-status">
              {TAKE_ON_BATCH_STATUS_LABEL[batch.status]}
            </Badge>
            {batch.cutoverDate && (
              <span data-testid="take-on-batch-cutover">
                Cutover date <span className="font-mono tabular-nums">{batch.cutoverDate}</span>
              </span>
            )}
          </div>
        </div>
        {batch.allowedActions.includes("CANCEL") && (
          <CancelTakeOnDialog batchId={batch.id} onCancelled={changeBatch} />
        )}
      </div>

      {cancelled ? (
        <p
          role="status"
          data-testid="take-on-cancelled"
          className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
        >
          This take-on was cancelled. Its uploaded files and row values were deleted.
        </p>
      ) : (
        <>
          <nav aria-label="Take-on steps">
            <ol className="flex flex-wrap items-center gap-2">
              {STEPS.map(({ id, label }, index) => {
                const active = id === step;
                const done = STEPS.findIndex((s) => s.id === step) > index;
                return (
                  <li key={id} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setStep(id)}
                      disabled={!reachable(id)}
                      aria-current={active ? "step" : undefined}
                      data-testid={`take-on-step-${id}`}
                      className="flex items-center gap-2 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      <span
                        className={`flex size-8 items-center justify-center rounded-full text-sm font-medium ${
                          active
                            ? "bg-teal-600 text-white"
                            : done
                              ? "bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                              : "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400"
                        }`}
                      >
                        {index + 1}
                      </span>
                      <span
                        className={`text-sm ${
                          active
                            ? "font-medium text-slate-950 dark:text-slate-50"
                            : "text-slate-500 dark:text-slate-400"
                        }`}
                      >
                        {label}
                      </span>
                    </button>
                    {index < STEPS.length - 1 && (
                      <div className="mx-2 h-px w-8 bg-slate-200 dark:bg-slate-700" />
                    )}
                  </li>
                );
              })}
            </ol>
          </nav>

          {(step === "sheet" || step === "mapping") && batch.sources.length > 1 && (
            <div className="flex flex-wrap gap-2" role="group" aria-label="File">
              {batch.sources.map((src) => (
                <Button
                  key={src.id}
                  type="button"
                  size="sm"
                  variant={src.id === activeSource?.id ? "soft" : "ghost"}
                  aria-pressed={src.id === activeSource?.id}
                  onClick={() => setActiveSourceId(src.id)}
                  data-testid={`take-on-source-tab-${takeOnKebab(src.section)}`}
                >
                  {TAKE_ON_SECTION_LABEL[src.section]}
                  {src.mapped ? " ✓" : ""}
                </Button>
              ))}
            </div>
          )}

          {step === "upload" && (
            <TakeOnFilesPanel
              batch={batch}
              dataset={dataset}
              onBatchChange={changeBatch}
              onNext={() => goToSourceWork(batch)}
            />
          )}
          {step === "sheet" && activeSource && (
            <SheetPicker
              key={activeSource.id}
              batchId={batch.id}
              source={activeSource}
              onDone={(result) => {
                if (result) changeBatch(result.batch);
                setStep("mapping");
              }}
            />
          )}
          {step === "mapping" && activeSource && (
            <div className="space-y-4">
              <Button
                type="button"
                variant="link"
                className="px-0"
                onClick={() => setStep("sheet")}
                data-testid="take-on-change-sheet"
              >
                Change the sheet or header row
              </Button>
              <MappingStep
                key={activeSource.id}
                batchId={batch.id}
                source={activeSource}
                fields={sectionFields(activeSource)}
                customFieldEntityType={dataset.customFieldEntityType}
                onSaved={(next) => {
                  changeBatch(next);
                  goToSourceWork(next);
                }}
              />
            </div>
          )}
          {step === "validation" && (
            <ValidationStep batch={batch} dataset={dataset} onBatchChange={changeBatch} />
          )}
        </>
      )}
    </div>
  );
}

interface CancelTakeOnDialogProps {
  batchId: string;
  onCancelled: (batch: TakeOnBatch) => void;
}

/** Destructive confirm that owns its trigger (frontend/CLAUDE.md "Dialog Trigger Composition"). */
function CancelTakeOnDialog({ batchId, onCancelled }: CancelTakeOnDialogProps) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setError(null);
    setBusy(true);
    try {
      const result = await cancelBatch(batchId);
      if (!result.success || !result.data) {
        setError(result.error ?? "The take-on could not be cancelled.");
        return;
      }
      setOpen(false);
      onCancelled(result.data);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setOpen(true)}
        data-testid="take-on-cancel"
      >
        Cancel take-on
      </Button>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">Cancel this take-on?</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            The uploaded file and its rows are deleted. This cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <TakeOnError message={error} testId="take-on-cancel-error" />}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={busy}>
            Keep it
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={confirm}
            disabled={busy}
            data-testid="take-on-cancel-confirm"
          >
            {busy ? "Cancelling…" : "Cancel take-on"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
