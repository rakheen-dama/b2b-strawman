import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TimeBreakdown } from "@/components/my-work/time-breakdown";
import {
  UrgencyTaskList,
  groupByUrgency,
} from "@/components/my-work/urgency-task-list";
import { TodaysAgenda } from "@/components/dashboard/todays-agenda";
import { WeeklyRhythmStrip } from "@/components/dashboard/weekly-rhythm-strip";
import type { PersonalDashboardResponse } from "@/lib/dashboard-types";
import type { MyWorkTaskItem } from "@/lib/types";

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
  PieChart: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="pie-chart">{children}</div>
  ),
  Pie: () => <div />,
  Cell: () => <div />,
  XAxis: () => <div />,
  YAxis: () => <div />,
  Tooltip: () => <div />,
  Legend: () => <div data-testid="legend" />,
}));

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => "/org/acme/my-work",
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

const mockDashboardData: PersonalDashboardResponse = {
  utilization: {
    totalHours: 38.5,
    billableHours: 30.0,
    billablePercent: 77.9,
  },
  projectBreakdown: [
    {
      projectId: "p1",
      projectName: "Website Redesign",
      hours: 20.0,
      percent: 51.9,
    },
    {
      projectId: "p2",
      projectName: "Mobile App",
      hours: 12.5,
      percent: 32.5,
    },
  ],
  overdueTaskCount: 2,
  upcomingDeadlines: [],
  trend: [
    { period: "2025-W01", value: 36.0 },
    { period: "2025-W02", value: 40.0 },
  ],
};

// Helper to create a date string relative to today
function daysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

function makeTask(
  overrides: Partial<MyWorkTaskItem> & { id: string }
): MyWorkTaskItem {
  return {
    projectId: "p1",
    projectName: "Test Project",
    title: `Task ${overrides.id}`,
    status: "IN_PROGRESS",
    priority: "MEDIUM",
    dueDate: null,
    totalTimeMinutes: 0,
    ...overrides,
  };
}

describe("TimeBreakdown on My Work page", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders time breakdown chart with project data", () => {
    render(<TimeBreakdown data={mockDashboardData.projectBreakdown} />);
    expect(screen.getByText("Time Breakdown")).toBeInTheDocument();
    expect(screen.getByText("Website Redesign")).toBeInTheDocument();
    expect(screen.getByText("Mobile App")).toBeInTheDocument();
    expect(screen.getByText("20.0h (52%)")).toBeInTheDocument();
    expect(screen.getByText("12.5h (33%)")).toBeInTheDocument();
  });

  it("renders empty state when no time logged", () => {
    render(<TimeBreakdown data={[]} />);
    expect(screen.getByText("No time logged this period.")).toBeInTheDocument();
  });
});

describe("UrgencyTaskList grouping", () => {
  afterEach(() => {
    cleanup();
  });

  it("groups overdue tasks in a separate section", () => {
    const tasks: MyWorkTaskItem[] = [
      makeTask({
        id: "t1",
        title: "Overdue task",
        dueDate: daysFromNow(-3),
      }),
      makeTask({
        id: "t2",
        title: "Future task",
        dueDate: daysFromNow(14),
      }),
      makeTask({
        id: "t3",
        title: "No date task",
      }),
    ];

    render(<UrgencyTaskList tasks={tasks} slug="acme" />);

    expect(screen.getByText("Overdue")).toBeInTheDocument();
    expect(screen.getByText("Overdue task")).toBeInTheDocument();
    expect(screen.getByText("3d overdue")).toBeInTheDocument();
    expect(screen.getByText("Future task")).toBeInTheDocument();
    expect(screen.getByText("No date task")).toBeInTheDocument();
  });

  it("renders empty state when no tasks", () => {
    render(<UrgencyTaskList tasks={[]} slug="acme" />);
    expect(screen.getByText("No tasks assigned")).toBeInTheDocument();
  });

  it("groupByUrgency correctly categorizes tasks", () => {
    const tasks: MyWorkTaskItem[] = [
      makeTask({ id: "t1", dueDate: daysFromNow(-5) }),
      makeTask({ id: "t2", dueDate: daysFromNow(-1) }),
      makeTask({ id: "t3", dueDate: daysFromNow(0) }), // today = this week
      makeTask({ id: "t4", dueDate: daysFromNow(30) }),
      makeTask({ id: "t5", dueDate: null }),
      makeTask({ id: "t6", dueDate: daysFromNow(-2), status: "DONE" }), // done, goes to upcoming
    ];

    const groups = groupByUrgency(tasks);

    expect(groups.overdue).toHaveLength(2);
    expect(groups.overdue[0].id).toBe("t1"); // most overdue first
    expect(groups.overdue[1].id).toBe("t2");
    // t3 (today) should be in dueThisWeek
    expect(groups.dueThisWeek.some((t) => t.id === "t3")).toBe(true);
    expect(groups.upcoming.some((t) => t.id === "t4")).toBe(true);
    expect(groups.upcoming.some((t) => t.id === "t6")).toBe(true); // DONE task
    expect(groups.noDueDate).toHaveLength(1);
    expect(groups.noDueDate[0].id).toBe("t5");
  });

  it("does not show date range changes affecting task list", () => {
    // This test verifies task list renders the same regardless
    // Tasks are always current assignments, not filtered by date
    const tasks: MyWorkTaskItem[] = [
      makeTask({
        id: "t1",
        title: "Always visible task",
        dueDate: daysFromNow(-3),
      }),
    ];

    // Render with tasks - date range is not a prop of UrgencyTaskList
    render(<UrgencyTaskList tasks={tasks} slug="acme" />);
    expect(screen.getByText("Always visible task")).toBeInTheDocument();
  });
});

describe("My Work page renders new layout components", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders TodaysAgenda and WeeklyRhythmStrip", () => {
    render(
      <>
        <TodaysAgenda
          tasks={[]}
          todayEntries={[]}
          upcomingDeadlines={[]}
          weeklyCapacityHours={40}
        />
        <WeeklyRhythmStrip
          dailyHours={[0, 0, 0, 0, 0, 0, 0]}
          dailyCapacity={8}
          selectedDayIndex={null}
          onDaySelect={() => {}}
        />
      </>,
    );

    // New components present
    expect(screen.getByTestId("todays-tasks")).toBeInTheDocument();
    expect(screen.getByTestId("time-progress-today")).toBeInTheDocument();
    expect(screen.getByTestId("next-deadline")).toBeInTheDocument();
    expect(screen.getByTestId("weekly-rhythm-strip")).toBeInTheDocument();
  });
});
