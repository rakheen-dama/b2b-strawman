"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
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
import { addLpffRateSchema, type AddLpffRateFormData } from "@/lib/schemas/trust";
import { addLpffRate } from "@/app/(app)/org/[slug]/trust-accounting/interest/actions";

interface LpffRateDialogProps {
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function getDefaultValues(): AddLpffRateFormData {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return {
    effectiveFrom: local.toISOString().slice(0, 10),
    ratePercent: 0,
    lpffSharePercent: 0,
    notes: "",
  };
}

export function LpffRateDialog({ accountId, open, onOpenChange, onSuccess }: LpffRateDialogProps) {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<AddLpffRateFormData>({
    resolver: zodResolver(addLpffRateSchema),
    defaultValues: getDefaultValues(),
  });

  function handleOpenChange(newOpen: boolean) {
    onOpenChange(newOpen);
    if (!newOpen) {
      form.reset(getDefaultValues());
      setError(null);
    }
  }

  async function handleSubmit(data: AddLpffRateFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await addLpffRate(accountId, data);
      if (result.success) {
        onSuccess();
        router.refresh();
        handleOpenChange(false);
      } else {
        setError(result.error ?? "Failed to add LPFF rate");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg" data-testid="lpff-rate-dialog">
        <DialogHeader>
          <DialogTitle>Add LPFF Rate</DialogTitle>
          <DialogDescription>
            Set a new Lawyers Professional Fidelity Fund rate for interest calculations.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="effectiveFrom"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Effective From</FormLabel>
                  <FormControl>
                    <Input type="date" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="ratePercent"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Rate %</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.001"
                      min="0.001"
                      placeholder="e.g. 8.5"
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
              name="lpffSharePercent"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>LPFF Share %</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="0.1"
                      min="0"
                      max="100"
                      placeholder="e.g. 50"
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
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Notes (optional)</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Optional notes about this rate change"
                      maxLength={500}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

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
                    Adding...
                  </>
                ) : (
                  "Add Rate"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
