// ---- Invoices (from InvoiceController.java) ----

import type { ExpenseCategory } from "./expense";

export type InvoiceStatus = "DRAFT" | "APPROVED" | "SENT" | "PAID" | "VOID";

export type TaxType = "VAT" | "GST" | "SALES_TAX" | "NONE";

export type InvoiceLineType = "TIME" | "EXPENSE" | "RETAINER" | "MANUAL" | "TARIFF" | "FIXED_FEE";

export interface InvoiceLineResponse {
  id: string;
  projectId: string | null;
  projectName: string | null;
  timeEntryId: string | null;
  expenseId: string | null;
  lineType: InvoiceLineType;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  sortOrder: number;
  taxRateId: string | null;
  taxRateName: string | null;
  taxRatePercent: number | null;
  taxAmount: number | null;
  taxExempt: boolean;
  tariffItemId: string | null;
  lineSource: string | null;
}

export interface TaxBreakdownEntry {
  taxRateName: string;
  taxRatePercent: number;
  taxableAmount: number;
  taxAmount: number;
}

export interface InvoiceResponse {
  id: string;
  customerId: string;
  invoiceNumber: string | null;
  status: InvoiceStatus;
  currency: string;
  issueDate: string | null;
  dueDate: string | null;
  subtotal: number;
  taxAmount: number;
  total: number;
  notes: string | null;
  paymentTerms: string | null;
  poNumber: string | null;
  taxType: TaxType | null;
  billingPeriodStart: string | null;
  billingPeriodEnd: string | null;
  paymentReference: string | null;
  paidAt: string | null;
  customerName: string;
  customerEmail: string | null;
  customerAddress: string | null;
  orgName: string;
  createdBy: string;
  createdByName: string | null;
  approvedBy: string | null;
  approvedByName: string | null;
  createdAt: string;
  updatedAt: string;
  lines: InvoiceLineResponse[];
  paymentSessionId: string | null;
  paymentUrl: string | null;
  paymentDestination: string | null;
  customFields?: Record<string, unknown>;
  appliedFieldGroups?: string[];
  taxBreakdown: TaxBreakdownEntry[];
  taxInclusive: boolean;
  taxRegistrationNumber: string | null;
  taxRegistrationLabel: string | null;
  taxLabel: string | null;
  hasPerLineTax: boolean;
}

export type PaymentEventStatus =
  | "CREATED"
  | "PENDING"
  | "COMPLETED"
  | "FAILED"
  | "EXPIRED"
  | "CANCELLED";

export interface PaymentEvent {
  id: string;
  providerSlug: string;
  sessionId: string | null;
  paymentReference: string | null;
  status: PaymentEventStatus;
  amount: number;
  currency: string;
  paymentDestination: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInvoiceDraftRequest {
  customerId: string;
  currency: string;
  timeEntryIds: string[];
  expenseIds?: string[];
  disbursementIds?: string[];
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
}

export interface UpdateInvoiceRequest {
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
  taxAmount?: number;
  poNumber?: string;
  taxType?: TaxType;
  billingPeriodStart?: string;
  billingPeriodEnd?: string;
}

export interface AddLineItemRequest {
  projectId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  sortOrder?: number;
  taxRateId?: string | null;
  tariffItemId?: string;
}

export interface UpdateLineItemRequest {
  description: string;
  quantity: number;
  unitPrice: number;
  sortOrder?: number;
  taxRateId?: string | null;
}

export interface RecordPaymentRequest {
  paymentReference?: string;
}

export interface CurrencyTotal {
  hours: number;
  amount: number;
}

export interface UnbilledTimeEntry {
  id: string;
  taskTitle: string;
  memberName: string;
  date: string;
  durationMinutes: number;
  billingRateSnapshot: number | null;
  billingRateCurrency: string | null;
  billableValue: number | null;
  description: string | null;
  rateSource: "SNAPSHOT" | "RESOLVED" | null;
}

export interface UnbilledProjectGroup {
  projectId: string;
  projectName: string;
  entries: UnbilledTimeEntry[];
  totals: Record<string, CurrencyTotal>;
}

export interface UnbilledTimeResponse {
  customerId: string;
  customerName: string;
  projects: UnbilledProjectGroup[];
  grandTotals: Record<string, CurrencyTotal>;
  unbilledExpenses: UnbilledExpenseEntry[];
  unbilledExpenseTotals: Record<string, number>;
  /**
   * Legal disbursements (slice 488A). Populated only when the
   * `disbursements` vertical module is enabled; omitted or empty otherwise,
   * keeping the response byte-compatible with non-legal tenants.
   * Currency is implied ZAR for the MVP (matches backend).
   */
  disbursements?: UnbilledDisbursementEntry[];
}

/**
 * Compact, read-only view of an approved disbursement eligible for billing.
 * Mirrors `UnbilledDisbursementDto` on the backend (487A read model).
 */
export interface UnbilledDisbursementEntry {
  id: string;
  incurredDate: string;
  category: string;
  description: string;
  amount: number;
  vatTreatment: string;
  vatAmount: number;
  supplierName: string;
}

export interface UnbilledExpenseEntry {
  id: string;
  projectId: string;
  projectName: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  category: ExpenseCategory;
  markupPercent: number | null;
  billableAmount: number;
  notes: string | null;
}

// ---- Invoice Validation ----

export type ValidationSeverity = "INFO" | "WARNING" | "CRITICAL";

export interface ValidationCheck {
  name: string;
  severity: ValidationSeverity;
  passed: boolean;
  message: string;
}

// ---- Retainers (shared types for client components — API functions live in @/lib/api/retainers) ----

export type RetainerType = "HOUR_BANK" | "FIXED_FEE";

export interface RetainerSummaryResponse {
  hasActiveRetainer: boolean;
  agreementId: string | null;
  agreementName: string | null;
  type: RetainerType | null;
  allocatedHours: number | null;
  consumedHours: number | null;
  remainingHours: number | null;
  percentConsumed: number | null;
  isOverage: boolean;
  periodStart: string | null;
  periodEnd: string | null;
}
