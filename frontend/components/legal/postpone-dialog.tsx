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
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { postponeCourtDateSchema, type PostponeCourtDateFormData } from "@/lib/schemas/legal";
import { postponeCourtDate } from "@/app/(app)/org/[slug]/court-calendar/actions";

interface PostponeDialogProps {
  slug: string;
  courtDateId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function PostponeDialog({
  slug,
  courtDateId,
  open,
  onOpenChange,
  onSuccess,
}: PostponeDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<PostponeCourtDateFormData>({
    resolver: zodResolver(postponeCourtDateSchema),
    defaultValues: {
      newDate: "",
      reason: "",
    },
  });

  async function onSubmit(values: PostponeCourtDateFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await postponeCourtDate(slug, courtDateId, values);
      if (result.success) {
        form.reset();
        onOpenChange(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to postpone court date");
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
      <DialogContent data-testid="postpone-dialog">
        <DialogHeader>
          <DialogTitle>Postpone Court Date</DialogTitle>
          <DialogDescription>
            Select a new date and provide a reason for the postponement.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="newDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>New Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Reason for postponement..."
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
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Postponing..." : "Postpone"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
