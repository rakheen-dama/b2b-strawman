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
import { useSubscription } from "@/lib/subscription-context";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { fetchIntakeFields } from "@/app/(app)/org/[slug]/customers/intake-actions";
import { IntakeFieldsSection, isFieldVisible } from "@/components/customers/intake-fields-section";
import type { FieldValue } from "@/components/prerequisite/inline-field-editor";
import type { IntakeFieldGroup } from "@/components/prerequisite/types";
import type { CustomerType } from "@/lib/types";
import { createCustomerSchema, type CreateCustomerFormData } from "@/lib/schemas/customer";
import { COUNTRIES } from "@/lib/constants/countries";
import { ENTITY_TYPES } from "@/lib/constants/entity-types";
import { nativeSelectClassName } from "@/lib/styles/native-select";

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
  const { isWriteEnabled } = useSubscription();
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
      addressLine1: "",
      addressLine2: "",
      city: "",
      stateProvince: "",
      postalCode: "",
      country: "",
      taxNumber: "",
      contactName: "",
      contactEmail: "",
      contactPhone: "",
      registrationNumber: "",
      entityType: "",
      financialYearEnd: "",
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

    // Validate step 1 fields (including optional promoted fields — ensures
    // bad email / bad country code / bad date shows an inline error before
    // advancing to step 2)
    const isValid = await form.trigger([
      "name",
      "email",
      "phone",
      "idNumber",
      "notes",
      "customerType",
      "addressLine1",
      "addressLine2",
      "city",
      "stateProvince",
      "postalCode",
      "country",
      "taxNumber",
      "contactName",
      "contactEmail",
      "contactPhone",
      "registrationNumber",
      "entityType",
      "financialYearEnd",
    ]);
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
      // Only submit fields that are visible for the selected customer type
      // (trust-variant fields are hidden for non-TRUST types)
      const visibleFieldSlugs = new Set(
        filteredIntakeGroups.flatMap((group) => group.fields.map((field) => field.slug))
      );
      const customFields: Record<string, unknown> = {};
      for (const [fieldSlug, value] of Object.entries(fieldValues)) {
        if (!visibleFieldSlugs.has(fieldSlug)) continue;
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
        addressLine1: values.addressLine1?.trim() || undefined,
        addressLine2: values.addressLine2?.trim() || undefined,
        city: values.city?.trim() || undefined,
        stateProvince: values.stateProvince?.trim() || undefined,
        postalCode: values.postalCode?.trim() || undefined,
        country: values.country?.trim() || undefined,
        taxNumber: values.taxNumber?.trim() || undefined,
        contactName: values.contactName?.trim() || undefined,
        contactEmail: values.contactEmail?.trim() || undefined,
        contactPhone: values.contactPhone?.trim() || undefined,
        registrationNumber: values.registrationNumber?.trim() || undefined,
        entityType: values.entityType || undefined,
        financialYearEnd: values.financialYearEnd || undefined,
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

  // Filter out trust-variant groups when the selected customer type is not TRUST
  const selectedCustomerType = form.watch("customerType");
  const filteredIntakeGroups =
    selectedCustomerType === "TRUST"
      ? intakeGroups
      : intakeGroups.filter((g) => !g.slug.includes("trust"));

  // Compute whether all visible required fields are filled
  const allRequiredFilled = filteredIntakeGroups.every((group) =>
    group.fields
      .filter((f) => f.required && isFieldVisible(f, fieldValues))
      .every((f) => {
        const v = fieldValues[f.slug];
        return v !== null && v !== undefined && v !== "";
      })
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger asChild>
            <span tabIndex={0} className="inline-flex">
              <Button size="sm" disabled={!isWriteEnabled} onClick={() => setOpen(true)}>
                <Plus className="mr-1.5 size-4" />
                New {t("Customer")}
              </Button>
            </span>
          </TooltipTrigger>
          {!isWriteEnabled && <TooltipContent>Subscribe to enable this action</TooltipContent>}
        </Tooltip>
      </TooltipProvider>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{step === 1 ? t("Create Customer") : "Additional Information"}</DialogTitle>
          <DialogDescription>
            {step === 1
              ? `Step 1 of 2 — Add a new ${t("customer")} to your organization.`
              : "Step 2 of 2 — Fill in any required intake fields."}
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] space-y-4 overflow-y-auto py-2">
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
                          placeholder={`${t("Customer")} name`}
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
                          className={nativeSelectClassName}
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
                        Phone <span className="text-muted-foreground font-normal">(optional)</span>
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
                        <span className="text-muted-foreground font-normal">(optional)</span>
                      </FormLabel>
                      <FormControl>
                        <Input placeholder="e.g. CUS-001" maxLength={100} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="taxNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        Tax Number{" "}
                        <span className="text-muted-foreground font-normal">
                          (required for activation)
                        </span>
                      </FormLabel>
                      <FormControl>
                        <Input
                          placeholder="e.g. VAT or tax registration number"
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
                        Notes <span className="text-muted-foreground font-normal">(optional)</span>
                      </FormLabel>
                      <FormControl>
                        <Textarea
                          placeholder={`Any additional notes about this ${t("customer")}...`}
                          maxLength={2000}
                          rows={3}
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Address Section */}
                <div className="border-t pt-4">
                  <h3 className="mb-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Address
                  </h3>
                  <div className="space-y-4">
                    <FormField
                      control={form.control}
                      name="addressLine1"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Address Line 1{" "}
                            <span className="text-muted-foreground font-normal">(optional)</span>
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
                      name="addressLine2"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>
                            Address Line 2{" "}
                            <span className="text-muted-foreground font-normal">(optional)</span>
                          </FormLabel>
                          <FormControl>
                            <Input maxLength={255} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField
                        control={form.control}
                        name="city"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>City</FormLabel>
                            <FormControl>
                              <Input maxLength={100} {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      <FormField
                        control={form.control}
                        name="stateProvince"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>State / Province</FormLabel>
                            <FormControl>
                              <Input maxLength={100} {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                    <div className="grid gap-4 sm:grid-cols-2">
                      <FormField
                        control={form.control}
                        name="postalCode"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Postal Code</FormLabel>
                            <FormControl>
                              <Input maxLength={20} {...field} />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                      <FormField
                        control={form.control}
                        name="country"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>
                              Country{" "}
                              <span className="text-muted-foreground font-normal">
                                (required for activation)
                              </span>
                            </FormLabel>
                            <FormControl>
                              <select
                                value={field.value ?? ""}
                                onChange={field.onChange}
                                className={nativeSelectClassName}
                              >
                                <option value="">Select country…</option>
                                {COUNTRIES.map((c) => (
                                  <option key={c.code} value={c.code}>
                                    {c.name} ({c.code})
                                  </option>
                                ))}
                              </select>
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>
                </div>

                {/* Contact Section */}
                <div className="border-t pt-4">
                  <h3 className="mb-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Contact
                  </h3>
                  <div className="space-y-4">
                    <FormField
                      control={form.control}
                      name="contactName"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Contact Name</FormLabel>
                          <FormControl>
                            <Input maxLength={255} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="contactEmail"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Contact Email</FormLabel>
                          <FormControl>
                            <Input type="email" maxLength={255} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="contactPhone"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Contact Phone</FormLabel>
                          <FormControl>
                            <Input type="tel" maxLength={50} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </div>

                {/* Business Details Section */}
                <div className="border-t pt-4">
                  <h3 className="mb-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Business Details
                  </h3>
                  <div className="space-y-4">
                    <FormField
                      control={form.control}
                      name="registrationNumber"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Registration Number</FormLabel>
                          <FormControl>
                            <Input maxLength={100} {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="entityType"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Entity Type</FormLabel>
                          <FormControl>
                            <select
                              value={field.value ?? ""}
                              onChange={field.onChange}
                              className={nativeSelectClassName}
                            >
                              <option value="">Select entity type…</option>
                              {ENTITY_TYPES.map((et) => (
                                <option key={et.value} value={et.value}>
                                  {et.label}
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
                      name="financialYearEnd"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Financial Year End</FormLabel>
                          <FormControl>
                            <Input type="date" {...field} />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </div>
                </div>
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
                <p className="text-destructive text-sm">{fetchError}</p>
              ) : filteredIntakeGroups.length === 0 ? (
                <p className="text-sm text-slate-500">No additional fields required.</p>
              ) : (
                <IntakeFieldsSection
                  groups={filteredIntakeGroups}
                  values={fieldValues}
                  onChange={handleFieldChange}
                />
              )}

              {allRequiredFilled && !isLoadingFields && filteredIntakeGroups.length > 0 && (
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

          {error && <p className="text-destructive text-sm">{error}</p>}
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
              <Button type="button" onClick={handleNext} disabled={isLoadingFields}>
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
              <Button type="button" variant="outline" onClick={handleBack} disabled={isSubmitting}>
                Back
              </Button>
              <Button
                type="button"
                onClick={handleSubmit}
                disabled={isSubmitting || isLoadingFields}
              >
                {isSubmitting ? "Creating..." : t("Create Customer")}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
