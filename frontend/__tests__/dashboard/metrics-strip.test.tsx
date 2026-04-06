import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MetricsStrip } from "@/components/dashboard/metrics-strip";
import { TerminologyProvider } from "@/lib/terminology";
import type { KpiResponse, ProjectHealth } from "@/lib/dashboard-types";
import type { TeamCapacityGrid } from "@/lib/api/capacity";

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

const mockKpis: KpiResponse = {
  activeProjectCount: 12,
  totalHoursLogged: 847.5,
  billablePercent: 72.3,
  overdueTaskCount: 8,
  averageMarginPercent: 34.2,
  trend: [
    { period: "2025-W01", value: 120.0 },
    { period: "2025-W02", value: 145.5 },
    { period: "2025-W03", value: 138.0 },
  ],
  previousPeriod: {
    activeProjectCount: 11,
    totalHoursLogged: 792.0,
    billablePercent: 68.5,
    overdueTaskCount: 5,
    averageMarginPercent: 31.8,
  },
};

const mockCapacityData: TeamCapacityGrid = {
  members: [],
  weekSummaries: [
    {
      weekStart: "2025-01-06",
      teamTotalAllocated: 120,
      teamTotalCapacity: 160,
      teamUtilizationPct: 75,
    },
  ],
};

const mockProjectHealth: ProjectHealth[] = [
  {
    projectId: "p1",
    projectName: "Website Redesign",
    customerName: "Acme Corp",
    healthStatus: "HEALTHY",
    healthReasons: [],
    tasksDone: 8,
    tasksTotal: 10,
    completionPercent: 80,
    budgetConsumedPercent: 60,
    hoursLogged: 120,
  },
  {
    projectId: "p2",
    projectName: "Mobile App",
    customerName: "Beta Inc",
    healthStatus: "AT_RISK",
    healthReasons: ["Behind schedule"],
    tasksDone: 3,
    tasksTotal: 12,
    completionPercent: 25,
    budgetConsumedPercent: 85,
    hoursLogged: 200,
  },
  {
    projectId: "p3",
    projectName: "API Migration",
    customerName: null,
    healthStatus: "CRITICAL",
    healthReasons: ["Over budget"],
    tasksDone: 1,
    tasksTotal: 8,
    completionPercent: 12,
    budgetConsumedPercent: 120,
    hoursLogged: 350,
  },
];

function renderWithProvider(ui: React.ReactElement) {
  return render(
    <TerminologyProvider verticalProfile={null}>{ui}</TerminologyProvider>,
  );
}

describe("MetricsStrip", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders 6 metric cells", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );

    expect(screen.getByTestId("metrics-strip")).toBeInTheDocument();
    expect(screen.getByTestId("metric-active-projects")).toBeInTheDocument();
    expect(screen.getByTestId("metric-hours-month")).toBeInTheDocument();
    expect(screen.getByTestId("metric-revenue")).toBeInTheDocument();
    expect(screen.getByTestId("metric-overdue-tasks")).toBeInTheDocument();
    expect(screen.getByTestId("metric-team-utilization")).toBeInTheDocument();
    expect(screen.getByTestId("metric-budget-health")).toBeInTheDocument();
  });

  it("handles null/undefined KPI values gracefully", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={null}
        capacityData={null}
        projectHealth={null}
      />,
    );

    expect(screen.getByTestId("metrics-strip")).toBeInTheDocument();
    // Should show 0 for active projects when kpis is null
    expect(screen.getByTestId("metric-active-projects")).toHaveTextContent("0");
    // Should show "--" for margin when null
    expect(screen.getByTestId("metric-revenue")).toHaveTextContent("--");
  });

  it("displays correct active project count", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );

    expect(screen.getByTestId("metric-active-projects")).toHaveTextContent(
      "12",
    );
  });

  it("displays overdue tasks with severity color", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );

    // 8 overdue tasks >= 5, should have red styling
    expect(screen.getByTestId("metric-overdue-tasks")).toHaveTextContent("8");
  });

  it("displays budget health dot counts", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );

    const budgetCell = screen.getByTestId("metric-budget-health");
    // 1 healthy, 1 at-risk, 1 critical
    expect(budgetCell).toHaveTextContent("1");
  });

  it("displays team utilization from capacity data", () => {
    renderWithProvider(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );

    expect(screen.getByTestId("metric-team-utilization")).toHaveTextContent(
      "75%",
    );
  });
});
