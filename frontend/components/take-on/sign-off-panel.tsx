"use client";

import { useState } from "react";
import useSWR, { useSWRConfig } from "swr";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  getSignOffReview,
  signOff,
  withdrawMySignOff,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { formatCurrency, formatDateTime } from "@/lib/format";
import {
  TAKE_ON_ROW_STATUS_LABEL,
  TAKE_ON_SECTION_LABEL,
  TAKE_ON_TRUST_FLAG_LABEL,
  takeOnLongDate,
  type TakeOnPosition,
  type TakeOnRow,
  type TakeOnSignOffReview,
} from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";
import { TrustEquationIndicator } from "./trust-equation-indicator";

const PAGE_SIZE = 50;

const reviewKey = (batchId: string, page: number) => `take-on-sign-off-review-${batchId}-${page}`;

/** The position inputs in X3 words (§17.10), in the order the form asks for them. */
const POSITION_LABEL: ReadonlyArray<readonly [keyof TakeOnPosition, string]> = [
  ["bankBalance", "Bank statement balance at cutover"],
  ["outstandingDepositsTotal", "Outstanding deposits at cutover"],
  ["outstandingPaymentsTotal", "Outstanding payments at cutover"],
  ["cashbookBalance", "Cashbook balance at cutover"],
  ["fundLedgerBalance", "Fund interest and charges ledger at cutover"],
  ["investedTotal", "Total invested (per the institutions' confirmations)"],
];

/** Product copy (657C, logged): why there is nothing to sign. */
const NOT_SIGNABLE =
  "This trust take-on cannot be signed off now. It must be validated, with no errors or blocks, and not signed off already.";

/** Product copy (657C, RG-10, D-202): a batch past sign-off says where it is instead. */
const PAST_SIGN_OFF: Partial<Record<TakeOnSignOffReview["status"], string>> = {
  COMMITTING: "This trust take-on is being committed.",
  COMPLETED: "This trust take-on has been committed.",
};

/** The row's reference in the previous system: client and matter, the bank reference or the account. */
function rowReference(row: TakeOnRow): string {
  const v = row.values ?? {};
  const text = (key: string) => (typeof v[key] === "string" ? (v[key] as string) : "");
  const parts = [text("clientRef"), text("matterRef")].filter(Boolean);
  if (parts.length > 0) return parts.join(" · ");
  return text("bankReference") || text("investmentAccount") || text("reference") || "—";
}

/** The row's amount (balance, amount or principal) — the figure the signer agrees to. */
function rowAmount(row: TakeOnRow): string {
  const v = row.values ?? {};
  const raw = v.balance ?? v.amount ?? v.principal;
  if (raw === null || raw === undefined || raw === "") return "—";
  const n = Number(raw);
  return Number.isFinite(n) ? formatCurrency(n, "ZAR") : String(raw);
}

interface SignOffPanelProps {
  batchId: string;
  /** A sign-off or withdrawal succeeded: the review as the server answered it (page 0). */
  onChange?: (review: TakeOnSignOffReview) => void;
}

/**
 * The trust take-on sign-off (Phase 93 657C, §17.3.9, FD-4, FD-36): what the partner or director
 * signs — the reconciled position, the five control totals, blocks and flags, a page of the rows —
 * then the TAKEON-SIGNOFF-v1 declaration exactly as the server words it, an accept box and "Sign
 * off"; the signer may "Withdraw my sign-off" before the commit. Reads only `sign-off-review` and
 * writes only the signer's own endpoints — never a MANAGE_TAKE_ON one — so the Home sheet works
 * for a role that may sign but not manage take-ons. Controls follow the review's allowedActions.
 */
