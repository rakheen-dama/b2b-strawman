"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Badge } from "@b2mash/ui/badge";
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
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { EmptyState } from "@/components/empty-state";
import { FileText, Loader2, Plus } from "lucide-react";
import { scrollToFirstError } from "@/lib/error-handler";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { formatCurrency } from "@/lib/format";
import { createDealProposalAction } from "@/app/(app)/org/[slug]/pipeline/[id]/actions";
import {
  createDealProposalSchema,
  type CreateDealProposalFormData,
} from "@/lib/schemas/deal-proposal";
import type {
  CreateDealProposalRequest,
  FeeModel,
  LinkedProposalDto,
  ProposalStatus,
} from "@/lib/api/crm";

const STATUS_BADGE: Record<
  ProposalStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" | "warning" }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  SENT: { label: "Sent", variant: "lead" },
  ACCEPTED: { label: "Accepted", variant: "success" },
  DECLINED: { label: "Declined", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "warning" },
};

interface DealProposalsPanelProps {
  slug: string;
  dealId: string;
  proposals: LinkedProposalDto[];
  currency: string;
  canManage: boolean;
}

export function DealProposalsPanel({
  slug,
  dealId,
  proposals,
  currency,
  canManage,
}: DealProposalsPanelProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CreateDealProposalFormData>({
    resolver: zodResolver(createDealProposalSchema),
    defaultValues: { title: "", feeModel: "FIXED", amount: "" },
  });

  const feeModel = form.watch("feeModel");
  // amount only applies to fixed/retainer fee models; hourly/contingency carry none.
  const showAmount = feeModel === "FIXED" || feeModel === "RETAINER";

  function handleOpenChange(o: boolean) {
    setOpen(o);
    if (!o) {
      setError(null);
      form.reset();
    }
  }

  async function onSubmit(values: CreateDealProposalFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const amount =
        values.amount && values.amount.trim() !== "" ? Number(values.amount) : undefined;
      const req: CreateDealProposalRequest = {
        title: values.title.trim(),
        feeModel: values.feeModel as FeeModel,
      };
      if (values.feeModel === "FIXED" && amount != null) {
        req.fixedFeeAmount = amount;
        req.fixedFeeCurrency = currency;
      } else if (values.feeModel === "RETAINER" && amount != null) {
        req.retainerAmount = amount;
        req.retainerCurrency = currency;
      }

      const result = await createDealProposalAction(slug, dealId, req);
      if (result.success) {
        handleOpenChange(false);
        router.refresh();
      } else {
        setError(result.error ?? "Something went wrong.");
        scrollToFirstError();
      }
    } catch {
      setError("A network error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-slate-900 dark:text-slate-100">Proposals</h2>
        {proposals.length > 0 && <Badge variant="neutral">{proposals.length}</Badge>}
      </div>
      {canManage && (
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus className="mr-1.5 size-4" /> New Proposal
        </Button>
      )}
    </div>
  );

  return (
    <div className="space-y-4" data-testid="deal-proposals-panel">
      {header}

      {proposals.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No proposals yet"
          description="Create a proposal to send to the customer and progress this deal."
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Number
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Status
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Amount
                </th>
              </tr>
            </thead>
            <tbody>
              {proposals.map((proposal) => {
                const badge = STATUS_BADGE[proposal.status] ?? {
                  label: proposal.status,
                  variant: "neutral" as const,
                };
                return (
                  <tr
                    key={proposal.id}
                    className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${slug}/proposals/${proposal.id}`}
                        className="font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
                      >
                        {proposal.proposalNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={badge.variant} data-testid="deal-proposal-status-badge">
                        {badge.label}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-slate-600 tabular-nums dark:text-slate-400">
                      {proposal.amount != null ? formatCurrency(proposal.amount, currency) : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>New Proposal</DialogTitle>
            <DialogDescription>Draft a proposal attached to this deal.</DialogDescription>
          </DialogHeader>
          <Form {...form}>
            <form
              id="deal-proposal-form"
              onSubmit={form.handleSubmit(onSubmit, scrollToFirstError)}
              className="space-y-4"
            >
              <FormField
                control={form.control}
                name="title"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Title</FormLabel>
                    <FormControl>
                      <Input placeholder="Proposal title" maxLength={200} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <div className="grid grid-cols-2 gap-3">
                <FormField
                  control={form.control}
                  name="feeModel"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Fee model</FormLabel>
                      <FormControl>
                        <select
                          value={field.value}
                          onChange={field.onChange}
                          className={nativeSelectClassName}
                        >
                          <option value="FIXED">Fixed</option>
                          <option value="HOURLY">Hourly</option>
                          <option value="RETAINER">Retainer</option>
                          <option value="CONTINGENCY">Contingency</option>
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                {showAmount && (
                  <FormField
                    control={form.control}
                    name="amount"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Amount ({currency})</FormLabel>
                        <FormControl>
                          <Input type="number" min={0} step="0.01" {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                )}
              </div>
            </form>
          </Form>
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
            <Button type="submit" form="deal-proposal-form" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
              {isSubmitting ? "Creating..." : "Create Proposal"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
