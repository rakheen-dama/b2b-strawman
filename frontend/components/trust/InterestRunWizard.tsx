"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@b2mash/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Badge } from "@b2mash/ui/badge";
import { createInterestRunSchema, type CreateInterestRunFormData } from "@/lib/schemas/trust";
import {
  createInterestRun,
  calculateInterest,
  approveInterestRun,
  postInterestRun,
  fetchInterestRunDetail,
} from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import { formatCurrency } from "@/lib/format";
import {
  INTEREST_RUN_STATUS_LABELS,
  type InterestRun,
  type InterestAllocation,
  type InterestRunStatus,
} from "@/lib/types/trust";

interface InterestRunWizardProps {
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
  canApprove: boolean;
  currency: string;
  /** 630B: resume a persisted run — loads its detail on open and jumps to the step its state implies. */
  initialRun?: InterestRun;
  /** The viewer's members.id (MyCapabilitiesResponse.memberId); hides self-approval — the backend still refuses it. */
  memberId?: string | null;
  /** MANAGE_TRUST — gates Post (step 4). Default true: the "New Interest Run" mount is MANAGE_TRUST-gated. */
  canManage?: boolean;
}

const STATUS_BADGE_VARIANT: Record<InterestRunStatus, "neutral" | "warning" | "success"> = {
  DRAFT: "neutral",
  APPROVED: "warning",
  POSTED: "success",
};

/** Step a persisted run resumes at (§14.3.2): never-calculated DRAFT → 2, calculated DRAFT → 3, APPROVED → 4. */
function resumeStep(run: InterestRun, allocationCount: number): number {
  if (run.status === "APPROVED") return 4;
  return allocationCount > 0 ? 3 : 2;
}

