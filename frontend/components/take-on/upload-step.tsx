"use client";

import { useState, type ChangeEvent } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import useSWR from "swr";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  addTakeOnSource,
  fetchTakeOnBankingAccounts,
  removeTakeOnSource,
  savePosition,
  uploadTakeOnFile,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { takeOnFileError, takeOnUploadSchema, type TakeOnUploadFormData } from "@/lib/schemas";
import {
  TAKE_ON_SECTION_LABEL,
  takeOnKebab,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnSection,
  type TakeOnUploadResult,
} from "@/lib/types";

/** The file input of every take-on upload: refuses a wrong file before any action runs. */
export function TakeOnFileInput({
  onFile,
  disabled,
  testId = "take-on-file-input",
}: {
  onFile: (file: File | null, error: string | null) => void;
  disabled?: boolean;
  testId?: string;
}) {
  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (!file) {
      onFile(null, null);
      return;
    }
    const refusal = takeOnFileError(file);
    onFile(refusal ? null : file, refusal);
    if (refusal) e.target.value = "";
  }
  return (
    <input
      type="file"
      accept=".csv,.xlsx"
      aria-label="File from the previous system (.csv or .xlsx)"
      onChange={handleChange}
      disabled={disabled}
      data-testid={testId}
      className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-md file:border file:border-slate-200 file:bg-white file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-50 dark:text-slate-300 dark:file:border-slate-700 dark:file:bg-slate-900 dark:file:text-slate-200"
    />
  );
}

export function TakeOnError({ message, testId }: { message: string; testId: string }) {
  return (
    <p
      role="alert"
      data-testid={testId}
      className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
    >
      {message}
    </p>
  );
}

export function TakeOnWarnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null;
  return (
    <ul
      data-testid="take-on-upload-warnings"
      className="list-disc space-y-1 rounded-md border border-amber-200 bg-amber-50 py-2 pr-3 pl-7 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300"
    >
      {warnings.map((w, i) => (
        <li key={i}>{w}</li>
      ))}
    </ul>
  );
}

interface UploadStepProps {
  dataset: TakeOnDatasetCatalogue;
  /** A new batch was created (its cutover date saved when the dataset needs one). */
  onUploaded: (result: TakeOnUploadResult) => void;
  onClose: () => void;
}

/**
 * The first file of a new take-on batch (Phase 93 653A, C-17): the dataset is fixed by the card;
 * a trust take-on also names the banking account and the part of the position the file holds;
 * trust and debtor take-ons set the cutover date here (P-11).
 */
