export interface CalculatedDeadline {
  customerId: string;
  customerName: string;
  deadlineTypeSlug: string;
  deadlineTypeName: string;
  category: "tax" | "corporate" | "vat" | "payroll";
  dueDate: string; // ISO date "2026-08-31"
  status: "pending" | "filed" | "overdue" | "not_applicable";
  linkedProjectId: string | null;
  filingStatusId: string | null;
}

export interface DeadlineSummary {
  month: string; // "2026-03"
  category: string;
  total: number;
  filed: number;
  pending: number;
  overdue: number;
}

export interface FilingStatusRequest {
  customerId: string;
  deadlineTypeSlug: string;
  periodKey: string; // "2026", "2026-01", "2026-Q1"
  status: "filed" | "not_applicable";
  notes?: string;
  linkedProjectId?: string;
}

export interface DeadlineFiltersType {
  category?: string; // "tax" | "corporate" | "vat" | "payroll"
  status?: string; // "pending" | "filed" | "overdue" | "not_applicable"
  customerId?: string;
  from: string; // ISO date
  to: string; // ISO date
}
