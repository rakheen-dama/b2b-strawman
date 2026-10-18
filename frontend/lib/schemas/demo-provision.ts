import { z } from "zod";

export const demoProvisionSchema = z.object({
  organizationName: z
    .string()
    .trim()
    .min(1, "Organization name is required")
    .max(255, "Organization name must be 255 characters or fewer"),
  // Phase 86 (ADR-332): legal-za is the only vertical profile; the backend rejects any other id.
  verticalProfile: z.enum(["legal-za"]),
  adminEmail: z.string().trim().min(1, "Email is required").email("Invalid email address"),
  seedDemoData: z.boolean(),
});

export type DemoProvisionFormData = z.infer<typeof demoProvisionSchema>;
