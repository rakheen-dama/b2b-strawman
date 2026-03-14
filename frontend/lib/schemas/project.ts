import { z } from "zod";

export const createProjectSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  description: z.string().max(2000).optional().or(z.literal("")),
  dueDate: z.string().optional().or(z.literal("")),
  customerId: z.string().optional().or(z.literal("")),
});

export type CreateProjectFormData = z.infer<typeof createProjectSchema>;

export const editProjectSchema = z.object({
  name: z.string().min(1, "Name is required").max(255, "Name must be 255 characters or fewer"),
  description: z.string().max(2000).optional().or(z.literal("")),
  dueDate: z.string().optional().or(z.literal("")),
  customerId: z.string().optional().or(z.literal("")),
});

export type EditProjectFormData = z.infer<typeof editProjectSchema>;