export function InterestRunWizard({
  accountId,
  open,
  onOpenChange,
  onSuccess,
  canApprove,
  currency,
  initialRun,
  memberId,
  canManage = true,
}: InterestRunWizardProps) {
  const router = useRouter();
  const [step, setStep] = useState(1);
  const [runId, setRunId] = useState<string | null>(null);
  const [run, setRun] = useState<InterestRun | null>(null);
  const [allocations, setAllocations] = useState<InterestAllocation[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CreateInterestRunFormData>({
    resolver: zodResolver(createInterestRunSchema),
    defaultValues: {
      periodStart: "",
      periodEnd: "",
    },
  });

  // Resume a persisted run: setState only in the promise callbacks; a close resets local state
  // (handleOpenChange), so the next open refetches. A router.refresh() while open hands a new
  // initialRun object with the same id — no refetch, no step reset.
  const initialRunId = initialRun?.id;
  useEffect(() => {
    if (!open || !initialRunId) return;
    let cancelled = false;
    fetchInterestRunDetail(initialRunId)
      .then((detail) => {
        if (cancelled) return;
        setRunId(detail.run.id);
        setRun(detail.run);
        setAllocations(detail.allocations);
        setStep(resumeStep(detail.run, detail.allocations.length));
      })
      .catch(() => {
        if (!cancelled) setError("Unable to load the interest run. Please try again.");
      });
    return () => {
      cancelled = true;
    };
  }, [open, initialRunId]);

  const isResuming = !!initialRunId && runId !== initialRunId;
  // The two-member rule (InterestService.approveInterestRun): the creator never approves.
  const isCreator = !!memberId && !!run?.createdBy && run.createdBy === memberId;

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      setStep(1);
      setRunId(null);
      setRun(null);
      setAllocations([]);
      setError(null);
      setIsLoading(false);
      form.reset({ periodStart: "", periodEnd: "" });
    }
  }

  async function handleCreate(data: CreateInterestRunFormData) {
    setError(null);
    setIsLoading(true);
    try {
      const result = await createInterestRun(accountId, data);
      if (result.success && result.run) {
        setRunId(result.run.id);
        setRun(result.run);
        setStep(2);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to create interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleCalculate() {
    if (!runId) return;
    setError(null);
    setIsLoading(true);
    try {
      const calcResult = await calculateInterest(runId);
      if (calcResult.success && calcResult.run) {
        setRun(calcResult.run);
        const detail = await fetchInterestRunDetail(runId);
        setAllocations(detail.allocations);
        router.refresh();
      } else {
        setError(calcResult.error ?? "Failed to calculate interest");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleApprove() {
    if (!runId) return;
    setError(null);
    setIsLoading(true);
    try {
      const result = await approveInterestRun(runId);
      if (result.success && result.run) {
        setRun(result.run);
        setStep(4);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to approve interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsLoading(false);
    }
  }

  async function handlePost() {
    if (!runId) return;
    setError(null);
    setIsLoading(true);
    try {
      const result = await postInterestRun(runId);
      if (result.success && result.run) {
        setRun(result.run);
        onSuccess();
        router.refresh();
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to post interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsLoading(false);
    }
  }

  const stepLabels = ["Create", "Calculate", "Approve", "Post"];

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-2xl" data-testid="interest-run-wizard">
        <DialogHeader>
          <DialogTitle>{initialRun ? "Interest Run" : "New Interest Run"}</DialogTitle>
          <DialogDescription>
            {isResuming ? "Loading…" : `Step ${step} of 4 — ${stepLabels[step - 1]}`}
          </DialogDescription>
        </DialogHeader>

        {/* Resuming a persisted run: never flash the step-1 create form */}
        {isResuming && (
          <div className="space-y-4">
            {error ? (
              <p className="text-destructive text-sm">{error}</p>
            ) : (
              <p className="flex items-center text-sm text-slate-500 dark:text-slate-400">
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Loading interest run…
              </p>
            )}
            <DialogFooter>
              <Button type="button" variant="plain" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Step indicators */}
        <div className="flex items-center gap-2" data-testid="step-indicators">
          {stepLabels.map((label, i) => (
            <div key={label} className="flex items-center gap-2">
              <div
                className={`flex size-7 items-center justify-center rounded-full text-xs font-medium ${
                  i + 1 <= step
                    ? "bg-teal-600 text-white"
                    : "bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400"
                }`}
              >
                {i + 1}
              </div>
              <span
                className={`text-xs ${
                  i + 1 === step
                    ? "font-medium text-slate-950 dark:text-slate-50"
                    : "text-slate-500 dark:text-slate-400"
                }`}
              >
                {label}
              </span>
              {i < stepLabels.length - 1 && (
                <div className="mx-1 h-px w-6 bg-slate-300 dark:bg-slate-600" />
              )}
            </div>
          ))}
        </div>

        {/* Step 1 — Create */}
        {!isResuming && step === 1 && (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(handleCreate)} className="space-y-4">
              <FormField
                control={form.control}
                name="periodStart"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Period Start</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="periodEnd"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Period End</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {error && <p className="text-destructive text-sm">{error}</p>}

              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => handleOpenChange(false)}
                  disabled={isLoading}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Creating...
                    </>
                  ) : (
                    "Create Run"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        )}

        {/* Step 2 — Calculate */}
        {!isResuming && step === 2 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                      Period: {run.periodStart} to {run.periodEnd}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">Run ID: {run.id}</p>
                  </div>
                  <Badge variant={STATUS_BADGE_VARIANT[run.status]}>
                    {INTEREST_RUN_STATUS_LABELS[run.status]}
                  </Badge>
                </div>
              </div>
            )}

            {allocations.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="allocations-table">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Client ID
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Avg Daily Balance
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Gross Interest
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        LPFF Rate
                      </th>
                      <th className="pr-4 pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        LPFF Share
                      </th>
                      <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Client Share
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {allocations.map((a) => (
                      <tr
                        key={a.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                      >
                        <td className="py-3 pr-4 font-mono text-xs">{a.clientId}</td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.averageDailyBalance, currency)}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.grossInterest, currency)}
                        </td>
                        <td className="py-3 pr-4">
                          {a.statutoryRateApplied ? (
                            <span className="font-medium text-teal-600 dark:text-teal-400">
                              5% (statutory)
                            </span>
                          ) : (
                            <span className="text-slate-600 dark:text-slate-400">Arrangement</span>
                          )}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.lpffShare, currency)}
                        </td>
                        <td className="py-3 text-right font-mono tabular-nums">
                          {formatCurrency(a.clientShare, currency)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {allocations.some((a) => a.statutoryRateApplied) && (
                  <p
                    className="mt-3 text-xs text-slate-500 dark:text-slate-400"
                    data-testid="statutory-footnote"
                  >
                    Section 86(5): client-instructed investments carry a statutory 5% LPFF share.
                  </p>
                )}
              </div>
            )}

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isLoading}
              >
                Cancel
              </Button>
              {allocations.length === 0 ? (
                <Button type="button" onClick={handleCalculate} disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Calculating...
                    </>
                  ) : (
                    "Calculate Interest"
                  )}
                </Button>
              ) : canApprove && !isCreator ? (
                <Button type="button" onClick={() => setStep(3)} disabled={isLoading}>
                  Approve
                </Button>
              ) : isCreator ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setStep(3)}
                  disabled={isLoading}
                >
                  Review
                </Button>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Pending Approval — you do not have permission to approve interest runs.
                </p>
              )}
            </DialogFooter>
          </div>
        )}

        {/* Step 3 — Approve */}
        {!isResuming && step === 3 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <h3 className="mb-3 text-sm font-medium text-slate-950 dark:text-slate-50">
                  Interest Run Totals
                </h3>
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">Total Interest</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalInterest, currency)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">LPFF Share</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalLpffShare, currency)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">Client Share</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalClientShare, currency)}
                    </dd>
                  </div>
                </dl>
              </div>
            )}

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isLoading}
              >
                Cancel
              </Button>
              {canApprove && !isCreator ? (
                <Button type="button" onClick={handleApprove} disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Approving...
                    </>
                  ) : (
                    "Approve Run"
                  )}
                </Button>
              ) : isCreator ? (
                <div className="flex flex-col items-end gap-1">
                  <Button type="button" disabled>
                    Approve Run
                  </Button>
                  <p
                    className="text-xs text-slate-500 dark:text-slate-400"
                    data-testid="interest-run-awaiting-approval"
                  >
                    Awaiting approval by another member
                  </p>
                </div>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Pending Approval — you do not have permission to approve interest runs.
                </p>
              )}
            </DialogFooter>
          </div>
        )}

        {/* Step 4 — Post */}
        {!isResuming && step === 4 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <div className="mb-3 flex items-center justify-between">
                  <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    Approved Interest Run
                  </h3>
                  <Badge variant="warning">{INTEREST_RUN_STATUS_LABELS.APPROVED}</Badge>
                </div>
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">Total Interest</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalInterest, currency)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">LPFF Share</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalLpffShare, currency)}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">Client Share</dt>
                    <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                      {formatCurrency(run.totalClientShare, currency)}
                    </dd>
                  </div>
                </dl>
              </div>
            )}

            {error && <p className="text-destructive text-sm">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isLoading}
              >
                Cancel
              </Button>
              {canManage ? (
                <Button type="button" onClick={handlePost} disabled={isLoading}>
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Posting...
                    </>
                  ) : (
                    "Post to Ledger"
                  )}
                </Button>
              ) : (
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  You do not have permission to post interest runs.
                </p>
              )}
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
