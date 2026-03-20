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
