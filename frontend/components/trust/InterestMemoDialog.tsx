"use client";

import { useRef, useState, type ReactNode } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Info, Loader2 } from "lucide-react";
import { todayInSA } from "@b2mash/shared/date";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  fetchInterestMemos,
  saveInterestMemo,
} from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions";
import { saveInterestMemoSchema, type SaveInterestMemoFormData } from "@/lib/schemas/trust";
import type { InterestMemo } from "@/lib/types";

/** Shown for a month whose Rule 54.15 list is locked (650D's posting warning does not apply). */
export const LOCKED_MONTH_MEMO_NOTE =
  "This month's trust creditor list is locked. A memo is not part of the list: saving it changes no balance and no locked list.";

interface InterestMemoDialogProps {
  trustAccountId: string;
  /** Correct mode: the memo to correct (its month is fixed). Record mode when absent. */
  memo?: InterestMemo;
  /** The shown year's memos — record mode says so when the chosen month already has one. */
  existing: InterestMemo[];
  /**
   * The year `existing` belongs to. Record mode loads the memos of any other chosen year itself
   * (`fetchInterestMemos`, cached per year while the dialog is open); without it, every year loads.
   */
  existingYear?: number;
  /** Months (yyyy-MM) whose Rule 54.15 list is locked (650A). */
  lockedMonths: string[];
  triggerLabel: ReactNode;
  triggerVariant?: "default" | "outline" | "soft" | "plain";
  triggerTestId: string;
}

/** "2026-10" (or "2026-10-01") → "October 2026". */
export function monthLabel(yyyyMm: string): string {
  const [year, month] = yyyyMm.split("-").map(Number);
  if (!year || !month) return "";
  return new Date(year, month - 1, 1).toLocaleDateString("en-ZA", {
    month: "long",
    year: "numeric",
  });
}

/** A memo month (yyyy-MM) is from January 1900 (the list reads years from 1900). */
const EARLIEST_MONTH = "1900-01";

/**
 * Record mode: the memos of each other year chosen since the dialog opened ("loading" while in
 * flight, "error" when they could not be loaded), tagged with the opening they belong to so a
 * response from an earlier opening is dropped.
 */
interface OtherYears {
  opening: number;
  years: Record<number, InterestMemo[] | "loading" | "error">;
}

/** The month before today's month in South Africa (yyyy-MM). */
function previousMonth(): string {
  const [year, month] = todayInSA().split("-").map(Number);
  const prevYear = month === 1 ? year - 1 : year;
  const prevMonth = month === 1 ? 12 : month - 1;
  return `${prevYear}-${String(prevMonth).padStart(2, "0")}`;
}

function defaults(memo?: InterestMemo): SaveInterestMemoFormData {
  return memo
    ? {
        month: memo.periodMonth.slice(0, 7),
        grossInterest: memo.grossInterest,
        grossCharges: memo.grossCharges,
        chargesVat: memo.chargesVat,
        sourceNote: memo.sourceNote,
      }
    : { month: previousMonth(), grossInterest: 0, grossCharges: 0, chargesVat: 0, sourceNote: "" };
}

const AMOUNT_FIELDS = [
  {
    name: "grossInterest",
    label: "Gross interest earned (at source)",
    testId: "interest-memo-gross-interest",
  },
  {
    name: "grossCharges",
    label: "Gross charges levied (at source)",
    testId: "interest-memo-gross-charges",
  },
  { name: "chargesVat", label: "VAT included in the charges", testId: "interest-memo-vat" },
] as const;

/**
 * Record or correct one month's at-source memo (Phase 92 650B, ADR-384): the Rule 54.28 gross
 * interest, gross charges and the VAT in them that never reached the trust account, and where the
 * figures came from. Nothing is posted and no balance moves; a month whose list is locked takes a
 * memo too (it is not part of the list). Owns its trigger (Dialog Trigger Composition).
 */
