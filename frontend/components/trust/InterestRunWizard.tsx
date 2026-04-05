"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { Loader2, CheckCircle2, ArrowRight } from "lucide-react";
import {
  createInterestRun,
  calculateInterest,
  approveInterestRun,
  postInterestRun,
  fetchInterestRunDetail,
} from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";
import {
  createInterestRunSchema,
  type CreateInterestRunFormData,
} from "@/lib/schemas/trust";
import type { InterestRun, InterestAllocation } from "@/lib/types";

interface InterestRunWizardProps {
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

type WizardStep = "create" | "calculate" | "approve" | "post" | "done";

function formatCurrency(amount: number): string {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
  }).format(amount);
}

export function InterestRunWizard({
  accountId,
  open,
  onOpenChange,
}: InterestRunWizardProps) {
  const [step, setStep] = useState<WizardStep>("create");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentRun, setCurrentRun] = useState<InterestRun | null>(null);
  const [allocations, setAllocations] = useState<InterestAllocation[]>([]);

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
      form.reset({ periodStart: "", periodEnd: "" });
      setStep("create");
      setError(null);
      setCurrentRun(null);
      setAllocations([]);
    }
  }

  async function handleCreate(data: CreateInterestRunFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createInterestRun(accountId, data);
      if (result.success && result.data) {
        setCurrentRun(result.data);
        setStep("calculate");
      } else {
        setError(result.error ?? "Failed to create interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCalculate() {
    if (!currentRun) return;
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await calculateInterest(currentRun.id);
      if (result.success && result.data) {
        setCurrentRun(result.data);
        // Fetch allocations
        const detailResult = await fetchInterestRunDetail(currentRun.id);
        if (detailResult.success && detailResult.data) {
          setAllocations(detailResult.data.allocations);
        }
        setStep("approve");
      } else {
        setError(result.error ?? "Failed to calculate interest");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleApprove() {
    if (!currentRun) return;
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await approveInterestRun(currentRun.id);
      if (result.success && result.data) {
        setCurrentRun(result.data);
        setStep("post");
      } else {
        setError(result.error ?? "Failed to approve interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handlePost() {
    if (!currentRun) return;
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await postInterestRun(currentRun.id);
      if (result.success && result.data) {
        setCurrentRun(result.data);
        setStep("done");
      } else {
        setError(result.error ?? "Failed to post interest run");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  const stepLabels: Record<WizardStep, string> = {
    create: "Select Period",
    calculate: "Calculate Interest",
    approve: "Review & Approve",
    post: "Post to Ledger",
    done: "Complete",
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Interest Run Wizard</DialogTitle>
          <DialogDescription>
            Step: {stepLabels[step]}
          </DialogDescription>
        </DialogHeader>

        {/* Step indicators */}
        <div className="flex items-center gap-2 text-xs text-slate-500">
          {(["create", "calculate", "approve", "post"] as const).map(
            (s, i) => (
              <div key={s} className="flex items-center gap-1">
                {i > 0 && (
                  <ArrowRight className="size-3 text-slate-300 dark:text-slate-600" />
                )}
                <span
                  className={
                    step === s || step === "done"
                      ? "font-medium text-teal-600 dark:text-teal-400"
                      : ""
                  }
                >
                  {stepLabels[s]}
                </span>
              </div>
            ),
          )}
        </div>

        {/* Step 1: Create */}
        {step === "create" && (
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
                  disabled={isSubmitting}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? (
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

        {/* Step 2: Calculate */}
        {step === "calculate" && currentRun && (
          <div className="space-y-4">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900">
              <p className="text-sm text-slate-700 dark:text-slate-300">
                Interest run created for period{" "}
                <span className="font-medium">
                  {currentRun.periodStart}
                </span>{" "}
                to{" "}
                <span className="font-medium">{currentRun.periodEnd}</span>
                . Click calculate to compute interest allocations.
              </p>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button onClick={handleCalculate} disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Calculating...
                  </>
                ) : (
                  "Calculate Interest"
                )}
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Step 3: Approve (shows allocations) */}
        {step === "approve" && currentRun && (
          <div className="space-y-4">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-900">
              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600 dark:text-slate-400">
                  Total Interest
                </span>
                <span className="font-mono font-semibold tabular-nums text-slate-950 dark:text-slate-50">
                  {formatCurrency(currentRun.totalInterest)}
                </span>
              </div>
              <div className="mt-1 flex items-center justify-between text-sm">
                <span className="text-slate-600 dark:text-slate-400">
                  LPFF Share
                </span>
                <span className="font-mono tabular-nums text-slate-700 dark:text-slate-300">
                  {formatCurrency(currentRun.totalLpffShare)}
                </span>
              </div>
              <div className="mt-1 flex items-center justify-between text-sm">
                <span className="text-slate-600 dark:text-slate-400">
                  Client Share
                </span>
                <span className="font-mono tabular-nums text-slate-700 dark:text-slate-300">
                  {formatCurrency(currentRun.totalClientShare)}
                </span>
              </div>
            </div>

            {allocations.length > 0 && (
              <div className="overflow-x-auto">
                <table
                  className="w-full text-sm"
                  data-testid="allocation-table"
                >
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pb-2 pr-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Client
                      </th>
                      <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Avg Daily Balance
                      </th>
                      <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Gross Interest
                      </th>
                      <th className="pb-2 pr-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        LPFF Share
                      </th>
                      <th className="pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                        Client Share
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {allocations.map((alloc) => (
                      <tr
                        key={alloc.id}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                      >
                        <td className="py-2 pr-3 font-mono text-xs text-slate-700 dark:text-slate-300">
                          {alloc.customerId.slice(0, 8)}...
                        </td>
                        <td className="py-2 pr-3 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                          {formatCurrency(alloc.averageDailyBalance)}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono tabular-nums text-slate-950 dark:text-slate-50">
                          {formatCurrency(alloc.grossInterest)}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                          {formatCurrency(alloc.lpffShare)}
                        </td>
                        <td className="py-2 text-right font-mono tabular-nums text-slate-700 dark:text-slate-300">
                          {formatCurrency(alloc.clientShare)}
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
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button onClick={handleApprove} disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Approving...
                  </>
                ) : (
                  "Approve"
                )}
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Step 4: Post */}
        {step === "post" && currentRun && (
          <div className="space-y-4">
            <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 dark:border-teal-800 dark:bg-teal-950">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="size-5 text-teal-600 dark:text-teal-400" />
                <p className="text-sm font-medium text-teal-700 dark:text-teal-300">
                  Interest run approved
                </p>
              </div>
              <p className="mt-1 text-sm text-teal-600 dark:text-teal-400">
                Posting will create interest credit transactions on client
                ledgers. This action cannot be undone.
              </p>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => handleOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button onClick={handlePost} disabled={isSubmitting}>
                {isSubmitting ? (
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

        {/* Done */}
        {step === "done" && (
          <div className="space-y-4">
            <div className="rounded-lg border border-green-200 bg-green-50 p-4 text-center dark:border-green-800 dark:bg-green-950">
              <CheckCircle2 className="mx-auto size-8 text-green-600 dark:text-green-400" />
              <p className="mt-2 text-sm font-medium text-green-700 dark:text-green-300">
                Interest run posted successfully
              </p>
              <p className="mt-1 text-sm text-green-600 dark:text-green-400">
                Interest credits have been applied to client ledgers.
              </p>
            </div>
            <DialogFooter>
              <Button onClick={() => handleOpenChange(false)}>Done</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
