"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { CheckCircle2, XCircle, Loader2 } from "lucide-react";
import {
  manualMatch,
  unmatch,
  excludeLine,
  completeReconciliation,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type {
  BankStatementLine,
  TrustTransaction,
  TrustReconciliation,
} from "@/lib/types";

interface ReconciliationSplitPaneProps {
  reconciliationId: string;
  bankStatementLines: BankStatementLine[];
  unmatchedTransactions: TrustTransaction[];
  reconciliation: TrustReconciliation;
  currency: string;
  onComplete: () => void;
}

function getLineStatusClasses(status: BankStatementLine["matchStatus"]) {
  switch (status) {
    case "AUTO_MATCHED":
      return "border-l-4 border-green-500 bg-green-50 dark:bg-green-950/20";
    case "MANUALLY_MATCHED":
      return "border-l-4 border-blue-500 bg-blue-50 dark:bg-blue-950/20";
    case "EXCLUDED":
      return "border-l-4 border-slate-300 bg-slate-50 opacity-60 dark:border-slate-600 dark:bg-slate-900/40";
    case "UNMATCHED":
    default:
      return "border-l-4 border-amber-400 bg-amber-50 dark:bg-amber-950/20";
  }
}

export function ReconciliationSplitPane({
  reconciliationId,
  bankStatementLines: initialLines,
  unmatchedTransactions: initialTransactions,
  reconciliation: initialReconciliation,
  currency,
  onComplete,
}: ReconciliationSplitPaneProps) {
  const router = useRouter();
  const [lines, setLines] = useState(initialLines);
  const [transactions, setTransactions] = useState(initialTransactions);
  const [reconciliation, setReconciliation] = useState(initialReconciliation);
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null);
  const [selectedTransactionId, setSelectedTransactionId] = useState<
    string | null
  >(null);
  const [isMatching, setIsMatching] = useState(false);
  const [isCompleting, setIsCompleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [excludeLineId, setExcludeLineId] = useState<string | null>(null);
  const [excludeReason, setExcludeReason] = useState("");

  const sortedLines = [...lines].sort((a, b) => a.lineNumber - b.lineNumber);
  const selectedLine = lines.find((l) => l.id === selectedLineId);

  const matchedCount = lines.filter(
    (l) => l.matchStatus !== "UNMATCHED",
  ).length;
  const totalLines = lines.length;
  const progressPercent = totalLines > 0 ? (matchedCount / totalLines) * 100 : 0;

  async function handleMatch() {
    if (!selectedLineId || !selectedTransactionId) return;

    setIsMatching(true);
    setError(null);

    const result = await manualMatch(selectedLineId, selectedTransactionId);
    if (result.success) {
      // Optimistic update: mark line as matched, remove transaction from unmatched
      setLines((prev) =>
        prev.map((l) =>
          l.id === selectedLineId
            ? {
                ...l,
                matchStatus: "MANUALLY_MATCHED" as const,
                trustTransactionId: selectedTransactionId,
              }
            : l,
        ),
      );
      setTransactions((prev) =>
        prev.filter((t) => t.id !== selectedTransactionId),
      );
      setSelectedLineId(null);
      setSelectedTransactionId(null);
      router.refresh();
    } else {
      setError(result.error ?? "Failed to match");
    }
    setIsMatching(false);
  }

  async function handleUnmatch(lineId: string) {
    setError(null);
    const matchedLine = lines.find((l) => l.id === lineId);
    const result = await unmatch(lineId);
    if (result.success) {
      setLines((prev) =>
        prev.map((l) =>
          l.id === lineId
            ? { ...l, matchStatus: "UNMATCHED" as const, trustTransactionId: null }
            : l,
        ),
      );
      // If we had the transaction data, add it back. For simplicity, refresh.
      if (matchedLine?.trustTransactionId) {
        router.refresh();
      }
    } else {
      setError(result.error ?? "Failed to unmatch");
    }
  }

  async function handleExclude(lineId: string) {
    if (!excludeReason.trim()) return;
    setError(null);

    const result = await excludeLine(lineId, excludeReason.trim());
    if (result.success) {
      setLines((prev) =>
        prev.map((l) =>
          l.id === lineId
            ? {
                ...l,
                matchStatus: "EXCLUDED" as const,
                excludedReason: excludeReason.trim(),
              }
            : l,
        ),
      );
      setExcludeLineId(null);
      setExcludeReason("");
      router.refresh();
    } else {
      setError(result.error ?? "Failed to exclude line");
    }
  }

  async function handleComplete() {
    setIsCompleting(true);
    setError(null);

    const result = await completeReconciliation(reconciliationId);
    if (result.success) {
      setReconciliation((prev) => ({ ...prev, status: "COMPLETED" as const }));
      onComplete();
    } else {
      setError(result.error ?? "Failed to complete reconciliation");
    }
    setIsCompleting(false);
  }

  function handleLineClick(line: BankStatementLine) {
    if (line.matchStatus !== "UNMATCHED") return;
    setSelectedLineId(line.id === selectedLineId ? null : line.id);
    setSelectedTransactionId(null);
  }

  function handleTransactionClick(txn: TrustTransaction) {
    if (!selectedLineId) return;
    setSelectedTransactionId(txn.id === selectedTransactionId ? null : txn.id);
  }

  function isCandidateMatch(txn: TrustTransaction): boolean {
    if (!selectedLine) return false;
    // Use epsilon comparison to avoid IEEE 754 floating-point edge cases
    return Math.abs(txn.amount - Math.abs(selectedLine.amount)) < 0.005;
  }

  return (
    <div className="space-y-4" data-testid="split-pane">
      {/* Progress Bar */}
      <div data-testid="progress-bar">
        <div className="mb-1 flex items-center justify-between text-sm">
          <span className="text-slate-600 dark:text-slate-400">
            Matching Progress
          </span>
          <span className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
            {matchedCount}/{totalLines} ({Math.round(progressPercent)}%)
          </span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            className="h-full rounded-full bg-teal-600 transition-all"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {/* Match Button */}
      <div className="flex items-center gap-2">
        <Button
          onClick={handleMatch}
          disabled={!selectedLineId || !selectedTransactionId || isMatching}
          data-testid="match-btn"
        >
          {isMatching ? (
            <>
              <Loader2 className="mr-1.5 size-4 animate-spin" />
              Matching...
            </>
          ) : (
            "Match Selected"
          )}
        </Button>
        {selectedLineId && !selectedTransactionId && (
          <span className="text-sm text-slate-500 dark:text-slate-400">
            Select a transaction on the right to match
          </span>
        )}
      </div>

      {/* Split Pane */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {/* Left Panel — Bank Statement Lines */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Bank Statement Lines</CardTitle>
          </CardHeader>
          <CardContent>
            <div
              className="max-h-[500px] space-y-2 overflow-y-auto"
              data-testid="bank-lines-panel"
            >
              {sortedLines.map((line) => (
                <div
                  key={line.id}
                  data-testid={`bank-line-${line.id}`}
                  className={`cursor-pointer rounded-md p-3 transition-colors ${getLineStatusClasses(line.matchStatus)} ${
                    selectedLineId === line.id
                      ? "ring-2 ring-teal-500"
                      : ""
                  }`}
                  onClick={() => handleLineClick(line)}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      #{line.lineNumber} &middot;{" "}
                      {formatLocalDate(line.transactionDate)}
                    </span>
                    <span
                      className={`font-mono tabular-nums text-sm font-medium ${
                        line.amount >= 0
                          ? "text-green-700 dark:text-green-400"
                          : "text-red-700 dark:text-red-400"
                      }`}
                    >
                      {formatCurrency(line.amount, currency)}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-slate-950 dark:text-slate-50">
                    {line.description}
                  </p>
                  {line.reference && (
                    <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                      Ref: {line.reference}
                    </p>
                  )}
                  {line.matchStatus !== "UNMATCHED" &&
                    line.matchStatus !== "EXCLUDED" && (
                      <div className="mt-2 flex items-center justify-between">
                        <Badge
                          variant={
                            line.matchStatus === "AUTO_MATCHED"
                              ? "success"
                              : "neutral"
                          }
                        >
                          {line.matchStatus === "AUTO_MATCHED"
                            ? "Auto-matched"
                            : "Manual match"}
                        </Badge>
                        <Button
                          variant="plain"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            handleUnmatch(line.id);
                          }}
                        >
                          Unmatch
                        </Button>
                      </div>
                    )}
                  {line.matchStatus === "EXCLUDED" && (
                    <div className="mt-1">
                      <Badge variant="neutral">
                        Excluded{line.excludedReason ? `: ${line.excludedReason}` : ""}
                      </Badge>
                    </div>
                  )}
                  {line.matchStatus === "UNMATCHED" && (
                    <div className="mt-2">
                      {excludeLineId === line.id ? (
                        <div className="flex items-center gap-2">
                          <Input
                            value={excludeReason}
                            onChange={(e) => setExcludeReason(e.target.value)}
                            placeholder="Reason for exclusion"
                            className="h-7 text-xs"
                            onClick={(e) => e.stopPropagation()}
                          />
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleExclude(line.id);
                            }}
                          >
                            Confirm
                          </Button>
                          <Button
                            variant="plain"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              setExcludeLineId(null);
                              setExcludeReason("");
                            }}
                          >
                            Cancel
                          </Button>
                        </div>
                      ) : (
                        <Button
                          variant="plain"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            setExcludeLineId(line.id);
                          }}
                        >
                          Exclude
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              ))}
              {sortedLines.length === 0 && (
                <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
                  No bank statement lines
                </p>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Right Panel — Unmatched Trust Transactions */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">
              Unmatched Trust Transactions
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div
              className="max-h-[500px] space-y-2 overflow-y-auto"
              data-testid="transactions-panel"
            >
              {transactions.map((txn) => {
                const isCandidate = isCandidateMatch(txn);
                return (
                  <div
                    key={txn.id}
                    data-testid={`txn-row-${txn.id}`}
                    className={`cursor-pointer rounded-md border p-3 transition-colors ${
                      isCandidate && selectedLineId
                        ? "border-teal-500 bg-teal-50 dark:bg-teal-950/20"
                        : "border-slate-200 dark:border-slate-700"
                    } ${
                      selectedTransactionId === txn.id
                        ? "ring-2 ring-teal-500"
                        : ""
                    }`}
                    onClick={() => handleTransactionClick(txn)}
                  >
                    <div className="flex items-center justify-between">
                      <Badge variant="neutral">{txn.transactionType}</Badge>
                      <span className="font-mono tabular-nums text-sm font-medium text-slate-950 dark:text-slate-50">
                        {formatCurrency(txn.amount, currency)}
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-slate-950 dark:text-slate-50">
                      {txn.reference}
                    </p>
                    <div className="mt-0.5 flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                      <span>
                        {formatLocalDate(txn.transactionDate)}
                      </span>
                      {txn.description && <span>{txn.description}</span>}
                    </div>
                  </div>
                );
              })}
              {transactions.length === 0 && (
                <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
                  No unmatched transactions
                </p>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Three-Way Balance Check */}
      <Card data-testid="balance-summary">
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base">
            Three-Way Balance Check
            {reconciliation.isBalanced ? (
              <CheckCircle2 className="size-5 text-green-600" />
            ) : (
              <XCircle className="size-5 text-red-600" />
            )}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-1 font-mono text-sm tabular-nums">
            <div className="flex justify-between">
              <span className="text-slate-600 dark:text-slate-400">
                Bank Balance:
              </span>
              <span className="text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.bankBalance, currency)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600 dark:text-slate-400">
                + Outstanding Deposits:
              </span>
              <span className="text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.outstandingDeposits, currency)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600 dark:text-slate-400">
                - Outstanding Payments:
              </span>
              <span className="text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.outstandingPayments, currency)}
              </span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-1 dark:border-slate-700">
              <span className="font-medium text-slate-950 dark:text-slate-50">
                = Adjusted Bank:
              </span>
              <span className="font-medium text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.adjustedBankBalance, currency)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600 dark:text-slate-400">
                Cashbook Balance:
              </span>
              <span className="text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.cashbookBalance, currency)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600 dark:text-slate-400">
                Client Ledger Total:
              </span>
              <span className="text-slate-950 dark:text-slate-50">
                {formatCurrency(reconciliation.clientLedgerTotal, currency)}
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Complete Button */}
      <div className="flex justify-end">
        <Button
          onClick={handleComplete}
          disabled={!reconciliation.isBalanced || isCompleting}
          data-testid="complete-btn"
        >
          {isCompleting ? (
            <>
              <Loader2 className="mr-1.5 size-4 animate-spin" />
              Completing...
            </>
          ) : (
            "Complete Reconciliation"
          )}
        </Button>
      </div>
    </div>
  );
}
