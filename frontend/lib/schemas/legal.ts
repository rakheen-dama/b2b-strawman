import { z } from "zod";

export const createCourtDateSchema = z.object({
  projectId: z.string().uuid("Please select a matter"),
  dateType: z.enum(
    [
      "HEARING",
      "TRIAL",
      "MOTION",
      "CONFERENCE",
      "MEDIATION",
      "ARBITRATION",
      "MENTION",
      "OTHER",
    ],
    { message: "Date type is required" }
  ),
  scheduledDate: z.string().min(1, "Scheduled date is required"),
  scheduledTime: z.string().optional().or(z.literal("")),
  courtName: z.string().min(1, "Court name is required").max(255),
  courtReference: z.string().max(255).optional().or(z.literal("")),
  judgeMagistrate: z.string().max(255).optional().or(z.literal("")),
  description: z.string().max(2000).optional().or(z.literal("")),
  reminderDays: z.number().int().min(0).max(365),
});

export type CreateCourtDateFormData = z.infer<typeof createCourtDateSchema>;

export const postponeCourtDateSchema = z.object({
  newDate: z.string().min(1, "New date is required"),
  reason: z.string().min(1, "Reason is required").max(2000),
});

export type PostponeCourtDateFormData = z.infer<
  typeof postponeCourtDateSchema
>;

export const cancelCourtDateSchema = z.object({
  reason: z.string().min(1, "Reason is required").max(2000),
});

export type CancelCourtDateFormData = z.infer<typeof cancelCourtDateSchema>;

export const outcomeSchema = z.object({
  outcome: z.string().min(1, "Outcome is required").max(2000),
});

export type OutcomeFormData = z.infer<typeof outcomeSchema>;

export const createPrescriptionTrackerSchema = z.object({
  projectId: z.string().uuid("Please select a matter"),
  causeOfActionDate: z.string().min(1, "Cause of action date is required"),
  prescriptionType: z.enum(
    [
      "GENERAL_3Y",
      "DEBT_6Y",
      "MORTGAGE_30Y",
      "DELICT_3Y",
      "CONTRACT_3Y",
      "CUSTOM",
    ],
    { message: "Prescription type is required" }
  ),
  customYears: z.number().int().min(1).max(100).optional(),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreatePrescriptionTrackerFormData = z.infer<
  typeof createPrescriptionTrackerSchema
>;

export const interruptPrescriptionSchema = z.object({
  interruptionDate: z.string().min(1, "Interruption date is required"),
  interruptionReason: z.string().min(1, "Reason is required").max(2000),
});

export type InterruptPrescriptionFormData = z.infer<
  typeof interruptPrescriptionSchema
>;
