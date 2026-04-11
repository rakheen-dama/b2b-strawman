"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/components/ui/button";
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
import { cancelCourtDateSchema, type CancelCourtDateFormData } from "@/lib/schemas/legal";
import { cancelCourtDate } from "@/app/(app)/org/[slug]/court-calendar/actions";

interface CancelCourtDateDialogProps {
  slug: string;
  courtDateId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function CancelCourtDateDialog({
  slug,
  courtDateId,
  open,
  onOpenChange,
  onSuccess,
}: CancelCourtDateDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CancelCourtDateFormData>({
    resolver: zodResolver(cancelCourtDateSchema),
    defaultValues: {
      reason: "",
    },
  });

  async function onSubmit(values: CancelCourtDateFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await cancelCourtDate(slug, courtDateId, values);
      if (result.success) {
        form.reset();
        onOpenChange(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to cancel court date");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (v) {
          setError(null);
          form.reset();
        }
        onOpenChange(v);
      }}
    >
      <DialogContent data-testid="cancel-court-date-dialog">
        <DialogHeader>
          <DialogTitle>Cancel Court Date</DialogTitle>
          <DialogDescription>Provide a reason for cancelling this court date.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Reason for cancellation..."
                      maxLength={2000}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-sm text-red-600">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => onOpenChange(false)}
                disabled={isSubmitting}
              >
                Keep
              </Button>
              <Button type="submit" variant="destructive" disabled={isSubmitting}>
                {isSubmitting ? "Cancelling..." : "Cancel Court Date"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
