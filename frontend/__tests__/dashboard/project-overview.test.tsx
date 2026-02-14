import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OverviewHealthHeader } from "@/components/projects/overview-health-header";
import { OverviewMetricsStrip } from "@/components/projects/overview-metrics-strip";
import type { ProjectHealthDetail, ProjectHealthMetrics, MemberHoursEntry } from "@/lib/dashboard-types";
import type { BudgetStatusResponse } from "@/lib/types";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockMetrics: ProjectHealthMetrics = {
  tasksDone: 20,
  tasksInProgress: 10,
  tasksTodo: 4,
  tasksOverdue: 6,
  totalTasks: 40,
  completionPercent: 50.0,
  budgetConsumedPercent: 85.0,
  hoursThisPeriod: 32.5,
  daysSinceLastActivity: 3,
};

const mockHealth: ProjectHealthDetail = {
  healthStatus: "AT_RISK",
  healthReasons: ["Budget 85% consumed", "6 overdue tasks"],
  metrics: mockMetrics,
};

const mockMemberHours: MemberHoursEntry[] = [
  { memberId: "m1", memberName: "Alice Johnson", totalHours: 20.0, billableHours: 16.5 },
  { memberId: "m2", memberName: "Bob Smith", totalHours: 12.5, billableHours: 10.0 },
];

const mockBudgetStatus: BudgetStatusResponse = {
  projectId: "p1",
  budgetHours: 100,
  budgetAmount: 10000,
  budgetCurrency: "USD",
  alertThresholdPct: 80,
  notes: null,
  hoursConsumed: 85,
  hoursRemaining: 15,
  hoursConsumedPct: 85,
  amountConsumed: 8500,
  amountRemaining: 1500,
  amountConsumedPct: 85,
  hoursStatus: "AT_RISK",
  amountStatus: "AT_RISK",
  overallStatus: "AT_RISK",
};

describe("OverviewHealthHeader", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders health badge with correct status and reasons", () => {
    render(
      <OverviewHealthHeader
        health={mockHealth}
        projectName="Website Redesign"
        customerName="Acme Corp"
      />
    );

    expect(screen.getByText("At Risk")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Customer: Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("Budget 85% consumed")).toBeInTheDocument();
    expect(screen.getByText("6 overdue tasks")).toBeInTheDocument();
  });

  it("renders unknown status when health is null", () => {
    render(
      <OverviewHealthHeader
        health={null}
        projectName="Test Project"
        customerName={null}
      />
    );

    expect(screen.getByText("Unknown")).toBeInTheDocument();
    expect(screen.getByText("Test Project")).toBeInTheDocument();
    expect(screen.queryByText(/Customer:/)).not.toBeInTheDocument();
  });
});

describe("OverviewMetricsStrip", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders metrics strip with task counts and hours", () => {
    render(
      <OverviewMetricsStrip
        metrics={mockMetrics}
        memberHours={mockMemberHours}
        budgetStatus={mockBudgetStatus}
        marginPercent={34.2}
        showMargin={true}
      />
    );

    // Tasks: 20/40
    expect(screen.getByText("Tasks")).toBeInTheDocument();
    expect(screen.getByText("20/40")).toBeInTheDocument();
    expect(screen.getByText("complete")).toBeInTheDocument();

    // Hours: 32.5h (sum of 20.0 + 12.5)
    expect(screen.getByText("Hours")).toBeInTheDocument();
    expect(screen.getByText("32.5h")).toBeInTheDocument();

    // Budget: 85% used
    expect(screen.getByText("Budget")).toBeInTheDocument();
    expect(screen.getByText("used")).toBeInTheDocument();
    // 85% appears in both the value and the progress bar, so check at least one exists
    expect(screen.getAllByText("85%").length).toBeGreaterThanOrEqual(1);

    // Margin: 34.2%
    expect(screen.getByText("Margin")).toBeInTheDocument();
    expect(screen.getByText("34.2%")).toBeInTheDocument();
  });

  it("handles Phase 8 endpoint failures gracefully (budget/margin null)", () => {
    render(
      <OverviewMetricsStrip
        metrics={mockMetrics}
        memberHours={mockMemberHours}
        budgetStatus={null}
        marginPercent={null}
        showMargin={true}
      />
    );

    // Budget shows "No budget"
    expect(screen.getByText("No budget")).toBeInTheDocument();

    // Margin shows "--"
    expect(screen.getByText("--")).toBeInTheDocument();
  });

  it("hides margin for non-managers", () => {
    render(
      <OverviewMetricsStrip
        metrics={mockMetrics}
        memberHours={mockMemberHours}
        budgetStatus={null}
        marginPercent={34.2}
        showMargin={false}
      />
    );

    // Margin shows "--" even when marginPercent has a value, because showMargin=false
    expect(screen.getByText("--")).toBeInTheDocument();
    expect(screen.queryByText("34.2%")).not.toBeInTheDocument();
  });
});
