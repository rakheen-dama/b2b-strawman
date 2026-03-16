"use client";

import { useState, useCallback } from "react";
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
  DialogTrigger,
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
import { Loader2, Plus } from "lucide-react";
import { createCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import { createMessages } from "@/lib/messages";
import { scrollToFirstError } from "@/lib/error-handler";
import { useTerminology } from "@/lib/terminology";
import { fetchIntakeFields } from "@/app/(app)/org/[slug]/customers/intake-actions";
import {
  IntakeFieldsSection,
  isFieldVisible,
} from "@/components/customers/intake-fields-section";
import type { FieldValue } from "@/components/prerequisite/inline-field-editor";
import type { IntakeFieldGroup } from "@/components/prerequisite/types";
import type { CustomerType } from "@/lib/types";
import {
  createCustomerSchema,
  type CreateCustomerFormData,
} from "@/lib/schemas/customer";

const CUSTOMER_TYPES: { value: CustomerType; label: string }[] = [
  { value: "INDIVIDUAL", label: "Individual" },
  { value: "COMPANY", label: "Company" },
  { value: "TRUST", label: "Trust" },
];

interface CreateCustomerDialogProps {
  slug: string;
}

export function CreateCustomerDialog({ slug }: CreateCustomerDialogProps) {
  const { t } = useTerminology();
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<CreateCustomerFormData>({
    resolver: zodResolver(createCustomerSchema),
    defaultValues: {
      name: "",
      email: "",
      phone: "",
      idNumber: "",
      notes: "",
      customerType: "INDIVIDUAL",
    },
  });

  // Step 2 intake fields state
  const [intakeGroups, setIntakeGroups] = useState<IntakeFieldGroup[]>([]);
  const [fieldValues, setFieldValues] = useState<Record<string, FieldValue>>({});
  const [isLoadingFields, setIsLoadingFields] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  function resetForm() {
    setStep(1);
    setError(null);
    setFetchError(null);
    form.reset();
    setIntakeGroups([]);
    setFieldValues({});
    setIsLoadingFields(false);
  }

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (!newOpen) resetForm();
  }

  async function loadIntakeFields(): Promise<boolean> {
    setIsLoadingFields(true);
    setFetchError(null);
    try {
      const response = await fetchIntakeFields("CUSTOMER");
      setIntakeGroups(response.groups);
      return true;
    } catch {
      setFetchError("Failed to load intake fields.");
      return false;
    } finally {
      setIsLoadingFields(false);
    }
  }

  async function handleNext() {
    setError(null);

    // Validate only step 1 fields
    const isValid = await form.trigger(["name", "email"]);
    if (!isValid) {
      scrollToFirstError();
      return;
    }

    const success = await loadIntakeFields();
    if (success) {
      setStep(2);
    }
  }

  function handleBack() {
    setStep(1);
    setError(null);
  }

  const handleFieldChange = useCallback((slug: string, value: FieldValue) => {
    setFieldValues((prev) => ({ ...prev, [slug]: value }));
  }, []);

  async function handleSubmit() {
    setError(null);
    setIsSubmitting(true);

    try {
      const values = form.getValues();
      const customFields: Record<string, unknown> = {};
      for (const [fieldSlug, value] of Object.entries(fieldValues)) {
        if (value !== null && value !== undefined && value !== "") {
          customFields[fieldSlug] = value;
        }
      }

      const result = await createCustomer(slug, {
        name: values.name.trim(),
        email: values.email.trim(),
        phone: values.phone?.trim() || undefined,
        idNumber: values.idNumber?.trim() || undefined,
        notes: values.notes?.trim() || undefined,
        customerType: values.customerType || undefined,
        customFields,
      });

      if (result.success) {
        setOpen(false);
        resetForm();
      } else {
        const { t } = createMessages("errors");
        setError(result.error ?? t("api.serverError"));
        scrollToFirstError();
      }
    } catch {
      const { t } = createMessages("errors");
      setError(t("api.networkError"));
    } finally {
      setIsSubmitting(false);
    }
  }

  // Compute whether all visible required fields are filled
  const allRequiredFilled = intakeGroups.every((group) =>
    group.fields
      .filter((f) => f.required && isFieldVisible(f, fieldValues))
      .every((f) => {
        const v = fieldValues[f.slug];
        return v !== null && v !== undefined && v !== "";
      }),
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="mr-1.5 size-4" />
          New {t("Customer")}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {step === 1 ? "Create Customer" : "Additional Information"}
          </DialogTitle>
          <DialogDescription>
            {step === 1
              ? "Step 1 of 2 — Add a new customer to your organization."
              : "Step 2 of 2 — Fill in any required intake fields."}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Step 1: Base fields */}
          {step === 1 && (
            <Form {...form}>
              <div className="space-y-4">
                <FormField
                  control={form.control}
                  name="name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Name</FormLabel>
                      <FormControl>
                        <Input
                          placeholder="Customer name"
                          maxLength={255}
                          autoFocus
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="customerType"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Type</FormLabel>
                      <FormControl>
                        <select
                          value={field.value}
                          onChange={field.onChange}
                          className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                        >
                          {CUSTOMER_TYPES.map((ct) => (
                            <option key={ct.value} value={ct.value}>
                              {ct.label}
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
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Email</FormLabel>
                      <FormControl>
                        <Input
                          type="email"
                          placeholder="customer@example.com"
                          maxLength={255}
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="phone"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Phone{" "}
                        <span className="font-normal text-muted-foreground">(optional)</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          type="tel"
                          placeholder="+1 (555) 000-0000"
                          maxLength={50}
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="idNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        ID Number{" "}
                        <span className="font-normal text-muted-foreground">(optional)</span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          placeholder="e.g. CUS-001"
                          maxLength={100}
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
                        <span className="font-normal text-muted-foreground">(optional)</span>
                      </FormLabel>
                      <FormControl>
                        <Textarea
                          placeholder="Any additional notes about this customer..."
                          maxLength={2000}
                          rows={3}
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            </Form>
          )}

          {/* Step 2: Intake fields */}
          {step === 2 && (
            <div className="space-y-4">
              {isLoadingFields ? (
                <div className="flex items-center gap-2 py-4 text-sm text-slate-500">
                  <Loader2 className="size-4 animate-spin" />
                  Loading fields...
                </div>
              ) : fetchError ? (
                <p className="text-sm text-destructive">{fetchError}</p>
              ) : intakeGroups.length === 0 ? (
                <p className="text-sm text-slate-500">
                  No additional fields required.
                </p>
              ) : (
                <IntakeFieldsSection
                  groups={intakeGroups}
                  values={fieldValues}
                  onChange={handleFieldChange}
                />
              )}

              {allRequiredFilled && !isLoadingFields && intakeGroups.length > 0 && (
                <div className="pt-1">
                  <button
                    type="button"
                    className="text-xs text-slate-500 underline hover:text-slate-700"
                    onClick={handleSubmit}
                    disabled={isSubmitting}
                  >
                    Skip for now
                  </button>
                </div>
              )}
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          {step === 1 ? (
            <>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleNext}
                disabled={isLoadingFields}
              >
                {isLoadingFields ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    Loading...
                  </>
                ) : (
                  "Next"
                )}
              </Button>
            </>
          ) : (
            <>
              <Button
                type="button"
                variant="outline"
                onClick={handleBack}
                disabled={isSubmitting}
              >
                Back
              </Button>
              <Button
                type="button"
                onClick={handleSubmit}
                disabled={isSubmitting || isLoadingFields}
              >
                {isSubmitting ? "Creating..." : "Create Customer"}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
