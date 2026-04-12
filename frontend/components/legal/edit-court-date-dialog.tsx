"use client";

import { useEffect, useState } from "react";
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
import { editCourtDateSchema, type EditCourtDateFormData } from "@/lib/schemas/legal";
import { updateCourtDate } from "@/app/(app)/org/[slug]/court-calendar/actions";
import type { CourtDate } from "@/lib/types";

interface EditCourtDateDialogProps {
  slug: string;
  courtDate: CourtDate;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

const DATE_TYPES = [
  { value: "HEARING", label: "Hearing" },
  { value: "TRIAL", label: "Trial" },
  { value: "MOTION", label: "Motion" },
  { value: "MEDIATION", label: "Mediation" },
  { value: "ARBITRATION", label: "Arbitration" },
  { value: "PRE_TRIAL", label: "Pre-Trial" },
  { value: "CASE_MANAGEMENT", label: "Case Management" },
  { value: "TAXATION", label: "Taxation" },
  { value: "OTHER", label: "Other" },
] as const;

export function EditCourtDateDialog({
  slug,
  courtDate,
  open,
  onOpenChange,
  onSuccess,
}: EditCourtDateDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<EditCourtDateFormData>({
    resolver: zodResolver(editCourtDateSchema),
    defaultValues: {
      dateType: courtDate.dateType,
      scheduledDate: courtDate.scheduledDate,
      scheduledTime: courtDate.scheduledTime ?? "",
      courtName: courtDate.courtName,
      courtReference: courtDate.courtReference ?? "",
      judgeMagistrate: courtDate.judgeMagistrate ?? "",
      description: courtDate.description ?? "",
      reminderDays: courtDate.reminderDays,
    },
  });

  // Reset form values when courtDate changes
  useEffect(() => {
    if (open) {
      form.reset({
        dateType: courtDate.dateType,
        scheduledDate: courtDate.scheduledDate,
        scheduledTime: courtDate.scheduledTime ?? "",
        courtName: courtDate.courtName,
        courtReference: courtDate.courtReference ?? "",
        judgeMagistrate: courtDate.judgeMagistrate ?? "",
        description: courtDate.description ?? "",
        reminderDays: courtDate.reminderDays,
      });
      setError(null);
    }
  }, [open, courtDate, form]);

  async function onSubmit(values: EditCourtDateFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateCourtDate(slug, courtDate.id, {
        dateType: values.dateType,
        scheduledDate: values.scheduledDate,
        scheduledTime: values.scheduledTime || undefined,
        courtName: values.courtName,
        courtReference: values.courtReference || undefined,
        judgeMagistrate: values.judgeMagistrate || undefined,
        description: values.description || undefined,
        reminderDays: values.reminderDays,
      });
      if (result.success) {
        onOpenChange(false);
        onSuccess?.();
      } else {
        setError(result.error ?? "Failed to update court date");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="edit-court-date-dialog">
        <DialogHeader>
          <DialogTitle>Edit Court Date</DialogTitle>
          <DialogDescription>Update the details for this court date.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="dateType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Type</FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300"
                    >
                      {DATE_TYPES.map((dt) => (
                        <option key={dt.value} value={dt.value}>
                          {dt.label}
                        </option>
                      ))}
                    </select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="scheduledDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Date</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="scheduledTime"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Time <span className="font-normal text-slate-500">(optional)</span>
                    </FormLabel>
                    <FormControl>
                      <Input type="time" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="courtName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Court Name</FormLabel>
                  <FormControl>
                    <Input placeholder="e.g. Johannesburg High Court" maxLength={255} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="courtReference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Court Reference <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="Case number or reference" maxLength={255} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="judgeMagistrate"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Judge / Magistrate{" "}
                    <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input maxLength={255} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Description <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="Additional details..."
                      maxLength={2000}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reminderDays"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Reminder (days before)</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      min={0}
                      max={365}
                      {...field}
                      onChange={(e) =>
                        field.onChange(e.target.value === "" ? 7 : Number(e.target.value))
                      }
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
                {isSubmitting ? "Saving..." : "Save Changes"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
