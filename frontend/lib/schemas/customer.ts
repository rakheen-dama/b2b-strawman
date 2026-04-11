import { z } from "zod";
import { ENTITY_TYPES } from "@/lib/constants/entity-types";

const customerTypeEnum = z.enum(["INDIVIDUAL", "COMPANY", "TRUST"]);

// Derive the enum values from the shared constant so the schema cannot drift
// from the UI Select options.
const entityTypeValues = ENTITY_TYPES.map((t) => t.value) as [string, ...string[]];
const entityTypeEnum = z.enum(entityTypeValues);

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
  financialYearEnd: z.iso.date().or(z.literal("")).optional(),
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