export function InterestMemoDialog({
  trustAccountId,
  memo,
  existing,
  existingYear,
  lockedMonths,
  triggerLabel,
  triggerVariant = "outline",
  triggerTestId,
}: InterestMemoDialogProps) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Radix only tracks a DialogTrigger, so the owned button restores focus itself on close.
  const triggerRef = useRef<HTMLButtonElement>(null);
  // Record mode: the memos of the other years chosen since the dialog opened (D-21).
  const [otherYears, setOtherYears] = useState<OtherYears>({ opening: 0, years: {} });

  const form = useForm<SaveInterestMemoFormData>({
    resolver: zodResolver(saveInterestMemoSchema),
    defaultValues: defaults(memo),
  });
  // useWatch, not form.watch (React Compiler: 650D D-12).
  const month = useWatch({ control: form.control, name: "month" });
  const locked = Boolean(month) && lockedMonths.includes(month);
  const monthYear = Number(month?.slice(0, 4));
  const yearMemos = monthYear === existingYear ? existing : otherYears.years[monthYear];
  const replaces =
    !memo && Array.isArray(yearMemos) && yearMemos.some((m) => m.periodMonth.slice(0, 7) === month);
  const replaceUnknown = !memo && yearMemos === "error";

  /**
   * Record mode: loads the memos of `yyyyMm`'s year when it is not the shown year — once per
   * opening; a failed load is retried when the year is chosen again.
   */
  function loadYearOf(yyyyMm: string, state: OtherYears = otherYears) {
    if (memo || !/^\d{4}-(0[1-9]|1[0-2])$/.test(yyyyMm) || yyyyMm < EARLIEST_MONTH) return;
    const year = Number(yyyyMm.slice(0, 4));
    const cached = state.years[year];
    if (year === existingYear || (cached !== undefined && cached !== "error")) return;
    const { opening } = state;
    const settle = (value: InterestMemo[] | "loading" | "error") =>
      setOtherYears((prev) =>
        prev.opening === opening ? { opening, years: { ...prev.years, [year]: value } } : prev
      );
    settle("loading");
    fetchInterestMemos(trustAccountId, year).then(settle, () => settle("error"));
  }

  function handleOpenChange(next: boolean) {
    // Escape / overlay / X while a request is in flight: stay open so its outcome is shown.
    if (!next && isSubmitting) return;
    setDialogOpen(next);
  }

  function setDialogOpen(next: boolean) {
    setOpen(next);
    if (next) {
      // Fresh values on every open (the previous month as of the opening; the memo's latest figures).
      setError(null);
      const values = defaults(memo);
      form.reset(values);
      // Other years' memos may have changed since the last opening: load them afresh.
      const fresh: OtherYears = { opening: otherYears.opening + 1, years: {} };
      setOtherYears(fresh);
      loadYearOf(values.month, fresh);
    }
  }

  async function handleSubmit(values: SaveInterestMemoFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await saveInterestMemo(trustAccountId, values);
      if (!result.success) {
        setError(result.error ?? "Failed to save the memo");
        return;
      }
      setDialogOpen(false);
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <Button
        type="button"
        variant={triggerVariant}
        size="sm"
        ref={triggerRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setDialogOpen(true)}
        data-testid={triggerTestId}
      >
        {triggerLabel}
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent
          className="sm:max-w-lg"
          data-testid="interest-memo-dialog"
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            triggerRef.current?.focus();
          }}
        >
          <DialogHeader>
            <DialogTitle>
              {memo
                ? `Correct the memo for ${monthLabel(memo.periodMonth)}`
                : "Record an interest and charges memo"}
            </DialogTitle>
            <DialogDescription>
              Gross interest and charges the bank swept to the Fund or showed only net, so they are
              not in the cashbook. A memo changes no balance; it is reported on the Rule 54.28
              schedule.
            </DialogDescription>
          </DialogHeader>

          <Form {...form}>
            <form noValidate onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
              {/* Every field is read-only while the save is in flight; a refusal re-enables them. */}
              <fieldset
                disabled={isSubmitting}
                className="space-y-4"
                data-testid="interest-memo-fields"
              >
                {memo ? (
                  <div className="space-y-1">
                    <p className="text-sm font-medium">Month</p>
                    <p className="text-sm" data-testid="interest-memo-month-fixed">
                      {monthLabel(memo.periodMonth)}
                    </p>
                  </div>
                ) : (
                  <FormField
                    control={form.control}
                    name="month"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Month</FormLabel>
                        <FormControl>
                          <Input
                            type="month"
                            min={EARLIEST_MONTH}
                            max={todayInSA().slice(0, 7)}
                            data-testid="interest-memo-month"
                            {...field}
                            onChange={(e) => {
                              field.onChange(e);
                              loadYearOf(e.target.value);
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                )}

                {locked && (
                  // Informational, not a warning: role="status" (the Alert primitive defaults to "alert").
                  <Alert role="status" data-testid="interest-memo-locked-note">
                    <Info />
                    <AlertDescription>{LOCKED_MONTH_MEMO_NOTE}</AlertDescription>
                  </Alert>
                )}
                {replaces && (
                  <p
                    role="status"
                    className="text-sm text-slate-500 dark:text-slate-400"
                    data-testid="interest-memo-replaces-note"
                  >
                    A memo for {monthLabel(month)} is already saved. Saving replaces its figures;
                    the previous figures stay in the audit trail.
                  </p>
                )}
                {replaceUnknown && (
                  <p
                    role="status"
                    className="text-sm text-slate-500 dark:text-slate-400"
                    data-testid="interest-memo-replaces-unknown-note"
                  >
                    Could not check whether a memo for {monthLabel(month)} is already saved. If one
                    is, saving replaces its figures; the previous figures stay in the audit trail.
                  </p>
                )}

                {AMOUNT_FIELDS.map((amount) => (
                  <FormField
                    key={amount.name}
                    control={form.control}
                    name={amount.name}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{amount.label}</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            step="0.01"
                            min="0"
                            data-testid={amount.testId}
                            name={field.name}
                            ref={field.ref}
                            onBlur={field.onBlur}
                            value={field.value ?? ""}
                            onChange={(e) =>
                              field.onChange(
                                e.target.value === "" ? undefined : parseFloat(e.target.value)
                              )
                            }
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                ))}

                <FormField
                  control={form.control}
                  name="sourceNote"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Where the figures come from</FormLabel>
                      <FormControl>
                        <Textarea
                          maxLength={500}
                          placeholder="e.g. the bank's interest advice reference or the Fund statement"
                          data-testid="interest-memo-source"
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </fieldset>

              {error && (
                <p role="alert" className="text-sm text-red-600 dark:text-red-400">
                  {error}
                </p>
              )}

              <DialogFooter>
                <Button
                  type="button"
                  variant="plain"
                  onClick={() => handleOpenChange(false)}
                  disabled={isSubmitting}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={isSubmitting} data-testid="interest-memo-submit">
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Saving...
                    </>
                  ) : (
                    "Save memo"
                  )}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        </DialogContent>
      </Dialog>
    </>
  );
}
