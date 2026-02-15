import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LogTimeDialog } from "@/components/tasks/log-time-dialog";
import { TimeEntryList } from "@/components/tasks/time-entry-list";
import { EditTimeEntryDialog } from "@/components/tasks/edit-time-entry-dialog";
import type { TimeEntry } from "@/lib/types";

// Mock server actions
const mockCreateTimeEntry = vi.fn();
const mockResolveRate = vi.fn();
const mockUpdateTimeEntry = vi.fn();
const mockDeleteTimeEntry = vi.fn();

vi.mock("@/app/(app)/org/[slug]/projects/[id]/time-entry-actions", () => ({
  createTimeEntry: (...args: unknown[]) => mockCreateTimeEntry(...args),
  resolveRate: (...args: unknown[]) => mockResolveRate(...args),
  updateTimeEntry: (...args: unknown[]) => mockUpdateTimeEntry(...args),
  deleteTimeEntry: (...args: unknown[]) => mockDeleteTimeEntry(...args),
}));

function makeTimeEntry(overrides: Partial<TimeEntry> = {}): TimeEntry {
  return {
    id: "te1",
    taskId: "t1",
    memberId: "m1",
    memberName: "Alice Johnson",
    date: "2025-06-15",
    durationMinutes: 120,
    billable: true,
    rateCents: null,
    billingRateSnapshot: 200,
    billingRateCurrency: "USD",
    costRateSnapshot: 100,
    costRateCurrency: "USD",
    billableValue: 400,
    costValue: 200,
    description: "Worked on feature",
    invoiceId: null,
    invoiceNumber: null,
    createdAt: "2025-06-15T10:00:00Z",
    updatedAt: "2025-06-15T10:00:00Z",
    ...overrides,
  };
}

function makeEntries(): TimeEntry[] {
  return [
    makeTimeEntry({ id: "te1", billable: true, memberName: "Alice Johnson" }),
    makeTimeEntry({
      id: "te2",
      billable: false,
      memberName: "Bob Smith",
      memberId: "m2",
      billingRateSnapshot: null,
      billingRateCurrency: null,
      billableValue: null,
      costRateSnapshot: null,
      costRateCurrency: null,
      costValue: null,
    }),
    makeTimeEntry({
      id: "te3",
      billable: true,
      memberName: "Carol Davis",
      memberId: "m3",
      durationMinutes: 60,
    }),
  ];
}

