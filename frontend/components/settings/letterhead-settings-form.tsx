"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader } from "@b2mash/ui/card";
import { Input } from "@b2mash/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  LETTERHEAD_FIELDS,
  letterheadSettingsSchema,
  toLetterheadFormValues,
  toLetterheadRequest,
  type LetterheadField,
  type LetterheadSettingsFormData,
} from "@/lib/schemas/letterhead";
import { updateLetterheadSettingsAction } from "@/app/(app)/org/[slug]/settings/firm/_tabs/general-actions";
import type { LetterheadSettings } from "@/lib/types";

const NOTE_MAX = 500;

interface FieldSpec {
  name: LetterheadField;
  label: string;
  placeholder?: string;
  type?: "text" | "email" | "tel";
  /** Short field: shares a row with its neighbour once the card is wide enough (@lg). */
  half?: boolean;
  multiline?: boolean;
}

interface FieldGroup {
  legend: string;
  description: string;
  fields: FieldSpec[];
}

const GROUPS: FieldGroup[] = [
  {
    legend: "Address",
    description: "The firm address printed in the letterhead.",
    fields: [
      { name: "addressLine1", label: "Address line 1", placeholder: "e.g. 12 Rissik Street" },
      { name: "addressLine2", label: "Address line 2" },
      { name: "addressLine3", label: "Address line 3" },
      { name: "city", label: "City", placeholder: "e.g. Johannesburg", half: true },
      { name: "postalCode", label: "Postal code", placeholder: "e.g. 2001", half: true },
      { name: "country", label: "Country", placeholder: "e.g. South Africa" },
    ],
  },
  {
    legend: "Contact",
    description: "Phone, email and website shown with the firm name.",
    fields: [
      { name: "phone", label: "Phone", type: "tel", placeholder: "e.g. +27 11 555 0100" },
      { name: "email", label: "Email", type: "email", placeholder: "e.g. info@yourfirm.co.za" },
      { name: "website", label: "Website", placeholder: "e.g. https://yourfirm.co.za" },
    ],
  },
  {
    legend: "Banking",
    description: "Payment details printed on fee notes. Leave blank to leave them off.",
    fields: [
      { name: "bankName", label: "Bank name", placeholder: "e.g. FNB" },
      { name: "bankAccountNumber", label: "Account number" },
      { name: "bankBranchCode", label: "Branch code", placeholder: "e.g. 250655", half: true },
      { name: "bankAccountType", label: "Account type", placeholder: "e.g. Cheque", half: true },
      {
        name: "paymentReferenceNote",
        label: "Payment reference note",
        placeholder: "e.g. Use the fee note number as your reference",
        multiline: true,
      },
    ],
  },
  {
    legend: "Signatory",
    description: "The person who signs documents on behalf of the firm.",
    fields: [
      {
        name: "signatoryName",
        label: "Signatory name",
        placeholder: "e.g. Thandi Mathebula",
        half: true,
      },
      {
        name: "signatoryDesignation",
        label: "Designation",
        placeholder: "e.g. Director",
        half: true,
      },
    ],
  },
];

interface LetterheadSettingsFormProps {
  slug: string;
  letterhead: LetterheadSettings;
}

/**
 * Firm letterhead (Phase 88 U8, ADR-353) — address, contact, banking and signatory details used by
 * generated documents. Saves as a full replace via PATCH /api/settings/letterhead; independent of
 * the General form's "Save Settings".
 */
export function LetterheadSettingsForm({ slug, letterhead }: LetterheadSettingsFormProps) {
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const form = useForm<LetterheadSettingsFormData>({
    resolver: zodResolver(letterheadSettingsSchema),
    defaultValues: toLetterheadFormValues(letterhead),
  });
  const { isDirty, isSubmitting } = form.formState;

  async function onSubmit(values: LetterheadSettingsFormData) {
    setStatus(null);
    setError(null);
    try {
      const result = await updateLetterheadSettingsAction(slug, toLetterheadRequest(values));
      if (result.success) {
        // Re-base the form on what the server stored, so it is pristine again.
        form.reset(result.letterhead ? toLetterheadFormValues(result.letterhead) : values);
        setStatus("Letterhead saved.");
        return;
      }
      let mapped = false;
      for (const fieldError of result.fieldErrors ?? []) {
        if ((LETTERHEAD_FIELDS as string[]).includes(fieldError.field)) {
          form.setError(fieldError.field as LetterheadField, { message: fieldError.message });
          mapped = true;
        }
      }
      setError(
        mapped
          ? "Fix the highlighted fields and save again."
          : (result.error ?? "An unexpected error occurred.")
      );
    } catch {
      setError("An unexpected error occurred.");
    }
  }

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Letterhead</h2>
        <CardDescription className="text-slate-600 dark:text-slate-400">
          Firm details printed on generated documents such as engagement letters and fee notes.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          {/* noValidate: the zod schema owns validation; the browser's type="email" bubble would
              otherwise block submit before the inline message can show. */}
          <form onSubmit={form.handleSubmit(onSubmit)} noValidate className="@container space-y-8">
            {GROUPS.map((group) => (
              <fieldset key={group.legend} className="space-y-3">
                <legend className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {group.legend}
                </legend>
                <p className="text-xs text-slate-500 dark:text-slate-400">{group.description}</p>
                {/* Container query, not a viewport breakpoint: at a 1024px viewport this card is
                    ~390px wide, so pairs only go side by side when the card itself is wide. */}
                <div className="grid grid-cols-1 gap-4 @lg:grid-cols-2">
                  {group.fields.map((spec) => (
                    <FormField
                      key={spec.name}
                      control={form.control}
                      name={spec.name}
                      render={({ field }) => (
                        <FormItem className={spec.half ? undefined : "@lg:col-span-2"}>
                          <FormLabel>{spec.label}</FormLabel>
                          <FormControl>
                            {spec.multiline ? (
                              <Textarea rows={3} placeholder={spec.placeholder} {...field} />
                            ) : (
                              <Input
                                type={spec.type ?? "text"}
                                placeholder={spec.placeholder}
                                autoComplete="off"
                                {...field}
                              />
                            )}
                          </FormControl>
                          {spec.multiline && (
                            <FormDescription className="text-xs text-slate-400">
                              {field.value.length}/{NOTE_MAX} characters
                            </FormDescription>
                          )}
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  ))}
                </div>
              </fieldset>
            ))}

            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" disabled={isSubmitting || !isDirty}>
                {isSubmitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
                Save letterhead
              </Button>
              {status && !isDirty && (
                <p role="status" className="text-sm text-teal-600 dark:text-teal-400">
                  {status}
                </p>
              )}
              {error && (
                <p role="alert" className="text-destructive text-sm">
                  {error}
                </p>
              )}
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
