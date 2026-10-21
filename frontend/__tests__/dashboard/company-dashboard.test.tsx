import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { MatterHealthWidget } from "@/components/dashboard/matter-health-widget";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";
import { useOnboardingProgress } from "@/hooks/use-onboarding-progress";
import type { CrossMatterActivityItem, MatterHealth } from "@/lib/dashboard-types";

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

// Mock onboarding progress hook — no matter yet unless a test says otherwise.
vi.mock("@/hooks/use-onboarding-progress", () => ({ useOnboardingProgress: vi.fn() }));

function mockOnboarding(steps: { code: string; completed: boolean }[]) {
  vi.mocked(useOnboardingProgress).mockReturnValue({
    steps,
    completedCount: steps.filter((s) => s.completed).length,
    totalCount: 5,
    percentComplete: 20,
    allComplete: false,
    dismissed: false,
    loading: false,
    dismiss: vi.fn(),
  });
}

// Mock messages
vi.mock("@/lib/messages", () => ({
  createMessages: () => ({
    t: (key: string) => key,
  }),
}));

const mockProjectHealth: MatterHealth[] = [
  {
    matterId: "p1",
    matterName: "Website Redesign",
    clientName: "Acme Corp",
    healthStatus: "HEALTHY",
    healthReasons: [],
    tasksDone: 8,
    tasksTotal: 10,
    completionPercent: 80,
    budgetConsumedPercent: 60,
    hoursLogged: 120,
  },
];

const mockActivityItems: CrossMatterActivityItem[] = [
  {
    eventId: "e1",
    eventType: "task.created",
    description: "Alice created task 'Fix login bug'",
    actorName: "Alice Johnson",
    matterId: "p1",
    matterName: "Website Redesign",
    occurredAt: new Date(Date.now() - 3600 * 1000).toISOString(),
  },
  {
    eventId: "e2",
    eventType: "comment.added",
    description: "Bob commented on 'API spec review'",
    actorName: "Bob Smith",
    matterId: "p2",
    matterName: "Mobile App",
    occurredAt: new Date(Date.now() - 7200 * 1000).toISOString(),
  },
  {
    eventId: "e3",
    eventType: "time_entry.created",
    description: "Carol logged 2h on 'Dashboard feature'",
    actorName: "Carol Davis",
    matterId: "p3",
    matterName: "API Migration",
    occurredAt: new Date(Date.now() - 86400 * 1000).toISOString(),
  },
];

describe("RecentActivityWidget", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders activity items with icons and project badges", () => {
    render(<RecentActivityWidget items={mockActivityItems} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(screen.getByText("Alice created task 'Fix login bug'")).toBeInTheDocument();
    expect(screen.getByText("Bob commented on 'API spec review'")).toBeInTheDocument();
    expect(screen.getByText("Carol logged 2h on 'Dashboard feature'")).toBeInTheDocument();

    // Initials avatars
    expect(screen.getByText("AJ")).toBeInTheDocument();
    expect(screen.getByText("BS")).toBeInTheDocument();
    expect(screen.getByText("CD")).toBeInTheDocument();
  });

  it("renders empty state when items is empty array", () => {
    render(<RecentActivityWidget items={[]} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(screen.getByText("No recent activity")).toBeInTheDocument();
  });

  it("renders error state when items is null", () => {
    render(<RecentActivityWidget items={null} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(screen.getByText("Unable to load activity. Please try again.")).toBeInTheDocument();
  });
});

