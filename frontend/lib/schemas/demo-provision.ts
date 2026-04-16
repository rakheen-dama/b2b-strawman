import { z } from "zod";

export const demoProvisionSchema = z.object({
  organizationName: z
    .string()
    .trim()
    .min(1, "Organization name is required")
    .max(255, "Organization name must be 255 characters or fewer"),
  verticalProfile: z.enum(["consulting-generic", "accounting-za", "legal-za"], {
    message: "Please select a vertical profile",
  }),
  adminEmail: z.string().trim().min(1, "Email is required").email("Invalid email address"),
  seedDemoData: z.boolean(),
});

export type DemoProvisionFormData = z.infer<typeof demoProvisionSchema>;
