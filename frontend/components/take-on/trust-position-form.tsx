"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  savePosition,
  validateBatch,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import {
  takeOnAmountValue,
  takeOnTrustPositionSchema,
  type TakeOnTrustPositionFormData,
} from "@/lib/schemas";
import type { TakeOnBatch } from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";

type AmountField = Exclude<
  keyof TakeOnTrustPositionFormData,
  "cutoverDate" | "midMonthAcknowledged"
>;

/** The six figures in X3 order, with their X3 labels (§17.10) and stable testids. */
const AMOUNT_FIELDS: ReadonlyArray<{
  name: AmountField;
  label: string;
  testId: string;
  description?: string;
}> = [
  {
    name: "bankBalance",
    label: "Bank statement balance at cutover",
    testId: "take-on-position-bank-balance",
  },
  {
    name: "outstandingDepositsTotal",
    label: "Outstanding deposits at cutover",
    testId: "take-on-position-outstanding-deposits",
  },
  {
    name: "outstandingPaymentsTotal",
    label: "Outstanding payments at cutover",
    testId: "take-on-position-outstanding-payments",
  },
  {
    name: "cashbookBalance",
    label: "Cashbook balance at cutover",
    testId: "take-on-position-cashbook",
  },
  {
    name: "fundLedgerBalance",
    label: "Fund interest and charges ledger at cutover",
    testId: "take-on-position-fund-ledger",
    // "Fund" in full on its first use on the screen (X3 terms); product copy, D-190.
    description:
      "The Legal Practitioners' Fidelity Fund interest and charges ledger; negative if it is in debit.",
  },
  {
    name: "investedTotal",
    label: "Total invested (per the institutions' confirmations)",
    testId: "take-on-position-invested-total",
  },
];

/** A stored figure as the form shows it ("418500.00"); blank when never entered. */
const shown = (amount: number | null | undefined) =>
  amount === null || amount === undefined ? "" : amount.toFixed(2);

interface TrustPositionFormProps {
  batch: TakeOnBatch;
  onBatchChange: (batch: TakeOnBatch) => void;
  /**
   * Another request on this batch is in flight (the Validation step's manual dry run): the form
   * is disabled, so a slower response can never overwrite a newer batch.
   */
  disabled?: boolean;
  /** Called with true when the save starts and false when it ends (the shared in-flight state). */
  onBusyChange?: (busy: boolean) => void;
}

/**
 * The trust take-on's cutover date and the reconciled position in the previous system at that
 * date (Phase 93 657B, P-11, §17.10 X3 labels). Lives on the Validation step (OP-5): a save that
 * changes the position moves the batch back to Mapped, and the dry run then runs straight away so
 * the equation indicator shows the new figures. Editable while the batch is open (CANCEL).
 */
export function TrustPositionForm({
  batch,
  onBatchChange,
  disabled = false,
  onBusyChange,
}: TrustPositionFormProps) {
  const open = batch.allowedActions.includes("CANCEL");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const form = useForm<TakeOnTrustPositionFormData>({
    resolver: zodResolver(takeOnTrustPositionSchema),
    defaultValues: {
      cutoverDate: batch.cutoverDate ?? "",
      bankBalance: shown(batch.position?.bankBalance),
      outstandingDepositsTotal: shown(batch.position?.outstandingDepositsTotal),
      outstandingPaymentsTotal: shown(batch.position?.outstandingPaymentsTotal),
      cashbookBalance: shown(batch.position?.cashbookBalance),
      fundLedgerBalance: shown(batch.position?.fundLedgerBalance),
      investedTotal: shown(batch.position?.investedTotal),
      midMonthAcknowledged: batch.midMonthAcknowledged,
    },
  });

  async function onSubmit(values: TakeOnTrustPositionFormData) {
    setError(null);
    setBusy(true);
    onBusyChange?.(true);
    try {
      const saved = await savePosition(batch.id, {
        cutoverDate: values.cutoverDate,
        bankBalance: takeOnAmountValue(values.bankBalance),
        outstandingDepositsTotal: takeOnAmountValue(values.outstandingDepositsTotal),
        outstandingPaymentsTotal: takeOnAmountValue(values.outstandingPaymentsTotal),
        cashbookBalance: takeOnAmountValue(values.cashbookBalance),
        // Blank means R0,00 (what the screen shows), never "keep the stored figure" (D-190).
        fundLedgerBalance: takeOnAmountValue(values.fundLedgerBalance) ?? 0,
        investedTotal: takeOnAmountValue(values.investedTotal) ?? 0,
        midMonthAcknowledged: values.midMonthAcknowledged,
      });
      if (!saved.success || !saved.data) {
        setError(saved.error ?? "The position could not be saved. Try again.");
        return;
      }
      let next = saved.data;
      // A changed position needs a new dry run (VALIDATED → MAPPED): run it now (OP-5).
      if (next.status === "MAPPED") {
        const validated = await validateBatch(next.id);
        if (validated.success && validated.data) {
          next = validated.data;
        } else {
          setError(validated.error ?? "The dry run could not run.");
        }
      }
      onBatchChange(next);
    } finally {
      setBusy(false);
      onBusyChange?.(false);
    }
  }

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-800"
        data-testid="take-on-position-form"
        aria-labelledby="take-on-position-heading"
        noValidate
      >
        <div className="space-y-1">
          <h3
            id="take-on-position-heading"
            className="text-sm font-medium text-slate-950 dark:text-slate-50"
          >
            Reconciled position at cutover
          </h3>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Enter the figures of the previous system&apos;s reconciled trust position at the cutover
            date. Saving them runs the dry run again.
          </p>
        </div>
        <fieldset disabled={!open || busy || disabled} className="space-y-4">
          <FormField
            control={form.control}
            name="cutoverDate"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Cutover date</FormLabel>
                <FormControl>
                  <Input
                    type="date"
                    className="w-48"
                    data-testid="take-on-position-cutover-date"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            {AMOUNT_FIELDS.map((spec) => (
              <FormField
                key={spec.name}
                control={form.control}
                name={spec.name}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{spec.label}</FormLabel>
                    <FormControl>
                      <Input
                        inputMode="decimal"
                        autoComplete="off"
                        className="font-mono tabular-nums"
                        data-testid={spec.testId}
                        {...field}
                      />
                    </FormControl>
                    {spec.description && <FormDescription>{spec.description}</FormDescription>}
                    <FormMessage />
                  </FormItem>
                )}
              />
            ))}
          </div>
          <FormField
            control={form.control}
            name="midMonthAcknowledged"
            render={({ field }) => (
              <FormItem className="flex flex-row items-center gap-2">
                <FormControl>
                  <Checkbox
                    checked={field.value}
                    onCheckedChange={(c) => field.onChange(c === true)}
                    data-testid="take-on-position-mid-month"
                  />
                </FormControl>
                <FormLabel className="font-normal">This is a mid-month cutover</FormLabel>
              </FormItem>
            )}
          />
          {open && (
            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={busy || disabled}
              data-testid="take-on-position-save"
            >
              {busy ? "Saving…" : "Save position"}
            </Button>
          )}
        </fieldset>
        {error && <TakeOnError message={error} testId="take-on-position-error" />}
      </form>
    </Form>
  );
}