export function UploadStep({ dataset, onUploaded, onClose }: UploadStepProps) {
  const isTrust = dataset.requiresTrustAccount;
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const accounts = useSWR(isTrust ? "take-on-banking-accounts" : null, () =>
    fetchTakeOnBankingAccounts()
  );

  const form = useForm<TakeOnUploadFormData>({
    resolver: zodResolver(takeOnUploadSchema),
    defaultValues: {
      requiresTrustAccount: isTrust,
      requiresCutoverDate: dataset.requiresCutoverDate,
      name: "",
      trustAccountId: "",
      section: "",
      cutoverDate: "",
    },
  });

  async function onSubmit(values: TakeOnUploadFormData) {
    setError(null);
    if (!file) {
      setFileError(fileError ?? "Choose a file to upload.");
      return;
    }
    setSubmitting(true);
    try {
      const result = await uploadTakeOnFile({
        dataset: dataset.dataset,
        section: isTrust ? (values.section as TakeOnSection) : undefined,
        trustAccountId: isTrust ? values.trustAccountId : undefined,
        name: values.name,
        file,
      });
      if (!result.success || !result.data) {
        setError(result.error ?? "The file could not be uploaded.");
        return;
      }
      let uploaded = result.data;
      if (dataset.requiresCutoverDate) {
        const position = await savePosition(uploaded.batch.id, {
          cutoverDate: values.cutoverDate,
        });
        if (position.success && position.data) {
          uploaded = { ...uploaded, batch: position.data };
        } else {
          // The file is in; only the date failed — say so before moving on (set it again later).
          uploaded = {
            ...uploaded,
            warnings: [
              ...uploaded.warnings,
              `The cutover date was not saved: ${position.error ?? "try again"}. Enter it again before the dry run.`,
            ],
          };
        }
      }
      onUploaded(uploaded);
    } finally {
      setSubmitting(false);
    }
  }

  const accountList = accounts.data?.data ?? [];

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4"
        data-testid="take-on-upload-form"
        noValidate
      >
        {isTrust && (
          <>
            {accounts.data && !accounts.data.success && (
              <TakeOnError
                message={accounts.data.error ?? "Turn on trust accounting first."}
                testId="take-on-accounts-error"
              />
            )}
            <FormField
              control={form.control}
              name="trustAccountId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Trust banking account</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger data-testid="take-on-trust-account">
                        <SelectValue placeholder="Choose the account" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {accountList.map((a) => (
                        <SelectItem key={a.id} value={a.id}>
                          {a.accountName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="section"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>This file holds</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger data-testid="take-on-section">
                        <SelectValue placeholder="Choose the part of the trust position" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {dataset.sections.map((s) => (
                        <SelectItem key={s.section} value={s.section}>
                          {TAKE_ON_SECTION_LABEL[s.section]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />
          </>
        )}
        {dataset.requiresCutoverDate && (
          <FormField
            control={form.control}
            name="cutoverDate"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Cutover date</FormLabel>
                <FormControl>
                  <Input
                    type="date"
                    className="w-48"
                    data-testid="take-on-cutover-date"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Batch name (optional)</FormLabel>
              <FormControl>
                <Input data-testid="take-on-batch-name" maxLength={200} {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="space-y-2">
          <p className="text-sm font-medium text-slate-950 dark:text-slate-50">File</p>
          <TakeOnFileInput
            disabled={submitting}
            onFile={(f, refusal) => {
              setFile(f);
              setFileError(refusal);
            }}
          />
          <p className="text-xs text-slate-600 dark:text-slate-400">
            A .csv or .xlsx file of up to 10 MB, exported from your previous system.
          </p>
        </div>
        {fileError && <TakeOnError message={fileError} testId="take-on-upload-error" />}
        {error && <TakeOnError message={error} testId="take-on-upload-server-error" />}
        <div className="flex gap-2">
          <Button
            type="submit"
            variant="accent"
            disabled={submitting || !file}
            data-testid="take-on-upload-submit"
          >
            {submitting ? "Uploading…" : "Upload"}
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={onClose}
            disabled={submitting}
            data-testid="take-on-upload-close"
          >
            Close
          </Button>
        </div>
      </form>
    </Form>
  );
}

interface TakeOnFilesPanelProps {
  batch: TakeOnBatch;
  dataset: TakeOnDatasetCatalogue;
  onBatchChange: (batch: TakeOnBatch) => void;
  onNext: () => void;
}

/**
 * The upload step of an existing batch: its files (one per trust section), "Add file" while a
 * section is left (ADD_SOURCE), "Remove" (REMOVE_SOURCE), and the cutover date of a trust or
 * debtor take-on. Controls follow the batch's allowedActions.
 */
export function TakeOnFilesPanel({ batch, dataset, onBatchChange, onNext }: TakeOnFilesPanelProps) {
  const isTrust = dataset.requiresTrustAccount;
  const allowed = new Set(batch.allowedActions);
  const open = allowed.has("CANCEL");
  const leftSections = dataset.sections
    .map((s) => s.section)
    .filter((section) => !batch.sources.some((src) => src.section === section));

  const [section, setSection] = useState<string>("");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [inputKey, setInputKey] = useState(0);
  const [cutoverDate, setCutoverDate] = useState(batch.cutoverDate ?? "");

  async function act<T>(call: () => Promise<{ success: boolean; data?: T; error?: string }>) {
    setError(null);
    setBusy(true);
    try {
      const result = await call();
      if (!result.success || result.data === undefined) {
        setError(result.error ?? "Something went wrong. Try again.");
        return undefined;
      }
      return result.data;
    } finally {
      setBusy(false);
    }
  }

  async function addFile() {
    if (!file) return;
    if (isTrust && !section) {
      setError("Choose which part of the trust position this file holds.");
      return;
    }
    const uploaded = await act(() =>
      addTakeOnSource(batch.id, isTrust ? (section as TakeOnSection) : null, file)
    );
    if (uploaded) {
      setWarnings(uploaded.warnings);
      setFile(null);
      setSection("");
      setInputKey((k) => k + 1);
      onBatchChange(uploaded.batch);
    }
  }

  async function remove(sourceId: string) {
    const updated = await act(() => removeTakeOnSource(batch.id, sourceId));
    if (updated) onBatchChange(updated);
  }

  async function saveCutover() {
    const updated = await act(() => savePosition(batch.id, { cutoverDate }));
    if (updated) onBatchChange(updated);
  }

  return (
    <div className="space-y-6" data-testid="take-on-files">
      <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
        {batch.sources.length === 0 && (
          <li className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
            No file yet. Upload the file from your previous system.
          </li>
        )}
        {batch.sources.map((src) => (
          <li
            key={src.id}
            className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
            data-testid={`take-on-source-${takeOnKebab(src.section)}`}
          >
            <div className="min-w-0 space-y-0.5">
              <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                {isTrust ? `${TAKE_ON_SECTION_LABEL[src.section]} — ` : ""}
                {src.fileName}
              </p>
              <p className="text-xs text-slate-600 dark:text-slate-400">
                <span className="font-mono tabular-nums">{src.rowCount}</span> rows read
                {src.malformedCount > 0 && (
                  <>
                    {" · "}
                    <span className="font-mono tabular-nums">{src.malformedCount}</span> rows with a
                    problem
                  </>
                )}
                {src.mapped ? " · columns mapped" : " · columns not mapped yet"}
                {src.purged ? " · file deleted" : ""}
              </p>
            </div>
            {allowed.has("REMOVE_SOURCE") && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={busy}
                onClick={() => remove(src.id)}
                data-testid={`take-on-remove-source-${takeOnKebab(src.section)}`}
              >
                Remove
              </Button>
            )}
          </li>
        ))}
      </ul>

      {allowed.has("ADD_SOURCE") && (
        <div className="space-y-3" data-testid="take-on-add-source-form">
          <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
            {isTrust ? "Add a file for another part of the trust position" : "Upload the file"}
          </p>
          {isTrust && (
            <Select value={section} onValueChange={setSection}>
              <SelectTrigger className="w-80" data-testid="take-on-add-source-section">
                <SelectValue placeholder="Choose the part of the trust position" />
              </SelectTrigger>
              <SelectContent>
                {leftSections.map((s) => (
                  <SelectItem key={s} value={s}>
                    {TAKE_ON_SECTION_LABEL[s]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <TakeOnFileInput
            key={inputKey}
            disabled={busy}
            testId="take-on-add-source-file"
            onFile={(f, refusal) => {
              setFile(f);
              setFileError(refusal);
            }}
          />
          {fileError && <TakeOnError message={fileError} testId="take-on-upload-error" />}
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy || !file}
            onClick={addFile}
            data-testid="take-on-add-source"
          >
            {busy ? "Uploading…" : isTrust ? "Add file" : "Upload"}
          </Button>
        </div>
      )}

      <TakeOnWarnings warnings={warnings} />

      {dataset.requiresCutoverDate && open && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <label
              htmlFor="take-on-cutover"
              className="text-sm font-medium text-slate-950 dark:text-slate-50"
            >
              Cutover date
            </label>
            <Input
              id="take-on-cutover"
              type="date"
              className="w-48"
              value={cutoverDate}
              onChange={(e) => setCutoverDate(e.target.value)}
              data-testid="take-on-cutover-date"
            />
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={busy || !cutoverDate || cutoverDate === batch.cutoverDate}
            onClick={saveCutover}
            data-testid="take-on-save-cutover"
          >
            Save cutover date
          </Button>
        </div>
      )}

      {error && <TakeOnError message={error} testId="take-on-files-error" />}

      <Button
        type="button"
        variant="accent"
        disabled={batch.sources.length === 0}
        onClick={onNext}
        data-testid="take-on-files-next"
      >
        Next
      </Button>
    </div>
  );
}
