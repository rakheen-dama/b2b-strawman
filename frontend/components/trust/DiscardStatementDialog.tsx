"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Trash2 } from "lucide-react";
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
import { discardBankStatement } from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { discardStatementSchema, type DiscardStatementFormData } from "@/lib/schemas/trust";
import { useIdempotencyKey } from "@/hooks/use-idempotency-key";
import { UnsettledSubmitNotice } from "./unsettled-submit-notice";

interface DiscardStatementDialogProps {
  statementId: string;
  fileName: string;
}

/**
 * FU-6b (F-652C-1): discards an imported bank statement — the statement left behind when the
 * reconciliation wizard refused it, or one uploaded in error. The statement is retired with the
 * reason given and kept for the audit trail; it no longer counts in the period checks or in the
 * unresolved statement lines. A refusal (a line recorded from it, a reconciliation that uses it)
 * shows the server's reason. The dialog owns its trigger button (Dialog Trigger Composition).
 */
export function DiscardStatementDialog({ statementId, fileName }: DiscardStatementDialogProps) {
  const idempotency = useIdempotencyKey(["discardBankStatement", statementId]);
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<DiscardStatementFormData>({
    resolver: zodResolver(discardStatementSchema),
    defaultValues: { reason: "" },
  });

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      form.reset();
      setError(null);
    }
  }

  async function handleSubmit(data: DiscardStatementFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await idempotency.run(
        ["discardBankStatement", statementId, data.reason],
        (idempotencyKey) => discardBankStatement(statementId, data.reason, idempotencyKey)
      );
      if (result.success) {
        handleOpenChange(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to discard the statement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => setOpen(true)}
        data-testid={`discard-statement-${statementId}`}
      >
        <Trash2 className="mr-1 size-3.5" />
        Discard
      </Button>
      <DialogContent className="sm:max-w-md" data-testid="discard-statement-dialog">
        <DialogHeader>
          <DialogTitle>Discard statement?</DialogTitle>
          <DialogDescription>
            {fileName} is retired: it is no longer used for reconciliation and its lines stop
            counting as unresolved. Matched lines are released. The statement and its lines are kept
            for the audit trail.
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
                      placeholder="Why this statement is discarded"
                      maxLength={500}
                      data-testid="discard-reason-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <UnsettledSubmitNotice show={idempotency.unsettled} />
            {error && (
              <p className="text-destructive text-sm" role="alert" data-testid="discard-error">
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
                data-testid="discard-statement-confirm"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Discarding...
                  </>
                ) : (
                  "Discard statement"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