export function SignOffPanel({ batchId, onChange }: SignOffPanelProps) {
  const [page, setPage] = useState(0);
  // The declaration is accepted for the validation hash the signer reviewed: a re-validation that
  // changes the hash clears the acceptance, so only the accepted hash can be signed.
  const [acceptedHash, setAcceptedHash] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { mutate } = useSWRConfig();

  const review = useSWR(
    reviewKey(batchId, page),
    async () => {
      const result = await getSignOffReview(batchId, page, PAGE_SIZE);
      if (!result.success || !result.data) {
        throw new Error(result.error ?? "The trust take-on could not be loaded.");
      }
      return result.data;
    },
    { keepPreviousData: true }
  );

  if (review.error && !review.data) {
    return (
      <TakeOnError message={String(review.error.message)} testId="takeon-signoff-load-error" />
    );
  }
  if (!review.data) {
    return (
      <p
        role="status"
        className="text-sm text-slate-600 dark:text-slate-400"
        data-testid="takeon-signoff-loading"
      >
        Loading the trust take-on…
      </p>
    );
  }

  const data = review.data;
  const allowed = new Set(data.allowedActions);
  const live = data.liveSignOff;
  const declaration = data.declaration;
  const summary = data.summary;
  const totalPages = data.rows.page.totalPages;
  const accepted = acceptedHash !== null && acceptedHash === data.validationHash;

  async function submit(call: () => ReturnType<typeof signOff>, fallback: string) {
    setError(null);
    setBusy(true);
    try {
      const result = await call();
      if (!result.success || !result.data) {
        setError(result.error ?? fallback);
        return;
      }
      setAcceptedHash(null);
      // The answer is page 0 of the review: seed it so the panel re-renders from one shape.
      await mutate(reviewKey(batchId, 0), result.data, { revalidate: false });
      setPage(0);
      onChange?.(result.data);
    } finally {
      setBusy(false);
    }
  }

  function signNow() {
    if (!declaration || !accepted || acceptedHash === null) return;
    const request = {
      validationHash: acceptedHash,
      statementVersion: declaration.version,
      declarationAccepted: true,
    };
    return submit(() => signOff(batchId, request), "The trust take-on could not be signed off.");
  }

  function withdraw() {
    return submit(() => withdrawMySignOff(batchId), "The sign-off could not be withdrawn.");
  }

  return (
    <div className="space-y-6" data-testid="takeon-signoff-panel">
      <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
        <dt className="text-slate-500 dark:text-slate-400">Trust account</dt>
        <dd className="text-slate-900 dark:text-slate-100" data-testid="takeon-signoff-account">
          {data.trustAccountName ?? "—"}
        </dd>
        <dt className="text-slate-500 dark:text-slate-400">Cutover date</dt>
        <dd className="text-slate-900 dark:text-slate-100" data-testid="takeon-signoff-cutover">
          {takeOnLongDate(data.cutoverDate)}
          {data.midMonthAcknowledged && " (a mid-month cutover)"}
        </dd>
        <dt className="text-slate-500 dark:text-slate-400">Prepared by</dt>
        <dd className="text-slate-900 dark:text-slate-100" data-testid="takeon-signoff-prepared-by">
          {data.preparedByName ?? "—"}
          {data.validatedAt && `, validated ${formatDateTime(data.validatedAt)}`}
        </dd>
      </dl>

      {data.position && (
        <section className="space-y-2" data-testid="takeon-signoff-position">
          <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">
            Reconciled position at cutover
          </h4>
          <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[1fr_max-content]">
            {POSITION_LABEL.map(([key, label]) => (
              <div key={key} className="contents">
                <dt className="text-slate-600 dark:text-slate-400">{label}</dt>
                <dd
                  className="text-right font-mono text-slate-900 tabular-nums dark:text-slate-100"
                  data-testid={`takeon-signoff-position-${key}`}
                >
                  {data.position?.[key] == null
                    ? "—"
                    : formatCurrency(data.position[key] as number, "ZAR")}
                </dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      {summary?.accountErrors && summary.accountErrors.length > 0 && (
        <ul
          role="alert"
          data-testid="takeon-signoff-account-errors"
          className="list-disc space-y-1 rounded-lg bg-red-50 py-3 pr-4 pl-9 text-sm text-red-800 dark:bg-red-950 dark:text-red-200"
        >
          {summary.accountErrors.map((m, i) => (
            <li key={`${m.code}-${i}`}>{m.message}</li>
          ))}
        </ul>
      )}
      {summary?.blocks && summary.blocks.length > 0 && (
        <ul
          role="alert"
          data-testid="takeon-signoff-blocks"
          className="list-disc space-y-1 rounded-lg border-l-8 border-red-700 bg-red-50 py-3 pr-4 pl-9 text-sm text-red-900 dark:border-red-500 dark:bg-red-950 dark:text-red-100"
        >
          {summary.blocks.map((m, i) => (
            <li key={`${m.code}-${i}`}>{m.message}</li>
          ))}
        </ul>
      )}

      <TrustEquationIndicator equation={summary?.equation} />

      <SignOffFlags summary={summary} />

      <SignOffRows
        rows={data.rows.content}
        page={page}
        totalPages={totalPages}
        onPage={(next) => setPage(next)}
      />

      {declaration && (
        <figure className="space-y-2">
          <blockquote
            data-testid="takeon-signoff-declaration"
            className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-relaxed text-slate-800 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
          >
            {declaration.text}
          </blockquote>
          <figcaption className="text-xs text-slate-500 dark:text-slate-400">
            Declaration{" "}
            <span className="font-mono" data-testid="takeon-signoff-declaration-version">
              {declaration.version}
            </span>
          </figcaption>
        </figure>
      )}

      {live ? (
        <div
          className="space-y-3 rounded-lg border border-teal-200 bg-teal-50 px-4 py-3 dark:border-teal-900 dark:bg-teal-950"
          data-testid="takeon-signoff-live"
        >
          <p className="text-sm text-teal-900 dark:text-teal-100">
            Signed off by {data.signedByCaller ? "you" : live.memberName} ({live.roleName}) on{" "}
            {formatDateTime(live.signedAt)}.
          </p>
          {allowed.has("WITHDRAW_SIGN_OFF") && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={withdraw}
              data-testid="takeon-signoff-withdraw"
            >
              {busy ? "Withdrawing…" : "Withdraw my sign-off"}
            </Button>
          )}
        </div>
      ) : allowed.has("SIGN_OFF") && declaration ? (
        <div className="space-y-3">
          <label className="flex items-start gap-2 text-sm text-slate-800 dark:text-slate-200">
            <Checkbox
              checked={accepted}
              onCheckedChange={(c) =>
                setAcceptedHash(c === true ? (data.validationHash ?? null) : null)
              }
              disabled={busy}
              className="mt-0.5"
              data-testid="takeon-signoff-accept"
            />
            I have reviewed this take-on and I make the declaration above.
          </label>
          <Button
            type="button"
            variant="accent"
            disabled={!accepted || busy}
            onClick={signNow}
            data-testid="takeon-signoff-submit"
          >
            {busy ? "Signing off…" : "Sign off"}
          </Button>
        </div>
      ) : (
        <p
          className="text-sm text-slate-700 dark:text-slate-300"
          data-testid="takeon-signoff-unavailable"
        >
          {PAST_SIGN_OFF[data.status] ?? NOT_SIGNABLE}
        </p>
      )}

      {error && <TakeOnError message={error} testId="takeon-signoff-error" />}
    </div>
  );
}

/** Flags, never blocks: the summary's warnings verbatim, then any other flag by its label. */
function SignOffFlags({ summary }: { summary: TakeOnSignOffReview["summary"] }) {
  const warnings = summary?.warnings ?? [];
  const withText = new Set(warnings.map((w) => w.code));
  const others = (summary?.flags ?? []).filter((f) => !withText.has(f));
  if (warnings.length === 0 && others.length === 0) return null;
  return (
    <div
      data-testid="takeon-signoff-flags"
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

/** A page of the batch's rows: part, reference, amount, status and the dry run's messages. */
function SignOffRows({
  rows,
  page,
  totalPages,
  onPage,
}: {
  rows: TakeOnRow[];
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  return (
    <section className="space-y-2">
      <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">Rows</h4>
      {rows.length === 0 ? (
        <p className="text-sm text-slate-600 dark:text-slate-400">This take-on has no rows.</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
          <Table data-testid="takeon-signoff-rows">
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">Row</TableHead>
                <TableHead>Part</TableHead>
                <TableHead>Reference</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Messages</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-testid={`takeon-signoff-row-${row.section}-${row.rowNumber}`}
                >
                  <TableCell className="font-mono text-xs tabular-nums">{row.rowNumber}</TableCell>
                  <TableCell className="text-xs">{TAKE_ON_SECTION_LABEL[row.section]}</TableCell>
                  <TableCell className="text-xs">
                    {row.values?.redacted === true
                      ? "Removed when the client's personal information was anonymised"
                      : rowReference(row)}
                  </TableCell>
                  <TableCell className="text-right font-mono text-xs tabular-nums">
                    {rowAmount(row)}
                  </TableCell>
                  <TableCell>
                    <Badge variant={row.status === "ERROR" ? "destructive" : "neutral"}>
                      {TAKE_ON_ROW_STATUS_LABEL[row.status]}
                    </Badge>
                    {row.block && (
                      <Badge variant="destructive" className="ml-1">
                        Block
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="max-w-xs text-xs whitespace-normal">
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
      )}
      {totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm" data-testid="takeon-signoff-rows-pager">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onPage(page - 1)}
            data-testid="takeon-signoff-rows-prev"
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
            onClick={() => onPage(page + 1)}
            data-testid="takeon-signoff-rows-next"
          >
            Next
          </Button>
        </div>
      )}
    </section>
  );
}
