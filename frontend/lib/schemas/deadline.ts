import { z } from "zod";

export const filingStatusSchema = z.object({
  customerId: z.string().uuid("Invalid customer ID"),
  deadlineTypeSlug: z.string().min(1, "Deadline type is required"),
  periodKey: z.string().min(1, "Period is required"),
  status: z.enum(["filed", "not_applicable"], {
    message: "Status is required",
  }),
  notes: z.string().max(1000).optional().or(z.literal("")),
  linkedProjectId: z.string().uuid().optional().or(z.literal("")),
});

export type FilingStatusFormData = z.infer<typeof filingStatusSchema>;

export const dialogFormSchema = z.object({
  notes: z
    .string()
    .max(1000, "Notes must be 1000 characters or less")
    .optional()
    .or(z.literal("")),
  referenceNumber: z
    .string()
    .max(100, "Reference must be 100 characters or less")
    .optional()
    .or(z.literal("")),
});

export type DialogFormData = z.infer<typeof dialogFormSchema>;
