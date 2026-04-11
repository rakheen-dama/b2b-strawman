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
import {
  interruptPrescriptionSchema,
  type InterruptPrescriptionFormData,
} from "@/lib/schemas/legal";
import { interruptPrescription } from "@/app/(app)/org/[slug]/court-calendar/actions";

interface InterruptDialogProps {
  slug: string;
  prescriptionId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function InterruptDialog({
  slug,
  prescriptionId,
  open,
  onOpenChange,
  onSuccess,
}: InterruptDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<InterruptPrescriptionFormData>({
    resolver: zodResolver(interruptPrescriptionSchema),
    defaultValues: {
      interruptionDate: "",
      interruptionReason: "",
    },
  });

  async function onSubmit(values: InterruptPrescriptionFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await interruptPrescription(slug, prescriptionId, values);
      if (result.success) {
        form.reset();
        onOpenChange(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to interrupt prescription");
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
      <DialogContent data-testid="interrupt-dialog">
        <DialogHeader>
          <DialogTitle>Interrupt Prescription</DialogTitle>
          <DialogDescription>
            Record an interruption event for this prescription period.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="interruptionDate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Interruption Date</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="interruptionReason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reason</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Describe the interruption event..."
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
                {isSubmitting ? "Recording..." : "Record Interruption"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
