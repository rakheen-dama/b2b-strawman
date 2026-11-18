import { z } from "zod";
import { todayInSA } from "@b2mash/shared/date";

export const createTrustAccountSchema = z
  .object({
    accountName: z.string().min(1, "Account name is required").max(200),
    bankName: z.string().min(1, "Bank name is required").max(200),
    branchCode: z.string().min(1, "Branch code is required").max(20),
    accountNumber: z.string().min(1, "Account number is required").max(30),
    accountType: z.enum(["GENERAL", "INVESTMENT", "SECTION_86"], {
      message: "Account type is required",
    }),
    /** ADR-379: required exactly when the type is INVESTMENT (the backend refuses it otherwise). */
    investmentBasis: z.enum(["FIRM_DISCRETION", "CLIENT_INSTRUCTION"]).optional(),
    openedDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
    isPrimary: z.boolean().optional(),
    requireDualApproval: z.boolean(),
    paymentApprovalThreshold: z.number().positive("Threshold must be positive").optional(),
    notes: z.string().max(2000).optional().or(z.literal("")),
  })
  .superRefine((data, ctx) => {
    if (data.accountType === "INVESTMENT" && data.investmentBasis === undefined) {
      ctx.addIssue({
        code: "custom",
        path: ["investmentBasis"],
        message: "Choose the investment basis",
      });
    }
  });

export type CreateTrustAccountFormData = z.infer<typeof createTrustAccountSchema>;

/**
 * G89-F28 (D36): every client trust posting names a matter — the client's portal trust view is
 * per matter, so a client-level row never reached it. The backend refuses a missing matter with
 * "A matter is required for client trust transactions".
 */
const requiredMatter = z
  .string()
  .min(1, "A matter is required for client trust transactions")
  .uuid("Must be a valid matter");

export const recordDepositSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  projectId: requiredMatter,
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordDepositFormData = z.infer<typeof recordDepositSchema>;

export const recordPaymentSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  projectId: requiredMatter,
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordPaymentFormData = z.infer<typeof recordPaymentSchema>;

export const recordTransferSchema = z.object({
  sourceCustomerId: z.string().uuid("Must be a valid source client UUID"),
  targetCustomerId: z.string().uuid("Must be a valid target client UUID"),
  /** The source client's matter: TRANSFER_OUT debits it. */
  projectId: requiredMatter,
  /** The target client's matter: TRANSFER_IN credits it (D39). */
  targetProjectId: requiredMatter,
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  description: z.string().max(2000).optional().or(z.literal("")),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
});

export type RecordTransferFormData = z.infer<typeof recordTransferSchema>;

export const recordFeeTransferSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  invoiceId: z.string().uuid("Must be a valid fee note UUID"),
  amount: z.number().positive("Amount must be greater than zero"),
  reference: z.string().min(1, "Reference is required").max(200),
  // Optional (ruling #6, 646D): the date the bank shows the transfer; blank = today. Never in the
  // future — the server refuses it too, with the same sentence.
  transactionDate: z
    .string()
    .refine((d) => d === "" || /^\d{4}-\d{2}-\d{2}$/.test(d), {
      message: "Date must be YYYY-MM-DD",
    })
    .refine((d) => d === "" || d <= todayInSA(), {
      message: "The fee transfer date cannot be in the future",
    })
    .optional(),
});

export type RecordFeeTransferFormData = z.infer<typeof recordFeeTransferSchema>;

export const recordRefundSchema = z.object({
  customerId: z.string().uuid("Must be a valid client UUID"),
  projectId: requiredMatter,
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
  // Optional: the date the bank shows the reversal (blank = today). The server is the authority
  // for "not in the future" and "not before the transaction's own date".
  reversalDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD")
    .optional()
    .or(z.literal("")),
});

export type ReversalReasonFormData = z.infer<typeof reversalReasonSchema>;

/**
 * Allocate an unidentified deposit to a client and matter (647D, §16.3.6). The allocation date —
 * the day the firm identified the owner — is optional (blank = today); the server is the authority
 * for its window (from the received date to today).
 */
