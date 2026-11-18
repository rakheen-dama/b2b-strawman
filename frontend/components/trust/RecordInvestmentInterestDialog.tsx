"use client";

import { useEffect, useState } from "react";
import { useForm, useWatch, type DefaultValues } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { todayInSA } from "@b2mash/shared/date";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { InterestShares } from "@/components/trust/InterestShares";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { fundShareToleranceMessage, fundShareWithinTolerance } from "@/lib/trust-interest";
import {
  recordInvestmentInterestSchema,
  type RecordInvestmentInterestFormData,
} from "@/lib/schemas/trust";
import {
  INTEREST_PRESENTATION_LABELS,
  type BankStatementLineResponse,
  type InterestPresentation,
} from "@/lib/types/trust";
import {
  fetchUnmatchedCreditLines,
  recordInvestmentInterest,
} from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";

interface RecordInvestmentInterestDialogProps {
  investmentId: string;
  /** The investment account that holds it: its UNMATCHED credit lines can be recorded from. */
  investmentAccountId: string;
  /** The investment's deposit date (ISO): the earliest entry date the server accepts. */
  depositDate: string;
  currency: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function getDefaultValues(): DefaultValues<RecordInvestmentInterestFormData> {
  return {
    entryDate: todayInSA(),
    grossAmount: undefined,
    // No statement line: a bank advice, so the bank credited the net (OQ-4)
    presentation: "NET",
    fundShareAmount: undefined,
    bankStatementLineId: "",
  };
}

/** The server's sentence for an entry dated before the placement. */
const BEFORE_DEPOSIT_MESSAGE = "The entry date cannot be before the investment's deposit date.";

const PRESENTATIONS: InterestPresentation[] = ["GROSS", "NET"];

const amountOrUndefined = (value: string) => (value === "" ? undefined : parseFloat(value));

/**
 * 648B (ADR-379): one section 86(4) interest entry — the gross, the Legal Practitioners' Fidelity
 * Fund's 5% and the client's 95%, shown live. Picking one of the investment account's unmatched
 * credit lines dates the entry the line's date; a GROSS line is the gross (the Fund's 5% leaves in
 * a separate debit), a NET line is the client's 95% and the firm types the gross the bank advised.
 * Without a line (a bank advice) the presentation is NET: a gross credit always has a line (OQ-4).
 * The Fund's share may be typed as the bank shows it, within R0,01 of 5%. A section 86(3)
 * investment never opens this dialog — its interest is the Fund's.
 */
export function RecordInvestmentInterestDialog({
  investmentId,
  investmentAccountId,
  depositDate,
  currency,
  open,
  onOpenChange,
  onSuccess,
}: RecordInvestmentInterestDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lines, setLines] = useState<BankStatementLineResponse[]>([]);