describe("Dashboard Layout Components", () => {
  afterEach(() => {
    cleanup();
  });

  it("matter health panel renders on Home (renamed from project health, 614B)", () => {
    render(<MatterHealthWidget matters={mockProjectHealth} orgSlug="acme" />);
    expect(screen.getByTestId("matter-health-panel")).toBeInTheDocument();
    expect(screen.getByText("Matter Health")).toBeInTheDocument();
  });

  it("dashboard page no longer renders a pipeline summary widget (Epic 596)", async () => {
    // Module-level proof: the widget file is gone and the page source has no reference to it.
    // The page itself is an async Server Component with server fetches, so we assert on the
    // module graph rather than rendering it.
    const fs = await import("node:fs");
    const path = await import("node:path");
    const pageSrc = fs.readFileSync(
      path.resolve(process.cwd(), "app/(app)/org/[slug]/dashboard/page.tsx"),
      "utf8"
    );
    expect(pageSrc).not.toContain("PipelineSummaryWidget");
    expect(pageSrc).not.toContain("pipeline-summary");
    expect(
      fs.existsSync(path.resolve(process.cwd(), "components/dashboard/pipeline-summary-widget.tsx"))
    ).toBe(false);
  });

  it("dashboard page no longer renders capacity/utilization widgets (Epic 599)", async () => {
    // Module-level proof (same shape as the Epic 596 test above): the capacity widget files
    // and their data sources are gone, and the page source no longer references them.
    const fs = await import("node:fs");
    const path = await import("node:path");
    const pageSrc = fs.readFileSync(
      path.resolve(process.cwd(), "app/(app)/org/[slug]/dashboard/page.tsx"),
      "utf8"
    );
    expect(pageSrc).not.toContain("TeamUtilizationWidget");
    expect(pageSrc).not.toContain("getTeamCapacityGrid");
    expect(pageSrc).not.toContain("lib/api/capacity");
    expect(pageSrc).not.toContain("capacityData");
    for (const f of [
      "components/dashboard/team-capacity-widget.tsx",
      "components/dashboard/my-schedule-widget.tsx",
      "components/dashboard/team-utilization-widget.tsx",
      "lib/api/capacity.ts",
      "lib/actions/utilization.ts",
    ]) {
      expect(fs.existsSync(path.resolve(process.cwd(), f))).toBe(false);
    }
  });

  it("Home no longer renders the KPI strip, team workload, admin stats or my-week widgets (Epic 614B)", async () => {
    // Module-level proof (same shape as the Epic 596/599 tests): the widget files and their data
    // sources are gone, and the page source no longer references them. Replaces the Epic 599
    // "MetricsStrip renders no team-utilization tile" render test — MetricsStrip itself is deleted.
    const fs = await import("node:fs");
    const path = await import("node:path");
    const pageSrc = fs.readFileSync(
      path.resolve(process.cwd(), "app/(app)/org/[slug]/dashboard/page.tsx"),
      "utf8"
    );
    for (const ref of [
      "TeamWorkloadWidget",
      "MetricsStrip",
      "AdminStatsColumn",
      "MyWeekColumn",
      "team-workload",
      "fetchDashboardKpis",
      "UpcomingCourtDatesWidget",
    ]) {
      expect(pageSrc).not.toContain(ref);
    }
    for (const f of [
      "components/dashboard/team-workload-widget.tsx",
      "components/dashboard/metrics-strip.tsx",
      "components/dashboard/kpi-card-row.tsx",
      "components/dashboard/admin-stats-column.tsx",
      "components/dashboard/my-week-column.tsx",
      "components/dashboard/project-health-widget.tsx",
    ]) {
      expect(fs.existsSync(path.resolve(process.cwd(), f))).toBe(false);
    }
    const actionsSrc = fs.readFileSync(
      path.resolve(process.cwd(), "lib/actions/dashboard.ts"),
      "utf8"
    );
    for (const fn of [
      "fetchDashboardKpis",
      "fetchTeamWorkload",
      "fetchAggregatedCompleteness",
      "fetchInformationRequestSummary",
      "fetchAutomationSummary",
    ]) {
      expect(actionsSrc).not.toContain(fn);
    }
  });

  it("GettingStartedCard hides once the first matter exists (CREATE_PROJECT complete)", () => {
    mockOnboarding([{ code: "CREATE_PROJECT", completed: true }]);
    render(<GettingStartedCard />);
    expect(screen.queryByTestId("getting-started-banner")).not.toBeInTheDocument();
  });

  it("GettingStartedCard shows until the first matter exists", () => {
    mockOnboarding([{ code: "CREATE_PROJECT", completed: false }]);
    render(<GettingStartedCard />);
    expect(screen.getByTestId("getting-started-banner")).toBeInTheDocument();
  });
});
