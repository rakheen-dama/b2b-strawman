import { z } from "zod";

const customerTypeEnum = z.enum(["INDIVIDUAL", "COMPANY", "TRUST"]);

export const createCustomerSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  email: z.string().min(1, "Email is required").email("Invalid email address").max(255),
  phone: z.string().max(50).optional().or(z.literal("")),
  idNumber: z.string().max(100).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
  customerType: customerTypeEnum,
});

export type CreateCustomerFormData = z.infer<typeof createCustomerSchema>;

export const editCustomerSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  email: z.string().min(1, "Email is required").email("Invalid email address").max(255),
  phone: z.string().max(50).optional().or(z.literal("")),
  idNumber: z.string().max(100).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
  customerType: customerTypeEnum,
});

export type EditCustomerFormData = z.infer<typeof editCustomerSchema>;
