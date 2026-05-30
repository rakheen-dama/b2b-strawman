import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ClientOverviewTab } from "@/components/customers/client-overview-tab";

// Mock SetupProgressCard and TemplateReadinessCard as simple divs
vi.mock("@/components/setup/setup-progress-card", () => ({
  SetupProgressCard: (props: { title: React.ReactNode }) => (
    <div data-testid="setup-progress-card">{props.title}</div>
  ),
}));

vi.mock("@/components/setup/template-readiness-card", () => ({
  TemplateReadinessCard: (props: { baseHref: string }) => (
    <div data-testid="template-readiness-card">Templates ({props.baseHref})</div>
  ),
}));

vi.mock("@/components/compliance/LifecycleStatusBadge", () => ({
  LifecycleStatusBadge: (props: { status: string }) => (
    <span data-testid="lifecycle-status-badge">{props.status}</span>
  ),
}));

const baseProps = {
  setupProgressData: null,
  lifecyclePrompt: null,
  unbilledTimeData: null,
  activeRetainer: null,
  templateReadiness: null,
  pendingSuggestions: null,
  ficaPanel: null,
  customerName: "Acme Corp",
  lifecycleStatus: null,
  linkedProjectCount: 0,
};

describe("ClientOverviewTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Renders SetupProgressCard when setup data exists
  it("renders SetupProgressCard when setup data exists", () => {
    render(
      <ClientOverviewTab
        {...baseProps}
        setupProgressData={{
          title: "Client Readiness",
          completionPercentage: 50,
          overallComplete: false,
          steps: [{ label: "Projects linked", complete: true }],
          canManage: true,
        }}
      />
    );

    expect(screen.getByTestId("setup-progress-card")).toBeInTheDocument();
    expect(screen.getByText("Client Readiness")).toBeInTheDocument();
  });

  // Test 2: Hides SetupProgressCard when setup data is null
  it("hides SetupProgressCard when setup data is null", () => {
    render(
      <ClientOverviewTab
        {...baseProps}
        unbilledTimeData={{
          amount: "$1,000.00",
          hours: "10.0",
          createInvoiceHref: "?tab=invoices",
          viewTimeHref: "?tab=time",
        }}
      />
    );

    expect(screen.queryByTestId("setup-progress-card")).not.toBeInTheDocument();
    // Should still render something (the financial card)
    expect(screen.getByTestId("unbilled-time-card")).toBeInTheDocument();
  });

  // Test 3: Renders unbilled time and retainer cards in 2-column grid
  it("renders unbilled time and retainer cards in 2-column grid", () => {
    render(
      <ClientOverviewTab
        {...baseProps}
        unbilledTimeData={{
          amount: "$2,400.00",
          hours: "16.0",
          createInvoiceHref: "?tab=invoices",
          viewTimeHref: "?tab=time",
        }}
        activeRetainer={{
          name: "Monthly Retainer",
          status: "ACTIVE",
          allocatedHours: 40,
          consumedHours: 12,
          remainingHours: 28,
          periodStart: "2026-05-01",
          periodEnd: "2026-05-31",
        }}
      />
    );

    // Unbilled time card
    expect(screen.getByTestId("unbilled-time-card")).toBeInTheDocument();
    expect(screen.getByText("$2,400.00")).toBeInTheDocument();
    expect(screen.getByText("16.0 hours")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create Invoice" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View Time" })).toBeInTheDocument();

    // Retainer card
    expect(screen.getByTestId("retainer-status-card")).toBeInTheDocument();
    expect(screen.getByText("Monthly Retainer")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText(/\/ 40 hours used/)).toBeInTheDocument();
    expect(screen.getByText(/(28 remaining)/)).toBeInTheDocument();
  });

  // Test 4: Renders "everything looks good" state when all sections empty
  it("renders 'everything looks good' state when all sections empty", () => {
    render(
      <ClientOverviewTab
        {...baseProps}
        customerName="Acme Corp"
        lifecycleStatus="ACTIVE"
        linkedProjectCount={3}
      />
    );

    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByTestId("lifecycle-status-badge")).toBeInTheDocument();
    expect(screen.getByText("Everything looks good — 3 linked projects.")).toBeInTheDocument();
  });

  // Test 5: Renders AI suggestion and FICA panels when provided
  it("renders AI suggestion and FICA panels when provided", () => {
    render(
      <ClientOverviewTab
        {...baseProps}
        pendingSuggestions={<div data-testid="ai-suggestions">AI Suggestions Content</div>}
        ficaPanel={<div data-testid="fica-panel">FICA Verification Content</div>}
      />
    );

    expect(screen.getByTestId("ai-suggestions")).toBeInTheDocument();
    expect(screen.getByText("AI Suggestions Content")).toBeInTheDocument();
    expect(screen.getByTestId("fica-panel")).toBeInTheDocument();
    expect(screen.getByText("FICA Verification Content")).toBeInTheDocument();
  });
});
