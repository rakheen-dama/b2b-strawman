"use client";

import { useCallback, useState } from "react";
import { CheckCircle2, XCircle, Link2, Unlink } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { RecordFromStatementDialog } from "@/components/trust/RecordFromStatementDialog";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import {
  hasBlockingFlag,
  integrityFlagLabel,
  integritySeverityLabel,
  isBlockingFlag,
} from "@/lib/trust-integrity";
import type {
  BankStatementLineResponse,
  BankStatementLineMatchStatus,
  TrustAccountSection,
  TrustInvestment,
  TrustTransaction,
  TrustReconciliationResponse,
} from "@/lib/types";
import type { RecordFromStatementFormData } from "@/lib/schemas/trust";

// ── Helpers ───────────────────────────────────────────────────────

function lineColorClasses(status: BankStatementLineMatchStatus): string {
  switch (status) {
    case "AUTO_MATCHED":
      return "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950";
    case "MANUALLY_MATCHED":
      return "border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-950";
    case "EXCLUDED":
      return "border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800";
    case "RECORDED":
      return "border-teal-200 bg-teal-50 dark:border-teal-900 dark:bg-teal-950";
    case "UNMATCHED":
    default:
      return "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950";
  }
}

function statusLabel(status: BankStatementLineMatchStatus): string {
  switch (status) {
    case "AUTO_MATCHED":
      return "Auto-matched";
    case "MANUALLY_MATCHED":
      return "Matched";
    case "EXCLUDED":
      return "Excluded";
    case "RECORDED":
      return "Recorded";
    case "UNMATCHED":
    default:
      return "Unmatched";
  }
}

/** A line is resolved once it is matched, recorded from or excluded. */
const RESOLVED_STATUSES: ReadonlySet<BankStatementLineMatchStatus> = new Set([
  "AUTO_MATCHED",
  "MANUALLY_MATCHED",
  "EXCLUDED",
  "RECORDED",
]);

/**
 * Unmatch of a line recorded from the statement (647A): reverses the entries it created, so it asks
 * first. Owns its trigger (a plain button — never an adjacent `asChild` trigger).
 */
