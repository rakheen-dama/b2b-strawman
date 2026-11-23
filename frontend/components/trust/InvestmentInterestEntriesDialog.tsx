"use client";

import { useState } from "react";
import useSWR from "swr";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { INTEREST_PRESENTATION_LABELS } from "@/lib/types/trust";
import {
  interestReversalReasonSchema,
  type InterestReversalReasonFormData,
} from "@/lib/schemas/trust";
import {
  fetchInvestmentInterest,
  reverseInvestmentInterest,
} from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";
import { useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { UnsettledSubmitNotice } from "./unsettled-submit-notice";

interface InvestmentInterestEntriesDialogProps {
  investmentId: string;
  currency: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called after an entry is reversed, so the register's interest totals and balance refresh. */
  onReversed: () => void;
}

/**
 * 648B F-1: the interest entries of one section 86(4) investment, each POSTED bank-advice entry
 * with a Reverse action (a confirm step asking for the reason — required, D-2 — and no date: the
 * contra is dated the entry's date). An entry recorded
 * from a statement line is reversed by unmatching the line in the reconciliation wizard, so it
 * carries no Reverse here. The page only mounts this for a user with MANAGE_TRUST, the
 * capability the endpoint enforces.
 */
export function InvestmentInterestEntriesDialog({
  investmentId,
  currency,
  open,
  onOpenChange,
  onReversed,
}: InvestmentInterestEntriesDialogProps) {
  const idempotency = useIdempotencyKey();
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<InterestReversalReasonFormData>({
    resolver: zodResolver(interestReversalReasonSchema),
    defaultValues: { reason: "" },
  });

  function startConfirm(id: string | null) {
    setError(null);
    form.reset({ reason: "" });
    setConfirmingId(id);
  }

  const {
    data: entries,
    error: loadFailure,
    mutate,
  } = useSWR(
    open ? `investment-interest-${investmentId}` : null,
    () => fetchInvestmentInterest(investmentId),
    { revalidateOnFocus: false, errorRetryCount: 0, dedupingInterval: 0 }
  );
  const loadError = loadFailure !== undefined;

  function handleOpenChange(next: boolean) {
    if (!next) {
      startConfirm(null);
    }
    onOpenChange(next);
  }

  async function handleConfirm(interestId: string, data: InterestReversalReasonFormData) {
    if (isSubmitting) return;
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await idempotency.run(
        ["reverseInvestmentInterest", interestId, data],
        (idempotencyKey) => reverseInvestmentInterest(interestId, data, idempotencyKey)
      );
      if (result.success) {
        startConfirm(null);
        await mutate();
        onReversed();
      } else {
        setError(result.error ?? "Failed to reverse interest entry");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  const confirming = entries?.find((e) => e.id === confirmingId) ?? null;
  // §1.5: an earlier reversal of this entry may already have been recorded (client-only: the
  // confirm step renders only after the entries load in the browser)
  const confirmingUnsettled =
    confirming !== null && idempotency.hasUnsettled(["reverseInvestmentInterest", confirming.id]);

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Interest entries</DialogTitle>
          <DialogDescription>
            Every section 86(4) interest entry recorded on this investment. Reversing an entry posts
            offsetting entries to the client and the Fund ledgers.
          </DialogDescription>
        </DialogHeader>

        {loadError && (
          <p role="alert" className="text-destructive text-sm">
            Unable to load the interest entries. Please try again.
          </p>
        )}

        {!loadError && entries === undefined && (
          <p className="text-sm text-slate-500 dark:text-slate-400">Loading...</p>
        )}

        {entries !== undefined && entries.length === 0 && (
          <p className="text-sm text-slate-500 dark:text-slate-400" data-testid="no-interest">
            No interest has been recorded on this investment.
          </p>
        )}

        {entries !== undefined && entries.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm" data-testid="interest-entries-table">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700">
                  <th className="pr-3 pb-2 text-left font-medium text-slate-500 dark:text-slate-400">
                    Date
                  </th>
                  <th className="pr-3 pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                    Gross
                  </th>
                  <th className="pr-3 pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                    Fund 5%
                  </th>
                  <th className="pr-3 pb-2 text-right font-medium text-slate-500 dark:text-slate-400">
                    Net to client
                  </th>
                  <th className="pb-2 text-left font-medium text-slate-500 dark:text-slate-400">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => {
                  const posted = entry.status === "POSTED";
                  const fromLine = entry.bankStatementLineId !== null;
                  return (
                    <tr
                      key={entry.id}
                      className="border-b border-slate-100 align-top last:border-0 dark:border-slate-800"
                      data-testid={`interest-entry-${entry.id}`}
                    >
                      <td className="py-2 pr-3">
                        <span className="block">{formatLocalDate(entry.entryDate)}</span>
                        <span className="block text-xs text-slate-500 dark:text-slate-400">
                          {INTEREST_PRESENTATION_LABELS[entry.presentation]}
                        </span>
                      </td>
                      <td className="py-2 pr-3 text-right font-mono tabular-nums">
                        {formatCurrency(entry.grossAmount, currency)}
                      </td>
                      <td className="py-2 pr-3 text-right font-mono tabular-nums">
                        {formatCurrency(entry.fundShareAmount, currency)}
                      </td>
                      <td className="py-2 pr-3 text-right font-mono tabular-nums">
                        {formatCurrency(entry.netAmount, currency)}
                      </td>
                      <td className="py-2">
                        {posted ? (
                          <>
                            <Badge variant="success" className="shrink whitespace-normal">
                              Posted
                            </Badge>
                            {fromLine ? (
                              <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">
                                Recorded from a statement line. Unmatch the statement line in
                                reconciliation to reverse it.
                              </span>
                            ) : (
                              <Button
                                variant="outline"
                                size="sm"
                                className="mt-1"
                                disabled={isSubmitting}
                                onClick={() => {
                                  startConfirm(entry.id);
                                }}
                              >
                                Reverse
                              </Button>
                            )}
                          </>
                        ) : (
                          <Badge variant="neutral" className="shrink whitespace-normal">
                            Reversed
                          </Badge>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {confirming && (
          <div
            className="space-y-3 rounded-lg border border-slate-200 p-3 dark:border-slate-700"
            data-testid="reverse-interest-confirm"
          >
            <p className="text-sm text-slate-950 dark:text-slate-50">
              Reverse the {formatLocalDate(confirming.entryDate)} interest entry (net{" "}
              {formatCurrency(confirming.netAmount, currency)})? This cannot be undone.
            </p>
            <Form {...form}>
              <form
                onSubmit={form.handleSubmit((data) => handleConfirm(confirming.id, data))}
                className="space-y-3"
              >
                <FormField
                  control={form.control}
                  name="reason"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Reason</FormLabel>
                      <FormControl>
                        <Textarea
                          placeholder="Reason for reversal"
                          maxLength={500}
                          data-testid="reverse-interest-reason-input"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <UnsettledSubmitNotice show={confirmingUnsettled} />
                <div className="flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="plain"
                    disabled={isSubmitting}
                    onClick={() => startConfirm(null)}
                  >
                    Keep entry
                  </Button>
                  <Button
                    type="submit"
                    variant="destructive"
                    disabled={isSubmitting}
                    data-testid="confirm-reverse-interest"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="mr-1.5 size-4 animate-spin" />
                        Reversing...
                      </>
                    ) : (
                      "Reverse interest entry"
                    )}
                  </Button>
                </div>
              </form>
            </Form>
          </div>
        )}

        {error && (
          <p role="alert" className="text-destructive text-sm">
            {error}
          </p>
        )}

        <DialogFooter>
          <Button type="button" variant="plain" onClick={() => handleOpenChange(false)}>
            Close
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
