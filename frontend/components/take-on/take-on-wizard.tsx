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
  TAKE_ON_COMMIT_TRAITS,
  TAKE_ON_DATASET_LABEL,
  TAKE_ON_SECTION_LABEL,
  takeOnKebab,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnSource,
  type TakeOnStep,
} from "@/lib/types";
import { AttestationStep, liveAttestation } from "./attestation-step";
import { CommitTakeOnDialog } from "./commit-dialog";
import { MappingStep } from "./mapping-step";
import { ResultsStep, useTakeOnCommitProgress } from "./results-step";
import { SheetPicker } from "./sheet-picker";
import { TakeOnError } from "./take-on-feedback";
import { TakeOnFilesPanel } from "./upload-step";
import { ValidationStep } from "./validation-step";

export type { TakeOnStep };

const STEP_LABEL: Record<TakeOnStep, string> = {
  upload: "Upload",
  sheet: "Sheet",
  mapping: "Mapping",
  validation: "Validation",
  attestation: "Attestation",
  results: "Results",
};

const stepOf = (id: TakeOnStep) => ({ id, label: STEP_LABEL[id] });

const FILE_STEPS = (["upload", "sheet", "mapping", "validation"] as const).map(stepOf);

const traitsOf = (batch: TakeOnBatch) => TAKE_ON_COMMIT_TRAITS[batch.dataset];

/**
 * Matters need no sign-off: the commit sits under Validation, then Results (656A, D-169). The
 * trust position and debtors gain their commit steps in later slices.
 */
const commitsFromValidation = (batch: TakeOnBatch) => traitsOf(batch).commitsFromValidation;

/** Clients attest, then commit (655A); matters commit from Validation (656A). */
const stepsFor = (batch: TakeOnBatch) => {
  const { signOffStep } = traitsOf(batch);
  if (signOffStep) return [...FILE_STEPS, stepOf(signOffStep), stepOf("results")];
  if (commitsFromValidation(batch)) return [...FILE_STEPS, stepOf("results")];
  return FILE_STEPS;
};

const isCommitted = (batch: TakeOnBatch) =>
  batch.status === "COMMITTING" || batch.status === "COMPLETED";

/** A multi-sheet XLSX, or a file kept with no rows because row 1 is not its header row (OBS F-1). */
const needsSheetChoice = (src: TakeOnSource) =>
  src.rowCount === 0 || (src.fileFormat === "XLSX" && src.sheetNames.length > 1);

const firstUnmapped = (batch: TakeOnBatch) => batch.sources.find((s) => !s.mapped);

/**
 * Where a batch resumes: committing or completed → Results; a validated clients batch with a live
 * attestation → Attestation; no file → Upload; an unmapped file → Sheet (multi-sheet XLSX, or no
 * rows read yet) or Mapping; mapped → Validation.
 */
export function takeOnStepFor(batch: TakeOnBatch): TakeOnStep {
  if (isCommitted(batch)) return "results";
  if (
    batch.status === "VALIDATED" &&
    traitsOf(batch).signOffStep === "attestation" &&
    liveAttestation(batch)
  ) {
    return "attestation";
  }
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
 * 653A/655A/656A): Upload → Sheet → Mapping → Validation, then for clients → Attestation → Results
 * (attest, commit, progress, outcome) and for matters → Results (committed from Validation). While
 * the batch is COMMITTING the wizard re-reads it until the status changes. Every control follows
 * the batch's allowedActions.
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

  function committed(next: TakeOnBatch) {
    changeBatch(next);
    setStep("results");
  }

  useTakeOnCommitProgress(batch, (next) => {
    changeBatch(next);
    // A commit that could not run returns the batch to VALIDATED with lastError (D-91): back to
    // the step that holds the commit.
    if (next.status === "VALIDATED") {
      setStep(traitsOf(next).signOffStep ?? "validation");
    }
  });

  function goToSourceWork(next: TakeOnBatch) {
    const unmapped = firstUnmapped(next);
    if (!unmapped) {
      setStep("validation");
      return;
    }
    setActiveSourceId(unmapped.id);
    setStep(needsSheetChoice(unmapped) ? "sheet" : "mapping");
  }

  const steps = stepsFor(batch);
  const reachable = (id: TakeOnStep) => {
    if (id === "attestation") return batch.status === "VALIDATED" || isCommitted(batch);
    if (id === "results") return isCommitted(batch);
    return id === "upload" || hasSources;
  };
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
              {steps.map(({ id, label }, index) => {
                const active = id === step;
                const done = steps.findIndex((s) => s.id === step) > index;
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
                    {index < steps.length - 1 && (
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
                const read = result?.batch.sources.find((s) => s.id === activeSource.id);
                if (read?.rowCount === 0) return; // nothing below the header row: stay on Sheet
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
            <div className="space-y-6">
              <ValidationStep batch={batch} dataset={dataset} onBatchChange={changeBatch} />
              {traitsOf(batch).signOffStep === "attestation" && batch.status === "VALIDATED" && (
                <Button
                  type="button"
                  variant="accent"
                  onClick={() => setStep("attestation")}
                  data-testid="take-on-to-attestation"
                >
                  Continue to the FICA attestation
                </Button>
              )}
              {commitsFromValidation(batch) && batch.status === "VALIDATED" && batch.lastError && (
                <TakeOnError message={batch.lastError} testId="take-on-last-error" />
              )}
              {commitsFromValidation(batch) && batch.allowedActions.includes("COMMIT") && (
                <CommitTakeOnDialog batch={batch} onCommitted={committed} />
              )}
            </div>
          )}
          {step === "attestation" && (
            <AttestationStep
              batch={batch}
              dataset={dataset}
              onBatchChange={changeBatch}
              onCommitted={committed}
            />
          )}
          {step === "results" && <ResultsStep batch={batch} onBatchChange={changeBatch} />}
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
