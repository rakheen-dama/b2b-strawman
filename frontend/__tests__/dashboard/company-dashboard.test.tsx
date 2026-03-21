import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import { MetricsStrip } from "@/components/dashboard/metrics-strip";
import { AdminStatsColumn } from "@/components/dashboard/admin-stats-column";
import { MyWeekColumn } from "@/components/dashboard/my-week-column";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";
import type {
  TeamWorkloadEntry,
  CrossProjectActivityItem,
  KpiResponse,
  ProjectHealth,
} from "@/lib/dashboard-types";
import type { TeamCapacityGrid } from "@/lib/api/capacity";

// Mock recharts to avoid rendering issues in happy-dom
vi.mock("recharts", () => ({
  ResponsiveContainer: ({
    children,
  }: {
    children: React.ReactNode;
  }) => <div data-testid="responsive-container">{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="bar-chart">{children}</div>
  ),
  Bar: () => <div data-testid="bar" />,
  XAxis: () => <div />,
  YAxis: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div data-testid="legend" />,
  CartesianGrid: () => <div data-testid="cartesian-grid" />,
  ReferenceLine: () => <div data-testid="reference-line" />,
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: () => <div />,
  Cell: () => <div />,
}));

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/dashboard",
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock onboarding progress hook
vi.mock("@/hooks/use-onboarding-progress", () => ({
  useOnboardingProgress: () => ({
    steps: [],
    completedCount: 1,
    totalCount: 5,
    percentComplete: 20,
    allComplete: false,
    dismissed: false,
    loading: false,
    dismiss: vi.fn(),
  }),
}));

// Mock messages
vi.mock("@/lib/messages", () => ({
  createMessages: () => ({
    t: (key: string) => key,
  }),
}));

// Mock terminology
vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({
    t: (key: string) => key,
  }),
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
];

const mockWorkloadData: TeamWorkloadEntry[] = [
  {
    memberId: "m1",
    memberName: "Alice Johnson",
    totalHours: 42.5,
    billableHours: 35.0,
    projects: [
      { projectId: "p1", projectName: "Website Redesign", hours: 20.0 },
      { projectId: "p2", projectName: "Mobile App", hours: 12.5 },
      { projectId: "p3", projectName: "API Migration", hours: 10.0 },
    ],
  },
  {
    memberId: "m2",
    memberName: "Bob Smith",
    totalHours: 38.0,
    billableHours: 30.0,
    projects: [
      { projectId: "p1", projectName: "Website Redesign", hours: 25.0 },
      { projectId: "p3", projectName: "API Migration", hours: 13.0 },
    ],
  },
];

const mockActivityItems: CrossProjectActivityItem[] = [
  {
    eventId: "e1",
    eventType: "task.created",
    description: "Alice created task 'Fix login bug'",
    actorName: "Alice Johnson",
    projectId: "p1",
    projectName: "Website Redesign",
    occurredAt: new Date(Date.now() - 3600 * 1000).toISOString(),
  },
  {
    eventId: "e2",
    eventType: "comment.added",
    description: "Bob commented on 'API spec review'",
    actorName: "Bob Smith",
    projectId: "p2",
    projectName: "Mobile App",
    occurredAt: new Date(Date.now() - 7200 * 1000).toISOString(),
  },
  {
    eventId: "e3",
    eventType: "time_entry.created",
    description: "Carol logged 2h on 'Dashboard feature'",
    actorName: "Carol Davis",
    projectId: "p3",
    projectName: "API Migration",
    occurredAt: new Date(Date.now() - 86400 * 1000).toISOString(),
  },
];

describe("TeamWorkloadWidget", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders chart for admin with data", () => {
    render(<TeamWorkloadWidget data={mockWorkloadData} isAdmin={true} />);
    expect(screen.getByText("Team Time")).toBeInTheDocument();
    expect(screen.getByTestId("team-time-panel")).toBeInTheDocument();
    expect(screen.queryByText("Contact an admin")).not.toBeInTheDocument();
  });

  it("shows admin note for non-admin with data", () => {
    render(<TeamWorkloadWidget data={mockWorkloadData} isAdmin={false} />);
    expect(screen.getByText("Team Time")).toBeInTheDocument();
    expect(screen.getByTestId("team-time-panel")).toBeInTheDocument();
    expect(
      screen.getByText("Contact an admin to see team-wide data.")
    ).toBeInTheDocument();
  });

  it("renders empty state when data is empty array", () => {
    render(<TeamWorkloadWidget data={[]} isAdmin={true} />);
    expect(screen.getByText("Team Time")).toBeInTheDocument();
    expect(
      screen.getByText("No time logged")
    ).toBeInTheDocument();
  });

  it("renders error state when data is null", () => {
    render(<TeamWorkloadWidget data={null} isAdmin={true} />);
    expect(screen.getByText("Team Time")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load team workload. Please try again.")
    ).toBeInTheDocument();
  });
});

