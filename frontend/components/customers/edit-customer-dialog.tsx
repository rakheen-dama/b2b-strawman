"use client";

import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, type buttonVariants } from "@/components/ui/button";
import type { VariantProps } from "class-variance-authority";
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
import { updateCustomer } from "@/app/(app)/org/[slug]/customers/actions";
import type { Customer, CustomerType } from "@/lib/types";
import { editCustomerSchema, type EditCustomerFormData } from "@/lib/schemas/customer";
import { COUNTRIES } from "@/lib/constants/countries";
import { ENTITY_TYPES } from "@/lib/constants/entity-types";
import { nativeSelectClassName } from "@/lib/styles/native-select";

const CUSTOMER_TYPES: { value: CustomerType; label: string }[] = [
  { value: "INDIVIDUAL", label: "Individual" },
  { value: "COMPANY", label: "Company" },
  { value: "TRUST", label: "Trust" },
];

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface EditCustomerDialogProps {
  customer: Customer;
  slug: string;
  /**
   * OBS-2103b: dialog owns the trigger button. Pass a label and (optionally)
   * a variant/size + leading icon — the component renders the `<Button>`
   * itself rather than cloning a consumer-supplied element. This avoids both
   * the Radix `Slot` adjacency collision (OBS-2103) AND the lazy/RSC
   * `cloneElement` onClick-strip that bit OBS-2103b.
   */
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
}

function buildDefaults(customer: Customer): EditCustomerFormData {
  return {
    name: customer.name,
    email: customer.email,
    phone: customer.phone ?? "",
    idNumber: customer.idNumber ?? "",
    notes: customer.notes ?? "",
    customerType: (customer.customerType as CustomerType) ?? "INDIVIDUAL",
    addressLine1: customer.addressLine1 ?? "",
    addressLine2: customer.addressLine2 ?? "",
    city: customer.city ?? "",
    stateProvince: customer.stateProvince ?? "",
    postalCode: customer.postalCode ?? "",
    // Preserve raw value even if it is not in the curated COUNTRIES list —
    // the render adds a "(legacy)" fallback option so existing data never
    // silently disappears when the list changes.
    country: customer.country ?? "",
    taxNumber: customer.taxNumber ?? "",
    contactName: customer.contactName ?? "",
    contactEmail: customer.contactEmail ?? "",
    contactPhone: customer.contactPhone ?? "",
    registrationNumber: customer.registrationNumber ?? "",
    // Preserve raw value even if it is not in the curated ENTITY_TYPES list.
    entityType: (customer.entityType as EditCustomerFormData["entityType"]) ?? "",
    financialYearEnd: customer.financialYearEnd ?? "",
  };
}

export function EditCustomerDialog({
  customer,
  slug,
  triggerLabel,
  triggerVariant = "outline",
  triggerSize = "sm",
  triggerClassName,
  triggerIcon,
}: EditCustomerDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<EditCustomerFormData>({
    resolver: zodResolver(editCustomerSchema),
    defaultValues: buildDefaults(customer),
  });

  async function onSubmit(values: EditCustomerFormData) {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateCustomer(slug, customer.id, {
        name: values.name.trim(),
        email: values.email.trim(),
        phone: values.phone?.trim() || undefined,
        idNumber: values.idNumber?.trim() || undefined,
        notes: values.notes?.trim() || undefined,
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
      });
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update customer.");
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
      form.reset(buildDefaults(customer));
    }
    setOpen(newOpen);
  }

  // OBS-2103 / OBS-2103b: dialog owns the trigger button.
  //
  // OBS-2103 (PR #1239) avoided Radix `Slot` (`asChild`) because two adjacent
  // sibling Slots cloning <Button> children (Dialog + AlertDialog) collapsed
  // during React 19 commit and only one survived. We switched to
  // `cloneElement(children, { onClick })` — but that still failed under
  // OBS-2103b: the EditCustomerDialog children prop arrives as a lazy/RSC
  // element where `children.props` is undefined, so `cloneElement` returns
  // an element with default props only and the injected onClick disappears.
  //
  // The structurally correct fix is to render the trigger directly here.
  // No Slot wrapper, no cloneElement, no lazy-children fragility.
  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        onClick={() => setOpen(true)}
      >
        {triggerIcon}
        {triggerLabel}
      </Button>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit Customer</DialogTitle>
          <DialogDescription>Update customer information.</DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input maxLength={255} autoFocus {...field} />
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
                  <FormLabel>
                    Type <span className="text-muted-foreground font-normal">(not editable)</span>
                  </FormLabel>
                  <FormControl>
                    <select
                      value={field.value}
                      onChange={field.onChange}
                      disabled
                      className={`${nativeSelectClassName} disabled:cursor-not-allowed disabled:opacity-60`}
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
                    <Input type="email" maxLength={255} {...field} />
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
                    <Input type="tel" maxLength={50} {...field} />
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
                    ID Number <span className="text-muted-foreground font-normal">(optional)</span>
                  </FormLabel>
                  <FormControl>
                    <Input maxLength={100} {...field} />
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
                    <Textarea maxLength={2000} rows={3} {...field} />
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
                            {field.value && !COUNTRIES.find((c) => c.code === field.value) && (
                              <option value={field.value}>{field.value} (legacy)</option>
                            )}
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
                          {field.value && !ENTITY_TYPES.find((et) => et.value === field.value) && (
                            <option value={field.value}>{field.value} (legacy)</option>
                          )}
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

            {error && <p className="text-destructive text-sm">{error}</p>}
            <DialogFooter>
              <Button
                type="button"
                variant="plain"
                onClick={() => setOpen(false)}
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
