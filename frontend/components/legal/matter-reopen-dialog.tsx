"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
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
import { Textarea } from "@/components/ui/textarea";
import {
  reopenMatterSchema,
  type ReopenMatterFormData,
} from "@/lib/schemas/matter-reopen";
import { reopenMatterAction } from "@/app/(app)/org/[slug]/projects/[id]/matter-reopen-actions";

interface MatterReopenDialogProps {
  slug: string;
  projectId: string;
  projectName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function MatterReopenDialog({
  slug,
  projectId,
  projectName,
  open,
  onOpenChange,
}: MatterReopenDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const form = useForm<ReopenMatterFormData>({
    resolver: zodResolver(reopenMatterSchema),
    defaultValues: { notes: "" },
  });

  useEffect(() => {
    if (!open) return;
    setSubmitError(null);
    form.reset({ notes: "" });
  }, [open, projectId, form]);

  function handleOpenChange(nextOpen: boolean) {
    onOpenChange(nextOpen);
    if (!nextOpen) {
      setSubmitError(null);
      setIsSubmitting(false);
    }
  }

  async function onSubmit(values: ReopenMatterFormData) {
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const result = await reopenMatterAction(
        slug,
        projectId,
        values.notes.trim()
      );
      if (result.success) {
        toast.success("Matter reopened");
        // Server action revalidates the project detail + list routes;
        // RSC re-renders automatically.
        handleOpenChange(false);
        return;
      }
      if (result.kind === "retention_elapsed") {
        toast.error(result.error);
        setSubmitError(result.error);
        return;
      }
      if (result.kind === "forbidden") {
        const message =
          result.error ?? "You do not have permission to reopen this matter.";
        toast.error(message);
        setSubmitError(message);
        return;
      }
      setSubmitError(result.error);
    } catch {
      setSubmitError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        className="sm:max-w-md"
        data-testid="matter-reopen-dialog"
      >
        <DialogHeader>
          <DialogTitle>Reopen matter</DialogTitle>
          <DialogDescription>
            Reopen{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {projectName}
            </span>
            . Provide a brief note explaining why this matter is being reopened.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="notes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Reopen notes{" "}
                    <span className="text-xs font-normal text-slate-500">
                      (≥ 10 characters)
                    </span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      maxLength={5000}
                      rows={4}
                      placeholder="Why is this matter being reopened?"
                      data-testid="matter-reopen-notes-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {submitError && (
              <p
                role="alert"
                className="text-destructive text-sm"
                data-testid="matter-reopen-error"
              >
                {submitError}
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
                disabled={isSubmitting}
                data-testid="matter-reopen-confirm-btn"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Reopening...
                  </>
                ) : (
                  "Reopen matter"
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
