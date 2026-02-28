import type { ExpenseResponse, ExpenseCategory, ExpenseBillingStatus } from "@/lib/types";

export function makeExpense(overrides: Partial<ExpenseResponse> = {}): ExpenseResponse {
  return {
    id: "exp1",
    projectId: "p1",
    taskId: null,
    memberId: "m1",
    memberName: "Alice Johnson",
    date: "2026-02-15",
    description: "Court filing fee",
    amount: 250.0,
    currency: "ZAR",
    category: "FILING_FEE" as ExpenseCategory,
    receiptDocumentId: null,
    billable: true,
    billingStatus: "UNBILLED" as ExpenseBillingStatus,
    invoiceId: null,
    markupPercent: null,
    billableAmount: 250.0,
    notes: null,
    createdAt: "2026-02-15T10:00:00Z",
    updatedAt: "2026-02-15T10:00:00Z",
    ...overrides,
  };
}
