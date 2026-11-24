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
  TAKE_ON_COMMIT_ROW_STATUSES,
  TAKE_ON_COMMIT_TRAITS,
  TAKE_ON_DUPLICATE_PENDING_LABEL,
  TAKE_ON_ROW_STATUS_LABEL,
  TAKE_ON_SECTION_LABEL,
  TAKE_ON_TRUST_FLAG_LABEL,
  TAKE_ON_TRUST_VALUE_LABEL,
  type TakeOnBatch,
  type TakeOnDatasetCatalogue,
  type TakeOnRow,
  type TakeOnRowStatus,
  type TakeOnSection,
} from "@/lib/types";
import {
  CLIENT_COLUMN_KEYS,
  ClientColumnCells,
  ClientColumnHeads,
  clientValueText,
} from "./clients-step";
import {
  MATTER_COLUMN_KEYS,
  MatterColumnCells,
  MatterColumnHeads,
  matterValueText,
} from "./matters-step";
import { TakeOnError } from "./take-on-feedback";
import { TrustEquationIndicator } from "./trust-equation-indicator";
import { TrustPositionForm } from "./trust-position-form";

const PAGE_SIZE = 50;
/** A row message after cancel or purge: the code stays, the text (it may quote a cell) is gone. */
const PURGED_MESSAGE = "Details removed when the batch was cancelled.";
const ALL = "ALL";
/** Status-filter value for the valid rows that match an existing record (OBS-655A-1). */
const DUPLICATE = "DUPLICATE";

/** A valid row matching an existing record: the Duplicates card counts it, the commit skips it. */
function isPendingDuplicate(row: TakeOnRow): boolean {
  return row.status === "VALIDATED" && row.duplicateOfId !== null;
}

type FilterOption = readonly [value: string, label: string];

const statusOption = (s: TakeOnRowStatus): FilterOption => [s, TAKE_ON_ROW_STATUS_LABEL[s]];

/**
 * The row statuses a dry run produces, plus the valid rows that will be skipped as duplicates (none
 * are left once the batch is completed); a commit adds its own statuses (655A, D-40).
 */
function filterOptionsFor(batch: TakeOnBatch): readonly FilterOption[] {
  const dryRun: FilterOption[] = [statusOption("PENDING"), statusOption("VALIDATED")];
  if (batch.status !== "COMPLETED") dryRun.push([DUPLICATE, TAKE_ON_DUPLICATE_PENDING_LABEL]);
  dryRun.push(statusOption("ERROR"));
  if (batch.status !== "COMMITTING" && batch.status !== "COMPLETED") return dryRun;
  // A nil balance is skipped only in the balance datasets — never a client or a matter.
  const { skipsNilBalance } = TAKE_ON_COMMIT_TRAITS[batch.dataset];
  const commit = TAKE_ON_COMMIT_ROW_STATUSES.filter(
    (s) => skipsNilBalance || s !== "SKIPPED_ZERO"
  ).map(statusOption);
  return [...dryRun, ...commit];
}

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
  ["clientResolvedBy", "Client matched by"],
];

/**
 * Ids the trust dry run stores on a resolved row (TrustPositionDatasetHandler): internal, never
 * shown — the references they came from are (D-175 approach).
 */
const TRUST_HIDDEN_KEYS: ReadonlySet<string> = new Set([
  "clientId",
  "matterId",
  "investmentAccountId",
]);

/** A coded trust value's label ("DEPOSIT_IN_TRANSIT" → "Deposit in transit"); else as text. */
function trustValueText(key: string, value: unknown): string {
  if (value === null || value === undefined || value === "") return "";
  const text = String(value);
  return TAKE_ON_TRUST_VALUE_LABEL[key]?.[text] ?? text;
}

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
 * A valid row that matches an existing client shows "Duplicate — will be skipped" and has its own
 * filter, so the Duplicates card, the filter and the table agree (OBS-655A-1). After a commit the
 * same table shows each row's outcome (Created, Duplicate — skipped, Failed). Clients commit from
 * the Attestation step (655A); matters, which need no attestation, from under this step (656A,
 * D-169). Matters rows show the client the dry run matched and the responsible attorney.
 */
