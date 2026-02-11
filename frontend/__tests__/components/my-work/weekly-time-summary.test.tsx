import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WeeklyTimeSummary } from "@/components/my-work/weekly-time-summary";
import type { MyWorkTimeSummary } from "@/lib/types";

// Mock the server action
const mockFetchMyTimeSummary = vi.fn();

vi.mock("@/app/(app)/org/[slug]/my-work/actions", () => ({
  fetchMyTimeSummary: (...args: unknown[]) => mockFetchMyTimeSummary(...args),
}));

// --- Test data ---

const populatedSummary: MyWorkTimeSummary = {
  memberId: "m1",
  fromDate: "2026-02-09",
  toDate: "2026-02-15",
  billableMinutes: 480,
  nonBillableMinutes: 120,
  totalMinutes: 600,
  byProject: [
    {
      projectId: "p1",
      projectName: "Project Alpha",
      billableMinutes: 300,
      nonBillableMinutes: 60,
      totalMinutes: 360,
    },
    {
      projectId: "p2",
      projectName: "Project Beta",
      billableMinutes: 180,
      nonBillableMinutes: 60,
      totalMinutes: 240,
    },
  ],
};

const emptySummary: MyWorkTimeSummary = {
  memberId: "m1",
  fromDate: "2026-02-09",
  toDate: "2026-02-15",
  billableMinutes: 0,
  nonBillableMinutes: 0,
  totalMinutes: 0,
  byProject: [],
};

describe("WeeklyTimeSummary", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("displays formatted totals for billable and non-billable time", () => {
    render(
      <WeeklyTimeSummary
        initialSummary={populatedSummary}
        initialFrom="2026-02-09"
      />
    );

    expect(screen.getByText("This Week")).toBeInTheDocument();
    // Total: 600 min = 10h
    expect(screen.getByText("10h")).toBeInTheDocument();
    // Billable: 480 min = 8h
    expect(screen.getByText("8h")).toBeInTheDocument();
    // Non-billable: 120 min = 2h
    expect(screen.getByText("2h")).toBeInTheDocument();
  });

  it("renders by-project breakdown with project names", () => {
    render(
      <WeeklyTimeSummary
        initialSummary={populatedSummary}
        initialFrom="2026-02-09"
      />
    );

    expect(screen.getByText("By Project")).toBeInTheDocument();
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
    // Alpha total: 360 min = 6h
    expect(screen.getByText("6h")).toBeInTheDocument();
    // Beta total: 240 min = 4h
    expect(screen.getByText("4h")).toBeInTheDocument();
  });

  it("shows week navigation and calls fetchMyTimeSummary on week change", async () => {
    const newWeekSummary: MyWorkTimeSummary = {
      ...emptySummary,
      totalMinutes: 300,
      billableMinutes: 200,
      nonBillableMinutes: 100,
    };
    mockFetchMyTimeSummary.mockResolvedValue(newWeekSummary);

    const user = userEvent.setup();

    render(
      <WeeklyTimeSummary
        initialSummary={populatedSummary}
        initialFrom="2026-02-09"
      />
    );

    // Week navigation buttons should be present
    const prevButton = screen.getByRole("button", { name: /previous week/i });
    const nextButton = screen.getByRole("button", { name: /next week/i });
    expect(prevButton).toBeInTheDocument();
    expect(nextButton).toBeInTheDocument();

    // Click previous week
    await user.click(prevButton);

    // Should call fetchMyTimeSummary with the previous week's dates
    expect(mockFetchMyTimeSummary).toHaveBeenCalledTimes(1);
    const [from, to] = mockFetchMyTimeSummary.mock.calls[0];
    expect(from).toBe("2026-02-02");
    expect(to).toBe("2026-02-08");
  });

  it("shows empty state when no time is tracked", () => {
    render(
      <WeeklyTimeSummary
        initialSummary={null}
        initialFrom="2026-02-09"
      />
    );

    expect(
      screen.getByText("No time tracked this week")
    ).toBeInTheDocument();
    expect(screen.queryByText("By Project")).not.toBeInTheDocument();
    expect(screen.queryByText("Total")).not.toBeInTheDocument();
  });
});