describe("Time Entry Billable UI", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("billable checkbox renders checked by default in LogTimeDialog", async () => {
    const user = userEvent.setup();

    render(
      <LogTimeDialog
        slug="acme"
        projectId="p1"
        taskId="t1"
        memberId="m1"
      >
        <button>Open Log Time Dialog</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Dialog"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Time" }),
      ).toBeInTheDocument();
    });

    const checkbox = screen.getByRole("checkbox", { name: /billable/i });
    expect(checkbox).toBeChecked();
  });

  it("unchecking billable hides rate preview in LogTimeDialog", async () => {
    mockResolveRate.mockResolvedValue({
      hourlyRate: 200,
      currency: "USD",
      source: "MEMBER_DEFAULT",
      billingRateId: "br1",
    });

    const user = userEvent.setup();

    render(
      <LogTimeDialog
        slug="acme"
        projectId="p1"
        taskId="t1"
        memberId="m1"
      >
        <button>Open Log Time Rate Preview</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Rate Preview"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Time" }),
      ).toBeInTheDocument();
    });

    // Rate preview should appear (after debounce + resolve)
    await waitFor(() => {
      expect(screen.getByTestId("rate-preview")).toBeInTheDocument();
    });

    // Uncheck billable
    const checkbox = screen.getByRole("checkbox", { name: /billable/i });
    await user.click(checkbox);

    // Rate preview should be hidden
    await waitFor(() => {
      expect(screen.queryByTestId("rate-preview")).not.toBeInTheDocument();
    });
  });

  it("rate preview shows resolved rate with computed value", async () => {
    mockResolveRate.mockResolvedValue({
      hourlyRate: 200,
      currency: "USD",
      source: "PROJECT_OVERRIDE",
      billingRateId: "br1",
    });

    const user = userEvent.setup();

    render(
      <LogTimeDialog
        slug="acme"
        projectId="p1"
        taskId="t1"
        memberId="m1"
      >
        <button>Open Log Time Rate Resolve</button>
      </LogTimeDialog>,
    );

    await user.click(screen.getByText("Open Log Time Rate Resolve"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Log Time" }),
      ).toBeInTheDocument();
    });

    // Wait for rate resolution
    await waitFor(() => {
      const preview = screen.getByTestId("rate-preview");
      expect(preview).toHaveTextContent("$200.00/hr");
      expect(preview).toHaveTextContent("project override");
    });

    // Enter duration to see computed value
    const hoursInput = document.getElementById("time-hours") as HTMLInputElement;
    await user.clear(hoursInput);
    await user.type(hoursInput, "2");

    // Computed value should appear
    await waitFor(() => {
      const preview = screen.getByTestId("rate-preview");
      expect(preview).toHaveTextContent("$400.00");
    });
  });

  it("billing status badges render in TimeEntryList", () => {
    const entries = makeEntries();

    render(<TimeEntryList entries={entries} />);

    // Scope to the table body to avoid matching header/filter text
    const tbody = screen.getByRole("table").querySelector("tbody")!;
    // te1 and te3 are billable with no invoiceId → "Unbilled" badges
    const unbilledBadges = within(tbody).getAllByText("Unbilled");
    expect(unbilledBadges).toHaveLength(2);
    // te2 is non-billable → no badge rendered
    expect(within(tbody).queryByText("Non-billable")).not.toBeInTheDocument();
  });

  it("filter toggle filters entries by billing status", async () => {
    const entries = makeEntries();
    const user = userEvent.setup();

    render(<TimeEntryList entries={entries} />);

    // All filter should show all 3 entries initially
    const allRows = screen.getAllByRole("row");
    // 1 header + 3 data rows
    expect(allRows).toHaveLength(4);

    // Click "Unbilled" filter (billable entries without invoiceId)
    const filterGroup = screen.getByRole("group", {
      name: "Billing status filter",
    });
    const unbilledBtn = within(filterGroup).getByText("Unbilled");
    await user.click(unbilledBtn);

    // Only unbilled entries (te1 and te3: billable, no invoiceId) should show
    const unbilledRows = screen.getAllByRole("row");
    expect(unbilledRows).toHaveLength(3); // 1 header + 2 data rows

    // Click "Non-billable" filter
    const nonBillableBtn = within(filterGroup).getByText("Non-billable");
    await user.click(nonBillableBtn);

    // Only non-billable entry (te2) should show
    const nonBillableRows = screen.getAllByRole("row");
    expect(nonBillableRows).toHaveLength(2); // 1 header + 1 data row
  });

  it("edit dialog shows rate snapshot as read-only info", async () => {
    const entry = makeTimeEntry({
      billingRateSnapshot: 150,
      billingRateCurrency: "USD",
      billableValue: 300,
      costRateSnapshot: 80,
      costRateCurrency: "USD",
      costValue: 160,
    });

    const user = userEvent.setup();

    render(
      <EditTimeEntryDialog
        entry={entry}
        slug="acme"
        projectId="p1"
      >
        <button>Open Edit Time Entry</button>
      </EditTimeEntryDialog>,
    );

    await user.click(screen.getByText("Open Edit Time Entry"));

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Edit Time Entry" }),
      ).toBeInTheDocument();
    });

    // Rate snapshot should show
    const snapshot = screen.getByTestId("rate-snapshot");
    expect(snapshot).toHaveTextContent("Billing rate:");
    expect(snapshot).toHaveTextContent("$150.00/hr");
    expect(snapshot).toHaveTextContent("$300.00");
    expect(snapshot).toHaveTextContent("Cost rate:");
    expect(snapshot).toHaveTextContent("$80.00/hr");
    expect(snapshot).toHaveTextContent("$160.00");
  });
});
