import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { TimeEntryList } from "@/components/tasks/time-entry-list";
import type { TimeEntry } from "@/lib/types";

// Mock the server actions used by edit/delete dialogs
vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  updateTimeEntry: vi.fn(),
  deleteTimeEntry: vi.fn(),
}));

// --- Test data ---

function makeTimeEntry(overrides: Partial<TimeEntry> = {}): TimeEntry {
  return {
    id: "te1",
    taskId: "t1",
    memberId: "m1",
    memberName: "Alice",
    date: "2026-02-10",
    durationMinutes: 90,
    billable: true,
    rateCents: null,
    description: "Worked on feature",
    createdAt: "2026-02-10T10:00:00Z",
    updatedAt: "2026-02-10T10:00:00Z",
    ...overrides,
  };
}

const ownEntry = makeTimeEntry({
  id: "te1",
  memberId: "current-member",
  memberName: "Me",
  durationMinutes: 90,
  billable: true,
  description: "My work",
});

const otherEntry = makeTimeEntry({
  id: "te2",
  memberId: "other-member",
  memberName: "Bob",
  durationMinutes: 45,
  billable: false,
  description: "Bob's work",
});

describe("TimeEntryList", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders entries with formatted duration and member names", () => {
    render(
      <TimeEntryList
        entries={[ownEntry, otherEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
      />,
    );

    // Check header with total duration: 90 + 45 = 135 min = "2h 15m"
    expect(screen.getByText("Time Entries")).toBeInTheDocument();
    expect(screen.getByText("2h 15m")).toBeInTheDocument();

    // Check individual durations
    expect(screen.getByText("1h 30m")).toBeInTheDocument();
    expect(screen.getByText("45m")).toBeInTheDocument();

    // Check member names
    expect(screen.getByText("Me")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();

    // Check billable badge variants via data-variant attribute
    const billableBadge = screen.getAllByText(/^Billable$/).find(
      (el) => el.getAttribute("data-variant") === "success",
    );
    expect(billableBadge).toBeDefined();
    const nonBillableBadge = screen.getAllByText(/^Non-billable$/).find(
      (el) => el.getAttribute("data-variant") === "neutral",
    );
    expect(nonBillableBadge).toBeDefined();
  });

  it("shows empty state when no entries exist", () => {
    render(
      <TimeEntryList
        entries={[]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
      />,
    );

    expect(screen.getByText("No time logged yet")).toBeInTheDocument();
    expect(
      screen.getByText("Use the Log Time button to record time spent on this task"),
    ).toBeInTheDocument();
  });

  it("shows edit/delete buttons for own entries", () => {
    render(
      <TimeEntryList
        entries={[ownEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
      />,
    );

    const table = screen.getByRole("table");
    expect(
      within(table).getByRole("button", { name: /edit time entry by me/i }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("button", { name: /delete time entry by me/i }),
    ).toBeInTheDocument();
  });

  it("hides edit/delete buttons for other members entries when contributor", () => {
    render(
      <TimeEntryList
        entries={[otherEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
      />,
    );

    // With only another member's entry and org:member role,
    // no actions column should be shown at all
    expect(
      screen.queryByRole("button", { name: /edit time entry/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete time entry/i }),
    ).not.toBeInTheDocument();
  });

  it("shows edit/delete buttons for all entries when user is admin", () => {
    render(
      <TimeEntryList
        entries={[ownEntry, otherEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:admin"
      />,
    );

    const table = screen.getByRole("table");
    // Admin should see edit/delete on both entries
    expect(
      within(table).getByRole("button", { name: /edit time entry by me/i }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("button", { name: /edit time entry by bob/i }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("button", { name: /delete time entry by me/i }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("button", { name: /delete time entry by bob/i }),
    ).toBeInTheDocument();
  });
});
