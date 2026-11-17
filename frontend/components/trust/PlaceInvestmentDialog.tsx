"use client";

import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
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
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@b2mash/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Section866Advisory } from "@/components/trust/Section866Advisory";
import { CreateTrustAccountDialog } from "@/components/trust/CreateTrustAccountDialog";
import { placeInvestmentSchema, type PlaceInvestmentFormData } from "@/lib/schemas/trust";
import type { InvestmentBasis, TrustAccount } from "@/lib/types/trust";
import { placeInvestment } from "@/app/(app)/org/[slug]/trust-accounting/investments/actions";

const INVESTMENT_BASIS_HELP: Record<InvestmentBasis, string> = {
  FIRM_DISCRETION:
    "Interest on a section 86(3) investment belongs to the Legal Practitioners\u2019 Fidelity Fund.",
  CLIENT_INSTRUCTION: "Interest paid to client, with 5% to the LPFF (Section 86(5)).",
};

const SECTION: Record<InvestmentBasis, string> = {
  FIRM_DISCRETION: "section 86(3)",
  CLIENT_INSTRUCTION: "section 86(4)",
};

interface PlaceInvestmentDialogProps {
  /** The funding trust banking account the money leaves. */
  accountId: string;
  /** The firm's investment accounts; the picker offers those of the chosen basis (648A). */
  investmentAccounts: TrustAccount[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function getDefaultValues(): PlaceInvestmentFormData {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return {
    investmentBasis: "FIRM_DISCRETION",
    investmentAccountId: "",
    customerId: "",
    projectId: "",
    institution: "",
    accountNumber: "",
    principal: 0,
    interestRate: 0,
    depositDate: local.toISOString().slice(0, 10),
    maturityDate: "",
    notes: "",
  };
}

export function PlaceInvestmentDialog({
  accountId,
  investmentAccounts,
  open,
  onOpenChange,
  onSuccess,
}: PlaceInvestmentDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [addAccountOpen, setAddAccountOpen] = useState(false);
  // Accounts added from this dialog, until the page refresh brings them in with the rest
  const [addedAccounts, setAddedAccounts] = useState<TrustAccount[]>([]);

  const form = useForm<PlaceInvestmentFormData>({
    resolver: zodResolver(placeInvestmentSchema),
    defaultValues: getDefaultValues(),
  });
  const basis = useWatch({ control: form.control, name: "investmentBasis" });

  const accountsOfBasis = [
    ...investmentAccounts,
    ...addedAccounts.filter((added) => !investmentAccounts.some((a) => a.id === added.id)),
  ].filter(
    (a) => a.accountType === "INVESTMENT" && a.status === "ACTIVE" && a.investmentBasis === basis
  );

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset(getDefaultValues());
      setError(null);
    }
  }

  /** A new basis needs an account of that basis; section 86(3) names no client or matter. */
  function handleBasisChange(next: InvestmentBasis) {
    form.setValue("investmentBasis", next);
    form.setValue("investmentAccountId", "");
    form.setValue("customerId", "");
    form.setValue("projectId", "");
    form.clearErrors(["investmentAccountId", "customerId", "projectId"]);
  }

  function handleAccountCreated(account: TrustAccount) {
    setAddedAccounts((previous) => [...previous, account]);
    // The basis may have changed while the account was being created: select it only if it still
    // fits the placement (the picker lists it under its own basis either way)
    if (account.investmentBasis === form.getValues("investmentBasis")) {
      form.setValue("investmentAccountId", account.id, { shouldValidate: true });
    }
  }

  async function handleSubmit(data: PlaceInvestmentFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await placeInvestment(accountId, data);
      if (result.success) {
        onSuccess();
        router.refresh();
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to place investment");
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
        data-testid="place-investment-dialog"
      >
        <DialogHeader>
          <DialogTitle>Place Investment</DialogTitle>
          <DialogDescription>
            Place trust money from this account into an investment account.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="investmentBasis"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Investment basis</FormLabel>
                  <FormControl>
                    <RadioGroup
                      value={field.value}
                      onValueChange={(value) => handleBasisChange(value as InvestmentBasis)}
                      data-testid="investment-basis-radio"
                    >
                      <div className="flex items-center gap-2">
                        <RadioGroupItem value="FIRM_DISCRETION" id="basis-firm" />
                        <Label htmlFor="basis-firm" className="font-normal">
                          Section 86(3) — the firm invests surplus trust money
                        </Label>
                      </div>
                      <div className="flex items-center gap-2">
                        <RadioGroupItem value="CLIENT_INSTRUCTION" id="basis-client" />
                        <Label htmlFor="basis-client" className="font-normal">
                          Section 86(4) — invested on a client&apos;s instruction
                        </Label>
                      </div>
                    </RadioGroup>
                  </FormControl>
                  <p
                    className="text-xs text-slate-500 dark:text-slate-400"
                    data-testid="investment-basis-help"
                  >
                    {INVESTMENT_BASIS_HELP[field.value]}
                  </p>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="investmentAccountId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Investment account</FormLabel>
                  {/* Radix reports "" when the value names an item its hidden native select
                      has not rendered yet (a just-added account); the value is cleared only
                      by a basis change, never through here. */}
                  <Select
                    onValueChange={(value) => {
                      if (value) field.onChange(value);
                    }}
                    value={field.value}
                  >
                    <FormControl>
                      <SelectTrigger data-testid="investment-account-select" className="w-full">
                        <SelectValue placeholder="Select the investment account" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {accountsOfBasis.map((a) => (
                        <SelectItem key={a.id} value={a.id}>
                          {a.accountName} ({a.bankName} {a.accountNumber})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {accountsOfBasis.length === 0 && (
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      The firm has no {SECTION[basis]} investment account yet.
                    </p>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="w-fit"
                    onClick={() => setAddAccountOpen(true)}
                    data-testid="add-investment-account"
                  >
                    Add investment account
                  </Button>
                  <FormMessage />
                </FormItem>
              )}
            />

            {basis === "CLIENT_INSTRUCTION" && (
              <>
                <FormField
                  control={form.control}
                  name="customerId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Client ID</FormLabel>
                      <FormControl>
                        <Input placeholder="Client UUID" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="projectId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Matter ID</FormLabel>
                      <FormControl>
                        <Input placeholder="Matter UUID" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            )}

            <FormField
              control={form.control}
              name="institution"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Institution</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. FNB Money Market" maxLength={200} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="accountNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Account Number</FormLabel>
                  <FormControl>
                    <Input placeholder="Investment account number" maxLength={50} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="principal"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Principal</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="0.00"
                      {...field}
                      onChange={(e) => field.onChange(parseFloat(e.target.value) || 0)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="interestRate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Interest Rate %</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.01"
                      min="0"
                      max="100"
                      placeholder="e.g. 7.5"
                      {...field}
                      onChange={(e) => field.onChange(parseFloat(e.target.value) || 0)}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="depositDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Deposit Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="maturityDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Maturity Date (optional)</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
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
                  <FormLabel>Notes (optional)</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Optional notes about this investment"
                      maxLength={2000}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Section866Advisory />

            {error && <p className="text-destructive text-sm">{error}</p>}

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
                    Placing...
                  </>
                ) : (
                  "Place Investment"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
      {/* 648A: opened from the picker, preset to the chosen basis; re-mounted per basis */}
      <CreateTrustAccountDialog
        key={basis}
        open={addAccountOpen}
        onOpenChange={setAddAccountOpen}
        preset={{ accountType: "INVESTMENT", investmentBasis: basis }}
        onCreated={handleAccountCreated}
      />
    </Dialog>
  );
}
