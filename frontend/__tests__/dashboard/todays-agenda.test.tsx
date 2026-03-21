import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TodaysAgenda } from "@/components/dashboard/todays-agenda";
import type { MyWorkTaskItem, MyWorkTimeEntryItem } from "@/lib/types";
import type { PersonalDeadline } from "@/lib/dashboard-types";

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

function today(): string {
  return new Date().toLocaleDateString("en-CA");
}

function daysFromNow(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${dd}`;
}

function makeTask(
  overrides: Partial<MyWorkTaskItem> & { id: string },
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

const emptyEntries: MyWorkTimeEntryItem[] = [];
const emptyDeadlines: PersonalDeadline[] = [];

describe("TodaysAgenda", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders today's tasks section", () => {
    const tasks = [
      makeTask({ id: "t1", title: "Fix login bug", dueDate: today() }),
      makeTask({
        id: "t2",
        title: "Review PR",
        dueDate: daysFromNow(-1),
      }),
    ];

    render(
      <TodaysAgenda
        tasks={tasks}
        todayEntries={emptyEntries}
        upcomingDeadlines={emptyDeadlines}
      />,
    );

    expect(screen.getByTestId("todays-tasks")).toBeInTheDocument();
    expect(screen.getByText("Fix login bug")).toBeInTheDocument();
    expect(screen.getByText("Review PR")).toBeInTheDocument();
  });

  it("renders time progress bar", () => {
    const entries: MyWorkTimeEntryItem[] = [
      {
        id: "e1",
        taskId: "t1",
        taskTitle: "Task 1",
        projectId: "p1",
        projectName: "Project 1",
        date: today(),
        durationMinutes: 135, // 2h 15m
        billable: true,
        description: null,
      },
    ];

    render(
      <TodaysAgenda
        tasks={[]}
        todayEntries={entries}
        upcomingDeadlines={emptyDeadlines}
        weeklyCapacityHours={40}
      />,
    );

    const progressSection = screen.getByTestId("time-progress-today");
    expect(progressSection).toBeInTheDocument();
    expect(screen.getByText("2h 15m")).toBeInTheDocument();
  });

  it("renders next deadline", () => {
    const deadlines: PersonalDeadline[] = [
      {
        taskId: "t1",
        taskName: "Ship feature",
        projectName: "Alpha Project",
        dueDate: daysFromNow(5),
      },
    ];

    render(
      <TodaysAgenda
        tasks={[]}
        todayEntries={emptyEntries}
        upcomingDeadlines={deadlines}
      />,
    );

    const deadlineSection = screen.getByTestId("next-deadline");
    expect(deadlineSection).toBeInTheDocument();
    expect(screen.getByText("Ship feature")).toBeInTheDocument();
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    expect(screen.getByText("5d remaining")).toBeInTheDocument();
  });

  it("handles empty tasks without crashing", () => {
    render(
      <TodaysAgenda
        tasks={[]}
        todayEntries={emptyEntries}
        upcomingDeadlines={emptyDeadlines}
      />,
    );

    expect(screen.getByTestId("todays-tasks")).toBeInTheDocument();
    expect(screen.getByText("No tasks due today")).toBeInTheDocument();
    expect(screen.getByTestId("next-deadline")).toBeInTheDocument();
    expect(screen.getByText("No upcoming deadlines")).toBeInTheDocument();
  });
});
