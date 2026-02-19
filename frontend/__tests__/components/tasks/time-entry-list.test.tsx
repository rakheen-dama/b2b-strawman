import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    billingRateSnapshot: null,
    billingRateCurrency: null,
    costRateSnapshot: null,
    costRateCurrency: null,
    billableValue: null,
    costValue: null,
    description: "Worked on feature",
    invoiceId: null,
    invoiceNumber: null,
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

    // Check billing status badges via data-variant attribute (scope to table body)
    const tbody = screen.getByRole("table").querySelector("tbody")!;
    // ownEntry is billable with no invoiceId → "Unbilled" (neutral)
    const unbilledBadge = within(tbody).getByText("Unbilled");
    expect(unbilledBadge.getAttribute("data-variant")).toBe("neutral");
    // otherEntry is non-billable → no badge rendered
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

  it("shows edit/delete buttons for all entries when user is project lead (canManage)", () => {
    render(
      <TimeEntryList
        entries={[ownEntry, otherEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
        canManage={true}
      />,
    );

    const table = screen.getByRole("table");
    // Project lead (canManage=true) should see edit/delete on all entries
    // even with org:member role
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

  it("hides edit/delete for other entries when canManage is false and role is member", () => {
    render(
      <TimeEntryList
        entries={[otherEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
        canManage={false}
      />,
    );

    // Non-lead member should not see edit/delete on other member's entries
    expect(
      screen.queryByRole("button", { name: /edit time entry/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete time entry/i }),
    ).not.toBeInTheDocument();
  });

  it("disables edit/delete buttons for billed entries with tooltip", () => {
    const billedEntry = makeTimeEntry({
      id: "te-billed",
      memberId: "current-member",
      memberName: "Me",
      billable: true,
      invoiceId: "inv-123",
      invoiceNumber: "INV-0001",
    });

    render(
      <TimeEntryList
        entries={[billedEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:admin"
      />,
    );

    // Edit and delete buttons should be disabled
    const editBtn = screen.getByRole("button", {
      name: /edit time entry by me/i,
    });
    expect(editBtn).toBeDisabled();
    const deleteBtn = screen.getByRole("button", {
      name: /delete time entry by me/i,
    });
    expect(deleteBtn).toBeDisabled();
  });

  it("renders Billed badge with link for billed entries", () => {
    const billedEntry = makeTimeEntry({
      id: "te-billed-link",
      memberId: "current-member",
      memberName: "Me",
      billable: true,
      invoiceId: "inv-456",
      invoiceNumber: "INV-0002",
    });

    render(
      <TimeEntryList
        entries={[billedEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="current-member"
        orgRole="org:member"
      />,
    );

    // Scope to table body to avoid matching the filter button "Billed"
    const tbody = screen.getByRole("table").querySelector("tbody")!;
    const billedBadge = within(tbody).getByText("Billed");
    expect(billedBadge.getAttribute("data-variant")).toBe("success");
    const link = billedBadge.closest("a");
    expect(link).toHaveAttribute("href", "/org/acme/invoices/inv-456");
  });

  it("shows EmptyState when billing filter returns zero entries", async () => {
    const user = userEvent.setup();

    // Entry is billable+invoiced (billed), so filtering "unbilled" yields 0
    const billedEntry = makeTimeEntry({
      id: "te-billed",
      memberId: "m1",
      billable: true,
      invoiceId: "inv-1",
      invoiceNumber: "INV-001",
    });

    render(
      <TimeEntryList
        entries={[billedEntry]}
        slug="acme"
        projectId="p1"
        currentMemberId="m1"
        orgRole="org:member"
      />,
    );

    // Click "Unbilled" filter button
    await user.click(screen.getByRole("button", { name: "Unbilled" }));

    expect(screen.getByText("No matching time entries")).toBeInTheDocument();
    expect(
      screen.getByText("Try a different billing status filter."),
    ).toBeInTheDocument();
  });
});
