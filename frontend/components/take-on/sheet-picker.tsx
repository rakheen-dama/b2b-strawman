"use client";

import { useState } from "react";
import useSWR from "swr";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  fetchTakeOnPreview,
  setSheet,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { takeOnSheetSchema } from "@/lib/schemas";
import type { TakeOnPreview, TakeOnSource, TakeOnUploadResult } from "@/lib/types";
import { TakeOnError, TakeOnWarnings } from "./take-on-feedback";

/** SWR key of a source's preview — changes whenever the stored sheet or header row changes. */
export function takeOnPreviewKey(batchId: string, source: TakeOnSource): string {
  return `take-on-preview-${batchId}-${source.id}-${source.sheetName ?? ""}-${source.headerRow}`;
}

export function useTakeOnPreview(batchId: string, source: TakeOnSource) {
  return useSWR(takeOnPreviewKey(batchId, source), async () => {
    const result = await fetchTakeOnPreview(batchId, source.id, 20);
    if (!result.success || !result.data) {
      throw new Error(result.error ?? "The file could not be read.");
    }
    return result.data;
  });
}

/** The first rows of the file as stored (file order), with its headers. */
export function TakeOnPreviewTable({ preview }: { preview: TakeOnPreview }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
      <Table data-testid="take-on-preview">
        <TableHeader>
          <TableRow>
            <TableHead className="w-16">Row</TableHead>
            {preview.headers.map((h, i) => (
              <TableHead key={i}>{h}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {preview.rows.map((row) => (
            <TableRow key={row.rowNumber}>
              <TableCell className="font-mono text-xs tabular-nums">{row.rowNumber}</TableCell>
              {row.cells.map((cell, i) => (
                <TableCell key={i} className="max-w-48 truncate text-xs">
                  {cell}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

interface SheetPickerProps {
  batchId: string;
  source: TakeOnSource;
  /** The sheet and header row were applied (rows re-read, mapping cleared) or left unchanged. */
  onDone: (result: TakeOnUploadResult | null) => void;
}

/**
 * Sheet and header row of one file (Phase 93 653A, §17.3.1). An XLSX lists every sheet, hidden
 * ones marked; a CSV has one sheet. Changing either re-reads the rows and clears the mapping, so
 * an unchanged choice moves on without calling the backend.
 */
export function SheetPicker({ batchId, source, onDone }: SheetPickerProps) {
  const isXlsx = source.fileFormat === "XLSX";
  const [sheetName, setSheetName] = useState(source.sheetName ?? source.sheetNames[0]?.name ?? "");
  const [headerRow, setHeaderRow] = useState(String(source.headerRow));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const preview = useTakeOnPreview(batchId, source);

  const parsed = takeOnSheetSchema.safeParse({
    sheetName: isXlsx ? sheetName : (source.sheetName ?? "CSV"),
    headerRow: Number(headerRow),
  });
  const issue = parsed.success ? null : parsed.error.issues[0]?.message;

  async function apply() {
    if (!parsed.success) return;
    const unchanged =
      parsed.data.headerRow === source.headerRow &&
      (!isXlsx || parsed.data.sheetName === source.sheetName);
    if (unchanged) {
      onDone(null);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const result = await setSheet(batchId, source.id, {
        sheetName: isXlsx ? parsed.data.sheetName : null,
        headerRow: parsed.data.headerRow,
      });
      if (!result.success || !result.data) {
        setError(result.error ?? "The sheet could not be read.");
        return;
      }
      onDone(result.data);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6" data-testid="take-on-sheet-picker">
      {isXlsx && (
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-slate-950 dark:text-slate-50">Sheet</legend>
          <RadioGroup value={sheetName} onValueChange={setSheetName} aria-label="Sheet">
            {source.sheetNames.map((sheet, i) => (
              <div key={sheet.name} className="flex items-center gap-2">
                <RadioGroupItem
                  value={sheet.name}
                  id={`take-on-sheet-${i}`}
                  data-testid={`take-on-sheet-${i}`}
                />
                <label htmlFor={`take-on-sheet-${i}`} className="text-sm">
                  {sheet.name}
                </label>
                {sheet.hidden && (
                  <Badge variant="neutral" data-testid={`take-on-sheet-hidden-${i}`}>
                    Hidden sheet
                  </Badge>
                )}
              </div>
            ))}
          </RadioGroup>
        </fieldset>
      )}
      <div className="space-y-1">
        <label
          htmlFor="take-on-header-row"
          className="text-sm font-medium text-slate-950 dark:text-slate-50"
        >
          Header row
        </label>
        <Input
          id="take-on-header-row"
          type="number"
          min={1}
          max={50}
          className="w-24"
          value={headerRow}
          onChange={(e) => setHeaderRow(e.target.value)}
          data-testid="take-on-header-row"
        />
        <p className="text-xs text-slate-600 dark:text-slate-400">
          The row that holds the column names (1–50). The rows below it are taken on.
        </p>
      </div>

      {issue && <TakeOnError message={issue} testId="take-on-sheet-issue" />}
      {error && <TakeOnError message={error} testId="take-on-sheet-error" />}

      <div className="space-y-2">
        <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
          First rows of {source.sheetName && isXlsx ? `“${source.sheetName}”` : "the file"}
        </p>
        {preview.error && (
          <TakeOnError message={String(preview.error.message)} testId="take-on-preview-error" />
        )}
        {preview.data && (
          <>
            <TakeOnWarnings warnings={preview.data.warnings} />
            <TakeOnPreviewTable preview={preview.data} />
          </>
        )}
      </div>

      <Button
        type="button"
        variant="accent"
        disabled={busy || !parsed.success}
        onClick={apply}
        data-testid="take-on-apply-sheet"
      >
        {busy ? "Reading the sheet…" : "Use this sheet and header row"}
      </Button>
    </div>
  );
}
