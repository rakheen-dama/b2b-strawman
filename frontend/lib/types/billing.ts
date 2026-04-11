// ---- BillingRate (from BillingRateController.java) ----

export type BillingRateScope = "MEMBER_DEFAULT" | "PROJECT_OVERRIDE" | "CUSTOMER_OVERRIDE";

export interface BillingRate {
  id: string;
  memberId: string;
  memberName: string;
  projectId: string | null;
  projectName: string | null;
  customerId: string | null;
  customerName: string | null;
  scope: BillingRateScope;
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBillingRateRequest {
  memberId: string;
  projectId?: string;
  customerId?: string;
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface UpdateBillingRateRequest {
  currency: string;
  hourlyRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface ResolvedRate {
  hourlyRate: number;
  currency: string;
  source: string;
  billingRateId: string;
}

// ---- CostRate (from CostRateController.java) ----

export interface CostRate {
  id: string;
  memberId: string;
  memberName: string;
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCostRateRequest {
  memberId: string;
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

export interface UpdateCostRateRequest {
  currency: string;
  hourlyCost: number;
  effectiveFrom: string;
  effectiveTo?: string;
}

// ---- Budget (from ProjectBudgetController.java) ----

export type BudgetStatus = "ON_TRACK" | "AT_RISK" | "OVER_BUDGET";

export interface BudgetStatusResponse {
  projectId: string;
  budgetHours: number | null;
  budgetAmount: number | null;
  budgetCurrency: string | null;
  alertThresholdPct: number;
  notes: string | null;
  hoursConsumed: number;
  hoursRemaining: number;
  hoursConsumedPct: number;
  amountConsumed: number;
  amountRemaining: number;
  amountConsumedPct: number;
  hoursStatus: BudgetStatus;
  amountStatus: BudgetStatus;
  overallStatus: BudgetStatus;
}

export interface LightweightBudgetStatus {
  hoursConsumedPct: number;
  amountConsumedPct: number;
  hoursStatus: BudgetStatus | null;
  amountStatus: BudgetStatus | null;
  overallStatus: BudgetStatus | null;
}

export interface UpsertBudgetRequest {
  budgetHours?: number;
  budgetAmount?: number;
  budgetCurrency?: string;
  alertThresholdPct?: number;
  notes?: string;
}

// ---- Profitability Reports (from ReportController.java) ----

export interface MemberValueBreakdown {
  currency: string;
  billableValue: number;
  costValue: number;
}

export interface MemberUtilizationRecord {
  memberId: string;
  memberName: string;
  totalHours: number;
  billableHours: number;
  nonBillableHours: number;
  utilizationPercent: number;
  currencies: MemberValueBreakdown[];
}

export interface UtilizationResponse {
  from: string;
  to: string;
  members: MemberUtilizationRecord[];
}

export interface ProjectProfitabilitySummary {
  projectId: string;
  projectName: string;
  customerName: string | null;
  currency: string;
  billableHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
}

export interface OrgProfitabilityResponse {
  projects: ProjectProfitabilitySummary[];
}

export interface CurrencyBreakdown {
  currency: string;
  totalBillableHours: number;
  totalNonBillableHours: number;
  totalHours: number;
  billableValue: number;
  costValue: number | null;
  margin: number | null;
  marginPercent: number | null;
}

export interface ProjectProfitabilityResponse {
  projectId: string;
  projectName: string;
  currencies: CurrencyBreakdown[];
}

export interface CustomerProfitabilityResponse {
  customerId: string;
  customerName: string;
  currencies: CurrencyBreakdown[];
}

// ---- Tax Rates (from TaxRateController.java) ----

export interface TaxRateResponse {
  id: string;
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaxRateRequest {
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  sortOrder: number;
}

export interface UpdateTaxRateRequest {
  name: string;
  rate: number;
  isDefault: boolean;
  isExempt: boolean;
  active: boolean;
  sortOrder: number;
}
