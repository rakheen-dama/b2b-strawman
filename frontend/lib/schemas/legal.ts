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

export const editCourtDateSchema = z.object({
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

export type EditCourtDateFormData = z.infer<typeof editCourtDateSchema>;

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

export const createPrescriptionTrackerSchema = z
  .object({
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
  })
  .refine(
    (data) =>
      data.prescriptionType !== "CUSTOM" ||
      (data.customYears != null && data.customYears >= 1),
    { message: "Custom years is required for Custom type", path: ["customYears"] }
  );

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

// Conflict check schemas

export const performConflictCheckSchema = z.object({
  checkedName: z.string().min(1, "Name to check is required").max(300),
  checkedIdNumber: z.string().max(20).optional().or(z.literal("")),
  checkedRegistrationNumber: z.string().max(30).optional().or(z.literal("")),
  checkType: z.enum(["NEW_CLIENT", "NEW_MATTER", "PERIODIC_REVIEW"], {
    message: "Check type is required",
  }),
  customerId: z.string().uuid().optional().or(z.literal("")),
  projectId: z.string().uuid().optional().or(z.literal("")),
});

export type PerformConflictCheckFormData = z.infer<
  typeof performConflictCheckSchema
>;

export const resolveConflictSchema = z.object({
  resolution: z.enum(["PROCEED", "DECLINED", "WAIVER_OBTAINED", "REFERRED"], {
    message: "Resolution is required",
  }),
  resolutionNotes: z.string().max(2000).optional().or(z.literal("")),
  waiverDocumentId: z.string().uuid().optional().or(z.literal("")),
});

export type ResolveConflictFormData = z.infer<typeof resolveConflictSchema>;

// Adverse party schemas

export const createAdversePartySchema = z.object({
  name: z.string().min(1, "Name is required").max(300),
  idNumber: z.string().max(20).optional().or(z.literal("")),
  registrationNumber: z.string().max(30).optional().or(z.literal("")),
  partyType: z.enum(
    [
      "NATURAL_PERSON",
      "COMPANY",
      "TRUST",
      "CLOSE_CORPORATION",
      "PARTNERSHIP",
      "OTHER",
    ],
    { message: "Party type is required" }
  ),
  aliases: z.string().max(1000).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreateAdversePartyFormData = z.infer<
  typeof createAdversePartySchema
>;

export const linkAdversePartySchema = z.object({
  projectId: z.string().uuid("Please select a matter"),
  customerId: z.string().uuid("Please select a customer"),
  relationship: z.enum(
    [
      "OPPOSING_PARTY",
      "WITNESS",
      "CO_ACCUSED",
      "RELATED_ENTITY",
      "GUARANTOR",
    ],
    { message: "Relationship is required" }
  ),
  description: z.string().max(2000).optional().or(z.literal("")),
});

export type LinkAdversePartyFormData = z.infer<typeof linkAdversePartySchema>;

// Tariff schedule schemas

export const createTariffScheduleSchema = z.object({
  name: z.string().min(1, "Name is required").max(100),
  code: z.string().min(1, "Code is required").max(20),
  description: z.string().max(500).optional().or(z.literal("")),
  effectiveFrom: z.string().min(1, "Effective from date is required"),
  effectiveTo: z.string().optional().or(z.literal("")),
});

export type CreateTariffScheduleFormData = z.infer<typeof createTariffScheduleSchema>;

export const createTariffItemSchema = z.object({
  itemNumber: z.string().min(1, "Item number is required").max(20),
  description: z.string().min(1, "Description is required").max(2000),
  unit: z.enum(
    ["PER_ITEM", "PER_PAGE", "PER_FOLIO", "PER_QUARTER_HOUR", "PER_HOUR", "PER_DAY"],
    { message: "Unit is required" }
  ),
  rateInCents: z.number().int().min(0, "Rate must be non-negative"),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreateTariffItemFormData = z.infer<typeof createTariffItemSchema>;
