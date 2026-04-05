"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Badge } from "@/components/ui/badge";
import {
  createInterestRunSchema,
  type CreateInterestRunFormData,
} from "@/lib/schemas/trust";
import {
  createInterestRun,
  calculateInterest,
  approveInterestRun,
  postInterestRun,
  fetchInterestRunDetail,
} from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import { formatCurrency } from "@/lib/format";
import type { InterestRun, InterestAllocation } from "@/lib/types/trust";

interface InterestRunWizardProps {
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

const STATUS_BADGE_VARIANT: Record<
  string,
  "neutral" | "warning" | "success"
> = {
  DRAFT: "neutral",
  APPROVED: "warning",
  POSTED: "success",
};

export function InterestRunWizard({
  accountId,
  open,
  onOpenChange,
  onSuccess,
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
      <DialogContent
        className="sm:max-w-2xl"
        data-testid="interest-run-wizard"
      >
        <DialogHeader>
          <DialogTitle>New Interest Run</DialogTitle>
          <DialogDescription>
            Step {step} of 4 — {stepLabels[step - 1]}
          </DialogDescription>
        </DialogHeader>

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
        {step === 1 && (
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(handleCreate)}
              className="space-y-4"
            >
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

              {error && <p className="text-sm text-destructive">{error}</p>}

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
        {step === 2 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-slate-950 dark:text-slate-50">
                      Period: {run.periodStart} to {run.periodEnd}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      Run ID: {run.id}
                    </p>
                  </div>
                  <Badge variant={STATUS_BADGE_VARIANT[run.status]}>
                    {run.status}
                  </Badge>
                </div>
              </div>
            )}

            {allocations.length > 0 && (
              <div className="overflow-x-auto">
                <table
                  className="w-full text-sm"
                  data-testid="allocations-table"
                >
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pb-3 pr-4 text-left font-medium text-slate-500 dark:text-slate-400">
                        Client ID
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                        Avg Daily Balance
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
                        Gross Interest
                      </th>
                      <th className="pb-3 pr-4 text-right font-medium text-slate-500 dark:text-slate-400">
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
                        <td className="py-3 pr-4 font-mono text-xs">
                          {a.customerId}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.averageDailyBalance, "ZAR")}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.grossInterest, "ZAR")}
                        </td>
                        <td className="py-3 pr-4 text-right font-mono tabular-nums">
                          {formatCurrency(a.lpffShare, "ZAR")}
                        </td>
                        <td className="py-3 text-right font-mono tabular-nums">
                          {formatCurrency(a.clientShare, "ZAR")}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}

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
                <Button
                  type="button"
                  onClick={handleCalculate}
                  disabled={isLoading}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Calculating...
                    </>
                  ) : (
                    "Calculate Interest"
                  )}
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={() => setStep(3)}
                  disabled={isLoading}
                >
                  Approve
                </Button>
              )}
            </DialogFooter>
          </div>
        )}

        {/* Step 3 — Approve */}
        {step === 3 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <h3 className="mb-3 text-sm font-medium text-slate-950 dark:text-slate-50">
                  Interest Run Totals
                </h3>
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      Total Interest
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalInterest, "ZAR")}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      LPFF Share
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalLpffShare, "ZAR")}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      Client Share
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalClientShare, "ZAR")}
                    </dd>
                  </div>
                </dl>
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isLoading}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleApprove}
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Approving...
                  </>
                ) : (
                  "Approve Run"
                )}
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Step 4 — Post */}
        {step === 4 && (
          <div className="space-y-4">
            {run && (
              <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
                <div className="mb-3 flex items-center justify-between">
                  <h3 className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    Approved Interest Run
                  </h3>
                  <Badge variant="warning">APPROVED</Badge>
                </div>
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      Total Interest
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalInterest, "ZAR")}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      LPFF Share
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalLpffShare, "ZAR")}
                    </dd>
                  </div>
                  <div className="flex justify-between">
                    <dt className="text-slate-500 dark:text-slate-400">
                      Client Share
                    </dt>
                    <dd className="font-mono tabular-nums text-slate-950 dark:text-slate-50">
                      {formatCurrency(run.totalClientShare, "ZAR")}
                    </dd>
                  </div>
                </dl>
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isLoading}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handlePost}
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Posting...
                  </>
                ) : (
                  "Post to Ledger"
                )}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
