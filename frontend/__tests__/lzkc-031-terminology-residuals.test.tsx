import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TerminologyProvider } from "@/lib/terminology";
import { AssignedTaskList } from "@/components/my-work/assigned-task-list";
import { AvailableTaskList } from "@/components/my-work/available-task-list";
import { UrgencyTaskList } from "@/components/my-work/urgency-task-list";
import { BillingRunSummaryCards } from "@/components/billing-runs/billing-run-summary-cards";
import { SendStep } from "@/components/billing-runs/send-step";
import { OnboardingPipelineSection } from "@/components/compliance/OnboardingPipelineSection";
import { DormancyCheckSection } from "@/components/compliance/DormancyCheckSection";
import { CustomerFinancialsTab } from "@/components/profitability/customer-financials-tab";
import type { MyWorkTaskItem } from "@/lib/types/project";
import type { BillingRun, BillingRunItem } from "@/lib/api/billing-runs";
import type { CustomerProfitabilityResponse } from "@/lib/types/billing";

// Task lists navigate on row click via the app router.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/test-org/my-work",
  useSearchParams: () => new URLSearchParams(),
}));

// DormancyCheckSection imports a server action module.
vi.mock("@/app/(app)/org/[slug]/compliance/actions", () => ({
  runDormancyCheck: vi.fn(() => Promise.resolve({ success: true, candidates: [] })),
}));

// SendStep loads items via server actions on mount.
vi.mock("@/app/(app)/org/[slug]/invoices/billing-runs/new/billing-step-actions", () => ({
  getItemsAction: vi.fn(() => Promise.resolve({ success: true, items: [sendStepItem()] })),
  getBillingRunAction: vi.fn(() => Promise.resolve({ success: true })),
  batchSendAction: vi.fn(() => Promise.resolve({ success: true })),
}));

afterEach(() => {
  cleanup();
});

function withProfile(profile: string | null, ui: React.ReactElement) {
  return render(<TerminologyProvider verticalProfile={profile}>{ui}</TerminologyProvider>);
}

function task(overrides: Partial<MyWorkTaskItem> = {}): MyWorkTaskItem {
  return {
    id: "t-1",
    projectId: "p-1",
    projectName: "Alpha",
    title: "Draft the thing",
    status: "OPEN",
    priority: "HIGH",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

function billingRun(): BillingRun {
  return {
    id: "br-1",
    name: "July run",
    status: "COMPLETED",
    periodFrom: "2026-07-01",
    periodTo: "2026-07-31",
    currency: "ZAR",
    includeExpenses: false,
    includeRetainers: false,
    totalCustomers: 3,
    totalInvoices: 4,
    totalAmount: 1000,
    totalSent: 4,
    totalFailed: 0,
    createdBy: "u-1",
    createdAt: "2026-07-31T00:00:00Z",
    updatedAt: "2026-07-31T00:00:00Z",
    completedAt: null,
  };
}

function sendStepItem(): BillingRunItem {
  return {
    id: "item-1",
    customerId: "c-1",
    customerName: "Dlamini Holdings",
    status: "APPROVED" as BillingRunItem["status"],
    unbilledTimeAmount: 100,
    unbilledExpenseAmount: 0,
    unbilledTimeCount: 1,
    unbilledExpenseCount: 0,
    totalUnbilledAmount: 100,
    hasPrerequisiteIssues: false,
    prerequisiteIssueReason: null,
    invoiceId: "abcdef12-0000-0000-0000-000000000000",
    failureReason: null,
  };
}

// ---- My Work task-table "Project" column headers ----

describe("LZKC-031: My Work task-table column headers", () => {
  it("AssignedTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <AssignedTaskList tasks={[task()]} slug="test-org" />);
    expect(screen.getByText("Matter")).toBeTruthy();
    expect(screen.queryByText("Project")).toBeNull();
  });

  it("AssignedTaskList keeps 'Project' column header without a profile", () => {
    withProfile(null, <AssignedTaskList tasks={[task()]} slug="test-org" />);
    expect(screen.getByText("Project")).toBeTruthy();
  });

  it("AvailableTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <AvailableTaskList tasks={[task({ id: "t-2" })]} slug="test-org" />);
    expect(screen.getByText("Matter")).toBeTruthy();
    expect(screen.queryByText("Project")).toBeNull();
  });

  it("UrgencyTaskList renders 'Matter' column header for legal-za", () => {
    withProfile("legal-za", <UrgencyTaskList tasks={[task({ id: "t-3" })]} slug="test-org" />);
    expect(screen.getAllByText("Matter").length).toBeGreaterThan(0);
    expect(screen.queryByText("Project")).toBeNull();
  });
});

