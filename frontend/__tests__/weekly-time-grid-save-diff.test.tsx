/**
 * F-668A-R1: the weekly grid pre-fills cells from the week's existing entries, so Save must send
 * only what the user changed against that baseline. It must never re-create existing time.
 *
 * Rules (orchestrator decision, F-668A-R1 fix):
 * 1. Cell increased → ONE new entry for the difference (batch, billable: true).
 * 2. Cell decreased/cleared, exactly one editable entry → PUT its duration / DELETE it.
 * 3. Cell decreased with several entries, or a billed entry the server refuses → not saved; an
 *    error names the day and task and points at the matter's Time tab. Other cells still save.
 * 4. Unchanged cells → nothing sent.
 * 5. After a save the week is re-fetched as the new baseline; a second Save sends nothing.
 * 6. Week navigation resets the baseline to the loaded week.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { WeeklyTimeGrid, type GridTaskRow } from "@/components/time-tracking/weekly-time-grid";
import type { MyWorkTimeEntryItem } from "@/lib/types";

const mockSaveWeeklyEntries = vi.fn();
const mockFetchWeekEntries = vi.fn();
const mockFetchPreviousWeekEntries = vi.fn();
const mockUpdateTimeEntry = vi.fn();
const mockDeleteTimeEntry = vi.fn();

vi.mock("@/app/(app)/org/[slug]/my-work/timesheet/actions", () => ({
  saveWeeklyEntries: (...args: unknown[]) => mockSaveWeeklyEntries(...args),
  fetchWeekEntries: (...args: unknown[]) => mockFetchWeekEntries(...args),
  fetchPreviousWeekEntries: (...args: unknown[]) => mockFetchPreviousWeekEntries(...args),
}));

vi.mock("@/app/(app)/org/[slug]/matters/[id]/time-entry-actions", () => ({
  updateTimeEntry: (...args: unknown[]) => mockUpdateTimeEntry(...args),
  deleteTimeEntry: (...args: unknown[]) => mockDeleteTimeEntry(...args),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}));

const task: GridTaskRow = {
  id: "t1",
  projectId: "p1",
  projectName: "Project Alpha",
  title: "Design wireframes",
};

const weekStart = "2026-03-09"; // Monday → Mon 9 … Sun 15

function entry(
  id: string,
  date: string,
  durationMinutes: number,
  extra: Partial<MyWorkTimeEntryItem> = {}
): MyWorkTimeEntryItem {
  return {
    id,
    taskId: "t1",
    taskTitle: "Design wireframes",
    matterId: "p1",
    matterName: "Project Alpha",
    date,
    durationMinutes,
    billable: true,
    description: null,
    ...extra,
  };
}

function okBatch(created: number) {
  return {
    success: true,
    result: { created: [], errors: [], totalCreated: created, totalErrors: 0 },
  };
}

function renderGrid(existingEntries: MyWorkTimeEntryItem[]) {
  return render(
    <WeeklyTimeGrid
      tasks={[task]}
      existingEntries={existingEntries}
      weekStart={weekStart}
      allTasks={[task]}
      slug="test-org"
    />
  );
}

async function setCell(user: ReturnType<typeof userEvent.setup>, day: number, value: string) {
  const input = screen.getByLabelText(`Hours for day ${day}`);
  await user.clear(input);
  if (value !== "") await user.type(input, value);
  await user.tab();
}

async function clickSave(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Save" }));
}

function allToastText(): string {
  const calls = [
    ...vi.mocked(toast.error).mock.calls,
    ...vi.mocked(toast.warning).mock.calls,
    ...vi.mocked(toast.success).mock.calls,
  ];
  return JSON.stringify(calls);
}

describe("WeeklyTimeGrid save — only changes are persisted (F-668A-R1)", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it("rule 1+4: a pre-filled cell is not re-created when another cell is added", async () => {
    const user = userEvent.setup();
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    mockFetchWeekEntries.mockResolvedValue([
      entry("e1", "2026-03-09", 60),
      entry("e2", "2026-03-10", 30),
    ]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await setCell(user, 2, "0.5");
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "t1", date: "2026-03-10", durationMinutes: 30, billable: true },
    ]);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });

  it("rule 1: increasing a cell creates one entry for the difference only", async () => {
    const user = userEvent.setup();
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    mockFetchWeekEntries.mockResolvedValue([
      entry("e1", "2026-03-09", 60),
      entry("e3", "2026-03-09", 30),
    ]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await setCell(user, 1, "1.5");
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "t1", date: "2026-03-09", durationMinutes: 30, billable: true },
    ]);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
  });

  it("rule 2: reducing a single-entry cell updates that entry's duration and nothing else", async () => {
    const user = userEvent.setup();
    mockUpdateTimeEntry.mockResolvedValue({ success: true });
    mockFetchWeekEntries.mockResolvedValue([
      entry("e1", "2026-03-09", 30, { billable: false, description: "Drafting" }),
    ]);
    renderGrid([entry("e1", "2026-03-09", 60, { billable: false, description: "Drafting" })]);

    await setCell(user, 1, "0.5");
    await clickSave(user);

    await waitFor(() => expect(mockUpdateTimeEntry).toHaveBeenCalledTimes(1));
    expect(mockUpdateTimeEntry).toHaveBeenCalledWith("test-org", "p1", "e1", {
      durationMinutes: 30,
    });
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });

  it("rule 2: clearing a single-entry cell deletes that entry", async () => {
    const user = userEvent.setup();
    mockDeleteTimeEntry.mockResolvedValue({ success: true });
    mockFetchWeekEntries.mockResolvedValue([]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await setCell(user, 1, "");
    await clickSave(user);

    await waitFor(() => expect(mockDeleteTimeEntry).toHaveBeenCalledTimes(1));
    expect(mockDeleteTimeEntry).toHaveBeenCalledWith("test-org", "p1", "e1");
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
  });

  it("rule 3: reducing a multi-entry cell is refused with a day+task error; other cells still save", async () => {
    const user = userEvent.setup();
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    const after = [
      entry("e1", "2026-03-09", 60),
      entry("e2", "2026-03-09", 30),
      entry("e4", "2026-03-10", 60),
    ];
    mockFetchWeekEntries.mockResolvedValue(after);
    renderGrid([entry("e1", "2026-03-09", 60), entry("e2", "2026-03-09", 30)]);

    await setCell(user, 1, "1"); // 1.5h → 1h across two entries: ambiguous
    await setCell(user, 2, "1"); // independent increase on Tuesday
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "t1", date: "2026-03-10", durationMinutes: 60, billable: true },
    ]);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Mon 9");
    expect(alert.textContent).toContain("Design wireframes");
    expect(alert.textContent).toContain("Time tab");
    expect(allToastText()).toContain("Mon 9");
    expect(screen.getByLabelText("Hours for day 1").className).toContain("border-red-500");
  });

  it("rule 3: a billed entry the server refuses is reported with day, task and the Time tab", async () => {
    const user = userEvent.setup();
    mockUpdateTimeEntry.mockResolvedValue({
      success: false,
      error: "Time entry is part of a fee note. Void the fee note to unlock.",
    });
    mockFetchWeekEntries.mockResolvedValue([entry("e1", "2026-03-09", 60)]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await setCell(user, 1, "0.5");
    await clickSave(user);

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain("Mon 9");
    expect(alert.textContent).toContain("Design wireframes");
    expect(alert.textContent).toContain("fee note");
    expect(alert.textContent).toContain("Time tab");
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
  });

  it("rule 5: Save refreshes the week as the new baseline, and a second Save sends nothing", async () => {
    const user = userEvent.setup();
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    mockFetchWeekEntries.mockResolvedValue([
      entry("e1", "2026-03-09", 60),
      entry("e2", "2026-03-10", 30),
    ]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await setCell(user, 2, "0.5");
    await clickSave(user);

    await waitFor(() =>
      expect(mockFetchWeekEntries).toHaveBeenCalledWith("2026-03-09", "2026-03-15")
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeDisabled());

    // Re-entering the saved values makes the grid dirty but changes nothing on the server.
    await setCell(user, 1, "1");
    await setCell(user, 2, "0.5");
    await clickSave(user);

    expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });

  it("Copy Previous Week overwrites only the cells it copies, so it never deletes other time", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "confirm",
      vi.fn(() => true)
    ); // "Current week has data…" prompt
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    mockFetchPreviousWeekEntries.mockResolvedValue([entry("p1", "2026-03-02", 120)]); // prev Mon 2h
    mockFetchWeekEntries.mockResolvedValue([
      entry("e1", "2026-03-10", 60),
      entry("e5", "2026-03-09", 120),
    ]);
    renderGrid([entry("e1", "2026-03-10", 60)]); // this week: Tue 1h

    await user.click(screen.getByRole("button", { name: /Copy Previous Week/ }));
    await waitFor(() => expect(toast.success).toHaveBeenCalled());
    expect((screen.getByLabelText("Hours for day 2") as HTMLInputElement).value).toBe("1");
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "t1", date: "2026-03-09", durationMinutes: 120, billable: true },
    ]);
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
  });

  it("rule 6: navigating weeks resets the baseline to the loaded week", async () => {
    const user = userEvent.setup();
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    // Next week (Mar 16–22) already has Monday 1h.
    mockFetchWeekEntries.mockResolvedValue([entry("n1", "2026-03-16", 60)]);
    renderGrid([entry("e1", "2026-03-09", 60)]);

    await user.click(screen.getByRole("button", { name: "Next week" }));
    await waitFor(() =>
      expect(mockFetchWeekEntries).toHaveBeenCalledWith("2026-03-16", "2026-03-22")
    );
    const row = screen.getAllByRole("row")[1];
    await waitFor(() =>
      expect((within(row).getByLabelText("Hours for day 1") as HTMLInputElement).value).toBe("1")
    );

    mockFetchWeekEntries.mockResolvedValue([
      entry("n1", "2026-03-16", 60),
      entry("n2", "2026-03-17", 120),
    ]);
    await setCell(user, 2, "2");
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "t1", date: "2026-03-17", durationMinutes: 120, billable: true },
    ]);
  });
});
