import { z } from "zod";

export const createTrustAccountSchema = z.object({
  accountName: z.string().min(1, "Account name is required").max(200),
  bankName: z.string().min(1, "Bank name is required").max(200),
  branchCode: z.string().min(1, "Branch code is required").max(20),
  accountNumber: z.string().min(1, "Account number is required").max(30),
  accountType: z.enum(["GENERAL", "INVESTMENT"], {
    message: "Account type is required",
  }),
  openedDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
  isPrimary: z.boolean().optional(),
  requireDualApproval: z.boolean(),
  paymentApprovalThreshold: z
    .number()
    .positive("Threshold must be positive")
    .optional(),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type CreateTrustAccountFormData = z.infer<
  typeof createTrustAccountSchema
>;

export const recordDepositSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordDepositFormData = z.infer<typeof recordDepositSchema>;

export const recordPaymentSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordPaymentFormData = z.infer<typeof recordPaymentSchema>;

export const recordTransferSchema = z.object({
  sourceCustomerId: z.string().uuid("Must be a valid source client UUID"),
  targetCustomerId: z.string().uuid("Must be a valid target client UUID"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordTransferFormData = z.infer<typeof recordTransferSchema>;

export const recordFeeTransferSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  invoiceId: z.string().uuid("Must be a valid invoice UUID"),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
});

export type RecordFeeTransferFormData = z.infer<
  typeof recordFeeTransferSchema
>;

export const recordRefundSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordRefundFormData = z.infer<typeof recordRefundSchema>;

export const rejectionReasonSchema = z.object({
  reason: z.string().min(1, "Reason is required").max(500),
});

export type RejectionReasonFormData = z.infer<typeof rejectionReasonSchema>;

export const reversalReasonSchema = z.object({
  reason: z.string().min(1, "Reason is required").max(500),
});

export type ReversalReasonFormData = z.infer<typeof reversalReasonSchema>;

export const createInterestRunSchema = z
  .object({
    periodStart: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
    periodEnd: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
  })
  .refine((data) => data.periodEnd > data.periodStart, {
    message: "End date must be after start date",
    path: ["periodEnd"],
  });

export type CreateInterestRunFormData = z.infer<typeof createInterestRunSchema>;

export const addLpffRateSchema = z.object({
  effectiveFrom: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
  ratePercent: z.number().positive("Rate must be greater than zero"),
  lpffSharePercent: z
    .number()
    .min(0, "Share must be at least 0")
    .max(100, "Share cannot exceed 100"),
  notes: z.string().max(500).optional().or(z.literal("")),
});

export type AddLpffRateFormData = z.infer<typeof addLpffRateSchema>;
