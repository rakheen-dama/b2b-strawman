"use client";

import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { FilePlus2, Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { STATEMENT_LINE_CLASSIFICATIONS, type BankStatementLineResponse } from "@/lib/types";
import {
  recordFromStatementSchemaFor,
  type RecordFromStatementFormData,
} from "@/lib/schemas/trust";

interface RecordFromStatementDialogProps {
  /** An UNMATCHED line; its sign picks the classifications offered. */
  line: BankStatementLineResponse;
  currency: string;
  disabled?: boolean;
  /** Records the line; a refusal comes back as `error` and is shown in the dialog. */
  onRecord: (
    lineId: string,
    input: RecordFromStatementFormData
  ) => Promise<{ success: boolean; error?: string }>;
}

/**
 * Record from statement (647A, ADR-380): the firm confirms what an unmatched bank statement line
 * is, and the entry is posted dated the line's date. Owns its trigger (a plain button beside
 * Exclude — never an adjacent `asChild` trigger). Offers only the classifications that agree with
 * the line's sign; a bank charge also asks whether it is recoverable and its VAT.
 */
export function RecordFromStatementDialog({
  line,
  currency,
  disabled = false,
  onRecord,
}: RecordFromStatementDialogProps) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const sign = line.amount >= 0 ? 1 : -1;
  const choices = STATEMENT_LINE_CLASSIFICATIONS.filter((c) => c.lineSign === sign);

  const form = useForm<RecordFromStatementFormData>({
    resolver: zodResolver(recordFromStatementSchemaFor(Math.abs(line.amount))),
    defaultValues: {
      classification: undefined,
      chargeRecoverable: undefined,
      chargeVatAmount: undefined,
      notes: "",
    },
  });
  const classification = useWatch({ control: form.control, name: "classification" });

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setError(null);
      form.reset();
    }
  }

  async function handleSubmit(values: RecordFromStatementFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await onRecord(line.id, values);
      if (!result.success) {
        setError(result.error ?? "Failed to record the line");
        return;
      }
      handleOpenChange(false);
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
        variant="ghost"
        size="sm"
        disabled={disabled}
        onClick={() => setOpen(true)}
        data-testid={`record-btn-${line.id}`}
      >
        <FilePlus2 className="mr-1 size-3" />
        Record from statement
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-lg" data-testid="record-from-statement-dialog">
          <DialogHeader>
            <DialogTitle>Record from statement</DialogTitle>
            <DialogDescription>
              Line #{line.lineNumber}, {formatLocalDate(line.transactionDate)} — {line.description}{" "}
              — {formatCurrency(Math.abs(line.amount), currency)}
              {line.amount < 0 ? " DR" : " CR"}. The entry is dated the line&apos;s date and posts
              at once; unmatching the line reverses it.
            </DialogDescription>
          </DialogHeader>

          <Form {...form}>
            {/* noValidate: the schema is the authority (a native step check must not silently
                block a valid VAT such as 7.50). */}
            <form noValidate onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="classification"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>What is this line?</FormLabel>
                    <FormControl>
                      <RadioGroup
                        value={field.value ?? ""}
                        onValueChange={field.onChange}
                        data-testid="classification-radio"
                      >
                        {choices.map((choice) => (
                          <div key={choice.value} className="flex items-center gap-2">
                            <RadioGroupItem
                              value={choice.value}
                              id={`classification-${choice.value}`}
                            />
                            <Label
                              htmlFor={`classification-${choice.value}`}
                              className="font-normal"
                            >
                              {choice.label}
                            </Label>
                          </div>
                        ))}
                      </RadioGroup>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {classification === "BANK_CHARGE" && (
                <>
                  <FormField
                    control={form.control}
                    name="chargeRecoverable"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Recoverable from the bank?</FormLabel>
                        <FormControl>
                          <RadioGroup
                            value={field.value === undefined ? "" : field.value ? "yes" : "no"}
                            onValueChange={(v) => field.onChange(v === "yes")}
                            data-testid="charge-recoverable-radio"
                          >
                            <div className="flex items-center gap-2">
                              <RadioGroupItem value="yes" id="charge-recoverable-yes" />
                              <Label htmlFor="charge-recoverable-yes" className="font-normal">
                                Recoverable
                              </Label>
                            </div>
                            <div className="flex items-center gap-2">
                              <RadioGroupItem value="no" id="charge-recoverable-no" />
                              <Label htmlFor="charge-recoverable-no" className="font-normal">
                                Not recoverable
                              </Label>
                            </div>
                          </RadioGroup>
                        </FormControl>
                        <FormDescription>
                          Either way the charge is taken from the Fidelity Fund interest and charges
                          ledger.
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="chargeVatAmount"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>VAT included in the charge</FormLabel>
                        <FormControl>
                          <Input
                            type="number"
                            step="0.01"
                            min="0"
                            data-testid="charge-vat-input"
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
                        <FormDescription>Enter 0 if the charge carries no VAT.</FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}

              <FormField
                control={form.control}
                name="notes"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Notes (optional)</FormLabel>
                    <FormControl>
                      <Textarea maxLength={2000} data-testid="record-notes-input" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

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
                <Button type="submit" disabled={isSubmitting} data-testid="record-submit">
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Recording...
                    </>
                  ) : (
                    "Record"
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
