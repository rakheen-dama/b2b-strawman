import { z } from "zod";

export const createTrustAccountSchema = z.object({
  accountName: z.string().min(1, "Account name is required").max(200),
  bankName: z.string().min(1, "Bank name is required").max(200),
  branchCode: z.string().min(1, "Branch code is required").max(20),
  accountNumber: z.string().min(1, "Account number is required").max(30),
  accountType: z.enum(["GENERAL", "INVESTMENT"], {
    message: "Account type is required",
  }),
  openedDate: z.string().min(1, "Opened date is required"),
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
  customerId: z.string().uuid("Please select a client"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().min(1, "Transaction date is required"),
});

export type RecordDepositFormData = z.infer<typeof recordDepositSchema>;

export const recordPaymentSchema = z.object({
  customerId: z.string().uuid("Please select a client"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().min(1, "Transaction date is required"),
});

export type RecordPaymentFormData = z.infer<typeof recordPaymentSchema>;

export const recordTransferSchema = z.object({
  sourceCustomerId: z.string().uuid("Please select source client"),
  targetCustomerId: z.string().uuid("Please select target client"),
  projectId: z.string().uuid().optional().or(z.literal("")),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().min(1, "Transaction date is required"),
});

export type RecordTransferFormData = z.infer<typeof recordTransferSchema>;

export const recordFeeTransferSchema = z.object({
  customerId: z.string().uuid("Please select a client"),
  invoiceId: z.string().uuid("Please select an invoice"),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
});

export type RecordFeeTransferFormData = z.infer<
  typeof recordFeeTransferSchema
>;

export const recordRefundSchema = z.object({
  customerId: z.string().uuid("Please select a client"),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().min(1, "Transaction date is required"),
});

export type RecordRefundFormData = z.infer<typeof recordRefundSchema>;
