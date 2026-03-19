import { z } from "zod";

export const informationOfficerSchema = z.object({
  informationOfficerName: z.string().max(255).optional().or(z.literal("")),
  informationOfficerEmail: z
    .string()
    .email("Invalid email address")
    .max(255)
    .optional()
    .or(z.literal("")),
});

export type InformationOfficerFormData = z.infer<
  typeof informationOfficerSchema
>;