export function ValidationStep({ batch, dataset, onBatchChange }: ValidationStepProps) {
  const allowed = new Set(batch.allowedActions);
  const canValidate = allowed.has("VALIDATE") || allowed.has("REVALIDATE");
  const validated = batch.status === "VALIDATED";
  const isTrust = dataset.requiresTrustAccount;
  const isClients = dataset.dataset === "CLIENTS";
  const isMatters = dataset.dataset === "MATTERS";

  const [status, setStatus] = useState<string>(ALL);
  const [section, setSection] = useState<string>(ALL);
  const [blocksOnly, setBlocksOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  /**
   * The one request on this batch in flight: the manual dry run or the position save (which runs
   * its own dry run). Each disables the other, so a stale response never overwrites a newer batch.
   */
  const [inFlight, setInFlight] = useState<"dryRun" | "position" | null>(null);

  const rowsKey = `take-on-rows-${batch.id}-${batch.status}-${batch.validatedAt ?? "none"}-${status}-${section}-${blocksOnly}-${page}`;
  const rows = useSWR(rowsKey, async () => {
    const result = await fetchTakeOnRows(batch.id, {
      status:
        status === ALL
          ? undefined
          : status === DUPLICATE
            ? "VALIDATED"
            : (status as TakeOnRowStatus),
      duplicatesOnly: status === DUPLICATE,
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
    setInFlight("dryRun");
    try {
      const result = await validateBatch(batch.id);
      if (!result.success || !result.data) {
        setError(result.error ?? "The dry run could not run.");
        return;
      }
      setPage(0);
      onBatchChange(result.data);
    } finally {
      setInFlight(null);
    }
  }

  /** A batch the position form saved (and re-validated): its rows start on the first page. */
  function positionSaved(next: TakeOnBatch) {
    setPage(0);
    onBatchChange(next);
  }

  function filterChanged(apply: () => void) {
    apply();
    setPage(0);
  }

  const counts = batch.counts;
  const totalPages = rows.data?.page.totalPages ?? 0;
  const summary = validated ? batch.summary : null;

  return (
    <div className="space-y-6" data-testid="take-on-validation">
      {isTrust && (
        <TrustPositionForm
          batch={batch}
          onBatchChange={positionSaved}
          disabled={inFlight === "dryRun"}
          onBusyChange={(b) => setInFlight(b ? "position" : null)}
        />
      )}
      <div className="flex flex-wrap items-center gap-3">
        {canValidate && (
          <Button
            type="button"
            variant="accent"
            disabled={inFlight !== null}
            onClick={runDryRun}
            data-testid="take-on-validate"
          >
            {inFlight === "dryRun"
              ? "Running the dry run…"
              : validated
                ? "Run the dry run again"
                : "Run dry run"}
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

      {isTrust && summary?.accountErrors && summary.accountErrors.length > 0 && (
        <div
          role="alert"
          data-testid="take-on-account-errors"
          className="space-y-1 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200"
        >
          <p className="font-medium">Correct these before the dry run can check the figures</p>
          <ul className="list-disc space-y-1 pl-5">
            {summary.accountErrors.map((m, i) => (
              <li key={`${m.code}-${i}`}>{m.message}</li>
            ))}
          </ul>
        </div>
      )}

      {isTrust && validated && counts.blocks > 0 && (
        <TrustBlockBanner blocks={summary?.blocks ?? []} rowBlocks={rowBlockCount(batch)} />
      )}

      {isTrust && <TrustEquationIndicator equation={summary?.equation} />}

      {isTrust && summary && <TrustFlags summary={summary} />}

      {!isTrust && validated && counts.blocks > 0 && (
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
            {filterOptionsFor(batch).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                {label}
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
      {rows.data && (
        <RowTable
          rows={rows.data.content}
          labels={labels}
          isTrust={isTrust}
          isClients={isClients}
          isMatters={isMatters}
        />
      )}
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

/** The rows holding a block: `counts.blocks` also counts the batch-level blocks (D-184). */
function rowBlockCount(batch: TakeOnBatch): number {
  const batchBlocks = batch.summary?.blocks?.length ?? 0;
  return Math.max(0, batch.counts.blocks - batchBlocks);
}

/**
 * The trust position's statutory blocks (§17.3.9, X3 texts verbatim, the deficit first as sent)
 * and the rows holding one. Never overridable: nothing here dismisses or acknowledges a block —
 * the only way on is a corrected file or position and a new dry run. Product copy: D-190.
 */
function TrustBlockBanner({
  blocks,
  rowBlocks,
}: {
  blocks: readonly { code: string; message: string }[];
  rowBlocks: number;
}) {
  return (
    <div
      role="alert"
      data-testid="take-on-trust-block-banner"
      className="space-y-2 rounded-lg border-2 border-l-8 border-red-700 bg-red-50 px-4 py-3 text-sm text-red-900 dark:border-red-500 dark:bg-red-950 dark:text-red-100"
    >
      <p className="font-semibold">Statutory block — cannot be overridden</p>
      {blocks.length > 0 && (
        <ul className="list-disc space-y-1 pl-5" data-testid="take-on-trust-blocks">
          {blocks.map((m, i) => (
            <li key={`${m.code}-${i}`} data-testid={`take-on-trust-block-${i}`}>
              {m.message}
            </li>
          ))}
        </ul>
      )}
      {rowBlocks > 0 && (
        <p data-testid="take-on-trust-row-blocks">
          <span className="font-mono tabular-nums">{rowBlocks}</span>{" "}
          {rowBlocks === 1 ? "row holds" : "rows hold"} a statutory block (marked Block below).
          Correct {rowBlocks === 1 ? "it" : "them"} in the previous system, then upload the
          corrected file.
        </p>
      )}
    </div>
  );
}

/** Flags, not blocks: the batch warnings' texts verbatim, then any other flag by its label. */
function TrustFlags({ summary }: { summary: NonNullable<TakeOnBatch["summary"]> }) {
  const warnings = summary.warnings ?? [];
  const withText = new Set(warnings.map((w) => w.code));
  const others = (summary.flags ?? []).filter((f) => !withText.has(f));
  if (warnings.length === 0 && others.length === 0) return null;
  return (
    <div
      data-testid="take-on-trust-flags"
      className="space-y-1 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100"
    >
      <p className="font-medium">Flags</p>
      <ul className="list-disc space-y-1 pl-5">
        {warnings.map((w, i) => (
          <li key={`${w.code}-${i}`}>{w.message}</li>
        ))}
        {others.map((f) => (
          <li key={f}>{TAKE_ON_TRUST_FLAG_LABEL[f] ?? "Flagged"}</li>
        ))}
      </ul>
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
  isClients,
  isMatters,
}: {
  rows: TakeOnRow[];
  labels: Map<string, string>;
  isTrust: boolean;
  isClients: boolean;
  isMatters: boolean;
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
            {isClients && <ClientColumnHeads />}
            {isMatters && <MatterColumnHeads />}
            <TableHead>Status</TableHead>
            <TableHead>{isClients || isMatters ? "Other values" : "Values"}</TableHead>
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
              {isClients && <ClientColumnCells rowNumber={row.rowNumber} values={row.values} />}
              {isMatters && <MatterColumnCells row={row} />}
              <TableCell>
                <Badge
                  variant={isPendingDuplicate(row) ? "warning" : STATUS_VARIANT[row.status]}
                  data-testid={`take-on-row-status-${row.rowNumber}`}
                >
                  {isPendingDuplicate(row)
                    ? TAKE_ON_DUPLICATE_PENDING_LABEL
                    : TAKE_ON_ROW_STATUS_LABEL[row.status]}
                </Badge>
                {row.block && (
                  <Badge variant="destructive" className="ml-1">
                    Block
                  </Badge>
                )}
              </TableCell>
              <TableCell className="max-w-md text-xs text-slate-700 dark:text-slate-300">
                <RowValues
                  values={row.values}
                  labels={labels}
                  exclude={
                    isClients
                      ? CLIENT_COLUMN_KEYS
                      : isMatters
                        ? MATTER_COLUMN_KEYS
                        : isTrust
                          ? TRUST_HIDDEN_KEYS
                          : undefined
                  }
                  valueText={
                    isMatters ? matterValueText : isTrust ? trustValueText : clientValueText
                  }
                />
              </TableCell>
              <TableCell className="max-w-md text-xs whitespace-normal">
                <ul className="space-y-0.5">
                  {isPendingDuplicate(row) && (
                    <li
                      className="text-amber-700 dark:text-amber-300"
                      data-testid={`take-on-row-duplicate-${row.rowNumber}`}
                    >
                      {isClients
                        ? "Matches a client already in Kazi"
                        : "Matches a record already in Kazi"}
                    </li>
                  )}
                  {row.failureReason && (
                    <li
                      className="text-red-700 dark:text-red-300"
                      data-testid={`take-on-row-failure-${row.rowNumber}`}
                    >
                      {row.failureReason}
                    </li>
                  )}
                  {row.errors.map((m, i) => (
                    <li key={`e${i}`} className="text-red-700 dark:text-red-300">
                      {m.message ?? PURGED_MESSAGE}
                    </li>
                  ))}
                  {row.warnings.map((m, i) => (
                    <li key={`w${i}`} className="text-amber-700 dark:text-amber-300">
                      {m.message ?? PURGED_MESSAGE}
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
  exclude,
  valueText,
}: {
  values: Record<string, unknown> | null;
  labels: Map<string, string>;
  exclude?: ReadonlySet<string>;
  /** The dataset's label for a coded value (never a raw enum on screen). */
  valueText: (key: string, value: unknown) => string;
}) {
  if (!values) return null;
  if (values.purged === true) {
    return <span>Deleted when the take-on was cancelled</span>;
  }
  if (values.redacted === true) {
    return <span>Removed when the client&apos;s personal information was anonymised</span>;
  }
  const entries = Object.entries(values).filter(
    ([k, v]) => v !== null && v !== "" && !exclude?.has(k)
  );
  return (
    <span className="line-clamp-2 whitespace-normal">
      {entries.map(([k, v], i) => (
        <span key={k}>
          {i > 0 && " · "}
          <span className="text-slate-500 dark:text-slate-400">{labels.get(k) ?? k}:</span>{" "}
          {typeof v === "string" || typeof v === "boolean" ? valueText(k, v) : formatValue(v)}
        </span>
      ))}
    </span>
  );
}
