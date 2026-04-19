"use client";

import { useEffect, useMemo, useRef, useState } from "react";
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
import {
  editDisbursementSchema,
  type EditDisbursementFormData,
} from "@/lib/schemas/legal";
import {
  DISBURSEMENT_CATEGORY_OPTIONS,
  VAT_TREATMENT_OPTIONS,
  defaultVatTreatmentForCategory,
} from "@/lib/legal/disbursement-defaults";
import { updateDisbursementAction } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

interface EditDisbursementDialogProps {
  slug: string;
  disbursement: DisbursementResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (updated: DisbursementResponse) => void;
}

export function EditDisbursementDialog({
  slug,
  disbursement,
  open,
  onOpenChange,
  onSuccess,
}: EditDisbursementDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const vatUserTouched = useRef(false);

  const form = useForm<EditDisbursementFormData>({
    resolver: zodResolver(editDisbursementSchema),
    defaultValues: {
      category: disbursement.category,
      description: disbursement.description,
      amount: disbursement.amount,
      vatTreatment: disbursement.vatTreatment,
      incurredDate: disbursement.incurredDate,
      supplierName: disbursement.supplierName,
      supplierReference: disbursement.supplierReference ?? "",
    },
  });

  const selectedCategory = form.watch("category");

  useEffect(() => {
    if (open) {
      vatUserTouched.current = false;
      form.reset({
        category: disbursement.category,
        description: disbursement.description,
        amount: disbursement.amount,
        vatTreatment: disbursement.vatTreatment,
        incurredDate: disbursement.incurredDate,
        supplierName: disbursement.supplierName,
        supplierReference: disbursement.supplierReference ?? "",
      });
      setError(null);
    }
  }, [open, disbursement, form]);

  useEffect(() => {
    if (open && !vatUserTouched.current) {
      form.setValue("vatTreatment", defaultVatTreatmentForCategory(selectedCategory));
    }
  }, [selectedCategory, open, form]);

  const selectClass = useMemo(
    () =>
      "flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-slate-500 focus-visible:ring-1 focus-visible:ring-slate-950 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:placeholder:text-slate-400 dark:focus-visible:ring-slate-300",
    []
  );

  async function onSubmit(values: EditDisbursementFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const result = await updateDisbursementAction(slug, disbursement.id, {
        category: values.category,
        description: values.description,
        amount: values.amount,
        vatTreatment: values.vatTreatment,
        incurredDate: values.incurredDate,
        supplierName: values.supplierName,
        supplierReference:
          values.supplierReference && values.supplierReference !== ""
            ? values.supplierReference
            : null,
      });
      if (result.success && result.data) {
        onSuccess?.(result.data);
        onOpenChange(false);
      } else {
        setError(result.error ?? "Failed to update disbursement");
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="edit-disbursement-dialog" className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Edit Disbursement</DialogTitle>
          <DialogDescription>Update disbursement details before approval.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="category"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Category</FormLabel>
                  <FormControl>
                    <select value={field.value} onChange={field.onChange} className={selectClass}>
                      {DISBURSEMENT_CATEGORY_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
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
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea maxLength={5000} rows={3} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="amount"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Amount (ZAR, excl VAT)</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={0.01}
                        step={0.01}
                        value={Number.isFinite(field.value) ? field.value : 0}
                        onChange={(e) =>
                          field.onChange(e.target.value === "" ? 0 : Number(e.target.value))
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="vatTreatment"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>VAT Treatment</FormLabel>
                    <FormControl>
                      <select
                        value={field.value}
                        onChange={(e) => {
                          vatUserTouched.current = true;
                          field.onChange(e);
                        }}
                        className={selectClass}
                      >
                        {VAT_TREATMENT_OPTIONS.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="incurredDate"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Incurred Date</FormLabel>
                    <FormControl>
                      <Input type="date" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="supplierName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Supplier</FormLabel>
                    <FormControl>
                      <Input maxLength={200} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              control={form.control}
              name="supplierReference"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Supplier Reference{" "}
                    <span className="font-normal text-slate-500">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input maxLength={100} {...field} />
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
                {isSubmitting ? "Saving..." : "Save changes"}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
