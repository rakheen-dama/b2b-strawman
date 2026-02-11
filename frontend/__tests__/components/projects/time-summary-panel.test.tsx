import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { TimeSummaryPanel } from "@/components/projects/time-summary-panel";
import type {
  ProjectTimeSummary,
  MemberTimeSummary,
  TaskTimeSummary,
} from "@/lib/types";

// Mock the server actions
vi.mock(
  "@/app/(app)/org/[slug]/projects/[id]/time-summary-actions",
  () => ({
    fetchProjectTimeSummary: vi.fn(),
    fetchTimeSummaryByMember: vi.fn(),
    fetchTimeSummaryByTask: vi.fn(),
  })
);

// --- Test data ---

const emptySummary: ProjectTimeSummary = {
  billableMinutes: 0,
  nonBillableMinutes: 0,
  totalMinutes: 0,
  contributorCount: 0,
  entryCount: 0,
};

const populatedSummary: ProjectTimeSummary = {
  billableMinutes: 480,
  nonBillableMinutes: 120,
  totalMinutes: 600,
  contributorCount: 3,
  entryCount: 15,
};

const taskSummaries: TaskTimeSummary[] = [
  {
    taskId: "t1",
    taskTitle: "Design mockups",
    billableMinutes: 240,
    totalMinutes: 300,
    entryCount: 5,
  },
  {
    taskId: "t2",
    taskTitle: "API integration",
    billableMinutes: 180,
    totalMinutes: 200,
    entryCount: 7,
  },
  {
    taskId: "t3",
    taskTitle: "Code review",
    billableMinutes: 60,
    totalMinutes: 100,
    entryCount: 3,
  },
];

const memberSummaries: MemberTimeSummary[] = [
  {
    memberId: "m1",
    memberName: "Alice Johnson",
    billableMinutes: 300,
    nonBillableMinutes: 60,
    totalMinutes: 360,
  },
  {
    memberId: "m2",
    memberName: "Bob Smith",
    billableMinutes: 180,
    nonBillableMinutes: 60,
    totalMinutes: 240,
  },
];

describe("TimeSummaryPanel", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders total summary cards with formatted durations", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={populatedSummary}
        initialByTask={taskSummaries}
        initialByMember={memberSummaries}
      />
    );

    // "Total Time" is unique to stat cards
    expect(screen.getByText("Total Time")).toBeInTheDocument();
    // "Contributors" is unique to stat cards
    expect(screen.getByText("Contributors")).toBeInTheDocument();

    // Scope to the stat card grid to verify values without colliding with table headers
    const statGrid = screen.getByText("Total Time").closest("div[class*='grid']")!;

    // Total: 600 min = 10h
    expect(within(statGrid).getByText("10h")).toBeInTheDocument();
    // Billable: 480 min = 8h
    expect(within(statGrid).getByText("8h")).toBeInTheDocument();
    // Non-billable: 120 min = 2h
    expect(within(statGrid).getByText("2h")).toBeInTheDocument();
    // Contributors: 3
    expect(within(statGrid).getByText("3")).toBeInTheDocument();
    // Entries: 15
    expect(within(statGrid).getByText("15")).toBeInTheDocument();
  });

  it("renders by-task table sorted by total descending", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={populatedSummary}
        initialByTask={taskSummaries}
        initialByMember={null}
      />
    );

    expect(screen.getByText("By Task")).toBeInTheDocument();

    // All task titles should be present
    expect(screen.getByText("Design mockups")).toBeInTheDocument();
    expect(screen.getByText("API integration")).toBeInTheDocument();
    expect(screen.getByText("Code review")).toBeInTheDocument();

    // Verify sort order: Design mockups (300) > API integration (200) > Code review (100)
    const table = screen.getByText("By Task").closest("div")!;
    const rows = within(table).getAllByRole("row");
    // rows[0] is header, rows[1] should be "Design mockups", rows[2] "API integration", rows[3] "Code review"
    expect(rows[1]).toHaveTextContent("Design mockups");
    expect(rows[2]).toHaveTextContent("API integration");
    expect(rows[3]).toHaveTextContent("Code review");
  });

  it("renders by-member table for leads/admins", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={populatedSummary}
        initialByTask={taskSummaries}
        initialByMember={memberSummaries}
      />
    );

    expect(screen.getByText("By Member")).toBeInTheDocument();
    expect(screen.getByText("Alice Johnson")).toBeInTheDocument();
    expect(screen.getByText("Bob Smith")).toBeInTheDocument();
  });

  it("hides by-member section when initialByMember is null (contributors)", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={populatedSummary}
        initialByTask={taskSummaries}
        initialByMember={null}
      />
    );

    expect(screen.queryByText("By Member")).not.toBeInTheDocument();
    expect(screen.queryByText("Alice Johnson")).not.toBeInTheDocument();
  });

  it("renders empty state when no time tracked", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={emptySummary}
        initialByTask={[]}
        initialByMember={null}
      />
    );

    expect(screen.getByText("No time tracked yet")).toBeInTheDocument();
    expect(
      screen.getByText("Log time on tasks to see project time summaries here")
    ).toBeInTheDocument();

    // Should not render stat cards or tables
    expect(screen.queryByText("Total Time")).not.toBeInTheDocument();
    expect(screen.queryByText("By Task")).not.toBeInTheDocument();
  });

  it("renders date range inputs", () => {
    render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={populatedSummary}
        initialByTask={taskSummaries}
        initialByMember={null}
      />
    );

    // Date range labels
    expect(screen.getByText("From")).toBeInTheDocument();
    expect(screen.getByText("To")).toBeInTheDocument();

    // Date inputs should be present
    const dateInputs = screen.getAllByDisplayValue("");
    const fromInput = dateInputs.find(
      (el) => el.getAttribute("type") === "date"
    );
    expect(fromInput).toBeDefined();
  });
});
