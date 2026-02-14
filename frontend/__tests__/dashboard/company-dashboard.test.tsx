import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import type {
  TeamWorkloadEntry,
  CrossProjectActivityItem,
} from "@/lib/dashboard-types";

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
}));

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/dashboard",
}));

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
    expect(screen.getByText("Team Workload")).toBeInTheDocument();
    expect(screen.getByTestId("responsive-container")).toBeInTheDocument();
    expect(screen.queryByText("Contact an admin")).not.toBeInTheDocument();
  });

  it("shows admin note for non-admin with data", () => {
    render(<TeamWorkloadWidget data={mockWorkloadData} isAdmin={false} />);
    expect(screen.getByText("Team Workload")).toBeInTheDocument();
    expect(screen.getByTestId("responsive-container")).toBeInTheDocument();
    expect(
      screen.getByText("Contact an admin to see team-wide data.")
    ).toBeInTheDocument();
  });

  it("renders empty state when data is empty array", () => {
    render(<TeamWorkloadWidget data={[]} isAdmin={true} />);
    expect(screen.getByText("Team Workload")).toBeInTheDocument();
    expect(
      screen.getByText("No time logged this period.")
    ).toBeInTheDocument();
  });

  it("renders error state when data is null", () => {
    render(<TeamWorkloadWidget data={null} isAdmin={true} />);
    expect(screen.getByText("Team Workload")).toBeInTheDocument();
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

    // Project name badges
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("API Migration")).toBeInTheDocument();

    // Initials avatars
    expect(screen.getByText("AJ")).toBeInTheDocument();
    expect(screen.getByText("BS")).toBeInTheDocument();
    expect(screen.getByText("CD")).toBeInTheDocument();
  });

  it("renders empty state when items is empty array", () => {
    render(<RecentActivityWidget items={[]} orgSlug="acme" />);
    expect(screen.getByText("Recent Activity")).toBeInTheDocument();
    expect(
      screen.getByText("No recent activity across your projects.")
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
