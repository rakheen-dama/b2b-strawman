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
import { resolveConflictSchema, type ResolveConflictFormData } from "@/lib/schemas/legal";
import { resolveConflict } from "@/app/(app)/org/[slug]/conflict-check/actions";

const RESOLUTIONS = [
  { value: "PROCEED", label: "Proceed" },
  { value: "DECLINED", label: "Decline" },
  { value: "WAIVER_OBTAINED", label: "Obtain Waiver" },
  { value: "REFERRED", label: "Refer" },
] as const;

interface ResolveConflictDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  conflictCheckId: string;
  slug: string;
  onSuccess?: () => void;
}

export function ResolveConflictDialog({
  open,
  onOpenChange,
  conflictCheckId,
  slug,
  onSuccess,
}: ResolveConflictDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<ResolveConflictFormData>({
    resolver: zodResolver(resolveConflictSchema),
    defaultValues: {
      resolution: "PROCEED",
      resolutionNotes: "",
      waiverDocumentId: "",
    },
  });

  async function onSubmit(values: ResolveConflictFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await resolveConflict(slug, conflictCheckId, {
        resolution: values.resolution,
        resolutionNotes: values.resolutionNotes || undefined,
        waiverDocumentId: values.waiverDocumentId || undefined,
      });
      if (result.success) {
        form.reset();
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to resolve conflict");
      }
    } catch {
      setError("An unexpected error occurred");
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
      <DialogContent data-testid="resolve-conflict-dialog">
        <DialogHeader>
          <DialogTitle>Resolve Conflict</DialogTitle>
          <DialogDescription>Choose how to resolve this conflict of interest.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="resolution"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Resolution *</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none dark:border-slate-800 dark:focus-visible:ring-slate-300"
                    >
                      {RESOLUTIONS.map((r) => (
                        <option key={r.value} value={r.value}>
                          {r.label}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="resolutionNotes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Notes</FormLabel>
                  <FormControl>
                    <Textarea placeholder="Add resolution notes..." rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {error && <p className="text-sm text-red-600">{error}</p>}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Resolving..." : "Resolve"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
