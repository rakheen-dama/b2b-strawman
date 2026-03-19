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

export const logDsarRequestSchema = z.object({
  subjectName: z.string().min(1, "Subject name is required").max(255),
  subjectEmail: z
    .string()
    .email("Invalid email")
    .max(255)
    .optional()
    .or(z.literal("")),
  requestType: z.enum(["ACCESS", "CORRECTION", "DELETION", "OBJECTION", "PORTABILITY"]),
  customerId: z.string().uuid("Invalid customer ID"),
  receivedDate: z.string().min(1, "Received date is required"),
});

export type LogDsarRequestFormData = z.infer<typeof logDsarRequestSchema>;

export const dsarStatusTransitionSchema = z.object({
  resolutionNotes: z
    .string()
    .min(1, "Resolution notes are required")
    .max(2000),
});

export type DsarStatusTransitionFormData = z.infer<
  typeof dsarStatusTransitionSchema
>;
