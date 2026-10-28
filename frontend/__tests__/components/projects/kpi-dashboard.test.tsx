import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Mock the matters API module (kpi-dashboard reads health, member hours, budget and deadlines
// directly from lib/api/matters since Phase 87 610C) — inline values to avoid hoisting issues
vi.mock("@/lib/api/matters", () => ({
  getMatterHealth: vi.fn().mockResolvedValue({
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
  getMatterMemberHours: vi.fn().mockResolvedValue([
    { memberId: "m1", memberName: "Alice", totalHours: 20, billableHours: 18 },
    { memberId: "m2", memberName: "Bob", totalHours: 15, billableHours: 12 },
  ]),
  getMatterBudget: vi.fn().mockResolvedValue(null),
  getMatterUpcomingDeadlines: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/lib/date-utils", () => ({
  resolveDateRange: vi.fn().mockReturnValue({ from: "2026-01-01", to: "2026-01-31" }),
}));

vi.mock("@/components/dashboard/health-badge", () => ({
  HealthBadge: ({ status }: { status: string }) => <span data-testid="health-badge">{status}</span>,
}));

vi.mock("@/components/dashboard/completion-progress-bar", () => ({
  CompletionProgressBar: ({ percent }: { percent: number }) => (
    <div data-testid="completion-progress-bar">{percent}%</div>
  ),
}));

vi.mock("@/components/compliance/FicaStatusCard", () => ({
  FicaStatusCard: () => <div data-testid="fica-status-card" />,
}));

// The retention card's firm-period note reads the firm setting (Phase 88 U17, ADR-354).
// lib/api/settings imports "server-only", so it is mocked here.
const { mockGetOrgSettings } = vi.hoisted(() => ({ mockGetOrgSettings: vi.fn() }));
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: () => mockGetOrgSettings(),
}));

vi.mock("@/components/legal/retention-card", () => ({
  RetentionCard: ({
    status,
    retentionEndsOn,
    firmRetentionYears,
  }: {
    status: string;
    retentionEndsOn: string | null;
    firmRetentionYears: number | null;
  }) => (
    <div
      data-testid="retention-card"
      data-status={status}
      data-ends-on={retentionEndsOn ?? ""}
      data-years={firmRetentionYears === null ? "null" : String(firmRetentionYears)}
    />
  ),
}));

vi.mock("@/components/projects/upcoming-deadlines-tile", () => ({
  UpcomingDeadlinesTile: ({ deadlines }: { deadlines: unknown[] }) => (
    <div data-testid="matter-upcoming-deadlines-tile">{deadlines.length} deadlines</div>
  ),
}));

vi.mock("@b2mash/ui/card", () => ({
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
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR", legalMatterRetentionYears: 5 });
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

  // Hides the outstanding fee notes card when disbursementsEnabled is false
  it("hides outstanding fee notes card when disbursementsEnabled is false", async () => {
    const jsx = await KPIDashboard({ ...defaultProps, disbursementsEnabled: false });
    render(jsx);

    expect(screen.queryByTestId("metric-card-invoices")).not.toBeInTheDocument();
    expect(screen.queryByText("Outstanding fee notes")).not.toBeInTheDocument();
  });

  // Shows the outstanding fee notes card when disbursementsEnabled is true
  it("shows outstanding fee notes card when disbursementsEnabled is true", async () => {
    const jsx = await KPIDashboard({ ...defaultProps, disbursementsEnabled: true });
    render(jsx);

    const link = screen.getByRole("link", { name: /Outstanding fee notes/i });
    expect(link).toHaveAttribute("href", "?tab=statements");
    expect(link).toContainElement(screen.getByTestId("metric-card-invoices"));
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
        clientAssigned: true,
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

  describe("retention card firm setting (Phase 88 U17)", () => {
    const closedProps = {
      ...defaultProps,
      projectStatus: "CLOSED",
      retentionClockStartedAt: "2026-10-22T06:44:25.034385Z",
      retentionEndsOn: "2031-10-22",
    };

    it("passes the firm's matter retention years to the card for a CLOSED matter", async () => {
      const jsx = await KPIDashboard(closedProps);
      render(jsx);

      const card = screen.getByTestId("retention-card");
      expect(card).toHaveAttribute("data-years", "5");
      expect(card).toHaveAttribute("data-ends-on", "2031-10-22");
      expect(mockGetOrgSettings).toHaveBeenCalledTimes(1);
    });

    it("passes the current setting for a COMPLETED matter", async () => {
      mockGetOrgSettings.mockResolvedValue({
        defaultCurrency: "ZAR",
        legalMatterRetentionYears: 7,
      });
      const jsx = await KPIDashboard({ ...defaultProps, projectStatus: "COMPLETED" });
      render(jsx);

      expect(screen.getByTestId("retention-card")).toHaveAttribute("data-years", "7");
    });

    it("passes null when the settings read fails (the overview still renders)", async () => {
      mockGetOrgSettings.mockRejectedValue(new Error("settings down"));
      const jsx = await KPIDashboard(closedProps);
      render(jsx);

      expect(screen.getByTestId("retention-card")).toHaveAttribute("data-years", "null");
      expect(screen.getByTestId("project-health-header")).toBeInTheDocument();
    });

    it("does not read settings for an ACTIVE matter (no card to annotate)", async () => {
      const jsx = await KPIDashboard(defaultProps);
      render(jsx);

      expect(mockGetOrgSettings).not.toHaveBeenCalled();
      expect(screen.getByTestId("retention-card")).toHaveAttribute("data-years", "null");
    });
  });
});
