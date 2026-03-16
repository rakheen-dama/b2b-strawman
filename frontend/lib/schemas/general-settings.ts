import { z } from "zod";

const currencyEnum = z.enum(["ZAR", "USD", "EUR", "GBP"]);

const hexColorRegex = /^#[0-9a-fA-F]{6}$/;

export const generalSettingsSchema = z.object({
  defaultCurrency: currencyEnum,
  brandColor: z
    .string()
    .regex(hexColorRegex, "Must be a valid hex color (e.g., #1a2b3c)")
    .optional()
    .or(z.literal("")),
  documentFooterText: z.string().max(500).optional().or(z.literal("")),
  taxRegistrationNumber: z.string().max(50).optional().or(z.literal("")),
  taxLabel: z.string().max(20).optional().or(z.literal("")),
  taxInclusive: z.boolean(),
});

export type GeneralSettingsFormData = z.infer<typeof generalSettingsSchema>;
