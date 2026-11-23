"use client";

import { useState } from "react";
import useSWR from "swr";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { StatCard } from "@b2mash/ui/stat-card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  fetchTakeOnRows,
  validateBatch,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import {
  TAKE_ON_ROW_STATUS_LABEL,
  TAKE_ON_SECTION_LABEL,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnRow,
  type TakeOnRowStatus,
  type TakeOnSection,
} from "@/lib/types";
import { TakeOnError } from "./upload-step";

const PAGE_SIZE = 50;
const ALL = "ALL";

/** The row statuses a 653A dry run can produce (commit statuses arrive with 655A). */
const FILTER_STATUSES: TakeOnRowStatus[] = ["PENDING", "VALIDATED", "ERROR"];

const STATUS_VARIANT: Record<TakeOnRowStatus, "success" | "warning" | "destructive" | "neutral"> = {
  PENDING: "neutral",
  VALIDATED: "success",
  ERROR: "destructive",
  CREATED: "success",
  SKIPPED_DUPLICATE: "warning",
  SKIPPED_ZERO: "neutral",
  FAILED: "destructive",
};

/** Keys the dry run adds beside the catalogue fields (GenericTakeOnDatasetHandler). */
const EXTRA_VALUE_LABELS: ReadonlyArray<readonly [string, string]> = [
  ["customFields", "Custom fields"],
  ["takeon_parked", "Kept as a note"],
];

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (Array.isArray(value)) return value.map(formatValue).join(", ");
  if (typeof value === "object") {
    return Object.entries(value as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${formatValue(v)}`)
      .join(", ");
  }
  return String(value);
}

interface ValidationStepProps {
  batch: TakeOnBatch;
  dataset: TakeOnDatasetCatalogue;
  onBatchChange: (batch: TakeOnBatch) => void;
}

/**
 * The dry run (Phase 93 653A, §17.3.2, X2): every row is checked, nothing is created. The cards
 * and the row table come from the same validation, so the table's status totals equal the cards.
 * There is no commit control here (Change #7 — it arrives with 655A).
 */
export function ValidationStep({ batch, dataset, onBatchChange }: ValidationStepProps) {
  const allowed = new Set(batch.allowedActions);
  const canValidate = allowed.has("VALIDATE") || allowed.has("REVALIDATE");
  const validated = batch.status === "VALIDATED";
  const isTrust = dataset.requiresTrustAccount;

  const [status, setStatus] = useState<string>(ALL);
  const [section, setSection] = useState<string>(ALL);
  const [blocksOnly, setBlocksOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const rowsKey = `take-on-rows-${batch.id}-${batch.status}-${batch.validatedAt ?? "none"}-${status}-${section}-${blocksOnly}-${page}`;
  const rows = useSWR(rowsKey, async () => {
    const result = await fetchTakeOnRows(batch.id, {
      status: status === ALL ? undefined : (status as TakeOnRowStatus),
      section: section === ALL ? undefined : (section as TakeOnSection),
      blocksOnly,
      page,
      size: PAGE_SIZE,
    });
    if (!result.success || !result.data) {
      throw new Error(result.error ?? "The rows could not be loaded.");
    }
    return result.data;
  });

  const labels = new Map([
    ...EXTRA_VALUE_LABELS,
    ...dataset.sections.flatMap((s) => s.fields.map((f) => [f.key, f.label] as const)),
  ]);

  async function runDryRun() {
    setError(null);
    setBusy(true);
    try {
      const result = await validateBatch(batch.id);
      if (!result.success || !result.data) {
        setError(result.error ?? "The dry run could not run.");
        return;
      }
      setPage(0);
      onBatchChange(result.data);
    } finally {
      setBusy(false);
    }
  }

  function filterChanged(apply: () => void) {
    apply();
    setPage(0);
  }

  const counts = batch.counts;
  const totalPages = rows.data?.page.totalPages ?? 0;

  return (
    <div className="space-y-6" data-testid="take-on-validation">
      <div className="flex flex-wrap items-center gap-3">
        {canValidate && (
          <Button
            type="button"
            variant="accent"
            disabled={busy}
            onClick={runDryRun}
            data-testid="take-on-validate"
          >
            {busy ? "Running the dry run…" : validated ? "Run the dry run again" : "Run dry run"}
          </Button>
        )}
        <p className="text-sm text-slate-600 dark:text-slate-400">
          The dry run checks every row. Nothing is created in Kazi.
        </p>
      </div>
      {batch.status === "UPLOADED" && (
        <p className="text-sm text-slate-700 dark:text-slate-300" data-testid="take-on-map-first">
          Map the columns of every file before the dry run.
        </p>
      )}
      {dataset.requiresCutoverDate && !batch.cutoverDate && (
        <p
          className="text-sm text-slate-700 dark:text-slate-300"
          data-testid="take-on-cutover-missing"
        >
          Enter the cutover date before validating.
        </p>
      )}
      {error && <TakeOnError message={error} testId="take-on-validate-error" />}

      {validated && (
        <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5" data-testid="take-on-cards">
          <StatCard
            label="Rows"
            value={<Figure n={counts.total} />}
            data-testid="take-on-card-total"
          />
          <StatCard
            label="Valid"
            value={<Figure n={counts.valid} />}
            data-testid="take-on-card-valid"
          />
          <StatCard
            label="Errors"
            value={<Figure n={counts.error} />}
            data-testid="take-on-card-error"
          />
          <StatCard
            label="Duplicates"
            value={<Figure n={counts.duplicate} />}
            data-testid="take-on-card-duplicate"
          />
          <StatCard
            label="Blocks"
            value={<Figure n={counts.blocks} />}
            data-testid="take-on-card-blocks"
          />
        </div>
      )}

      {validated && counts.blocks > 0 && (
        <div
          role="alert"
          data-testid="take-on-block-banner"
          className="rounded-lg border-l-4 border-red-600 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-500 dark:bg-red-950 dark:text-red-200"
        >
          <span className="font-mono tabular-nums">{counts.blocks}</span>{" "}
          {counts.blocks === 1 ? "row holds" : "rows hold"} a statutory block. They cannot be
          overridden — correct them in the previous system and upload again.
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3" data-testid="take-on-row-filters">
        <Select value={status} onValueChange={(v) => filterChanged(() => setStatus(v))}>
          <SelectTrigger
            className="w-48"
            aria-label="Row status"
            data-testid="take-on-filter-status"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            {FILTER_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {TAKE_ON_ROW_STATUS_LABEL[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {isTrust && (
          <Select value={section} onValueChange={(v) => filterChanged(() => setSection(v))}>
            <SelectTrigger
              className="w-64"
              aria-label="Part of the trust position"
              data-testid="take-on-filter-section"
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All parts</SelectItem>
              {dataset.sections.map((s) => (
                <SelectItem key={s.section} value={s.section}>
                  {TAKE_ON_SECTION_LABEL[s.section]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <label className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={blocksOnly}
            onCheckedChange={(c) => filterChanged(() => setBlocksOnly(c === true))}
            data-testid="take-on-filter-blocks"
          />
          Blocks only
        </label>
      </div>

      {rows.error && (
        <TakeOnError message={String(rows.error.message)} testId="take-on-rows-error" />
      )}
      {rows.data && <RowTable rows={rows.data.content} labels={labels} isTrust={isTrust} />}
      {rows.data && totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm" data-testid="take-on-rows-pager">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            data-testid="take-on-rows-prev"
          >
            Previous
          </Button>
          <span>
            Page <span className="font-mono tabular-nums">{page + 1}</span> of{" "}
            <span className="font-mono tabular-nums">{totalPages}</span>
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            data-testid="take-on-rows-next"
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}

function Figure({ n }: { n: number }) {
  return <span className="font-mono tabular-nums">{n.toLocaleString("en-ZA")}</span>;
}

function RowTable({
  rows,
  labels,
  isTrust,
}: {
  rows: TakeOnRow[];
  labels: Map<string, string>;
  isTrust: boolean;
}) {
  if (rows.length === 0) {
    return (
      <p className="text-sm text-slate-600 dark:text-slate-400" data-testid="take-on-rows-empty">
        No rows match these filters.
      </p>
    );
  }
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
      <Table data-testid="take-on-rows">
        <TableHeader>
          <TableRow>
            <TableHead className="w-16">Row</TableHead>
            {isTrust && <TableHead>Part</TableHead>}
            <TableHead>Previous system ID</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Values</TableHead>
            <TableHead>Messages</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={row.id}
              data-testid={`take-on-row-${row.rowNumber}`}
              data-section={row.section}
              className={row.block ? "bg-red-50/60 dark:bg-red-950/40" : undefined}
            >
              <TableCell className="font-mono text-xs tabular-nums">{row.rowNumber}</TableCell>
              {isTrust && (
                <TableCell className="text-xs">{TAKE_ON_SECTION_LABEL[row.section]}</TableCell>
              )}
              <TableCell className="text-xs">{row.sourceRef ?? "—"}</TableCell>
              <TableCell>
                <Badge
                  variant={STATUS_VARIANT[row.status]}
                  data-testid={`take-on-row-status-${row.rowNumber}`}
                >
                  {TAKE_ON_ROW_STATUS_LABEL[row.status]}
                </Badge>
                {row.block && (
                  <Badge variant="destructive" className="ml-1">
                    Block
                  </Badge>
                )}
              </TableCell>
              <TableCell className="max-w-md text-xs text-slate-700 dark:text-slate-300">
                <RowValues values={row.values} labels={labels} />
              </TableCell>
              <TableCell className="max-w-md text-xs whitespace-normal">
                <ul className="space-y-0.5">
                  {row.errors.map((m, i) => (
                    <li key={`e${i}`} className="text-red-700 dark:text-red-300">
                      {m.message}
                    </li>
                  ))}
                  {row.warnings.map((m, i) => (
                    <li key={`w${i}`} className="text-amber-700 dark:text-amber-300">
                      {m.message}
                    </li>
                  ))}
                </ul>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function RowValues({
  values,
  labels,
}: {
  values: Record<string, unknown> | null;
  labels: Map<string, string>;
}) {
  if (!values) return null;
  if (values.purged === true) {
    return <span>Deleted when the take-on was cancelled</span>;
  }
  const entries = Object.entries(values).filter(([, v]) => v !== null && v !== "");
  return (
    <span className="line-clamp-2 whitespace-normal">
      {entries.map(([k, v], i) => (
        <span key={k}>
          {i > 0 && " · "}
          <span className="text-slate-500 dark:text-slate-400">{labels.get(k) ?? k}:</span>{" "}
          {formatValue(v)}
        </span>
      ))}
    </span>
  );
}