describe("RecentActivityWidget", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders activity items with icons and project badges", () => {
    render(
      <RecentActivityWidget items={mockActivityItems} orgSlug="acme" />
    );
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(
      screen.getByText("Alice created task 'Fix login bug'")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Bob commented on 'API spec review'")
    ).toBeInTheDocument();
    expect(
      screen.getByText("Carol logged 2h on 'Dashboard feature'")
    ).toBeInTheDocument();

    // Initials avatars
    expect(screen.getByText("AJ")).toBeInTheDocument();
    expect(screen.getByText("BS")).toBeInTheDocument();
    expect(screen.getByText("CD")).toBeInTheDocument();
  });

  it("renders empty state when items is empty array", () => {
    render(<RecentActivityWidget items={[]} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(
      screen.getByText("No recent activity")
    ).toBeInTheDocument();
  });

  it("renders error state when items is null", () => {
    render(<RecentActivityWidget items={null} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(
      screen.getByText("Unable to load activity. Please try again.")
    ).toBeInTheDocument();
  });
});

describe("Dashboard Layout Components", () => {
  afterEach(() => {
    cleanup();
  });

  it("MetricsStrip renders in dashboard (not KpiCardRow)", () => {
    render(
      <MetricsStrip
        kpis={mockKpis}
        capacityData={mockCapacityData}
        projectHealth={mockProjectHealth}
      />,
    );
    expect(screen.getByTestId("metrics-strip")).toBeInTheDocument();
    expect(screen.getByTestId("metric-active-projects")).toBeInTheDocument();
  });

  it("hero panels render project health + team time", async () => {
    render(
      <>
        <ProjectHealthWidget projects={mockProjectHealth} orgSlug="acme" />
        <TeamWorkloadWidget data={mockWorkloadData} isAdmin={true} />
      </>,
    );
    expect(screen.getByText("Project Health")).toBeInTheDocument();
    expect(screen.getByText("Team Time")).toBeInTheDocument();
  });

  it("admin role shows AdminStatsColumn", () => {
    render(
      <AdminStatsColumn
        aggregatedCompleteness={{
          topMissingFields: [],
          incompleteCount: 3,
          totalCount: 10,
        }}
        requestSummary={{
          totalRequests: 5,
          draftCount: 0,
          sentCount: 2,
          inProgressCount: 1,
          completedCount: 2,
          cancelledCount: 0,
          itemsPendingReview: 3,
        }}
        automationSummary={{
          activeRulesCount: 2,
          todayTotal: 7,
          todaySucceeded: 6,
          todayFailed: 1,
        }}
        orgSlug="acme"
      />,
    );
    expect(screen.getByTestId("admin-stats-column")).toBeInTheDocument();
    expect(screen.getByText("Incomplete profiles")).toBeInTheDocument();
    expect(screen.getByText("Pending requests")).toBeInTheDocument();
    expect(screen.getByText("Automation runs today")).toBeInTheDocument();
  });

  it("member role shows MyWeekColumn", () => {
    render(
      <MyWeekColumn kpis={mockKpis} activity={mockActivityItems} />,
    );
    expect(screen.getByTestId("my-week-column")).toBeInTheDocument();
    expect(screen.getByText("My Week")).toBeInTheDocument();
    expect(screen.getByText("Avg. daily hours")).toBeInTheDocument();
    expect(screen.getByText("Tasks this week")).toBeInTheDocument();
  });

  it("GettingStartedCard auto-hides when activeProjects > 3", () => {
    render(<GettingStartedCard activeProjectCount={5} />);
    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
  });

  it("GettingStartedCard shows when activeProjects <= 3", () => {
    render(<GettingStartedCard activeProjectCount={2} />);
    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
  });
});
