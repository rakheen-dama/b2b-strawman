"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";
import {
  uploadBankStatement,
  autoMatch,
  manualMatch,
  excludeLine,
  unmatch,
  createReconciliation,
  calculateReconciliation,
  completeReconciliation,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchTransactions } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { formatCurrency } from "@/lib/format";
import type {
  TrustAccount,
  BankStatementResponse,
  BankStatementLineResponse,
  TrustTransaction,
  TrustReconciliationResponse,
} from "@/lib/types";

// ── Step Labels ───────────────────────────────────────────────────

const STEP_LABELS = [
  "Select Account",
  "Upload Statement",
  "Auto-Match",
  "Review & Match",
  "Complete",
];

// ── Page ────────────────────────────────────────────────────��─────

export default function NewReconciliationPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const router = useRouter();
  const [slug, setSlug] = useState<string>("");
  const [currentStep, setCurrentStep] = useState(1);

  // Step 1: Account selection
  const [accounts, setAccounts] = useState<TrustAccount[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [loadingAccounts, setLoadingAccounts] = useState(true);

  // Step 2: Upload
  const [statement, setStatement] = useState<BankStatementResponse | null>(
    null,
  );

  // Step 3: Auto-match
  const [autoMatchResult, setAutoMatchResult] = useState<{
    autoMatched: number;
    unmatched: number;
  } | null>(null);
  const [autoMatching, setAutoMatching] = useState(false);

  // Step 4: Review
  const [lines, setLines] = useState<BankStatementLineResponse[]>([]);
  const [unmatchedTransactions, setUnmatchedTransactions] = useState<
    TrustTransaction[]
  >([]);
  const [reconciliation, setReconciliation] =
    useState<TrustReconciliationResponse | null>(null);

  // Step 5: Complete
  const [isCompleting, setIsCompleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Resolve params
  useEffect(() => {
    params.then((p) => setSlug(p.slug));
  }, [params]);

  // Load accounts
  useEffect(() => {
    async function load() {
      try {
        const accts = await fetchTrustAccounts();
        setAccounts(accts);
        const primary = accts.find((a) => a.isPrimary) ?? accts[0];
        if (primary) setSelectedAccountId(primary.id);
      } catch {
        // ignore
      } finally {
        setLoadingAccounts(false);
      }
    }
    load();
  }, []);

  // Handle upload complete
  const handleUploadComplete = useCallback(
    (stmt: BankStatementResponse) => {
      setStatement(stmt);
      setLines(stmt.lines);
    },
    [],
  );

  // Handle auto-match
  const handleAutoMatch = useCallback(async () => {
    if (!statement) return;
    setAutoMatching(true);
    setError(null);
    try {
      const result = await autoMatch(statement.id);
      if (result.success && result.data) {
        setAutoMatchResult({
          autoMatched: result.data.autoMatched,
          unmatched: result.data.unmatched,
        });
        setLines(result.data.lines);
      } else {
        setError(result.error ?? "Auto-match failed");
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Auto-match failed",
      );
    } finally {
      setAutoMatching(false);
    }
  }, [statement]);

  // Load unmatched transactions + create reconciliation for review step
  const handleStartReview = useCallback(async () => {
    if (!selectedAccountId || !statement) return;
    setError(null);

    try {
      // Fetch unmatched transactions
      const txPage = await fetchTransactions(selectedAccountId, {
        status: "APPROVED",
        page: 0,
        size: 200,
      });
      // Filter to transactions without bankStatementLineId
      const unmatched = txPage.content.filter(
        (tx) => !tx.bankStatementLineId,
      );
      setUnmatchedTransactions(unmatched);

      // Create reconciliation if not already created
      if (!reconciliation) {
        const reconResult = await createReconciliation(selectedAccountId, {
          periodEnd,
          bankStatementId: statement.id,
        });
        if (reconResult.success && reconResult.data) {
          setReconciliation(reconResult.data);
        } else {
          setError(reconResult.error ?? "Failed to create reconciliation");
          return;
        }
      }

      setCurrentStep(4);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load review data",
      );
    }
  }, [selectedAccountId, statement, periodEnd, reconciliation]);

  // Handle manual match
  const handleMatch = useCallback(
    async (lineId: string, transactionId: string) => {
      const result = await manualMatch(lineId, transactionId);
      if (result.success) {
        // Update local state
        setLines((prev) =>
          prev.map((l) =>
            l.id === lineId
              ? {
                  ...l,
                  matchStatus: "MANUALLY_MATCHED" as const,
                  trustTransactionId: transactionId,
                }
              : l,
          ),
        );
        setUnmatchedTransactions((prev) =>
          prev.filter((tx) => tx.id !== transactionId),
        );
        // Recalculate reconciliation
        if (reconciliation) {
          const calcResult = await calculateReconciliation(reconciliation.id);
          if (calcResult.success && calcResult.data) {
            setReconciliation(calcResult.data);
          }
        }
      }
    },
    [reconciliation],
  );

  // Handle exclude
  const handleExclude = useCallback(
    async (lineId: string) => {
      const result = await excludeLine(lineId, "Non-trust item");
      if (result.success) {
        setLines((prev) =>
          prev.map((l) =>
            l.id === lineId
              ? { ...l, matchStatus: "EXCLUDED" as const }
              : l,
          ),
        );
        // Recalculate reconciliation
        if (reconciliation) {
          const calcResult = await calculateReconciliation(reconciliation.id);
          if (calcResult.success && calcResult.data) {
            setReconciliation(calcResult.data);
          }
        }
      }
    },
    [reconciliation],
  );

  // Handle unmatch
  const handleUnmatch = useCallback(
    async (lineId: string) => {
      const result = await unmatch(lineId);
      if (result.success) {
        setLines((prev) =>
          prev.map((l) =>
            l.id === lineId
              ? {
                  ...l,
                  matchStatus: "UNMATCHED" as const,
                  trustTransactionId: null,
                }
              : l,
          ),
        );
        // Refresh unmatched transactions
        if (selectedAccountId) {
          try {
            const txPage = await fetchTransactions(selectedAccountId, {
              status: "APPROVED",
              page: 0,
              size: 200,
            });
            setUnmatchedTransactions(
              txPage.content.filter((tx) => !tx.bankStatementLineId),
            );
          } catch {
            // ignore
          }
        }
        // Recalculate
        if (reconciliation) {
          const calcResult = await calculateReconciliation(reconciliation.id);
          if (calcResult.success && calcResult.data) {
            setReconciliation(calcResult.data);
          }
        }
      }
    },
    [selectedAccountId, reconciliation],
  );

  // Handle complete
  const handleComplete = useCallback(async () => {
    if (!reconciliation) return;
    setIsCompleting(true);
    setError(null);
    try {
      const result = await completeReconciliation(reconciliation.id);
      if (result.success) {
        setCurrentStep(5);
        setReconciliation(result.data ?? null);
      } else {
        setError(result.error ?? "Failed to complete reconciliation");
      }
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Failed to complete reconciliation",
      );
    } finally {
      setIsCompleting(false);
    }
  }, [reconciliation]);

  return (
    <div className="space-y-8">
      {/* Back link */}
      {slug && (
        <button
          type="button"
          onClick={() =>
            router.push(`/org/${slug}/trust-accounting/reconciliation`)
          }
          className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
        >
          <ArrowLeft className="size-4" />
          Back to Reconciliation
        </button>
      )}

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          New Reconciliation
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Reconcile your trust account with a bank statement
        </p>
      </div>

      {/* Step Indicator */}
      <nav aria-label="Wizard steps">
        <ol className="flex items-center gap-2" data-testid="step-indicator">
          {STEP_LABELS.map((label, index) => {
            const stepNum = index + 1;
            const isActive = stepNum === currentStep;
            const isCompleted = stepNum < currentStep;

            return (
              <li key={label} className="flex items-center gap-2">
                <div
                  className={`flex size-8 items-center justify-center rounded-full text-sm font-medium ${
                    isActive
                      ? "bg-teal-600 text-white"
                      : isCompleted
                        ? "bg-teal-100 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                        : "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400"
                  }`}
                >
                  {stepNum}
                </div>
                <span
                  className={`hidden text-sm sm:inline ${
                    isActive
                      ? "font-medium text-slate-950 dark:text-slate-50"
                      : "text-slate-500 dark:text-slate-400"
                  }`}
                >
                  {label}
                </span>
                {index < STEP_LABELS.length - 1 && (
                  <div className="mx-2 h-px w-8 bg-slate-200 dark:bg-slate-700" />
                )}
              </li>
            );
          })}
        </ol>
      </nav>

      {/* Error banner */}
      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
          {error}
        </div>
      )}

      {/* Step 1: Select Account + Period */}
      {currentStep === 1 && (
        <Card data-testid="step-select">
          <CardHeader>
            <CardTitle>Select Trust Account & Period</CardTitle>
            <CardDescription>
              Choose the trust account and the statement period end date
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {loadingAccounts ? (
              <p className="text-sm text-slate-500">Loading accounts...</p>
            ) : (
              <>
                <div className="space-y-2">
                  <label
                    htmlFor="account-select"
                    className="text-sm font-medium text-slate-700 dark:text-slate-300"
                  >
                    Trust Account
                  </label>
                  <select
                    id="account-select"
                    value={selectedAccountId}
                    onChange={(e) => setSelectedAccountId(e.target.value)}
                    className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-950 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50"
                    data-testid="account-select"
                  >
                    {accounts.map((acct) => (
                      <option key={acct.id} value={acct.id}>
                        {acct.accountName} ({acct.bankName} -{" "}
                        {acct.accountNumber})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="space-y-2">
                  <label
                    htmlFor="period-end"
                    className="text-sm font-medium text-slate-700 dark:text-slate-300"
                  >
                    Period End Date
                  </label>
                  <Input
                    id="period-end"
                    type="date"
                    value={periodEnd}
                    onChange={(e) => setPeriodEnd(e.target.value)}
                    className="w-48"
                    data-testid="period-end-input"
                  />
                </div>

                <div className="flex justify-end pt-2">
                  <Button
                    type="button"
                    onClick={() => setCurrentStep(2)}
                    disabled={!selectedAccountId || !periodEnd}
                    data-testid="step-next-btn"
                  >
                    Next
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}

      {/* Step 2: Upload Statement */}
      {currentStep === 2 && (
        <div className="space-y-4">
          <BankStatementUpload
            accountId={selectedAccountId}
            onUploadComplete={handleUploadComplete}
            uploadAction={uploadBankStatement}
          />
          <div className="flex justify-between">
            <Button
              type="button"
              variant="outline"
              onClick={() => setCurrentStep(1)}
            >
              Back
            </Button>
            <Button
              type="button"
              onClick={() => setCurrentStep(3)}
              disabled={!statement}
              data-testid="step-next-btn"
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Step 3: Auto-Match */}
      {currentStep === 3 && (
        <Card data-testid="step-auto-match">
          <CardHeader>
            <CardTitle>Auto-Match Transactions</CardTitle>
            <CardDescription>
              Automatically match bank statement lines with trust transactions
              based on reference, amount, and date
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {!autoMatchResult ? (
              <div className="space-y-4">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  {statement
                    ? `${statement.lineCount} bank statement lines ready to match`
                    : "No statement loaded"}
                </p>
                <Button
                  type="button"
                  onClick={handleAutoMatch}
                  disabled={autoMatching || !statement}
                  data-testid="auto-match-btn"
                >
                  {autoMatching ? "Matching..." : "Run Auto-Match"}
                </Button>
              </div>
            ) : (
              <div
                className="space-y-2"
                data-testid="auto-match-result"
              >
                <p className="text-sm text-green-600 dark:text-green-400">
                  Auto-matched {autoMatchResult.autoMatched} lines
                </p>
                <p className="text-sm text-amber-600 dark:text-amber-400">
                  {autoMatchResult.unmatched} lines remaining for manual review
                </p>
              </div>
            )}

            <div className="flex justify-between pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setCurrentStep(2)}
              >
                Back
              </Button>
              <Button
                type="button"
                onClick={handleStartReview}
                disabled={!autoMatchResult}
                data-testid="step-next-btn"
              >
                Review Matches
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Step 4: Review & Match */}
      {currentStep === 4 && (
        <div className="space-y-4">
          <ReconciliationSplitPane
            lines={lines}
            unmatchedTransactions={unmatchedTransactions}
            reconciliation={reconciliation}
            currency="ZAR"
            onMatch={handleMatch}
            onExclude={handleExclude}
            onUnmatch={handleUnmatch}
            onComplete={handleComplete}
            isCompleting={isCompleting}
          />
          <div className="flex justify-start">
            <Button
              type="button"
              variant="outline"
              onClick={() => setCurrentStep(3)}
            >
              Back
            </Button>
          </div>
        </div>
      )}

      {/* Step 5: Complete */}
      {currentStep === 5 && (
        <Card data-testid="step-complete">
          <CardHeader>
            <CardTitle>Reconciliation Complete</CardTitle>
            <CardDescription>
              The reconciliation has been completed successfully
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {reconciliation && (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Bank Balance
                  </p>
                  <p className="mt-1 font-mono text-lg tabular-nums text-slate-950 dark:text-slate-50">
                    {formatCurrency(reconciliation.bankBalance, "ZAR")}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Cashbook Balance
                  </p>
                  <p className="mt-1 font-mono text-lg tabular-nums text-slate-950 dark:text-slate-50">
                    {formatCurrency(reconciliation.cashbookBalance, "ZAR")}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Client Ledger Total
                  </p>
                  <p className="mt-1 font-mono text-lg tabular-nums text-slate-950 dark:text-slate-50">
                    {formatCurrency(reconciliation.clientLedgerTotal, "ZAR")}
                  </p>
                </div>
              </div>
            )}
            <div className="flex justify-end pt-2">
              {slug && (
                <Button
                  type="button"
                  onClick={() =>
                    router.push(
                      `/org/${slug}/trust-accounting/reconciliation`,
                    )
                  }
                >
                  Done
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
