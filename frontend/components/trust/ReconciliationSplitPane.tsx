"use client";

import { useCallback, useState } from "react";
import { CheckCircle2, XCircle, Link2, Unlink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type {
  BankStatementLineResponse,
  BankStatementLineMatchStatus,
  TrustTransaction,
  TrustReconciliationResponse,
} from "@/lib/types";

// ── Helpers ───────────────────────────────────────────────────────

function lineColorClasses(status: BankStatementLineMatchStatus): string {
  switch (status) {
    case "AUTO_MATCHED":
      return "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950";
    case "MANUALLY_MATCHED":
      return "border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-950";
    case "EXCLUDED":
      return "border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800";
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
    case "UNMATCHED":
    default:
      return "Unmatched";
  }
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
  onComplete: () => Promise<void>;
  isCompleting: boolean;
}

export function ReconciliationSplitPane({
  lines,
  unmatchedTransactions,
  reconciliation,
  currency,
  onMatch,
  onExclude,
  onUnmatch,
  onComplete,
  isCompleting,
}: ReconciliationSplitPaneProps) {
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  // Calculate progress
  const totalLines = lines.length;
  const matchedLines = lines.filter(
    (l) =>
      l.matchStatus === "AUTO_MATCHED" ||
      l.matchStatus === "MANUALLY_MATCHED" ||
      l.matchStatus === "EXCLUDED"
  ).length;
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
                      </div>
                    </div>

                    {/* Actions for matched/excluded lines */}
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
                        >
                          <Unlink className="mr-1 size-3" />
                          Unmatch
                        </Button>
                      </div>
                    )}

                    {/* Actions for unmatched lines */}
                    {line.matchStatus === "UNMATCHED" && isSelected && (
                      <div className="mt-2 flex justify-end">
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
                          {formatLocalDate(tx.transactionDate)} &middot;{" "}
                          {tx.transactionType.replace(/_/g, " ")}
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

      {/* Three-Way Balance Summary */}
      {reconciliation && (
        <Card data-testid="balance-summary">
          <CardHeader>
            <CardTitle className="text-base">Three-Way Balance Check</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Bank Balance
                </p>
                <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(reconciliation.bankBalance, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Cashbook Balance
                </p>
                <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(reconciliation.cashbookBalance, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Client Ledger Total
                </p>
                <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(reconciliation.clientLedgerTotal, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Outstanding Deposits
                </p>
                <p className="mt-1 font-mono text-sm text-slate-700 tabular-nums dark:text-slate-300">
                  {formatCurrency(reconciliation.outstandingDeposits, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Outstanding Payments
                </p>
                <p className="mt-1 font-mono text-sm text-slate-700 tabular-nums dark:text-slate-300">
                  {formatCurrency(reconciliation.outstandingPayments, currency)}
                </p>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  Adjusted Bank Balance
                </p>
                <p
                  className={`mt-1 font-mono text-lg tabular-nums ${
                    reconciliation.isBalanced
                      ? "text-green-600 dark:text-green-400"
                      : "text-red-600 dark:text-red-400"
                  }`}
                >
                  {formatCurrency(reconciliation.adjustedBankBalance, currency)}
                </p>
              </div>
            </div>

            <div className="mt-4 flex items-center justify-between border-t border-slate-200 pt-4 dark:border-slate-700">
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
              <Button
                type="button"
                onClick={onComplete}
                disabled={!reconciliation.isBalanced || isCompleting}
                data-testid="complete-reconciliation-btn"
              >
                {isCompleting ? "Completing..." : "Complete Reconciliation"}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
