"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { CheckCircle2, Loader2 } from "lucide-react";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";
import {
  autoMatch,
  fetchBankStatement,
  createReconciliation,
  calculateReconciliation,
  fetchUnmatchedTransactions,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import type {
  TrustAccount,
  BankStatement,
  BankStatementLine,
  TrustTransaction,
  TrustReconciliation,
  MatchResult,
} from "@/lib/types";

interface ReconciliationWizardProps {
  accounts: TrustAccount[];
  currency: string;
  slug: string;
}

const STEPS = [
  "Select Account",
  "Upload Statement",
  "Auto-Match",
  "Review",
  "Complete",
];

export function ReconciliationWizard({
  accounts,
  currency,
  slug,
}: ReconciliationWizardProps) {
  const router = useRouter();
  const [step, setStep] = useState(0);

  // Step 1 state
  const [selectedAccountId, setSelectedAccountId] = useState(
    accounts.find((a) => a.isPrimary)?.id ?? accounts[0]?.id ?? "",
  );
  const [periodEnd, setPeriodEnd] = useState("");

  // Step 2 state
  const [statement, setStatement] = useState<BankStatement | null>(null);

  // Step 3 state
  const [matchResult, setMatchResult] = useState<MatchResult | null>(null);
  const [isAutoMatching, setIsAutoMatching] = useState(false);

  // Step 4 state
  const [reconciliation, setReconciliation] =
    useState<TrustReconciliation | null>(null);
  const [bankLines, setBankLines] = useState<BankStatementLine[]>([]);
  const [unmatchedTxns, setUnmatchedTxns] = useState<TrustTransaction[]>([]);
  const [isCreating, setIsCreating] = useState(false);

  // Step 5 state
  const [isComplete, setIsComplete] = useState(false);

  const [error, setError] = useState<string | null>(null);

  function handleNext() {
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  }

  async function handleAutoMatch() {
    if (!statement) return;
    setIsAutoMatching(true);
    setError(null);

    try {
      const result = await autoMatch(statement.id);
      setMatchResult(result);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Auto-match failed",
      );
    } finally {
      setIsAutoMatching(false);
    }
  }

  async function handleCreateAndReview() {
    if (!statement || !selectedAccountId || !periodEnd) return;
    setIsCreating(true);
    setError(null);

    try {
      // Create reconciliation
      const rec = await createReconciliation(
        selectedAccountId,
        periodEnd,
        statement.id,
      );

      // Calculate balances
      const calculated = await calculateReconciliation(rec.id);
      setReconciliation(calculated);

      // Fetch updated bank statement with lines
      const updatedStatement = await fetchBankStatement(statement.id);
      setBankLines(updatedStatement.lines ?? []);

      // Fetch unmatched transactions
      const txns = await fetchUnmatchedTransactions(selectedAccountId);
      setUnmatchedTxns(txns);

      handleNext();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to create reconciliation",
      );
    } finally {
      setIsCreating(false);
    }
  }

  function handleComplete() {
    setIsComplete(true);
    router.push(`/org/${slug}/trust-accounting/reconciliation`);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Reconciliation
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Reconcile your trust account bank statement
        </p>
      </div>

      {/* Step Indicator */}
      <div className="flex items-center gap-2" data-testid="step-indicator">
        {STEPS.map((name, i) => (
          <div key={name} className="flex items-center gap-2">
            <div
              className={`flex size-7 items-center justify-center rounded-full text-xs font-medium ${
                i < step
                  ? "bg-teal-600 text-white"
                  : i === step
                    ? "bg-teal-100 text-teal-700 dark:bg-teal-900 dark:text-teal-200"
                    : "bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"
              }`}
            >
              {i < step ? <CheckCircle2 className="size-4" /> : i + 1}
            </div>
            <span
              className={`text-sm ${
                i === step
                  ? "font-medium text-slate-950 dark:text-slate-50"
                  : "text-slate-400 dark:text-slate-500"
              }`}
            >
              {name}
            </span>
            {i < STEPS.length - 1 && (
              <div className="mx-1 h-px w-6 bg-slate-200 dark:bg-slate-700" />
            )}
          </div>
        ))}
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {/* Step 1 — Select Account + Period */}
      {step === 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Select Account &amp; Period</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Trust Account
              </label>
              <select
                value={selectedAccountId}
                onChange={(e) => setSelectedAccountId(e.target.value)}
                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-900"
              >
                {accounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {acc.accountName} — {acc.bankName} ({acc.accountNumber})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                Period End Date
              </label>
              <Input
                type="date"
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
              />
            </div>
            <div className="flex justify-end">
              <Button
                onClick={handleNext}
                disabled={!selectedAccountId || !periodEnd}
                data-testid="next-btn"
              >
                Next
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 2 — Upload Statement */}
      {step === 1 && (
        <Card>
          <CardHeader>
            <CardTitle>Upload Bank Statement</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <BankStatementUpload
              accountId={selectedAccountId}
              onUploadComplete={(stmt) => {
                setStatement(stmt);
                handleNext();
              }}
            />
          </CardContent>
        </Card>
      )}

      {/* Step 3 — Auto-Match */}
      {step === 2 && statement && (
        <Card>
          <CardHeader>
            <CardTitle>Auto-Match</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-sm text-slate-600 dark:text-slate-400">
              <p>
                Statement: <strong>{statement.fileName}</strong> &mdash;{" "}
                {statement.lineCount} lines
              </p>
              <p>
                Period: {statement.periodStart} to {statement.periodEnd}
              </p>
            </div>

            {!matchResult ? (
              <Button
                onClick={handleAutoMatch}
                disabled={isAutoMatching}
                data-testid="next-btn"
              >
                {isAutoMatching ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Running Auto-Match...
                  </>
                ) : (
                  "Run Auto-Match"
                )}
              </Button>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <Badge variant="success">
                    {matchResult.autoMatchedCount} auto-matched
                  </Badge>
                  <Badge variant="warning">
                    {matchResult.unmatchedCount} remaining
                  </Badge>
                  {matchResult.excludedCount > 0 && (
                    <Badge variant="neutral">
                      {matchResult.excludedCount} excluded
                    </Badge>
                  )}
                </div>
                <div className="flex justify-end">
                  <Button
                    onClick={handleCreateAndReview}
                    disabled={isCreating}
                    data-testid="next-btn"
                  >
                    {isCreating ? (
                      <>
                        <Loader2 className="mr-1.5 size-4 animate-spin" />
                        Creating...
                      </>
                    ) : (
                      "Next — Review Matches"
                    )}
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Step 4 — Review (Split-Pane) */}
      {step === 3 && reconciliation && (
        <ReconciliationSplitPane
          reconciliationId={reconciliation.id}
          accountId={selectedAccountId}
          bankStatementLines={bankLines}
          unmatchedTransactions={unmatchedTxns}
          reconciliation={reconciliation}
          currency={currency}
          onComplete={handleComplete}
        />
      )}

      {/* Step 5 — Complete */}
      {step === 4 && isComplete && (
        <Card>
          <CardContent className="py-10 text-center">
            <CheckCircle2 className="mx-auto mb-3 size-10 text-green-600" />
            <h2 className="font-display text-xl text-slate-950 dark:text-slate-50">
              Reconciliation Complete
            </h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              The reconciliation has been completed successfully.
            </p>
            <div className="mt-4">
              <Button
                variant="outline"
                onClick={() =>
                  router.push(
                    `/org/${slug}/trust-accounting/reconciliation`,
                  )
                }
              >
                Back to Reconciliations
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
