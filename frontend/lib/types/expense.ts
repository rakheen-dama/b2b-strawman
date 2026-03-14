// ---- Expenses (from ExpenseController.java) ----

export type ExpenseCategory =
  | "FILING_FEE"
  | "TRAVEL"
  | "COURIER"
  | "SOFTWARE"
  | "SUBCONTRACTOR"
  | "PRINTING"
  | "COMMUNICATION"
  | "OTHER";

export type ExpenseBillingStatus = "BILLED" | "NON_BILLABLE" | "UNBILLED";

export interface ExpenseResponse {
  id: string;
  projectId: string;
  taskId: string | null;
  memberId: string;
  memberName: string | null;
  date: string;               // YYYY-MM-DD
  description: string;
  amount: number;
  currency: string;
  category: ExpenseCategory;
  receiptDocumentId: string | null;
  billable: boolean;
  billingStatus: ExpenseBillingStatus;
  invoiceId: string | null;
  markupPercent: number | null;
  billableAmount: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExpenseRequest {
  date: string;
  description: string;
  amount: number;
  currency?: string;
  category: ExpenseCategory;
  taskId?: string | null;
  receiptDocumentId?: string | null;
  markupPercent?: number | null;
  billable?: boolean;
  notes?: string | null;
}

export interface UpdateExpenseRequest {
  date?: string;
  description?: string;
  amount?: number;
  currency?: string;
  category?: ExpenseCategory;
  taskId?: string | null;
  receiptDocumentId?: string | null;
  markupPercent?: number | null;
  billable?: boolean;
  notes?: string | null;
}

export interface PaginatedExpenseResponse {
  content: ExpenseResponse[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}
