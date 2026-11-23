import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import {
  fetchProjectTimeSummary,
  fetchTimeSummaryByTask,
} from "@/app/(app)/org/[slug]/matters/[id]/time-summary-actions";
import { TimeSummaryPanel } from "@/components/projects/time-summary-panel";
import type { MatterTimeSummary, MemberTimeSummary, TaskTimeSummary } from "@/lib/types";

// Mock the server actions
vi.mock("@/app/(app)/org/[slug]/matters/[id]/time-summary-actions", () => ({
  fetchProjectTimeSummary: vi.fn(),
  fetchTimeSummaryByMember: vi.fn(),
  fetchTimeSummaryByTask: vi.fn(),
}));

// --- Test data ---

const emptySummary: MatterTimeSummary = {
  billableMinutes: 0,
  nonBillableMinutes: 0,
  totalMinutes: 0,
  contributorCount: 0,
  entryCount: 0,
};

const populatedSummary: MatterTimeSummary = {
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
    vi.clearAllMocks();
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
    const statGrid = screen.getByText("Total Time").closest<HTMLElement>("div[class*='grid']")!;

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
      screen.getByText("Log time on tasks to see matter time summaries here")
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
    const fromInput = dateInputs.find((el) => el.getAttribute("type") === "date");
    expect(fromInput).toBeDefined();
  });

  // F-657A-1: the matter page re-renders with fresh server data after a time entry is logged
  // (revalidatePath). If the Time tab mounted before that refresh committed, the panel must still
  // show the fresh data, not the values it mounted with.
  it("shows fresh server data when its initial props change after mount (no date filter)", () => {
    const before: TaskTimeSummary[] = [
      {
        taskId: "t1",
        taskTitle: "Report estate to Master of High Court",
        billableMinutes: 60,
        totalMinutes: 60,
        entryCount: 1,
      },
    ];
    const after: TaskTimeSummary[] = [
      ...before,
      {
        taskId: "t2",
        taskTitle: "Prepare Liquidation & Distribution account",
        billableMinutes: 360,
        totalMinutes: 360,
        entryCount: 1,
      },
    ];
    const oneHour: MatterTimeSummary = {
      billableMinutes: 60,
      nonBillableMinutes: 0,
      totalMinutes: 60,
      contributorCount: 1,
      entryCount: 1,
    };
    const sevenHours: MatterTimeSummary = {
      ...oneHour,
      billableMinutes: 420,
      totalMinutes: 420,
      entryCount: 2,
    };

    const { rerender } = render(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={oneHour}
        initialByTask={before}
        initialByMember={null}
      />
    );
    expect(
      screen.queryByText("Prepare Liquidation & Distribution account")
    ).not.toBeInTheDocument();

    rerender(
      <TimeSummaryPanel
        projectId="p1"
        initialSummary={sevenHours}
        initialByTask={after}
        initialByMember={null}
      />
    );

    const row = screen.getByText("Prepare Liquidation & Distribution account").closest("tr");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getAllByText("6h").length).toBeGreaterThan(0);
    expect(screen.getAllByText("7h").length).toBeGreaterThan(0);
  });

  describe("date filter", () => {
    const filteredSummary: MatterTimeSummary = {
      billableMinutes: 90,
      nonBillableMinutes: 0,
      totalMinutes: 90,
      contributorCount: 1,
      entryCount: 2,
    };
    const filteredByTask: TaskTimeSummary[] = [
      {
        taskId: "t9",
        taskTitle: "Filtered task",
        billableMinutes: 90,
        totalMinutes: 90,
        entryCount: 2,
      },
    ];
    const fromInput = () =>
      screen
        .getAllByDisplayValue("")
        .find((el) => el.getAttribute("type") === "date") as HTMLElement;

    it("fetches and shows the filtered data when a From date is set", async () => {
      vi.mocked(fetchProjectTimeSummary).mockResolvedValue(filteredSummary);
      vi.mocked(fetchTimeSummaryByTask).mockResolvedValue(filteredByTask);
      render(
        <TimeSummaryPanel
          projectId="p1"
          initialSummary={populatedSummary}
          initialByTask={taskSummaries}
          initialByMember={null}
        />
      );
      expect(fetchProjectTimeSummary).not.toHaveBeenCalled();

      fireEvent.change(fromInput(), { target: { value: "2026-11-01" } });

      expect(await screen.findByText("Filtered task")).toBeInTheDocument();
      expect(fetchProjectTimeSummary).toHaveBeenCalledWith("p1", "2026-11-01", undefined);
      expect(screen.queryByText("Design mockups")).not.toBeInTheDocument();
    });

    it("re-fetches the filtered view when the server data changes underneath it", async () => {
      vi.mocked(fetchProjectTimeSummary).mockResolvedValue(filteredSummary);
      vi.mocked(fetchTimeSummaryByTask).mockResolvedValue(filteredByTask);
      const { rerender } = render(
        <TimeSummaryPanel
          projectId="p1"
          initialSummary={populatedSummary}
          initialByTask={taskSummaries}
          initialByMember={null}
        />
      );
      fireEvent.change(fromInput(), { target: { value: "2026-11-01" } });
      await screen.findByText("Filtered task");
      expect(fetchProjectTimeSummary).toHaveBeenCalledTimes(1);

      rerender(
        <TimeSummaryPanel
          projectId="p1"
          initialSummary={{ ...populatedSummary, entryCount: 16 }}
          initialByTask={taskSummaries}
          initialByMember={null}
        />
      );

      await waitFor(() => expect(fetchProjectTimeSummary).toHaveBeenCalledTimes(2));
      expect(screen.getByText("Filtered task")).toBeInTheDocument();
    });

    it("shows the server data again, without a fetch, when the filter is cleared", async () => {
      vi.mocked(fetchProjectTimeSummary).mockResolvedValue(filteredSummary);
      vi.mocked(fetchTimeSummaryByTask).mockResolvedValue(filteredByTask);
      render(
        <TimeSummaryPanel
          projectId="p1"
          initialSummary={populatedSummary}
          initialByTask={taskSummaries}
          initialByMember={null}
        />
      );
      const input = fromInput();
      fireEvent.change(input, { target: { value: "2026-11-01" } });
      await screen.findByText("Filtered task");

      fireEvent.change(input, { target: { value: "" } });

      expect(screen.getByText("Design mockups")).toBeInTheDocument();
      expect(screen.queryByText("Filtered task")).not.toBeInTheDocument();
      expect(fetchProjectTimeSummary).toHaveBeenCalledTimes(1);
    });
  });
});