function UnmatchRecordedLineDialog({
  line,
  disabled,
  onConfirm,
}: {
  line: BankStatementLineResponse;
  disabled: boolean;
  onConfirm: (lineId: string) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [isWorking, setIsWorking] = useState(false);
  const entries = line.createdTransactionIds.length || 1;

  async function handleConfirm() {
    setIsWorking(true);
    try {
      await onConfirm(line.id);
      setOpen(false);
    } finally {
      setIsWorking(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => setOpen(true)}
        disabled={disabled}
        data-testid={`unmatch-btn-${line.id}`}
      >
        <Unlink className="mr-1 size-3" />
        Unmatch
      </Button>
      <AlertDialogContent data-testid="unmatch-recorded-dialog">
        <AlertDialogHeader>
          <AlertDialogTitle>Unmatch recorded line?</AlertDialogTitle>
          <AlertDialogDescription>
            This line was recorded as {line.classificationLabel ?? "an entry from the statement"}.
            Unmatching it reverses the {entries === 1 ? "entry" : `${entries} entries`} it created,
            dated {formatLocalDate(line.transactionDate)} — the line&apos;s date. The line returns
            to Unmatched.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isWorking}>
            Keep
          </AlertDialogCancel>
          <Button
            type="button"
            variant="destructive"
            onClick={handleConfirm}
            disabled={isWorking}
            data-testid="unmatch-recorded-confirm"
          >
            {isWorking ? "Reversing..." : "Unmatch and reverse"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

function SummaryRow({
  label,
  value,
  currency,
  muted = false,
}: {
  label: string;
  value: number;
  currency: string;
  muted?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4 text-sm">
      <dt
        className={
          muted ? "text-slate-500 dark:text-slate-400" : "text-slate-700 dark:text-slate-300"
        }
      >
        {label}
      </dt>
      <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
        {formatCurrency(value, currency)}
      </dd>
    </div>
  );
}

function DifferenceRow({
  value,
  currency,
  testId,
}: {
  value: number;
  currency: string;
  testId: string;
}) {
  const agrees = value === 0;
  return (
    <div className="flex items-baseline justify-between gap-4 border-t border-slate-200 pt-1 text-sm font-medium dark:border-slate-700">
      <dt className="text-slate-950 dark:text-slate-50">Difference</dt>
      <dd
        className={`font-mono tabular-nums ${
          agrees ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"
        }`}
        data-testid={testId}
      >
        {formatCurrency(value, currency)}
      </dd>
    </div>
  );
}

// ── Props ─────────────────────────────────────────────────────────

interface ReconciliationSplitPaneProps {
  lines: BankStatementLineResponse[];
  unmatchedTransactions: TrustTransaction[];
  reconciliation: TrustReconciliationResponse | null;
  currency: string;
  onMatch: (lineId: string, transactionId: string) => Promise<void>;
  onExclude: (lineId: string) => Promise<void>;
  onUnmatch: (lineId: string) => Promise<void>;
  /** Record from statement (647A); a refusal comes back as `error` for the dialog to show. */
  onRecord: (
    lineId: string,
    input: RecordFromStatementFormData
  ) => Promise<{ success: boolean; error?: string }>;
  onComplete: () => Promise<void>;
  isCompleting: boolean;
  /** A saved change could not be recalculated: the summary's figures are from before it (652A). */
  figuresStale?: boolean;
  /** The reconciled account's section: picks the classifications offered (648B; default 86(2)). */
  accountSection?: TrustAccountSection;
  /** A section 86(4) account's interest-bearing investments (648B). */
  investments?: TrustInvestment[];
}

export function ReconciliationSplitPane({
  accountSection,
  investments,
  lines,
  unmatchedTransactions,
  reconciliation,
  currency,
  onMatch,
  onExclude,
  onUnmatch,
  onRecord,
  onComplete,
  isCompleting,
  figuresStale = false,
}: ReconciliationSplitPaneProps) {
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Calculate progress
  const totalLines = lines.length;
  const matchedLines = lines.filter((l) => RESOLVED_STATUSES.has(l.matchStatus)).length;
  const progressPercent = totalLines > 0 ? (matchedLines / totalLines) * 100 : 0;

  // Find candidate transactions for selected line (same absolute amount)
  const selectedLine = lines.find((l) => l.id === selectedLineId);
  const candidateTransactionIds = new Set<string>();
  if (selectedLine && selectedLine.matchStatus === "UNMATCHED") {
    const targetAmount = Math.abs(selectedLine.amount);
    for (const tx of unmatchedTransactions) {
      if (Math.abs(tx.amount - targetAmount) < 0.01) {
        candidateTransactionIds.add(tx.id);
      }
    }
  }

  const handleMatch = useCallback(
    async (lineId: string, transactionId: string) => {
      setActionInProgress(lineId);
      try {
        await onMatch(lineId, transactionId);
        setSelectedLineId(null);
      } finally {
        setActionInProgress(null);
      }
    },
    [onMatch]
  );

  const handleExclude = useCallback(
    async (lineId: string) => {
      setActionInProgress(lineId);
      try {
        await onExclude(lineId);
        setSelectedLineId(null);
      } finally {
        setActionInProgress(null);
      }
    },
    [onExclude]
  );

  const handleUnmatch = useCallback(
    async (lineId: string) => {
      setActionInProgress(lineId);
      try {
        await onUnmatch(lineId);
      } finally {
        setActionInProgress(null);
      }
    },
    [onUnmatch]
  );

  const handleRecord = useCallback(
    async (lineId: string, input: RecordFromStatementFormData) => {
      setActionInProgress(lineId);
      try {
        const result = await onRecord(lineId, input);
        if (result.success) {
          setSelectedLineId(null);
        }
        return result;
      } finally {
        setActionInProgress(null);
      }
    },
    [onRecord]
  );

  return (
    <div className="space-y-6" data-testid="reconciliation-split-pane">
      {/* Progress Bar */}
      <div data-testid="match-progress">
        <div className="mb-1 flex items-center justify-between text-sm">
          <span className="text-slate-600 dark:text-slate-400">Matching Progress</span>
          <span className="font-medium text-slate-950 dark:text-slate-50">
            {matchedLines} / {totalLines} lines resolved
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            className="h-full rounded-full bg-teal-600 transition-all dark:bg-teal-500"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {/* Split Pane */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Left: Bank Statement Lines */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Bank Statement Lines</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {lines.length === 0 ? (
              <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
                No bank statement lines
              </p>
            ) : (
              lines.map((line) => {
                const isSelected = selectedLineId === line.id;
                const isProcessing = actionInProgress === line.id;

                return (
                  <div
                    key={line.id}
                    className={`cursor-pointer rounded-md border p-3 transition-colors ${lineColorClasses(line.matchStatus)} ${
                      isSelected ? "ring-2 ring-teal-500" : ""
                    }`}
                    onClick={() => {
                      if (line.matchStatus === "UNMATCHED") {
                        setSelectedLineId(isSelected ? null : line.id);
                      }
                    }}
                    data-testid={`bank-line-${line.id}`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-500 dark:text-slate-400">
                            #{line.lineNumber}
                          </span>
                          <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                            {formatLocalDate(line.transactionDate)}
                          </span>
                        </div>
                        <p className="mt-0.5 truncate text-sm text-slate-700 dark:text-slate-300">
                          {line.description}
                        </p>
                        {line.reference && (
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            Ref: {line.reference}
                          </p>
                        )}
                      </div>
                      <div className="text-right">
                        <p
                          className={`font-mono text-sm tabular-nums ${
                            line.amount >= 0
                              ? "text-green-600 dark:text-green-400"
                              : "text-slate-950 dark:text-slate-50"
                          }`}
                        >
                          {formatCurrency(Math.abs(line.amount), currency)}
                          {line.amount < 0 ? " DR" : " CR"}
                        </p>
                        {line.matchStatus === "RECORDED" ? (
                          <Badge
                            variant="success"
                            className="mt-1 max-w-56 text-left whitespace-normal"
                            data-testid={`recorded-badge-${line.id}`}
                          >
                            {line.classificationLabel
                              ? `Recorded: ${line.classificationLabel}`
                              : statusLabel(line.matchStatus)}
                          </Badge>
                        ) : (
                          <Badge
                            variant={
                              line.matchStatus === "UNMATCHED"
                                ? "warning"
                                : line.matchStatus === "AUTO_MATCHED"
                                  ? "success"
                                  : "neutral"
                            }
                            className="mt-1"
                          >
                            {statusLabel(line.matchStatus)}
                          </Badge>
                        )}
                      </div>
                    </div>

                    {/* Actions for matched lines (unmatch at once) */}
                    {(line.matchStatus === "AUTO_MATCHED" ||
                      line.matchStatus === "MANUALLY_MATCHED") && (
                      <div className="mt-2 flex justify-end">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleUnmatch(line.id);
                          }}
                          disabled={isProcessing}
                          data-testid={`unmatch-btn-${line.id}`}
                        >
                          <Unlink className="mr-1 size-3" />
                          Unmatch
                        </Button>
                      </div>
                    )}

                    {/* A recorded line: unmatch reverses its entries, so it asks first. The
                        wrapper stops clicks inside the (portalled) dialog reaching the line. */}
                    {line.matchStatus === "RECORDED" && (
                      <div
                        className="mt-2 flex justify-end"
                        role="presentation"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <UnmatchRecordedLineDialog
                          line={line}
                          disabled={isProcessing}
                          onConfirm={handleUnmatch}
                        />
                      </div>
                    )}

                    {/* Actions for unmatched lines. React events bubble out of the portalled
                        Record dialog: the wrapper keeps an inner click from deselecting the line
                        (which would unmount the dialog). */}
                    {line.matchStatus === "UNMATCHED" && isSelected && (
                      <div
                        className="mt-2 flex justify-end gap-1"
                        role="presentation"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <RecordFromStatementDialog
                          line={line}
                          currency={currency}
                          accountSection={accountSection}
                          investments={investments}
                          disabled={isProcessing}
                          onRecord={handleRecord}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleExclude(line.id);
                          }}
                          disabled={isProcessing}
                          data-testid={`exclude-btn-${line.id}`}
                        >
                          <XCircle className="mr-1 size-3" />
                          Exclude
                        </Button>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>

        {/* Right: Unmatched Trust Transactions */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Unmatched Trust Transactions</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {unmatchedTransactions.length === 0 ? (
              <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
                All transactions are matched
              </p>
            ) : (
              unmatchedTransactions.map((tx) => {
                const isCandidate = candidateTransactionIds.has(tx.id);

                return (
                  <div
                    key={tx.id}
                    className={`rounded-md border p-3 transition-colors ${
                      isCandidate
                        ? "border-teal-300 bg-teal-50 ring-1 ring-teal-200 dark:border-teal-800 dark:bg-teal-950 dark:ring-teal-900"
                        : "border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900"
                    }`}
                    data-testid={`transaction-${tx.id}`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                          {tx.reference}
                        </p>
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {formatLocalDate(tx.transactionDate)} &middot; {tx.typeLabel}
                        </p>
                        {tx.description && (
                          <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">
                            {tx.description}
                          </p>
                        )}
                      </div>
                      <div className="text-right">
                        <p className="font-mono text-sm text-slate-950 tabular-nums dark:text-slate-50">
                          {formatCurrency(tx.amount, currency)}
                        </p>
                        {isCandidate && selectedLineId && (
                          <Button
                            type="button"
                            size="sm"
                            className="mt-1"
                            onClick={() => handleMatch(selectedLineId, tx.id)}
                            disabled={actionInProgress !== null}
                            data-testid={`match-btn-${tx.id}`}
                          >
                            <Link2 className="mr-1 size-3" />
                            Match
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>

      {reconciliation && (
        <ReconciliationSummary
          reconciliation={reconciliation}
          currency={currency}
          onComplete={onComplete}
          isCompleting={isCompleting}
          figuresStale={figuresStale}
        />
      )}
    </div>
  );
}

/** The trust equation as at the period end: two comparisons and the creditor breakdown (646C). */
function ReconciliationSummary({
  reconciliation,
  currency,
  onComplete,
  isCompleting,
  figuresStale,
}: {
  reconciliation: TrustReconciliationResponse;
  currency: string;
  onComplete: () => Promise<void>;
  isCompleting: boolean;
  figuresStale: boolean;
}) {
  const breakdown = reconciliation.creditorBreakdown;
  const flags = reconciliation.integrityFlags ?? [];
  const canComplete =
    reconciliation.isBalanced &&
    reconciliation.unresolvedLineCount === 0 &&
    !hasBlockingFlag(flags);
  return (
    <Card data-testid="balance-summary">
      <CardHeader>
        <CardTitle className="text-base">
          As at {formatLocalDate(reconciliation.periodEnd)}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {figuresStale && (
          <p
            role="status"
            className="text-sm text-amber-700 dark:text-amber-300"
            data-testid="reconciliation-figures-stale"
          >
            These figures are out of date: they were calculated before your last change.
          </p>
        )}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          <section data-testid="comparison-bank-cashbook">
            <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
              Bank and cashbook
            </h3>
            <dl className="mt-2 space-y-1">
              <SummaryRow
                label="Bank balance"
                value={reconciliation.bankBalance}
                currency={currency}
              />
              <SummaryRow
                label="+ Outstanding deposits"
                value={reconciliation.outstandingDeposits}
                currency={currency}
                muted
              />
              <SummaryRow
                label="− Outstanding payments"
                value={reconciliation.outstandingPayments}
                currency={currency}
                muted
              />
              <SummaryRow
                label="= Adjusted bank balance"
                value={reconciliation.adjustedBankBalance}
                currency={currency}
              />
              <SummaryRow
                label="Cashbook"
                value={reconciliation.cashbookBalance}
                currency={currency}
              />
              <DifferenceRow
                value={reconciliation.bankToCashbookDifference}
                currency={currency}
                testId="bank-to-cashbook-difference"
              />
            </dl>
          </section>

          <section data-testid="comparison-cashbook-creditors">
            <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
              Cashbook and trust creditors
            </h3>
            <dl className="mt-2 space-y-1">
              <SummaryRow
                label="Cashbook"
                value={reconciliation.cashbookBalance}
                currency={currency}
              />
              <SummaryRow label="Trust creditors" value={breakdown.total} currency={currency} />
              <DifferenceRow
                value={reconciliation.cashbookToCreditorsDifference}
                currency={currency}
                testId="cashbook-to-creditors-difference"
              />
            </dl>
          </section>
        </div>

        <section
          data-testid="creditor-breakdown"
          className="border-t border-slate-200 pt-4 dark:border-slate-700"
        >
          <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">Trust creditors</h3>
          <dl className="mt-2 space-y-1">
            <SummaryRow
              label="Client ledgers"
              value={breakdown.clientLedgers}
              currency={currency}
            />
            {breakdown.investmentCreditors !== 0 && (
              <SummaryRow
                label="Investment creditors (section 86(4))"
                value={breakdown.investmentCreditors}
                currency={currency}
              />
            )}
            <SummaryRow
              label="Legal Practitioners' Fidelity Fund — interest and charges"
              value={breakdown.fundInterestAndCharges}
              currency={currency}
            />
            <SummaryRow
              label="Unidentified deposits"
              value={breakdown.unidentifiedDeposits}
              currency={currency}
            />
            {breakdown.interAccount !== 0 && (
              <SummaryRow
                label={
                  breakdown.interAccount < 0
                    ? "Placed in section 86(3) investment accounts"
                    : "Held for the firm's trust creditors"
                }
                value={breakdown.interAccount}
                currency={currency}
              />
            )}
            {breakdown.otherNonClient !== 0 && (
              <SummaryRow
                label="Other non-client trust creditors"
                value={breakdown.otherNonClient}
                currency={currency}
              />
            )}
          </dl>
        </section>

        <section className="space-y-1 border-t border-slate-200 pt-4 text-sm dark:border-slate-700">
          <p className="text-slate-700 dark:text-slate-300" data-testid="unresolved-line-count">
            {reconciliation.unresolvedLineCount === 1
              ? "1 statement line is neither matched, recorded nor excluded"
              : `${reconciliation.unresolvedLineCount} statement lines are neither matched, recorded nor excluded`}
          </p>
          <p className="text-slate-700 dark:text-slate-300" data-testid="excluded-lines-total">
            Excluded statement lines total{" "}
            <span className="font-mono tabular-nums">
              {formatCurrency(reconciliation.excludedLinesTotal, currency)}
            </span>
            . An excluded line adjusts nothing: excluding a real bank movement leaves its amount as
            the difference between the bank and the cashbook.
          </p>
        </section>

        {flags.length > 0 && (
          <section
            data-testid="reconciliation-integrity-flags"
            className="border-t border-slate-200 pt-4 dark:border-slate-700"
          >
            <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
              Integrity check
            </h3>
            <ul className="mt-2 space-y-2">
              {flags.map((flag, i) => (
                <li
                  key={`${flag.code}-${i}`}
                  className="space-y-0.5 text-sm"
                  data-testid={`reconciliation-flag-${flag.code}`}
                >
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <Badge
                      variant={isBlockingFlag(flag) ? "destructive" : "warning"}
                      className="shrink whitespace-normal"
                    >
                      {integritySeverityLabel(flag.severity)}
                    </Badge>
                    <span className="min-w-0 font-medium break-words text-slate-950 dark:text-slate-50">
                      {integrityFlagLabel(flag.code)}
                    </span>
                  </div>
                  <p className="text-muted-foreground break-words">{flag.message}</p>
                </li>
              ))}
            </ul>
          </section>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4 dark:border-slate-700">
          <div className="flex items-center gap-2">
            {reconciliation.isBalanced ? (
              <>
                <CheckCircle2 className="size-5 text-green-600 dark:text-green-400" />
                <span className="text-sm font-medium text-green-600 dark:text-green-400">
                  Reconciliation is balanced
                </span>
              </>
            ) : (
              <>
                <XCircle className="size-5 text-red-600 dark:text-red-400" />
                <span className="text-sm font-medium text-red-600 dark:text-red-400">
                  Reconciliation is not balanced
                </span>
              </>
            )}
          </div>
          {/* 649C (§16.6.1, ruling D-10): Complete is enabled only when the response says balanced
                with no unresolved line and no blocking flag; the backend gate G1–G4 is the
                authority and a refusal is shown by the wizard. */}
          <Button
            type="button"
            onClick={onComplete}
            disabled={!canComplete || isCompleting}
            data-testid="complete-reconciliation-btn"
          >
            {isCompleting ? "Completing..." : "Complete Reconciliation"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
