"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@b2mash/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Loader2, Plus } from "lucide-react";
import { scrollToFirstError } from "@/lib/error-handler";
import { nativeSelectClassName } from "@/lib/styles/native-select";
import { intakeDealSchema, type IntakeDealFormData } from "@/lib/schemas/deal";
import { intakeDealAction } from "@/app/(app)/org/[slug]/pipeline/actions";
import type { IntakeRequest, StageDto } from "@/lib/api/crm";

export interface IntakeCustomerOption {
  id: string;
  name: string;
}

export interface IntakeDialogProps {
  slug: string;
  customers: IntakeCustomerOption[];
  stages: StageDto[];
}

export function IntakeDialog({ slug, customers, stages }: IntakeDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const openStages = stages.filter((s) => s.stageType === "OPEN" && !s.archived);

  const form = useForm<IntakeDealFormData>({
    resolver: zodResolver(intakeDealSchema),
    defaultValues: {
      customerMode: "existing",
      customerId: "",
      customerName: "",
      customerEmail: "",
      customerPhone: "",
      title: "",
      valueAmount: "",
      stageId: "",
      source: "",
      expectedCloseDate: "",
    },
  });

  const customerMode = form.watch("customerMode");

  function resetForm() {
    setError(null);
    form.reset();
  }

  function handleOpenChange(o: boolean) {
    setOpen(o);
    if (!o) resetForm();
  }

  async function onSubmit(values: IntakeDealFormData) {
    setError(null);
    setIsSubmitting(true);
    try {
      const req: IntakeRequest = {
        title: values.title.trim(),
        valueAmount:
          values.valueAmount && values.valueAmount.trim() !== ""
            ? Number(values.valueAmount)
            : undefined,
        stageId: values.stageId || undefined,
        source: values.source?.trim() || undefined,
        expectedCloseDate: values.expectedCloseDate || undefined,
      };

      // Send EXACTLY ONE of customerId / customer.
      if (values.customerMode === "existing") {
        req.customerId = values.customerId || undefined;
      } else {
        req.customer = {
          name: (values.customerName ?? "").trim(),
          email: values.customerEmail?.trim() || undefined,
          phone: values.customerPhone?.trim() || undefined,
        };
      }

      const result = await intakeDealAction(slug, req);
      if (result.success) {
        handleOpenChange(false);
        router.refresh();
      } else {
        setError(result.error ?? "Something went wrong.");
        scrollToFirstError();
      }
    } catch {
      setError("A network error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button size="sm" onClick={() => setOpen(true)}>
        <Plus className="mr-1.5 size-4" /> New Enquiry
      </Button>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New Enquiry</DialogTitle>
          <DialogDescription>Create a deal from a new or existing customer.</DialogDescription>
        </DialogHeader>
        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
          <Form {...form}>
            <form
              id="intake-deal-form"
              onSubmit={form.handleSubmit(onSubmit, scrollToFirstError)}
              className="space-y-4"
            >
              <FormField
                control={form.control}
                name="customerMode"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Customer</FormLabel>
                    <FormControl>
                      <select
                        value={field.value}
                        onChange={field.onChange}
                        className={nativeSelectClassName}
                      >
                        <option value="existing">Pick existing customer</option>
                        <option value="new">Create new prospect</option>
                      </select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {customerMode === "existing" ? (
                <FormField
                  control={form.control}
                  name="customerId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Select customer</FormLabel>
                      <FormControl>
                        <select
                          value={field.value}
                          onChange={field.onChange}
                          className={nativeSelectClassName}
                        >
                          <option value="">Choose a customer…</option>
                          {customers.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              ) : (
                <>
                  <FormField
                    control={form.control}
                    name="customerName"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>New customer name</FormLabel>
                        <FormControl>
                          <Input placeholder="Customer name" maxLength={255} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="customerEmail"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Email (optional)</FormLabel>
                        <FormControl>
                          <Input type="email" maxLength={255} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="customerPhone"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Phone (optional)</FormLabel>
                        <FormControl>
                          <Input maxLength={50} {...field} />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </>
              )}

              <FormField
                control={form.control}
                name="title"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Title</FormLabel>
                    <FormControl>
                      <Input placeholder="Deal title" maxLength={200} {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className="grid grid-cols-2 gap-3">
                <FormField
                  control={form.control}
                  name="valueAmount"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Value (optional)</FormLabel>
                      <FormControl>
                        <Input type="number" min={0} step="0.01" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="stageId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Stage (optional)</FormLabel>
                      <FormControl>
                        <select
                          value={field.value}
                          onChange={field.onChange}
                          className={nativeSelectClassName}
                        >
                          <option value="">First open stage</option>
                          {openStages.map((s) => (
                            <option key={s.id} value={s.id}>
                              {s.name}
                            </option>
                          ))}
                        </select>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <FormField
                  control={form.control}
                  name="source"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Source (optional)</FormLabel>
                      <FormControl>
                        <Input maxLength={40} placeholder="e.g. Referral" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="expectedCloseDate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Expected close (optional)</FormLabel>
                      <FormControl>
                        <Input type="date" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </form>
          </Form>
          {error && <p className="text-destructive text-sm">{error}</p>}
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button type="submit" form="intake-deal-form" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            {isSubmitting ? "Creating..." : "Create Enquiry"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
