"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Lock } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { Input } from "@b2mash/ui/input";
import { BankStatementUpload } from "@/components/trust/BankStatementUpload";
import { ReconciliationSplitPane } from "@/components/trust/ReconciliationSplitPane";
import {
  uploadBankStatement,
  autoMatch,
  fetchBankStatement,
  manualMatch,
  excludeLine,
  unmatch,
  recordFromStatement,
  createReconciliation,
  calculateReconciliation,
  completeReconciliation,
} from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import {
  fetchTrustAccounts,
  fetchTrustTransactionTypes,
} from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { fetchTransactions } from "@/app/(app)/org/[slug]/trust-accounting/transactions/actions";
import { useInterestEligibleInvestments } from "@/hooks/use-interest-eligible-investments";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { todayInSA } from "@b2mash/shared/date";
import { clearedAtCutoverCodes } from "@/lib/types";
import type {
  TrustAccount,
  BankStatementResponse,
  BankStatementLineResponse,
  TrustTransaction,
  TrustTransactionStatus,
  TrustTransactionType,
  TrustReconciliationResponse,
} from "@/lib/types";
import type { RecordFromStatementFormData } from "@/lib/schemas/trust";
import { useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { freshIdempotencyKey } from "@/lib/idempotency";
import { UnsettledSubmitNotice } from "@/components/trust/unsettled-submit-notice";

// ── Constants ─────────────────────────────────────────────────────

/** Default currency — ideally read from org settings; centralised here to avoid inline magic strings. */
const DEFAULT_CURRENCY = "ZAR";

// ── Step Labels ───────────────────────────────────────────────────

/**
 * The banner's error: a plain message, or a refusal with its ProblemDetail title as the heading and
 * its sentences beneath (a refused complete, O-OBS-2).
 */
type WizardError = string | { title: string; message: string };

/**
 * Statuses a statement line can be matched to: posted rows (R9; TrustReconciliationService.manualMatch).
 * A reversed original is still posted — its effect stands and its contra cancels it — so the bank
 * line that shows it must be matchable (652H).
 */
const MATCHABLE_STATUSES = [
  "RECORDED",
  "APPROVED",
  "REVERSED",
] as const satisfies readonly TrustTransactionStatus[];

/**
 * Types with no bank movement (registry `cashbookSign` 0): internal transfer legs and allocations
 * of an unidentified deposit. The backend's sign check refuses to match them to any statement line,
 * so they are never offered (652B D-2). REVERSAL is kept: its cash effect is its original's, so it
 * can be a real bank movement (a reversal of a transfer is still offered — F-652B-1).
 */
const NO_BANK_MOVEMENT_TYPES = [
  "TRANSFER_OUT",
  "TRANSFER_IN",
  "UNIDENT_ALLOC_OUT",
  "UNIDENT_ALLOC_IN",
] as const satisfies readonly TrustTransactionType[];

/**
 * Types a row of which is only ever recorded from a statement line, linked to it at birth — the
 * mirror of the backend registry's `recordedOnlyFromStatementLine` (652H D-1). Such a row, once
 * REVERSED, was unrecorded from its line (the line unmatched); the backend refuses to match it to a
 * bank line again (R-1), so the pane does not offer it. Its unreversed rows are always linked.
 */
const STATEMENT_ONLY_TYPES = [
  "FUND_INTEREST",
  "FUND_SWEEP",
  "BANK_CHARGE",
  "CHARGE_REFUND",
  "UNIDENTIFIED_DEPOSIT",
  "FUND_INV_SHARE",
] as const satisfies readonly TrustTransactionType[];

/** Page size of the unmatched-transactions read, and the most pages it reads per status (657A). */
const UNMATCHED_PAGE_SIZE = 1000;
const UNMATCHED_MAX_PAGES = 50;

/**
 * Every transaction of one status on the account, page after page (the list is ordered by date,
 * then newest first, then id, so no row is skipped or repeated). A status with more than
 * {@link UNMATCHED_MAX_PAGES} pages fails the read rather than show a pane that silently lacks rows.
 */
async function fetchAllWithStatus(
  accountId: string,
  status: TrustTransactionStatus
): Promise<TrustTransaction[]> {
  const rows: TrustTransaction[] = [];
  for (let page = 0; page < UNMATCHED_MAX_PAGES; page++) {
    const txPage = await fetchTransactions(accountId, { status, page, size: UNMATCHED_PAGE_SIZE });
    rows.push(...txPage.content);
    if (txPage.content.length === 0 || page + 1 >= txPage.totalPages) {
      return rows;
    }
  }
  throw new Error(
    `The account has more than ${UNMATCHED_PAGE_SIZE * UNMATCHED_MAX_PAGES} transactions of one status, too many to list for matching.`
  );
}

/**
 * The account's posted transactions (RECORDED, APPROVED and REVERSED) not yet matched to a statement
 * line (the right pane), newest first, without types that move no bank money and without a reversed
 * row recorded from a statement line. The list endpoint takes one status, so each is read on its
 * own, every page of it (652B, 652H, 657A); if any read fails the whole read fails, so the pane is
 * never silently partly empty. Also without a row cleared at the cutover date (657A): the bank
 * never shows it and the backend refuses to match it (409 CLEARED_AT_CUTOVER). The registry says
 * which types are cleared; if it cannot be read the whole read fails too — offering cleared rows
 * as matchable would be wrong.
 */
async function fetchUnmatchedTransactions(accountId: string): Promise<TrustTransaction[]> {
  const [perStatus, cleared] = await Promise.all([
    Promise.all(MATCHABLE_STATUSES.map((status) => fetchAllWithStatus(accountId, status))),
    fetchTrustTransactionTypes().then(clearedAtCutoverCodes),
  ]);
  // A row inserted while the pages were read can push one already read onto the next page
  const byId = new Map<string, TrustTransaction>();
  for (const tx of perStatus.flat()) byId.set(tx.id, tx);
  return [...byId.values()]
    .filter(
      (tx) =>
        !tx.bankStatementLineId &&
        !cleared.has(tx.transactionType) &&
        !(NO_BANK_MOVEMENT_TYPES as readonly string[]).includes(tx.transactionType) &&
        !(
          tx.status === "REVERSED" &&
          (STATEMENT_ONLY_TYPES as readonly string[]).includes(tx.transactionType)
        )
    )
    .sort((a, b) => b.transactionDate.localeCompare(a.transactionDate));
}

/**
 * The banner after a saved change whose recalculation failed (652A): the change stands, the
 * summary's figures do not follow it. One wording for match, exclude, unmatch and record.
 */
function recalculationFailed(saved: string, error: string | undefined): string {
  return `${saved} but the balances could not be recalculated: ${error ?? "unknown error"}`;
}

/** A rejected calculate request (network failure) is reported like a refused one. */
function calculateRejected(err: unknown): { success: false; error?: string } {
  return { success: false, error: err instanceof Error ? err.message : undefined };
}

/** "2026-03-31" → "March 2026" (the month a list locks for). */
function monthYear(yyyyMmDd: string): string {
  const [year, month] = yyyyMmDd.split("-").map(Number);
  if (!year || !month) return "";
  return new Date(year, month - 1, 1).toLocaleDateString("en-ZA", {
    month: "long",
    year: "numeric",
  });
}

const STEP_LABELS = [
  "Select Account",
  "Upload Statement",
  "Auto-Match",
  "Review & Match",
  "Complete",
];

// ── Page ────────────────────────────────────────────────────��─────

export default function NewReconciliationPage({ params }: { params: Promise<{ slug: string }> }) {
  // 648B F-11: every trust write below carries an Idempotency-Key, one per submit intent — except
  // calculate, which posts nothing (it only recomputes and stores the figures) and is re-run after
  // every line change: each calculate takes a fresh key, so one transient 5xx (which leaves its key
  // in progress) never blocks the figures for the rest of the session.
  const idempotency = useIdempotencyKey();
  const { slug } = React.use(params);
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(1);

  // Step 1: Account selection
  const [accounts, setAccounts] = useState<TrustAccount[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<string>("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [periodEndError, setPeriodEndError] = useState<string | null>(null);
  const [loadingAccounts, setLoadingAccounts] = useState(true);

  // Step 2: Upload
  const [statement, setStatement] = useState<BankStatementResponse | null>(null);

  // Step 3: Auto-match
  const [autoMatchResult, setAutoMatchResult] = useState<{
    autoMatched: number;
    unmatched: number;
  } | null>(null);
  const [autoMatching, setAutoMatching] = useState(false);
  const [startingReview, setStartingReview] = useState(false);

  // Step 4: Review
  const [lines, setLines] = useState<BankStatementLineResponse[]>([]);
  const [unmatchedTransactions, setUnmatchedTransactions] = useState<TrustTransaction[]>([]);
  const [reconciliation, setReconciliation] = useState<TrustReconciliationResponse | null>(null);
  // Set when a saved change could not be recalculated: the summary shows figures from before it.
  const [figuresStale, setFiguresStale] = useState(false);

  // Step 5: Complete
  const [isCompleting, setIsCompleting] = useState(false);
  const [error, setError] = useState<WizardError | null>(null);

  // ── Reset helpers ──────────────────────────────────────────────
  // Clear downstream state when an earlier wizard step changes so that
  // stale data from a previous account/period/statement is never shown.
  // Dropping `reconciliation` loses nothing: create is get-or-create (652C, §16.3.12) — the next
  // Review Matches gets the same draft back, re-pointed to the statement now uploaded.

  // Bumped by every reset: an async result started before a reset belongs to the old wizard and
  // is dropped (647A; the match, exclude and unmatch handlers too since 652A D-6).
  const wizardGeneration = useRef(0);

  const resetFromStep2 = useCallback(() => {
    wizardGeneration.current += 1;
    setStatement(null);
    setLines([]);
    setAutoMatchResult(null);
    setReconciliation(null);
    setUnmatchedTransactions([]);
    setFiguresStale(false);
    setError(null);
  }, []);

  const resetFromStep3 = useCallback(() => {
    wizardGeneration.current += 1;
    setAutoMatchResult(null);
    setReconciliation(null);
    setUnmatchedTransactions([]);
    setFiguresStale(false);
    setError(null);
  }, []);

  // 648B: a section 86(4) account's interest is recorded against one of its investments (SWR,
  // keyed by account; a failed lookup shows with a retry in the review step)
  const accountSection = accounts.find((a) => a.id === selectedAccountId)?.section;
  const {
    investments,
    isLoading: loadingInvestments,
    error: investmentsError,
    retry: retryInvestments,
  } = useInterestEligibleInvestments(selectedAccountId, accountSection);

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

  // The upload, guarded like the other async handlers (652A D-6): the wizard generation is captured
  // when the upload starts, and a result that lands after a reset (a changed account or period, a
  // fresh wizard) is dropped — it never becomes this wizard's statement, so a later re-upload never
  // names a statement of another account or period as the one it replaces (FU-6b, CodeRabbit round
  // 1). The statement is stored by then; it is listed on the Reconciliation page to discard.
  const uploadForThisWizard = useCallback(
    async (accountId: string, file: File, replacesStatementId?: string) => {
      const generation = wizardGeneration.current;
      const result = await idempotency.run(
        [
          "uploadBankStatement",
          accountId,
          file.name,
          file.size,
          file.lastModified,
          replacesStatementId ?? null,
        ],
        (idempotencyKey) =>
          uploadBankStatement(accountId, file, replacesStatementId, idempotencyKey)
      );
      if (generation !== wizardGeneration.current) {
        return {
          success: false,
          error: result.success
            ? "The statement was imported, but the account or period changed while it uploaded, so this reconciliation does not use it. Upload it again, or discard it from the Reconciliation page."
            : result.error,
        };
      }
      return result;
    },
    [idempotency]
  );

  // Handle upload complete — also reset downstream state for replacement uploads
  const handleUploadComplete = useCallback(
    (stmt: BankStatementResponse) => {
      resetFromStep3();
      setStatement(stmt);
      setLines(stmt.lines);
    },
    [resetFromStep3]
  );

  // Handle auto-match
  const handleAutoMatch = useCallback(async () => {
    if (!statement) return;
    setAutoMatching(true);
    setError(null);
    try {
      const result = await idempotency.run(["autoMatch", statement.id], (idempotencyKey) =>
        autoMatch(statement.id, idempotencyKey)
      );
      if (result.success && result.data) {
        // The auto-match response is counts only; re-read the statement for the
        // lines with their post-match statuses (the review step renders them).
        const updated = await fetchBankStatement(statement.id);
        setLines(updated.lines);
        setAutoMatchResult({
          autoMatched: result.data.autoMatched,
          unmatched: result.data.unmatched,
        });
      } else {
        setError(result.error ?? "Auto-match failed");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Auto-match failed");
    } finally {
      setAutoMatching(false);
    }
  }, [statement, idempotency]);

  // Load unmatched transactions + create reconciliation for review step
  const handleStartReview = useCallback(async () => {
    if (!selectedAccountId || !statement) return;
    setError(null);
    setStartingReview(true);

    try {
      setUnmatchedTransactions(await fetchUnmatchedTransactions(selectedAccountId));

      // Create reconciliation if not already created
      let recon = reconciliation;
      if (!recon) {
        const create = { periodEnd, bankStatementId: statement.id };
        const reconResult = await idempotency.run(
          ["createReconciliation", selectedAccountId, create],
          (idempotencyKey) => createReconciliation(selectedAccountId, create, idempotencyKey)
        );
        if (reconResult.success && reconResult.data) {
          recon = reconResult.data;
          setReconciliation(recon);
        } else {
          const message = reconResult.error ?? "Failed to create reconciliation";
          setError(reconResult.errorTitle ? { title: reconResult.errorTitle, message } : message);
          return;
        }
      }

      // A new reconciliation starts with every balance at zero; calculate it so the
      // review step opens on real balances (and Complete is enabled if auto-match
      // already resolved every line). On failure stay here; a retry recalculates.
      // Every calculate and complete names the statement this wizard reviewed (652C): a draft
      // re-pointed since by a continuation in another tab is refused (409), never calculated.
      const calcResult = await calculateReconciliation(
        recon.id,
        statement.id,
        freshIdempotencyKey()
      );
      if (calcResult.success && calcResult.data) {
        setReconciliation(calcResult.data);
        setFiguresStale(false);
      } else {
        const message = calcResult.error ?? "Failed to calculate reconciliation";
        setError(calcResult.errorTitle ? { title: calcResult.errorTitle, message } : message);
        return;
      }

      setCurrentStep(4);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load review data");
    } finally {
      setStartingReview(false);
    }
  }, [selectedAccountId, statement, periodEnd, reconciliation, idempotency]);

  // Handle manual match
  const handleMatch = useCallback(
    async (lineId: string, transactionId: string) => {
      setError(null);
      const generation = wizardGeneration.current;
      try {
        const result = await idempotency.run(
          ["manualMatch", lineId, transactionId],
          (idempotencyKey) => manualMatch(lineId, transactionId, idempotencyKey)
        );
        if (generation !== wizardGeneration.current) return;
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
                : l
            )
          );
          setUnmatchedTransactions((prev) => prev.filter((tx) => tx.id !== transactionId));
          // Recalculate reconciliation
          if (reconciliation && statement) {
            const calcResult = await calculateReconciliation(
              reconciliation.id,
              statement.id,
              freshIdempotencyKey()
            ).catch(calculateRejected);
            if (generation !== wizardGeneration.current) return;
            if (calcResult.success && calcResult.data) {
              setReconciliation(calcResult.data);
              setFiguresStale(false);
            } else {
              setFiguresStale(true);
              setError(recalculationFailed("The match was saved", calcResult.error));
            }
          }
        } else {
          setError(result.error ?? "Failed to match line");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to match line");
      }
    },
    [reconciliation, statement, idempotency]
  );

  // Handle exclude
  const handleExclude = useCallback(
    async (lineId: string) => {
      setError(null);
      const generation = wizardGeneration.current;
      try {
        const result = await idempotency.run(
          ["excludeLine", lineId, "Non-trust item"],
          (idempotencyKey) => excludeLine(lineId, "Non-trust item", idempotencyKey)
        );
        if (generation !== wizardGeneration.current) return;
        if (result.success) {
          setLines((prev) =>
            prev.map((l) => (l.id === lineId ? { ...l, matchStatus: "EXCLUDED" as const } : l))
          );
          // Recalculate reconciliation
          if (reconciliation && statement) {
            const calcResult = await calculateReconciliation(
              reconciliation.id,
              statement.id,
              freshIdempotencyKey()
            ).catch(calculateRejected);
            if (generation !== wizardGeneration.current) return;
            if (calcResult.success && calcResult.data) {
              setReconciliation(calcResult.data);
              setFiguresStale(false);
            } else {
              setFiguresStale(true);
              setError(recalculationFailed("The line was excluded", calcResult.error));
            }
          }
        } else {
          setError(result.error ?? "Failed to exclude line");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to exclude line");
      }
    },
    [reconciliation, statement, idempotency]
  );

  // Handle unmatch
  const handleUnmatch = useCallback(
    async (lineId: string) => {
      setError(null);
      const generation = wizardGeneration.current;
      try {
        const result = await idempotency.run(["unmatch", lineId], (idempotencyKey) =>
          unmatch(lineId, idempotencyKey)
        );
        if (generation !== wizardGeneration.current) return;
        if (result.success) {
          setLines((prev) =>
            prev.map((l) =>
              l.id === lineId
                ? {
                    ...l,
                    matchStatus: "UNMATCHED" as const,
                    trustTransactionId: null,
                    classification: null,
                    classificationLabel: null,
                    createdTransactionIds: [],
                  }
                : l
            )
          );
          // The unmatch is saved: re-read the unmatched transactions and recalculate, each on its
          // own, so a failed re-read never skips the recalculation (652A D-6, CF-5).
          const [refreshed, calcResult] = await Promise.all([
            selectedAccountId
              ? fetchUnmatchedTransactions(selectedAccountId).catch(() => null)
              : undefined,
            reconciliation && statement
              ? calculateReconciliation(
                  reconciliation.id,
                  statement.id,
                  freshIdempotencyKey()
                ).catch(calculateRejected)
              : null,
          ]);
          if (generation !== wizardGeneration.current) return;
          if (refreshed) setUnmatchedTransactions(refreshed);
          const listNote =
            refreshed === null
              ? " The unmatched transactions could not be refreshed and may be out of date."
              : "";
          if (calcResult && !(calcResult.success && calcResult.data)) {
            setFiguresStale(true);
            setError(recalculationFailed("The line was unmatched", calcResult.error) + listNote);
          } else {
            if (calcResult?.data) {
              setReconciliation(calcResult.data);
              setFiguresStale(false);
            }
            if (listNote) setError(`The line was unmatched.${listNote}`);
          }
        } else {
          setError(result.error ?? "Failed to unmatch line");
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to unmatch line");
      }
    },
    [selectedAccountId, reconciliation, statement, idempotency]
  );

  // Handle record from statement (647A). A refusal is returned to the dialog (shown there, the
  // dialog stays open); a failed recalculation after a successful record goes to the banner.
  const handleRecord = useCallback(
    async (
      lineId: string,
      input: RecordFromStatementFormData
    ): Promise<{ success: boolean; error?: string }> => {
      setError(null);
      const generation = wizardGeneration.current;
      const result = await idempotency.run(
        ["recordFromStatement", lineId, input],
        (idempotencyKey) => recordFromStatement(lineId, input, idempotencyKey)
      );
      if (!result.success || !result.data) {
        return { success: false, error: result.error ?? "Failed to record the line" };
      }
      const updated = result.data.line;
      setLines((prev) => prev.map((l) => (l.id === lineId ? updated : l)));
      if (reconciliation && statement) {
        const calcResult = await calculateReconciliation(
          reconciliation.id,
          statement.id,
          freshIdempotencyKey()
        ).catch(calculateRejected);
        if (generation !== wizardGeneration.current) {
          // The wizard was reset while recalculating: this result is the old reconciliation's.
          return { success: true };
        }
        if (calcResult.success && calcResult.data) {
          setReconciliation(calcResult.data);
          setFiguresStale(false);
        } else {
          setFiguresStale(true);
          setError(recalculationFailed("The line was recorded", calcResult.error));
        }
      }
      return { success: true };
    },
    [reconciliation, statement, idempotency]
  );

  // Handle complete. The backend gate (G1–G4, 649C) recalculates and refuses once with every
  // failed condition; a refusal rolls back, so the stored figures are not refreshed — recalculate
  // to show the figures the gate saw, then show the refusal's title and sentences (ruling D-9).
  // A refusal can follow another user's change (a line unmatched elsewhere, F-OBS-1), so the
  // statement lines and the unmatched transactions are re-read too: the pane's progress count,
  // badges and line actions then follow the database. A failed re-read keeps the refusal on screen
  // and says the lines may be out of date.
  const handleComplete = useCallback(async () => {
    if (!reconciliation || !statement || !selectedAccountId) return;
    const generation = wizardGeneration.current;
    setIsCompleting(true);
    setError(null);
    try {
      // A 409 (the draft now uses another statement — 652C) is a refusal like the gate's: the banner
      // shows its title and detail, and the wizard stays on this step.
      const result = await idempotency.run(
        ["completeReconciliation", reconciliation.id, statement.id],
        (idempotencyKey) => completeReconciliation(reconciliation.id, statement.id, idempotencyKey)
      );
      if (generation !== wizardGeneration.current) return;
      if (result.success) {
        setCurrentStep(5);
        setReconciliation(result.data ?? null);
        return;
      }
      const [calcResult, refreshedStatement, refreshedTransactions] = await Promise.all([
        calculateReconciliation(reconciliation.id, statement.id, freshIdempotencyKey()),
        fetchBankStatement(statement.id).catch(() => null),
        fetchUnmatchedTransactions(selectedAccountId).catch(() => null),
      ]);
      if (generation !== wizardGeneration.current) return;
      if (calcResult.success && calcResult.data) {
        setReconciliation(calcResult.data);
        setFiguresStale(false);
      }
      if (refreshedStatement) {
        setLines(refreshedStatement.lines);
      }
      if (refreshedTransactions) {
        setUnmatchedTransactions(refreshedTransactions);
      }
      const refusal = result.error ?? "Failed to complete reconciliation";
      const message =
        refreshedStatement && refreshedTransactions
          ? refusal
          : `${refusal} The statement lines could not be refreshed and may be out of date.`;
      setError(result.errorTitle ? { title: result.errorTitle, message } : message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete reconciliation");
    } finally {
      setIsCompleting(false);
    }
  }, [reconciliation, statement, selectedAccountId, idempotency]);

  return (
    <div className="space-y-8">
      {/* Back link */}
      <button
        type="button"
        onClick={() => router.push(`/org/${slug}/trust-accounting/reconciliation`)}
        className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
      >
        <ArrowLeft className="size-4" />
        Back to Reconciliation
      </button>

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

      {/* §1.5: an earlier submit's outcome is unknown — check the ledger before submitting again */}
      <UnsettledSubmitNotice
        show={idempotency.unsettled}
        onSubmitAsNew={idempotency.discardUnsettled}
      />

      {/* Error banner */}
      {error && (
        <div
          data-testid="reconciliation-error"
          className="rounded-md bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
        >
          {typeof error === "string" ? (
            error
          ) : (
            <>
              <p className="font-medium" data-testid="reconciliation-error-title">
                {error.title}
              </p>
              <p className="mt-1">{error.message}</p>
            </>
          )}
        </div>
      )}

      {/* Step 1: Select Account + Period */}
      {currentStep === 1 && (
        <Card data-testid="step-select">
          <CardHeader>
            <CardTitle>Select Trust Account & Period</CardTitle>
            <CardDescription>
              Choose the trust account and the period end. The period end is the firm&apos;s choice
              and every figure is calculated as at that date; the bank statement must have no line
              after the period end.
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
                    onChange={(e) => {
                      setSelectedAccountId(e.target.value);
                      resetFromStep2();
                    }}
                    className="dark:bg-input/30 h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-950 dark:border-slate-700 dark:text-slate-50"
                    data-testid="account-select"
                  >
                    {accounts.map((acct) => (
                      <option key={acct.id} value={acct.id}>
                        {acct.accountName} ({acct.bankName} - {acct.accountNumber})
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
                    onChange={(e) => {
                      setPeriodEnd(e.target.value);
                      setPeriodEndError(null);
                      resetFromStep2();
                    }}
                    max={todayInSA()}
                    aria-invalid={periodEndError ? true : undefined}
                    aria-describedby={periodEndError ? "period-end-error" : undefined}
                    className="w-48"
                    data-testid="period-end-input"
                  />
                  {periodEndError && (
                    <p
                      id="period-end-error"
                      role="alert"
                      className="text-sm text-red-700 dark:text-red-300"
                      data-testid="period-end-error"
                    >
                      {periodEndError}
                    </p>
                  )}
                </div>

                <div className="flex justify-end pt-2">
                  <Button
                    type="button"
                    onClick={() => {
                      // `max` does not stop a typed date; the backend refuses a future period end.
                      if (periodEnd > todayInSA()) {
                        setPeriodEndError("The period end cannot be in the future");
                        return;
                      }
                      setCurrentStep(2);
                    }}
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
          {/* FU-6b: a re-upload replaces the statement this wizard already uploaded (its matches
              are released and a draft on it moves to the new statement) — never a second copy. */}
          <BankStatementUpload
            accountId={selectedAccountId}
            onUploadComplete={handleUploadComplete}
            replacesStatementId={statement?.id}
            uploadAction={uploadForThisWizard}
          />
          <div className="flex justify-between">
            <Button type="button" variant="outline" onClick={() => setCurrentStep(1)}>
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
              Automatically match bank statement lines with trust transactions based on reference,
              amount, and date
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
              <div className="space-y-2" data-testid="auto-match-result">
                <p className="text-sm text-green-600 dark:text-green-400">
                  Auto-matched {autoMatchResult.autoMatched} lines
                </p>
                <p className="text-sm text-amber-600 dark:text-amber-400">
                  {autoMatchResult.unmatched} lines remaining for manual review
                </p>
              </div>
            )}

            <div className="flex justify-between pt-2">
              {/* Blocked while auto-match or review preparation runs: a late completion must not land on a changed account/period. */}
              <Button
                type="button"
                variant="outline"
                onClick={() => setCurrentStep(2)}
                disabled={autoMatching || startingReview}
                data-testid="auto-match-back-btn"
              >
                Back
              </Button>
              <Button
                type="button"
                onClick={handleStartReview}
                disabled={!autoMatchResult || startingReview}
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
          {accountSection === "S86_4" && loadingInvestments && (
            <p className="text-muted-foreground text-sm" data-testid="interest-investments-loading">
              Loading the account&apos;s investments…
            </p>
          )}
          {accountSection === "S86_4" && investmentsError && (
            <div
              role="alert"
              className="flex items-center justify-between gap-3 text-sm"
              data-testid="interest-investments-error"
            >
              <span className="text-destructive">{investmentsError}</span>
              <Button type="button" variant="outline" size="sm" onClick={retryInvestments}>
                Retry
              </Button>
            </div>
          )}
          <ReconciliationSplitPane
            lines={lines}
            unmatchedTransactions={unmatchedTransactions}
            reconciliation={reconciliation}
            currency={DEFAULT_CURRENCY}
            onMatch={handleMatch}
            onExclude={handleExclude}
            onUnmatch={handleUnmatch}
            onRecord={handleRecord}
            onComplete={handleComplete}
            isCompleting={isCompleting}
            figuresStale={figuresStale}
            accountSection={accountSection}
            investments={investments}
          />
          <div className="flex justify-start">
            <Button type="button" variant="outline" onClick={() => setCurrentStep(3)}>
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
            <CardDescription>The reconciliation has been completed successfully</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {reconciliation && (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Bank Balance
                  </p>
                  <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                    {formatCurrency(reconciliation.bankBalance, DEFAULT_CURRENCY)}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Cashbook Balance
                  </p>
                  <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                    {formatCurrency(reconciliation.cashbookBalance, DEFAULT_CURRENCY)}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400">
                    Trust creditors
                  </p>
                  <p className="mt-1 font-mono text-lg text-slate-950 tabular-nums dark:text-slate-50">
                    {formatCurrency(reconciliation.creditorBreakdown.total, DEFAULT_CURRENCY)}
                  </p>
                </div>
              </div>
            )}
            {reconciliation &&
              (reconciliation.monthlyListId ? (
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-900">
                  <p
                    className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300"
                    data-testid="monthly-list-locked"
                  >
                    <Lock className="size-4 shrink-0 text-teal-600 dark:text-teal-400" />
                    {monthYear(reconciliation.periodEnd)} list locked —{" "}
                    {formatCurrency(reconciliation.creditorBreakdown.total, DEFAULT_CURRENCY)}{" "}
                    agrees with the cashbook and the adjusted bank balance.
                  </p>
                  <Button asChild variant="outline" size="sm">
                    <Link
                      href={`/org/${slug}/trust-accounting/reconciliation/lists/${reconciliation.monthlyListId}`}
                      data-testid="view-locked-list"
                    >
                      View the locked list
                    </Link>
                  </Button>
                </div>
              ) : (
                <p
                  className="text-sm text-slate-600 dark:text-slate-400"
                  data-testid="monthly-list-not-locked"
                >
                  No monthly list was locked: {formatLocalDate(reconciliation.periodEnd)} is not the
                  last day of a month.
                </p>
              ))}
            <div className="flex justify-end pt-2">
              <Button
                type="button"
                onClick={() => router.push(`/org/${slug}/trust-accounting/reconciliation`)}
              >
                Done
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
