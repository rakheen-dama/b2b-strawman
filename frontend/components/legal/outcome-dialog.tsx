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
import { outcomeSchema, type OutcomeFormData } from "@/lib/schemas/legal";
import { recordOutcome } from "@/app/(app)/org/[slug]/court-calendar/actions";

interface OutcomeDialogProps {
  slug: string;
  courtDateId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function OutcomeDialog({
  slug,
  courtDateId,
  open,
  onOpenChange,
  onSuccess,
}: OutcomeDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<OutcomeFormData>({
    resolver: zodResolver(outcomeSchema),
    defaultValues: {
      outcome: "",
    },
  });

  async function onSubmit(values: OutcomeFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await recordOutcome(slug, courtDateId, values);
      if (result.success) {
        form.reset();
        onOpenChange(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to record outcome");
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
      <DialogContent data-testid="outcome-dialog">
        <DialogHeader>
          <DialogTitle>Record Outcome</DialogTitle>
          <DialogDescription>Record the outcome of this court date hearing.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="outcome"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Outcome</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Describe the outcome of the hearing..."
                      maxLength={2000}
                      rows={4}
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
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Recording..." : "Record Outcome"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
