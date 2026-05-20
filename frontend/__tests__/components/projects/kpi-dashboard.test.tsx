import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Mock dashboard server actions — inline values to avoid hoisting issues
vi.mock("@/lib/actions/dashboard", () => ({
  fetchProjectHealthDetail: vi.fn().mockResolvedValue({
    healthStatus: "AT_RISK",
    healthReasons: ["Budget exceeded"],
    metrics: {
      tasksDone: 5,
      tasksInProgress: 3,
      tasksTodo: 2,
      tasksOverdue: 1,
      totalTasks: 10,
      completionPercent: 50,
      budgetConsumedPercent: 80,
      hoursThisPeriod: 40,
      daysSinceLastActivity: 2,
    },
  }),
  fetchProjectTaskSummary: vi.fn().mockResolvedValue({
    todo: 2,
    inProgress: 3,
    done: 5,
    cancelled: 0,
    total: 10,
    overdueCount: 1,
  }),
  fetchProjectMemberHours: vi.fn().mockResolvedValue([
    { memberId: "m1", memberName: "Alice", totalHours: 20, billableHours: 18 },
    { memberId: "m2", memberName: "Bob", totalHours: 15, billableHours: 12 },
  ]),
  fetchProjectUpcomingDeadlines: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn().mockResolvedValue(null) },
}));

vi.mock("@/lib/date-utils", () => ({
  resolveDateRange: vi.fn().mockReturnValue({ from: "2026-01-01", to: "2026-01-31" }),
}));

vi.mock("@/components/dashboard/health-badge", () => ({
  HealthBadge: ({ status }: { status: string }) => (
    <span data-testid="health-badge">{status}</span>
  ),
}));

vi.mock("@/components/dashboard/completion-progress-bar", () => ({
  CompletionProgressBar: ({ percent }: { percent: number }) => (
    <div data-testid="completion-progress-bar">{percent}%</div>
  ),
}));

vi.mock("@/components/compliance/FicaStatusCard", () => ({
  FicaStatusCard: () => <div data-testid="fica-status-card" />,
}));

vi.mock("@/components/legal/retention-card", () => ({
  RetentionCard: () => <div data-testid="retention-card" />,
}));

vi.mock("@/components/projects/upcoming-deadlines-tile", () => ({
  UpcomingDeadlinesTile: ({ deadlines }: { deadlines: unknown[] }) => (
    <div data-testid="matter-upcoming-deadlines-tile">
      {deadlines.length} deadlines
    </div>
  ),
}));

vi.mock("@/components/ui/card", () => ({
  Card: ({ children, ...props }: { children: React.ReactNode; [key: string]: unknown }) => (
    <div {...props}>{children}</div>
  ),
  CardContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardTitle: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/lib/utils", () => ({
  cn: (...args: unknown[]) => args.filter(Boolean).join(" "),
}));

import { KPIDashboard } from "@/components/projects/kpi-dashboard";

const defaultProps = {
  projectId: "proj-1",
  projectName: "Test Matter",
  projectStatus: "ACTIVE",
  slug: "test-org",
  canManage: true,
  customerName: "Acme Corp",
  customerId: "cust-1",
  setupStatus: null,
  setupSteps: [],
  ficaStatus: null,
  retentionClockStartedAt: null,
  retentionEndsOn: null,
  trustEnabled: false,
  disbursementsEnabled: false,
  projectDueDate: null,
};

describe("KPIDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  // Test 1: Renders health header with colored band
  it("renders health header with colored band for AT_RISK status", async () => {
    const jsx = await KPIDashboard(defaultProps);
    render(jsx);

    const header = screen.getByTestId("project-health-header");
    expect(header).toBeInTheDocument();
    expect(header.className).toContain("border-amber-500");
  });

  // Test 2: Renders metric cards for always-visible metrics
  it("renders metric cards for hours and tasks", async () => {
    const jsx = await KPIDashboard(defaultProps);
    render(jsx);

    expect(screen.getByTestId("metric-card-hours")).toBeInTheDocument();
    expect(screen.getByTestId("metric-card-tasks")).toBeInTheDocument();
  });

  // Test 3: Hides budget card when no budget data
  it("hides budget card when budgetStatus is null", async () => {
    const jsx = await KPIDashboard(defaultProps);
    render(jsx);

    expect(screen.queryByTestId("metric-card-budget")).not.toBeInTheDocument();
  });

  // Test 4: Hides trust card when trustEnabled is false
  it("hides trust card when trustEnabled is false", async () => {
    const jsx = await KPIDashboard(defaultProps);
    render(jsx);

    expect(screen.queryByTestId("metric-card-trust")).not.toBeInTheDocument();
  });

  // Test 5: Renders upcoming deadlines list
  it("renders upcoming deadlines tile", async () => {
    const jsx = await KPIDashboard(defaultProps);
    render(jsx);

    expect(screen.getByTestId("matter-upcoming-deadlines-tile")).toBeInTheDocument();
  });

  // Test 6: Setup bar hidden when all steps complete
  it("hides setup bar when all steps are complete", async () => {
    const jsx = await KPIDashboard({
      ...defaultProps,
      setupStatus: {
        customerAssigned: true,
        rateCardConfigured: true,
        budgetConfigured: true,
        teamAssigned: true,
        requiredFields: { total: 0, filled: 0 },
      },
      setupSteps: [
        { label: "Customer assigned", complete: true, actionHref: "?tab=customers" },
        { label: "Budget set", complete: true, actionHref: "?tab=budget" },
      ],
    });
    render(jsx);

    expect(screen.queryByTestId("setup-progress-bar")).not.toBeInTheDocument();
  });
});
