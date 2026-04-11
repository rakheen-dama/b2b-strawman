import { z } from "zod";

const priorityEnum = z.enum(["LOW", "MEDIUM", "HIGH"]);

const promotedProjectFields = {
  referenceNumber: z
    .string()
    .max(100, "Reference number must be 100 characters or fewer")
    .optional()
    .or(z.literal("")),
  priority: priorityEnum.optional().or(z.literal("")),
  workType: z
    .string()
    .max(50, "Work type must be 50 characters or fewer")
    .optional()
    .or(z.literal("")),
};

export const createProjectSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  description: z.string().max(2000).optional().or(z.literal("")),
  dueDate: z.string().optional().or(z.literal("")),
  customerId: z.string().optional().or(z.literal("")),
  ...promotedProjectFields,
});

export type CreateProjectFormData = z.infer<typeof createProjectSchema>;

export const editProjectSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  description: z.string().max(2000).optional().or(z.literal("")),
  dueDate: z.string().optional().or(z.literal("")),
  customerId: z.string().optional().or(z.literal("")),
  ...promotedProjectFields,
});

export type EditProjectFormData = z.infer<typeof editProjectSchema>;
