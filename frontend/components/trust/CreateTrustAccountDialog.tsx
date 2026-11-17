"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { Textarea } from "@/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { createTrustAccountSchema, type CreateTrustAccountFormData } from "@/lib/schemas/trust";
import { createTrustAccount } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import type { InvestmentBasis, TrustAccount } from "@/lib/types/trust";

/** 648A: the Place Investment dialog opens this one to add an investment account of its basis. */
export interface InvestmentAccountPreset {
  accountType: "INVESTMENT";
  investmentBasis: InvestmentBasis;
}

interface CreateTrustAccountDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
  /** Locks the type and basis selects to these values (and the account is not primary). */
  preset?: InvestmentAccountPreset;
  /** Called with the created account (648A: the Place Investment dialog selects it). */
  onCreated?: (account: TrustAccount) => void;
}

function getTodayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

function getDefaultValues(preset?: InvestmentAccountPreset): CreateTrustAccountFormData {
  return {
    accountName: "",
    bankName: "",
    branchCode: "",
    accountNumber: "",
    accountType: preset?.accountType ?? "GENERAL",
    investmentBasis: preset?.investmentBasis,
    openedDate: getTodayIso(),
    isPrimary: preset === undefined,
    requireDualApproval: false,
    paymentApprovalThreshold: undefined,
    notes: "",
  };
}

export function CreateTrustAccountDialog({
  open,
  onOpenChange,
  onSuccess,
  preset,
  onCreated,
}: CreateTrustAccountDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CreateTrustAccountFormData>({
    resolver: zodResolver(createTrustAccountSchema),
    defaultValues: getDefaultValues(preset),
  });
  const accountType = useWatch({ control: form.control, name: "accountType" });

  function handleOpenChange(next: boolean) {
    onOpenChange(next);
    if (!next) {
      form.reset(getDefaultValues(preset));
      setError(null);
    }
  }

  async function handleSubmit(values: CreateTrustAccountFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createTrustAccount({
        accountName: values.accountName,
        bankName: values.bankName,
        branchCode: values.branchCode,
        accountNumber: values.accountNumber,
        accountType: values.accountType,
        isPrimary: values.isPrimary ?? false,
        requireDualApproval: values.requireDualApproval,
        paymentApprovalThreshold:
          values.paymentApprovalThreshold != null && !Number.isNaN(values.paymentApprovalThreshold)
            ? values.paymentApprovalThreshold
            : null,
        openedDate: values.openedDate,
        notes: values.notes && values.notes.trim().length > 0 ? values.notes : undefined,
        // ADR-379: only an investment account carries a basis (the backend refuses it otherwise)
        investmentBasis: values.accountType === "INVESTMENT" ? values.investmentBasis : undefined,
      });

      if (result.success) {
        onCreated?.(result.account);
        onSuccess?.();
        handleOpenChange(false);
        router.refresh();
      } else {
        setError(result.error);
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="max-h-[90vh] overflow-y-auto sm:max-w-lg"
        data-testid="create-trust-account-dialog"
      >
        <DialogHeader>
          <DialogTitle>{preset ? "Add Investment Account" : "Add Trust Account"}</DialogTitle>
          <DialogDescription>
            {preset
              ? "Register the investment account the trust money will be placed in."
              : "Register a new trust bank account for the firm. This account will be available for receipts, payments, and reconciliation."}
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(handleSubmit)}
            className="space-y-4"
            data-testid="create-trust-account-form"
          >
            <FormField
              control={form.control}
              name="accountName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Account Name</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. Mathebula Trust Account" maxLength={200} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="bankName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Bank Name</FormLabel>
                    <FormControl>
                      <Input placeholder="e.g. Standard Bank" maxLength={200} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="branchCode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Branch Code</FormLabel>
                    <FormControl>
                      <Input placeholder="e.g. 051001" maxLength={20} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="accountNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Account Number</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. 1234567890" maxLength={30} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                control={form.control}
                name="accountType"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Account Type</FormLabel>
                    <Select
                      onValueChange={(value) => {
                        field.onChange(value);
                        if (value !== "INVESTMENT") {
                          form.setValue("investmentBasis", undefined);
                        }
                      }}
                      value={field.value}
                      disabled={preset !== undefined}
                    >
                      <FormControl>
                        <SelectTrigger data-testid="trust-account-type-select">
                          <SelectValue placeholder="Select account type" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="GENERAL">General</SelectItem>
                        <SelectItem value="INVESTMENT">Investment</SelectItem>
                        <SelectItem value="SECTION_86">Section 86 Trust Account</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="openedDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Opened Date</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {accountType === "INVESTMENT" && (
              <FormField
                control={form.control}
                name="investmentBasis"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Investment basis</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value ?? ""}
                      disabled={preset !== undefined}
                    >
                      <FormControl>
                        <SelectTrigger data-testid="trust-account-basis-select" className="w-full">
                          <SelectValue placeholder="Select the basis" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="FIRM_DISCRETION">
                          Section 86(3) — the firm invests surplus trust money
                        </SelectItem>
                        <SelectItem value="CLIENT_INSTRUCTION">
                          Section 86(4) — invested on a client&apos;s instruction
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    <FormDescription>
                      Interest on a section 86(3) investment belongs to the Legal
                      Practitioners&apos; Fidelity Fund. On a section 86(4) investment the client
                      receives 95% and the Fund 5%.
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={form.control}
              name="isPrimary"
              render={({ field }) => (
                <FormItem className="flex flex-row items-start gap-3 space-y-0">
                  <FormControl>
                    <Checkbox
                      checked={field.value ?? false}
                      onCheckedChange={(v) => field.onChange(v === true)}
                    />
                  </FormControl>
                  <div className="space-y-0.5">
                    <FormLabel className="text-sm font-medium">
                      Set as primary trust account
                    </FormLabel>
                    <p className="text-muted-foreground text-xs">
                      The primary account is used by default for dashboards and reporting.
                    </p>
                  </div>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="requireDualApproval"
              render={({ field }) => (
                <FormItem className="flex flex-row items-start gap-3 space-y-0">
                  <FormControl>
                    <Checkbox
                      checked={field.value}
                      onCheckedChange={(v) => field.onChange(v === true)}
                    />
                  </FormControl>
                  <div className="space-y-0.5">
                    <FormLabel className="text-sm font-medium">Require dual approval</FormLabel>
                    <p className="text-muted-foreground text-xs">
                      When enabled, trust payments need two approvers before release.
                    </p>
                  </div>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="paymentApprovalThreshold"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Payment Approval Threshold (ZAR)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder="Optional — leave blank for no threshold"
                      value={field.value ?? ""}
                      onChange={(e) => {
                        const raw = e.target.value;
                        field.onChange(raw === "" ? undefined : parseFloat(raw));
                      }}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Notes (Optional)</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Internal notes about this trust account"
                      maxLength={2000}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && (
              <p className="text-destructive text-sm" role="alert">
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
                    Creating...
                  </>
                ) : (
                  "Create Account"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
