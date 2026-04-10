import { z } from "zod";

const customerTypeEnum = z.enum(["INDIVIDUAL", "COMPANY", "TRUST"]);

const entityTypeEnum = z.enum([
  "INDIVIDUAL",
  "SOLE_PROP",
  "PTY_LTD",
  "CC",
  "PARTNERSHIP",
  "TRUST",
  "NON_PROFIT",
  "PUBLIC_COMPANY",
]);

/**
 * 13 promoted customer fields (Epic 463 / Phase 63 — custom field graduation).
 * All optional so existing flows continue to work unchanged.
 */
const promotedCustomerFields = {
  addressLine1: z.string().max(255).optional().or(z.literal("")),
  addressLine2: z.string().max(255).optional().or(z.literal("")),
  city: z.string().max(100).optional().or(z.literal("")),
  stateProvince: z.string().max(100).optional().or(z.literal("")),
  postalCode: z.string().max(20).optional().or(z.literal("")),
  country: z
    .string()
    .length(2, "Country must be a 2-letter ISO code")
    .optional()
    .or(z.literal("")),
  taxNumber: z.string().max(100).optional().or(z.literal("")),
  contactName: z.string().max(255).optional().or(z.literal("")),
  contactEmail: z
    .string()
    .email("Invalid email address")
    .max(255)
    .optional()
    .or(z.literal("")),
  contactPhone: z.string().max(50).optional().or(z.literal("")),
  registrationNumber: z.string().max(100).optional().or(z.literal("")),
  entityType: entityTypeEnum.optional().or(z.literal("")),
  financialYearEnd: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, "Invalid date")
    .optional()
    .or(z.literal("")),
};

export const createCustomerSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  email: z.string().min(1, "Email is required").email("Invalid email address").max(255),
  phone: z.string().max(50).optional().or(z.literal("")),
  idNumber: z.string().max(100).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
  customerType: customerTypeEnum,
  ...promotedCustomerFields,
});

export type CreateCustomerFormData = z.infer<typeof createCustomerSchema>;

export const editCustomerSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  email: z.string().min(1, "Email is required").email("Invalid email address").max(255),
  phone: z.string().max(50).optional().or(z.literal("")),
  idNumber: z.string().max(100).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
  customerType: customerTypeEnum,
  ...promotedCustomerFields,
});

export type EditCustomerFormData = z.infer<typeof editCustomerSchema>;
