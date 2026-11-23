"use client";

import type { ChangeEvent } from "react";
import { takeOnFileError } from "@/lib/schemas";

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

/**
 * Hands a downloaded CSV to the browser as a file. `bom` re-adds the UTF-8 byte-order mark the
 * backend sends (Response.text() drops it) so Excel reads accented names correctly.
 */
export function saveTakeOnFile(fileName: string, content: string, { bom = false } = {}) {
  if (typeof URL.createObjectURL !== "function") return;
  const body = bom && !content.startsWith("\uFEFF") ? `\uFEFF${content}` : content;
  const url = URL.createObjectURL(new Blob([body], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoked a task later: some browsers drop a download whose URL is revoked synchronously.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
