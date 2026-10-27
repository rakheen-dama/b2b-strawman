import { z } from "zod";
import type { LetterheadSettings, UpdateLetterheadSettingsRequest } from "@/lib/types";

/**
 * Firm letterhead form (Phase 88 U8, ADR-353). Mirrors the backend UpdateLetterheadSettingsRequest
 * exactly: every field is optional, values are trimmed first and the max lengths are checked on the
 * trimmed value (the backend trims before @Size / @Email too). Email format is the only format
 * rule. An empty string means "clear": toLetterheadRequest sends it as null (PATCH is a full
 * replace).
 */
function text(label: string, max: number) {
  return z.string().trim().max(max, `${label} must be ${max} characters or fewer`);
}

export const letterheadSettingsSchema = z.object({
  addressLine1: text("Address line 1", 255),
  addressLine2: text("Address line 2", 255),
  addressLine3: text("Address line 3", 255),
  city: text("City", 100),
  postalCode: text("Postal code", 20),
  country: text("Country", 100),
  phone: text("Phone", 50),
  email: text("Email", 255).refine((value) => value === "" || z.email().safeParse(value).success, {
    message: "Invalid email address",
  }),
  website: text("Website", 255),
  bankName: text("Bank name", 100),
  bankBranchCode: text("Branch code", 20),
  bankAccountNumber: text("Account number", 50),
  bankAccountType: text("Account type", 30),
  paymentReferenceNote: text("Payment reference note", 500),
  signatoryName: text("Signatory name", 255),
  signatoryDesignation: text("Designation", 255),
});

export type LetterheadSettingsFormData = z.infer<typeof letterheadSettingsSchema>;
export type LetterheadField = keyof LetterheadSettingsFormData;

/** The 16 field names, in backend record order. */
export const LETTERHEAD_FIELDS = Object.keys(letterheadSettingsSchema.shape) as LetterheadField[];

/** Saved letterhead (nulls) → form strings. */
export function toLetterheadFormValues(letterhead: LetterheadSettings): LetterheadSettingsFormData {
  const values = {} as LetterheadSettingsFormData;
  for (const field of LETTERHEAD_FIELDS) {
    values[field] = letterhead[field] ?? "";
  }
  return values;
}

/** Form strings → full-replace PATCH body: every key present, trimmed, blank → null. */
export function toLetterheadRequest(
  values: LetterheadSettingsFormData
): UpdateLetterheadSettingsRequest {
  const request = {} as UpdateLetterheadSettingsRequest;
  for (const field of LETTERHEAD_FIELDS) {
    request[field] = values[field].trim() || null;
  }
  return request;
}