// ---- Billing Runs summary cards + send-step table headers ----

describe("LZKC-031: Billing Runs terminology", () => {
  it("summary cards read 'Clients' / 'Fee Notes Generated' for legal-za", () => {
    withProfile("legal-za", <BillingRunSummaryCards billingRun={billingRun()} />);
    expect(screen.getByText("Clients")).toBeTruthy();
    expect(screen.getByText("Fee Notes Generated")).toBeTruthy();
    expect(screen.queryByText("Customers")).toBeNull();
    expect(screen.queryByText("Invoices Generated")).toBeNull();
  });

  it("summary cards keep 'Customers' / 'Invoices Generated' without a profile", () => {
    withProfile(null, <BillingRunSummaryCards billingRun={billingRun()} />);
    expect(screen.getByText("Customers")).toBeTruthy();
    expect(screen.getByText("Invoices Generated")).toBeTruthy();
  });

  it("send-step table headers read 'Client' / 'Fee Note #' for legal-za", async () => {
    withProfile(
      "legal-za",
      <SendStep slug="test-org" billingRunId="br-1" currency="ZAR" onBack={() => {}} />
    );
    expect(await screen.findByText("Client")).toBeTruthy();
    expect(screen.getByText("Fee Note #")).toBeTruthy();
    expect(screen.queryByText("Customer")).toBeNull();
    expect(screen.queryByText("Invoice #")).toBeNull();
  });
});

// ---- Compliance copy ----

describe("LZKC-031: Compliance terminology", () => {
  it("onboarding pipeline empty state reads 'No clients currently in onboarding' for legal-za", () => {
    withProfile("legal-za", <OnboardingPipelineSection customers={[]} orgSlug="test-org" />);
    expect(screen.getByText("No clients currently in onboarding")).toBeTruthy();
  });

  it("onboarding pipeline empty state keeps 'customers' without a profile", () => {
    withProfile(null, <OnboardingPipelineSection customers={[]} orgSlug="test-org" />);
    expect(screen.getByText("No customers currently in onboarding")).toBeTruthy();
  });

  it("dormancy check button reads 'Check for Dormant Clients' for legal-za", () => {
    withProfile("legal-za", <DormancyCheckSection orgSlug="test-org" />);
    expect(screen.getByRole("button", { name: "Check for Dormant Clients" })).toBeTruthy();
  });
});

// ---- Profitability headings ----

describe("LZKC-031: Profitability terminology", () => {
  const profitability: CustomerProfitabilityResponse = {
    customerId: "c-1",
    customerName: "Dlamini Holdings",
    currencies: [
      {
        currency: "ZAR",
        totalBillableHours: 10,
        totalNonBillableHours: 0,
        totalHours: 10,
        billableValue: 10000,
        costValue: null,
        margin: null,
        marginPercent: null,
      },
    ],
  };

  it("customer financials tab heading reads 'Client Profitability' for legal-za", () => {
    withProfile(
      "legal-za",
      <CustomerFinancialsTab profitability={profitability} projectBreakdown={null} />
    );
    expect(screen.getByText("Client Profitability")).toBeTruthy();
    expect(screen.queryByText("Customer Profitability")).toBeNull();
  });

  it("customer financials tab heading keeps 'Customer Profitability' without a profile", () => {
    withProfile(
      null,
      <CustomerFinancialsTab profitability={profitability} projectBreakdown={null} />
    );
    expect(screen.getByText("Customer Profitability")).toBeTruthy();
  });
});
