import { z } from "zod";

export const createCourtDateSchema = z.object({
  projectId: z.string().uuid("Please select a matter"),
  dateType: z.enum(
    [
      "HEARING",
      "TRIAL",
      "MOTION",
      "MEDIATION",
      "ARBITRATION",
      "PRE_TRIAL",
      "CASE_MANAGEMENT",
      "TAXATION",
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
      "MEDIATION",
      "ARBITRATION",
      "PRE_TRIAL",
      "CASE_MANAGEMENT",
      "TAXATION",
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

export type PostponeCourtDateFormData = z.infer<typeof postponeCourtDateSchema>;

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
      ["GENERAL_3Y", "DEBT_6Y", "MORTGAGE_30Y", "DELICT_3Y", "CONTRACT_3Y", "CUSTOM"],
      { message: "Prescription type is required" }
    ),
    customYears: z.number().int().min(1).max(100).optional(),
    notes: z.string().max(2000).optional().or(z.literal("")),
  })
  .refine(
    (data) =>
      data.prescriptionType !== "CUSTOM" || (data.customYears != null && data.customYears >= 1),
    { message: "Custom years is required for Custom type", path: ["customYears"] }
  );

export type CreatePrescriptionTrackerFormData = z.infer<typeof createPrescriptionTrackerSchema>;

export const interruptPrescriptionSchema = z.object({
  interruptionDate: z.string().min(1, "Interruption date is required"),
  interruptionReason: z.string().min(1, "Reason is required").max(2000),
});

export type InterruptPrescriptionFormData = z.infer<typeof interruptPrescriptionSchema>;

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

export type PerformConflictCheckFormData = z.infer<typeof performConflictCheckSchema>;

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
    ["NATURAL_PERSON", "COMPANY", "TRUST", "CLOSE_CORPORATION", "PARTNERSHIP", "OTHER"],
    { message: "Party type is required" }
  ),
  aliases: z.string().max(1000).optional().or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreateAdversePartyFormData = z.infer<typeof createAdversePartySchema>;

export const linkAdversePartySchema = z.object({
  projectId: z.string().uuid("Please select a matter"),
  customerId: z.string().uuid("Please select a customer"),
  relationship: z.enum(["OPPOSING_PARTY", "WITNESS", "CO_ACCUSED", "RELATED_ENTITY", "GUARANTOR"], {
    message: "Relationship is required",
  }),
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
  unit: z.enum(["PER_ITEM", "PER_PAGE", "PER_FOLIO", "PER_QUARTER_HOUR", "PER_HOUR", "PER_DAY"], {
    message: "Unit is required",
  }),
  amount: z.number().min(0, "Amount must be non-negative"),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreateTariffItemFormData = z.infer<typeof createTariffItemSchema>;

// Disbursement schemas (Phase 67, Epic 488)

const disbursementCategoryEnum = z.enum(
  [
    "SHERIFF_FEES",
    "COUNSEL_FEES",
    "SEARCH_FEES",
    "DEEDS_OFFICE_FEES",
    "COURT_FEES",
    "ADVOCATE_FEES",
    "EXPERT_WITNESS",
    "TRAVEL",
    "OTHER",
  ],
  { message: "Category is required" }
);

const vatTreatmentEnum = z.enum(["STANDARD_15", "ZERO_RATED_PASS_THROUGH", "EXEMPT"], {
  message: "VAT treatment is required",
});

const paymentSourceEnum = z.enum(["OFFICE_ACCOUNT", "TRUST_ACCOUNT"], {
  message: "Payment source is required",
});

export const createDisbursementSchema = z
  .object({
    projectId: z.string().uuid("Please select a matter"),
    customerId: z.string().uuid("Please select a customer"),
    category: disbursementCategoryEnum,
    description: z.string().min(1, "Description is required").max(5000),
    amount: z
      .number()
      .gt(0, "Amount must be greater than zero")
      .lte(10_000_000, "Amount must not exceed 10,000,000"),
    vatTreatment: vatTreatmentEnum,
    paymentSource: paymentSourceEnum,
    trustTransactionId: z
      .string()
      .uuid("Trust transaction must be a valid id")
      .optional()
      .or(z.literal("")),
    incurredDate: z.string().min(1, "Incurred date is required"),
    supplierName: z.string().min(1, "Supplier name is required").max(200),
    supplierReference: z.string().max(100).optional().or(z.literal("")),
    receiptDocumentId: z.string().uuid().optional().or(z.literal("")),
  })
  .refine(
    (data) =>
      data.paymentSource !== "TRUST_ACCOUNT" ||
      (data.trustTransactionId != null && data.trustTransactionId !== ""),
    {
      message: "Trust transaction is required when payment source is trust account",
      path: ["trustTransactionId"],
    }
  );

export type CreateDisbursementFormData = z.infer<typeof createDisbursementSchema>;

export const editDisbursementSchema = z.object({
  category: disbursementCategoryEnum,
  description: z.string().min(1, "Description is required").max(5000),
  amount: z
    .number()
    .gt(0, "Amount must be greater than zero")
    .lte(10_000_000, "Amount must not exceed 10,000,000"),
  vatTreatment: vatTreatmentEnum,
  incurredDate: z.string().min(1, "Incurred date is required"),
  supplierName: z.string().min(1, "Supplier name is required").max(200),
  supplierReference: z.string().max(100).optional().or(z.literal("")),
});

export type EditDisbursementFormData = z.infer<typeof editDisbursementSchema>;

export const approvalNotesSchema = z.object({
  notes: z.string().trim().max(2000).optional().or(z.literal("")),
});

export type ApprovalNotesFormData = z.infer<typeof approvalNotesSchema>;

export const rejectionNotesSchema = z.object({
  notes: z.string().trim().min(1, "Reason is required").max(2000),
});

export type RejectionNotesFormData = z.infer<typeof rejectionNotesSchema>;
