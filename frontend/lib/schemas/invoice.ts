import { z } from "zod";

// Fee-note Record Payment form (Phase 88 U15, ADR-347). Amount defaults to the balance
// due; an amount above the balance is refused by the backend (409), not here.
export const recordInvoicePaymentSchema = z.object({
  amount: z.number().positive("Amount must be greater than zero"),
  paymentMethod: z.enum(["EFT", "CARD", "CASH", "OTHER"]),
  paymentReference: z.string().max(255).optional().or(z.literal("")),
});

export type RecordInvoicePaymentFormData = z.infer<typeof recordInvoicePaymentSchema>;