  const form = useForm<RecordInvestmentInterestFormData>({
    resolver: zodResolver(recordInvestmentInterestSchema),
    defaultValues: getDefaultValues(),
  });
  const [lineId, presentation, grossAmount, fundShareAmount] = useWatch({
    control: form.control,
    name: ["bankStatementLineId", "presentation", "grossAmount", "fundShareAmount"],
  });
  const line = lines.find((l) => l.id === lineId);
  // A GROSS line's amount is the gross: shown, not typed
  const grossFromLine = line !== undefined && presentation === "GROSS";

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    fetchUnmatchedCreditLines(investmentAccountId)
      .then((result) => {
        if (!cancelled) setLines(result);
      })
      .catch(() => {
        if (!cancelled) setLines([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, investmentAccountId]);

  function handleOpenChange(newOpen: boolean) {
    // Escape / overlay / X while a record is in flight: stay open so its outcome is shown
    if (!newOpen && isSubmitting) return;
    setDialogOpen(newOpen);
  }

  function setDialogOpen(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset(getDefaultValues());
      setError(null);
    }
  }

  /** GROSS from a line: the line is the gross; otherwise the firm types the gross. */
  function fillGross(picked: BankStatementLineResponse | undefined, value: InterestPresentation) {
    if (picked && value === "GROSS") {
      form.setValue("grossAmount", picked.amount, { shouldValidate: true });
    } else {
      form.resetField("grossAmount");
    }
  }

  function selectLine(id: string) {
    const picked = lines.find((l) => l.id === id);
    form.setValue("bankStatementLineId", id);
    if (picked) {
      // The entry is the line's: dated the line's date
      form.setValue("entryDate", picked.transactionDate, { shouldValidate: true });
      fillGross(picked, form.getValues("presentation"));
    } else {
      // A bank advice is net of the Fund's 5% (OQ-4)
      form.setValue("presentation", "NET");
      form.resetField("grossAmount");
    }
  }

  function selectPresentation(value: InterestPresentation) {
    form.setValue("presentation", value, { shouldValidate: true });
    fillGross(line, value);
  }

  async function handleSubmit(data: RecordInvestmentInterestFormData) {
    setError(null);
    // ISO dates compare as strings; the server refuses this too, with the same sentence
    if (data.entryDate < depositDate) {
      form.setError("entryDate", { message: BEFORE_DEPOSIT_MESSAGE });
      return;
    }
    if (
      data.fundShareAmount !== undefined &&
      !fundShareWithinTolerance(data.grossAmount, data.fundShareAmount)
    ) {
      form.setError("fundShareAmount", { message: fundShareToleranceMessage(data.grossAmount) });
      return;
    }
    setIsSubmitting(true);
    try {
      const payload: RecordInvestmentInterestFormData = {
        entryDate: data.entryDate,
        grossAmount: data.grossAmount,
        presentation: data.presentation,
      };
      if (data.fundShareAmount !== undefined) payload.fundShareAmount = data.fundShareAmount;
      if (data.bankStatementLineId) payload.bankStatementLineId = data.bankStatementLineId;
      const result = await recordInvestmentInterest(investmentId, payload);
      if (!result.success) {
        setError(result.error ?? "Failed to record interest");
        return;
      }
      onSuccess();
      router.refresh();
      setDialogOpen(false);
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg" data-testid="record-investment-interest-dialog">
        <DialogHeader>
          <DialogTitle>Record interest</DialogTitle>
          <DialogDescription>
            Section 86(4) interest is the client&apos;s, less the Legal Practitioners&apos; Fidelity
            Fund&apos;s 5%. The entry posts at once.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          {/* noValidate: the schema is the authority (a native step check must not block 7.50) */}
          <form noValidate onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="bankStatementLineId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Statement line</FormLabel>
                  <FormControl>
                    <select
                      className={nativeSelectClassName}
                      name={field.name}
                      ref={field.ref}
                      onBlur={field.onBlur}
                      value={field.value ?? ""}
                      onChange={(e) => selectLine(e.target.value)}
                    >
                      <option value="">Bank advice — no statement line</option>
                      {lines.map((l) => (
                        <option key={l.id} value={l.id}>
                          {`Line #${l.lineNumber}, ${formatLocalDate(l.transactionDate)} — ${formatCurrency(l.amount, currency)}`}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormDescription>
                    An unmatched credit on the investment account&apos;s statement.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="presentation"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>How did the bank credit it?</FormLabel>
                  <FormControl>
                    <RadioGroup
                      value={field.value ?? ""}
                      onValueChange={(v) => selectPresentation(v as InterestPresentation)}
                    >
                      {PRESENTATIONS.map((value) => (
                        <div key={value} className="flex items-center gap-2">
                          <RadioGroupItem
                            value={value}
                            id={`interest-presentation-${value}`}
                            disabled={value === "GROSS" && !line}
                          />
                          <Label htmlFor={`interest-presentation-${value}`} className="font-normal">
                            {INTEREST_PRESENTATION_LABELS[value]}
                          </Label>
                        </div>
                      ))}
                    </RadioGroup>
                  </FormControl>
                  {!line && (
                    <FormDescription data-testid="interest-bank-advice-note">
                      Without a statement line this is a bank advice: the Fund&apos;s 5% was paid at
                      source, so only the client&apos;s share reached the account.
                    </FormDescription>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="entryDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Date credited</FormLabel>
                  <FormControl>
                    <Input
                      type="date"
                      min={depositDate}
                      max={todayInSA()}
                      readOnly={line !== undefined}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="grossAmount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Gross interest</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      readOnly={grossFromLine}
                      name={field.name}
                      ref={field.ref}
                      onBlur={field.onBlur}
                      value={field.value ?? ""}
                      onChange={(e) => field.onChange(amountOrUndefined(e.target.value))}
                    />
                  </FormControl>
                  {line && presentation === "NET" && (
                    <FormDescription>
                      The line is the client&apos;s 95%; enter the gross the bank advised.
                    </FormDescription>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="fundShareAmount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Fund&apos;s 5% as the bank shows it</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      name={field.name}
                      ref={field.ref}
                      onBlur={field.onBlur}
                      value={field.value ?? ""}
                      onChange={(e) => field.onChange(amountOrUndefined(e.target.value))}
                    />
                  </FormControl>
                  <FormDescription>
                    Optional — leave empty to use 5% of the gross. The bank&apos;s figure may differ
                    by R0,01.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <InterestShares
              gross={grossAmount}
              typedFundShare={fundShareAmount}
              currency={currency}
            />

            {error && (
              <p role="alert" className="text-destructive text-sm">
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
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Recording...
                  </>
                ) : (
                  "Record interest"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
