"use client";

import { useRef, useState, type ComponentProps, type ReactNode, type RefObject } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Ban, Loader2 } from "lucide-react";
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
import { writeOffDisbursementAction } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import { writeOffDisbursementSchema, type WriteOffDisbursementFormData } from "@/lib/schemas/legal";
import { formatCurrency } from "@/lib/format";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

/**
 * Write-off is offered for any UNBILLED disbursement, whatever its approval state — the backend
 * rule (LegalDisbursement.writeOff checks the billing axis only; architecture A14).
 */
export function canOfferWriteOff(disbursement: Pick<DisbursementResponse, "billingStatus">) {
  return disbursement.billingStatus === "UNBILLED";
}

interface DisbursementWriteOffFormDialogProps {
  slug: string;
  disbursement: DisbursementResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onWrittenOff?: (updated: DisbursementResponse) => void;
  /** Element to refocus on close (the owned trigger). Omitted → whatever had focus on open. */
  returnFocusRef?: RefObject<HTMLElement | null>;
}

/** Controlled write-off dialog — hosted by the list/tab parent for the row "…" menus. */
export function DisbursementWriteOffFormDialog({
  slug,
  disbursement,
  open,
  onOpenChange,
  onWrittenOff,
  returnFocusRef,
}: DisbursementWriteOffFormDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const openerRef = useRef<HTMLElement | null>(null);

  const form = useForm<WriteOffDisbursementFormData>({
    resolver: zodResolver(writeOffDisbursementSchema),
    defaultValues: { reason: "" },
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset({ reason: "" });
      setError(null);
    }
  }

  async function handleSubmit(values: WriteOffDisbursementFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await writeOffDisbursementAction(slug, disbursement.id, values.reason);
      if (result.success && result.data) {
        handleOpenChange(false);
        onWrittenOff?.(result.data);
      } else {
        setError(result.error ?? "Failed to write off disbursement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  const inclVat = disbursement.amount + disbursement.vatAmount;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-md"
        data-testid="disbursement-write-off-dialog"
        onOpenAutoFocus={() => {
          // e.g. the row's "…" trigger when opened from a row menu (after the menu has closed).
          openerRef.current =
            document.activeElement instanceof HTMLElement ? document.activeElement : null;
        }}
        onCloseAutoFocus={(event) => {
          // Radix only returns focus to a DialogTrigger, and there is none here. Skip a target
          // that left the DOM (the detail page swaps the trigger for Undo and focuses that).
          const target = returnFocusRef?.current ?? openerRef.current;
          if (target?.isConnected) {
            event.preventDefault();
            target.focus();
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>Write off disbursement</DialogTitle>
          <DialogDescription>
            {disbursement.supplierName} · {formatCurrency(inclVat, "ZAR")}. The firm absorbs this
            cost and it will not appear on a fee note. You can undo this later.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Why is the firm absorbing this cost?"
                      maxLength={5000}
                      data-testid="disbursement-write-off-reason-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && (
              <p role="alert" className="text-sm text-red-600">
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
              <Button
                type="submit"
                variant="destructive"
                disabled={isSubmitting}
                data-testid="disbursement-write-off-confirm-button"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Writing off...
                  </>
                ) : (
                  "Write off"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}

interface DisbursementWriteOffDialogProps {
  slug: string;
  disbursement: DisbursementResponse;
  onWrittenOff?: (updated: DisbursementResponse) => void;
  triggerLabel?: ReactNode;
  triggerVariant?: ComponentProps<typeof Button>["variant"];
  triggerSize?: ComponentProps<typeof Button>["size"];
  triggerClassName?: string;
  /** Lets the host focus the owned trigger (e.g. after an undo swaps it back in). */
  triggerRef?: RefObject<HTMLButtonElement | null>;
}

/** Dialog owns its button (frontend/CLAUDE.md "Dialog Trigger Composition") — detail page. */
export function DisbursementWriteOffDialog({
  slug,
  disbursement,
  onWrittenOff,
  triggerLabel = "Write off",
  triggerVariant = "outline",
  triggerSize = "sm",
  triggerClassName,
  triggerRef: hostTriggerRef,
}: DisbursementWriteOffDialogProps) {
  const [open, setOpen] = useState(false);
  const ownTriggerRef = useRef<HTMLButtonElement>(null);
  const triggerRef = hostTriggerRef ?? ownTriggerRef;

  return (
    <>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        ref={triggerRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
        data-testid="disbursement-write-off-button"
      >
        <Ban className="mr-1.5 size-4" />
        {triggerLabel}
      </Button>
      <DisbursementWriteOffFormDialog
        slug={slug}
        disbursement={disbursement}
        open={open}
        onOpenChange={setOpen}
        onWrittenOff={onWrittenOff}
        returnFocusRef={triggerRef}
      />
    </>
  );
}
