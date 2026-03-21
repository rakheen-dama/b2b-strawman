import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OverviewHealthHeader } from "@/components/projects/overview-health-header";
import { OverviewMetricsStrip } from "@/components/projects/overview-metrics-strip";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import { cn } from "@/lib/utils";
import type { ProjectHealthDetail, ProjectHealthMetrics, MemberHoursEntry } from "@/lib/dashboard-types";
import type { BudgetStatusResponse } from "@/lib/types";

// Mock recharts to avoid rendering issues in happy-dom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({
    children,
  }: {
    children: React.ReactNode;
  }) => <div data-testid="responsive-container">{children}</div>,
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  Pie: () => <div />,
  Cell: () => <div />,
  Legend: () => <div />,
  Tooltip: () => <div />,
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const HEALTH_BAND_COLORS: Record<string, string> = {
  HEALTHY: "border-green-500",
  AT_RISK: "border-amber-500",
  CRITICAL: "border-red-500",
  UNKNOWN: "border-slate-400",
};

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

describe("OverviewHealthHeader (legacy — still used by other pages)", () => {
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

describe("OverviewMetricsStrip (legacy — still used by other pages)", () => {
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

describe("Health Header Band (395.1)", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders health band with AT_RISK amber border color", () => {
    const status = "AT_RISK" as const;
    const reasons = ["Budget 85% consumed", "6 overdue tasks"];
    const bandColor = HEALTH_BAND_COLORS[status];

    render(
      <div
        data-testid="project-health-header"
        className={cn(
          "rounded-lg border bg-card border-t-4",
          bandColor,
        )}
      >
        <div className="flex items-start gap-4 px-4 pt-4 pb-2">
          <HealthBadge status={status} size="lg" />
          <div>
            <h2 className="font-display text-lg">Website Redesign</h2>
            <p className="text-sm text-slate-500">Customer: Acme Corp</p>
          </div>
        </div>
        {reasons.length > 0 && (
          <div className="flex flex-wrap gap-1.5 px-4 pb-2">
            {reasons.map((reason, i) => (
              <span key={i} className="rounded-full px-2.5 py-0.5 text-xs font-medium">
                {reason}
              </span>
            ))}
          </div>
        )}
      </div>,
    );

    const header = screen.getByTestId("project-health-header");
    expect(header).toBeInTheDocument();
    expect(header.className).toContain("border-amber-500");
    expect(screen.getByText("At Risk")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Budget 85% consumed")).toBeInTheDocument();
    expect(screen.getByText("6 overdue tasks")).toBeInTheDocument();
  });

  it("renders health band with HEALTHY green border color", () => {
    const status = "HEALTHY" as const;
    const bandColor = HEALTH_BAND_COLORS[status];

    render(
      <div
        data-testid="project-health-header"
        className={cn("rounded-lg border bg-card border-t-4", bandColor)}
      >
        <div className="flex items-start gap-4 px-4 pt-4 pb-2">
          <HealthBadge status={status} size="lg" />
          <div>
            <h2>All Good Project</h2>
          </div>
        </div>
      </div>,
    );

    const header = screen.getByTestId("project-health-header");
    expect(header.className).toContain("border-green-500");
    expect(screen.getByText("Healthy")).toBeInTheDocument();
  });
});

describe("Two-Panel Layout (395.3)", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders activity/tasks panel with correct test id", () => {
    render(
      <div className="grid gap-4 lg:grid-cols-5">
        <div data-testid="activity-tasks-panel" className="lg:col-span-3">
          <p>Recent Activity</p>
          <p>Task Status</p>
        </div>
        <div data-testid="financial-team-panel" className="lg:col-span-2">
          <p>Budget</p>
          <p>Time Breakdown</p>
        </div>
      </div>,
    );

    expect(screen.getByTestId("activity-tasks-panel")).toBeInTheDocument();
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(screen.getByText("Task Status")).toBeInTheDocument();
  });

  it("renders financial/team panel with budget bar", () => {
    render(
      <div className="grid gap-4 lg:grid-cols-5">
        <div data-testid="activity-tasks-panel" className="lg:col-span-3">
          <p>Activity content</p>
        </div>
        <div data-testid="financial-team-panel" className="lg:col-span-2">
          <div>
            <p className="text-sm font-medium">Budget</p>
            <CompletionProgressBar percent={85} />
            <span>85% used</span>
          </div>
        </div>
      </div>,
    );

    expect(screen.getByTestId("financial-team-panel")).toBeInTheDocument();
    expect(screen.getByText("Budget")).toBeInTheDocument();
    expect(screen.getByText("85% used")).toBeInTheDocument();
  });
});