export const allocateUnidentifiedSchema = z.object({
  customerId: z.string().uuid("Choose a client"),
  projectId: requiredMatter,
  allocationDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD")
    .optional()
    .or(z.literal("")),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type AllocateUnidentifiedFormData = z.infer<typeof allocateUnidentifiedSchema>;

/**
 * Pay an unidentified deposit to the Legal Practitioners' Fidelity Fund (647D, §16.3.6): the
 * payment's own reference and date. The server is the authority for the date's window.
 */
export const payToFundSchema = z.object({
  reference: z.string().trim().min(1, "Reference is required").max(200),
  transactionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD"),
  notes: z.string().max(2000).optional().or(z.literal("")),
});

export type PayToFundFormData = z.infer<typeof payToFundSchema>;

/**
 * Record from statement (647A, ADR-380): what an unmatched statement line is. A bank charge also
 * needs whether it is recoverable and its VAT; the server refuses both on any other
 * classification, and is the authority for every rule here.
 */
/** At most two decimal places (cents) — the column is numeric(15,2); tolerant of float error. */
const hasAtMostTwoDecimals = (value: number) =>
  Math.abs(value * 100 - Math.round(value * 100)) < 1e-6;

export const recordFromStatementSchema = z
  .object({
    classification: z.enum(
      [
        "FUND_INTEREST",
        "FUND_SWEEP",
        "BANK_CHARGE",
        "CHARGE_REFUND",
        "UNIDENTIFIED_DEPOSIT",
        "INVESTMENT_INTEREST",
      ],
      { message: "Choose what this line is" }
    ),
    chargeRecoverable: z.boolean().optional(),
    chargeVatAmount: z
      .number()
      .min(0, "VAT cannot be negative")
      .refine(hasAtMostTwoDecimals, "VAT has at most two decimal places")
      .optional(),
    // 648B: section 86(4) interest only — the investment, how the bank credited it, and (NET) the
    // gross the bank advised; a GROSS line's amount is the gross
    investmentId: z.string().optional(),
    presentation: z.enum(["GROSS", "NET"]).optional(),
    grossAmount: z
      .number()
      .positive("The gross interest must be more than R0,00")
      .refine(hasAtMostTwoDecimals, "The gross interest has at most two decimal places")
      .optional(),
    notes: z.string().max(2000).optional().or(z.literal("")),
  })
  .superRefine((data, ctx) => {
    if (data.classification === "INVESTMENT_INTEREST") {
      if (!data.investmentId) {
        ctx.addIssue({ code: "custom", path: ["investmentId"], message: "Choose the investment" });
      }
      if (data.presentation === undefined) {
        ctx.addIssue({
          code: "custom",
          path: ["presentation"],
          message: "Say whether the bank credited the gross or the net",
        });
      }
      if (
        data.presentation === "NET" &&
        (data.grossAmount === undefined || Number.isNaN(data.grossAmount))
      ) {
        ctx.addIssue({
          code: "custom",
          path: ["grossAmount"],
          message: "Enter the gross interest the bank advised",
        });
      }
      return;
    }
    if (data.classification !== "BANK_CHARGE") {
      return;
    }
    if (data.chargeRecoverable === undefined) {
      ctx.addIssue({
        code: "custom",
        path: ["chargeRecoverable"],
        message: "Say whether the charge is recoverable",
      });
    }
    if (data.chargeVatAmount === undefined || Number.isNaN(data.chargeVatAmount)) {
      ctx.addIssue({
        code: "custom",
        path: ["chargeVatAmount"],
        message: "Enter the VAT amount (0 if none)",
      });
    }
  });

export type RecordFromStatementFormData = z.infer<typeof recordFromStatementSchema>;

/** {@link recordFromStatementSchema} for a line of `chargeAmount`: the VAT must be below it. */
export const recordFromStatementSchemaFor = (chargeAmount: number) =>
  recordFromStatementSchema.superRefine((data, ctx) => {
    if (
      data.classification === "BANK_CHARGE" &&
      data.chargeVatAmount !== undefined &&
      data.chargeVatAmount >= chargeAmount
    ) {
      ctx.addIssue({
        code: "custom",
        path: ["chargeVatAmount"],
        message: "VAT must be less than the charge",
      });
    }
  });

/**
 * 650B (ADR-384): one month's at-source memo — the Rule 54.28 gross interest and gross charges
 * (with the VAT included in them) that never reached the trust account, and where they came from.
 * The month is yyyy-MM, never after the current month in South Africa; the server is the
 * authority for every rule here.
 */
const memoAmount = (label: string) =>
  z
    .number({ message: `Enter the ${label} (0 if none)` })
    .min(0, `The ${label} cannot be negative`)
    .refine(hasAtMostTwoDecimals, `The ${label} has at most two decimal places`);

export const saveInterestMemoSchema = z
  .object({
    month: z
      .string()
      .regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Choose the month")
      .refine((m) => m <= todayInSA().slice(0, 7), {
        message: "A memo can be saved for this month or an earlier one",
      }),
    grossInterest: memoAmount("gross interest"),
    grossCharges: memoAmount("gross charges"),
    chargesVat: memoAmount("VAT"),
    sourceNote: z
      .string()
      .trim()
      .min(1, "Say where the figures come from")
      .max(500, "At most 500 characters"),
  })
  .superRefine((data, ctx) => {
    if (data.chargesVat > data.grossCharges) {
      ctx.addIssue({
        code: "custom",
        path: ["chargesVat"],
        message: "The VAT cannot be more than the gross charges",
      });
    }
  });

export type SaveInterestMemoFormData = z.infer<typeof saveInterestMemoSchema>;

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

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date must be YYYY-MM-DD");

/**
 * 648A (ADR-379): an investment is placed into an investment account of its basis. A section
 * 86(4) investment names the client and the matter whose money it is; a section 86(3) investment
 * is the firm's surplus trust money and names neither (the backend refuses both mismatches).
 */
export const placeInvestmentSchema = z
  .object({
    investmentBasis: z.enum(["FIRM_DISCRETION", "CLIENT_INSTRUCTION"], {
      message: "Investment basis is required",
    }),
    investmentAccountId: z
      .string()
      .min(1, "Choose the investment account")
      .uuid("Choose the investment account"),
    customerId: z.string().optional().or(z.literal("")),
    projectId: z.string().optional().or(z.literal("")),
    institution: z.string().min(1, "Institution is required").max(200),
    accountNumber: z.string().min(1, "Account number is required").max(50),
    principal: z.number().positive("Principal must be greater than zero"),
    interestRate: z.number().min(0, "Rate must be non-negative").max(100, "Rate cannot exceed 100"),
    // Never in the future — the server refuses it too, with the same sentence (648A, CodeRabbit
    // cycle 3: a later-dated placement would escape the cover read as at today)
    depositDate: isoDate.refine((d) => d <= todayInSA(), {
      message: "The deposit date cannot be in the future.",
    }),
    maturityDate: isoDate.optional().or(z.literal("")),
    notes: z.string().max(2000).optional().or(z.literal("")),
  })
  .superRefine((data, ctx) => {
    const isUuid = (value: string) => z.string().uuid().safeParse(value).success;
    if (data.investmentBasis === "CLIENT_INSTRUCTION") {
      if (!data.customerId) {
        ctx.addIssue({
          code: "custom",
          path: ["customerId"],
          message: "A client is required for a section 86(4) investment",
        });
      } else if (!isUuid(data.customerId)) {
        ctx.addIssue({ code: "custom", path: ["customerId"], message: "Must be a valid client" });
      }
      if (!data.projectId) {
        ctx.addIssue({
          code: "custom",
          path: ["projectId"],
          message: "A matter is required for client trust transactions",
        });
      } else if (!isUuid(data.projectId)) {
        ctx.addIssue({ code: "custom", path: ["projectId"], message: "Must be a valid matter" });
      }
    } else {
      if (data.customerId) {
        ctx.addIssue({
          code: "custom",
          path: ["customerId"],
          message: "A section 86(3) investment names no client",
        });
      }
      if (data.projectId) {
        ctx.addIssue({
          code: "custom",
          path: ["projectId"],
          message: "A section 86(3) investment names no matter",
        });
      }
    }
  });

export type PlaceInvestmentFormData = z.infer<typeof placeInvestmentSchema>;

/**
 * 648A: the whole investment returns on the date; the amount is derived, never typed. Never in
 * the future — the server refuses it too, with the same sentence (review cycle 4). Not before the
 * investment's deposit date either: that bound is per investment, so the dialog holds it (input
 * `min` + a submit check with the server's sentence).
 */
export const withdrawInvestmentSchema = z.object({
  withdrawalDate: isoDate.refine((d) => d <= todayInSA(), {
    message: "The withdrawal date cannot be in the future.",
  }),
});

export type WithdrawInvestmentFormData = z.infer<typeof withdrawInvestmentSchema>;

/**
 * 648B (ADR-379): one section 86(4) interest entry — the date the bank credited it, the gross, how
 * it was credited (GROSS: the Fund's 5% leaves in a separate debit; NET: the 5% went at source),
 * optionally the Fund's share as the bank shows it (within R0,01 of 5%, checked in the dialog
 * against the gross) and the investment account's statement line it was credited on. Per
 * investment bounds (not before the deposit date) are held in the dialog, like the withdrawal.
 */
export const recordInvestmentInterestSchema = z.object({
  entryDate: isoDate.refine((d) => d <= todayInSA(), {
    message: "The entry date cannot be in the future.",
  }),
  grossAmount: z
    .number({ message: "Enter the gross interest" })
    .positive("The gross interest must be more than R0,00")
    .refine(hasAtMostTwoDecimals, "The gross interest has at most two decimal places"),
  presentation: z.enum(["GROSS", "NET"], {
    message: "Say whether the bank credited the gross or the net",
  }),
  fundShareAmount: z
    .number()
    .min(0, "The Fund's share must be zero or more, in rand and cents.")
    .refine(hasAtMostTwoDecimals, "The Fund's share must be zero or more, in rand and cents.")
    .optional(),
  bankStatementLineId: z.string().optional().or(z.literal("")),
});

export type RecordInvestmentInterestFormData = z.infer<typeof recordInvestmentInterestSchema>;
