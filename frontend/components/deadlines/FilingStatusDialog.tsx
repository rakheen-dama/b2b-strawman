"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
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
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { updateFilingStatus } from "@/app/(app)/org/[slug]/deadlines/actions";
import type { CalculatedDeadline } from "@/lib/types";

const dialogFormSchema = z.object({
  notes: z.string().max(1000, "Notes must be 1000 characters or less").optional().or(z.literal("")),
  referenceNumber: z.string().max(100, "Reference must be 100 characters or less").optional().or(z.literal("")),
});

type DialogFormData = z.infer<typeof dialogFormSchema>;

function derivePeriodKey(dueDate: string): string {
  return dueDate.substring(0, 4);
}

function buildCombinedNotes(referenceNumber?: string, notes?: string): string | undefined {
  const ref = referenceNumber?.trim();
  const note = notes?.trim();
  if (ref && note) return `[Ref: ${ref}] ${note}`;
  if (ref) return `[Ref: ${ref}]`;
  if (note) return note;
  return undefined;
}

interface FilingStatusDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  deadlines: CalculatedDeadline[];
  slug: string;
  onSuccess: () => void;
}

export function FilingStatusDialog({
  open,
  onOpenChange,
  deadlines,
  slug,
  onSuccess,
}: FilingStatusDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<DialogFormData>({
    resolver: zodResolver(dialogFormSchema),
    defaultValues: {
      notes: "",
      referenceNumber: "",
    },
  });

  async function onSubmit(values: DialogFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const combinedNotes = buildCombinedNotes(values.referenceNumber, values.notes);
      const items = deadlines.map((deadline) => ({
        customerId: deadline.customerId,
        deadlineTypeSlug: deadline.deadlineTypeSlug,
        periodKey: derivePeriodKey(deadline.dueDate),
        status: "filed" as const,
        notes: combinedNotes,
      }));

      const result = await updateFilingStatus(slug, items);
      if (result.success) {
        form.reset();
        onOpenChange(false);
        onSuccess();
      } else {
        setError(result.error ?? "Failed to update filing status.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
      form.reset();
    }
    onOpenChange(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Mark as Filed</DialogTitle>
          {deadlines.length > 1 && (
            <DialogDescription>
              Filing {deadlines.length} deadlines
            </DialogDescription>
          )}
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="referenceNumber"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Reference Number{" "}
                    <span className="font-normal text-muted-foreground">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      maxLength={100}
                      placeholder="e.g. SARS-2026-001"
                      {...field}
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
                  <FormLabel>
                    Notes{" "}
                    <span className="font-normal text-muted-foreground">
                      (optional)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      maxLength={1000}
                      rows={3}
                      placeholder="Additional notes about this filing..."
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {error && <p className="text-sm text-destructive">{error}</p>}
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
                {isSubmitting ? "Filing..." : "Mark as Filed"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
