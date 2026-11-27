/**
 * K-88 (FU-6): the weekly timesheet lists every task the user logged time on that week, not only
 * the tasks assigned to them, and keeps doing so on every week load (RISK-2). Rows for tasks with
 * time but no assignment ("entry-only" rows) save under the F-668A-R1 save-plan rules. Copy
 * Previous Week can no longer race Save or a week change.
 *
 * Rules:
 * 1. Rows: one row per task that is assigned OR has the user's time in the displayed week; a task
 *    with both appears once; entry-only rows take title and matter from the entry. Daily and
 *    weekly totals include every entry.
 * 2. Navigation: rows are rebuilt on every week load (first render / ?week=, Previous, Next,
 *    This Week).
 * 3. Editing entry-only rows: increase → one create for the difference; single-entry decrease or
 *    clear → PUT / DELETE; multi-entry decrease → refused. No duplication.
 * 4. Copy Previous Week: Save and week changes are blocked while the copy is in flight, so the
 *    copied values are never dropped or put on another week.
 */
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { MyWorkTimeEntryItem } from "@/lib/types";

const {
  mockApiGet,
  mockSaveWeeklyEntries,
  mockFetchWeekEntries,
  mockFetchPreviousWeekEntries,
  mockUpdateTimeEntry,
  mockDeleteTimeEntry,
} = vi.hoisted(() => ({
  mockApiGet: vi.fn(),
  mockSaveWeeklyEntries: vi.fn(),
  mockFetchWeekEntries: vi.fn(),
  mockFetchPreviousWeekEntries: vi.fn(),
  mockUpdateTimeEntry: vi.fn(),
  mockDeleteTimeEntry: vi.fn(),
}));

vi.mock("@/lib/api", () => ({ api: { get: mockApiGet } }));

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

import TimesheetPage from "@/app/(app)/org/[slug]/my-work/timesheet/page";
import { WeeklyTimeGrid } from "@/components/time-tracking/weekly-time-grid";

// --- Fixtures ---

// Assigned to the user.
const ASSIGNED = {
  id: "a1",
  matterId: "m1",
  matterName: "Khumalo",
  title: "Heads of argument",
  status: "OPEN",
  priority: "MEDIUM",
  dueDate: null,
  totalTimeMinutes: 0,
};

// Not assigned to the user: the user only logged time on them.
const TASKS: Record<string, { title: string; matterId: string; matterName: string }> = {
  a1: { title: "Heads of argument", matterId: "m1", matterName: "Khumalo" },
  x1: { title: "Draft transfer documents", matterId: "m2", matterName: "Ndlovu" },
  y1: { title: "Consult with counsel", matterId: "m3", matterName: "Pillay Trust" },
};

function entry(id: string, taskId: string, date: string, minutes: number): MyWorkTimeEntryItem {
  const t = TASKS[taskId];
  return {
    id,
    taskId,
    taskTitle: t.title,
    matterId: t.matterId,
    matterName: t.matterName,
    date,
    durationMinutes: minutes,
    billable: true,
    description: null,
  };
}

// Week of Mon 9 – Sun 15 March 2026 (the deep-linked week).
const WEEK_1 = [
  entry("e5", "a1", "2026-03-09", 60), // assigned task, also has time → one row
  entry("e1", "x1", "2026-03-10", 90), // entry-only: Tue 1.5h
  entry("e2", "x1", "2026-03-11", 30), // entry-only: Wed 0.5h (single entry)
  entry("e3", "x1", "2026-03-12", 60), // entry-only: Thu 1.5h across two entries
  entry("e4", "x1", "2026-03-12", 30),
];
// Week of Mon 16 – Sun 22 March 2026: time only on y1.
const WEEK_2 = [entry("f1", "y1", "2026-03-17", 120)];

function okBatch(created: number) {
  return {
    success: true,
    result: { created: [], errors: [], totalCreated: created, totalErrors: 0 },
  };
}

async function renderPage(week: string, entries: MyWorkTimeEntryItem[]) {
  mockApiGet.mockImplementation(async (url: string) => {
    if (url === "/api/my-work/tasks") return { assigned: [ASSIGNED], unassigned: [] };
    if (url.startsWith("/api/my-work/time-entries")) return entries;
    throw new Error(`unexpected GET ${url}`);
  });
  return render(
    await TimesheetPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ week }),
    })
  );
}

/** The grid's body row for a task title (getByText throws if the task has two rows). */
function rowFor(title: string): HTMLElement {
  const row = screen.getByText(title).closest("tr");
  if (!row) throw new Error(`no row for ${title}`);
  return row;
}

function cell(title: string, day: number): HTMLInputElement {
  return within(rowFor(title)).getByLabelText(`Hours for day ${day}`) as HTMLInputElement;
}

/** Daily Total cells Mon…Sun, then the weekly total. */
function totals(): string[] {
  const row = screen.getByText("Daily Total").closest("tr")!;
  return Array.from(row.querySelectorAll("td"))
    .slice(1)
    .map((td) => td.textContent ?? "");
}

function taskTitles(): string[] {
  const body = screen.getAllByRole("rowgroup")[1];
  return within(body)
    .getAllByRole("row")
    .map((r) => r.querySelector("p")?.textContent ?? "")
    .filter((t) => t !== "");
}

async function setCell(
  user: ReturnType<typeof userEvent.setup>,
  title: string,
  day: number,
  value: string
) {
  const input = cell(title, day);
  await user.clear(input);
  if (value !== "") await user.type(input, value);
  await user.tab();
}

async function clickSave(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Save" }));
}

beforeEach(() => {
  mockFetchWeekEntries.mockImplementation(async (from: string) => {
    if (from === "2026-03-09") return WEEK_1;
    if (from === "2026-03-16") return WEEK_2;
    return [];
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("K-88 rule 1: rows for every task with the user's time", () => {
  it("first render (?week= deep link) lists the entry-only task once, with its title and matter", async () => {
    await renderPage("2026-03-09", WEEK_1);

    expect(mockApiGet).toHaveBeenCalledWith(
      "/api/my-work/time-entries?from=2026-03-09&to=2026-03-15"
    );
    expect(taskTitles()).toEqual(["Heads of argument", "Draft transfer documents"]);
    expect(within(rowFor("Draft transfer documents")).getByText("Ndlovu")).toBeInTheDocument();
    expect(cell("Draft transfer documents", 2).value).toBe("1.5");
    expect(cell("Draft transfer documents", 3).value).toBe("0.5");
    expect(cell("Heads of argument", 1).value).toBe("1");
  });

  it("daily and weekly totals include time on tasks not assigned to the user", async () => {
    await renderPage("2026-03-09", WEEK_1);

    expect(totals()).toEqual(["1h", "1.5h", "0.5h", "1.5h", "—", "—", "—", "4.5h"]);
  });
});

describe("K-88 rule 2: rows are rebuilt on every week load (RISK-2)", () => {
  it("Next, Previous and This Week each show that week's entry-only tasks and totals", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 2, 18, 10, 0)); // Wed 18 March → This Week = 16 March
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);

    await user.click(screen.getByRole("button", { name: "Previous week" }));
    await waitFor(() => expect(screen.getByText("Mar 2 – Mar 8, 2026")).toBeInTheDocument());
    expect(taskTitles()).toEqual(["Heads of argument"]);
    expect(totals()[7]).toBe("—");

    await user.click(screen.getByRole("button", { name: "This Week" }));
    await waitFor(() => expect(screen.getByText("Consult with counsel")).toBeInTheDocument());
    expect(screen.getByText("Mar 16 – Mar 22, 2026")).toBeInTheDocument();
    expect(taskTitles()).toEqual(["Consult with counsel", "Heads of argument"]);
    expect(within(rowFor("Consult with counsel")).getByText("Pillay Trust")).toBeInTheDocument();
    expect(totals()).toEqual(["—", "2h", "—", "—", "—", "—", "—", "2h"]);

    await user.click(screen.getByRole("button", { name: "Previous week" }));
    await waitFor(() => expect(screen.getByText("Draft transfer documents")).toBeInTheDocument());
    expect(screen.queryByText("Consult with counsel")).toBeNull();
    expect(totals()[7]).toBe("4.5h");

    await user.click(screen.getByRole("button", { name: "Next week" }));
    await waitFor(() => expect(screen.getByText("Consult with counsel")).toBeInTheDocument());
    expect(screen.queryByText("Draft transfer documents")).toBeNull();
    expect(totals()[7]).toBe("2h");
  });
});

describe("K-88 rule 3: entry-only rows save under the F-668A-R1 plan", () => {
  it("an increase creates one entry for the difference, and a second Save sends nothing", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));
    const after = [...WEEK_1, entry("e9", "x1", "2026-03-10", 30)];
    mockFetchWeekEntries.mockResolvedValue(after);

    await setCell(user, "Draft transfer documents", 2, "2"); // Tue 1.5h → 2h
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "x1", date: "2026-03-10", durationMinutes: 30, billable: true },
    ]);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();

    // The reload keeps the row and its new baseline: re-entering the saved value sends nothing.
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeDisabled());
    expect(cell("Draft transfer documents", 2).value).toBe("2");
    await setCell(user, "Draft transfer documents", 2, "2");
    await clickSave(user);
    expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
  });

  it("a single-entry decrease updates that entry on its own matter", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    mockUpdateTimeEntry.mockResolvedValue({ success: true });

    await setCell(user, "Draft transfer documents", 3, "0.25"); // Wed 0.5h → 0.25h
    await clickSave(user);

    await waitFor(() => expect(mockUpdateTimeEntry).toHaveBeenCalledTimes(1));
    expect(mockUpdateTimeEntry).toHaveBeenCalledWith("acme", "m2", "e2", { durationMinutes: 15 });
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });

  it("clearing a single-entry cell deletes that entry", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    mockDeleteTimeEntry.mockResolvedValue({ success: true });

    await setCell(user, "Draft transfer documents", 3, "");
    await clickSave(user);

    await waitFor(() => expect(mockDeleteTimeEntry).toHaveBeenCalledTimes(1));
    expect(mockDeleteTimeEntry).toHaveBeenCalledWith("acme", "m2", "e2");
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
  });

  it("a multi-entry decrease is refused with the existing message and nothing is sent", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);

    await setCell(user, "Draft transfer documents", 4, "1"); // Thu 1.5h (two entries) → 1h
    await clickSave(user);

    const alert = await screen.findByTestId("timesheet-save-problems");
    expect(alert.textContent).toContain(
      "Thu 12 · Draft transfer documents: this day has 2 time entries, so the timesheet can’t reduce it."
    );
    expect(alert.textContent).toContain("Open the task from the matter’s Tasks tab");
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });

  it("an entry-only row that first appears after navigation saves only the difference", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));

    await user.click(screen.getByRole("button", { name: "Next week" }));
    await waitFor(() => expect(cell("Consult with counsel", 2).value).toBe("2"));

    await setCell(user, "Consult with counsel", 2, "3"); // Tue 17: 2h → 3h
    await clickSave(user);

    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "y1", date: "2026-03-17", durationMinutes: 60, billable: true },
    ]);
    expect(mockUpdateTimeEntry).not.toHaveBeenCalled();
    expect(mockDeleteTimeEntry).not.toHaveBeenCalled();
  });
});

describe("Copy Previous Week cannot race Save or a week change", () => {
  function pendingCopy() {
    let release: (entries: MyWorkTimeEntryItem[]) => void = () => {};
    mockFetchPreviousWeekEntries.mockImplementationOnce(
      () => new Promise<MyWorkTimeEntryItem[]>((resolve) => (release = resolve))
    );
    return (entries: MyWorkTimeEntryItem[]) => release(entries);
  }

  it("Save is disabled while a copy is in flight, so the copied values are not dropped", async () => {
    vi.stubGlobal(
      "confirm",
      vi.fn(() => true)
    ); // "Current week has data…"
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(2));

    await setCell(user, "Heads of argument", 5, "1"); // Fri 13: typed 1h, grid is dirty
    const release = pendingCopy();
    await user.click(screen.getByRole("button", { name: /Copy Previous Week/ }));
    await waitFor(() => expect(mockFetchPreviousWeekEntries).toHaveBeenCalledTimes(1));

    const save = screen.getByRole("button", { name: "Save" });
    expect(save).toBeDisabled();
    await user.click(save);
    expect(mockSaveWeeklyEntries).not.toHaveBeenCalled();

    release([entry("p1", "y1", "2026-03-06", 60)]); // previous Fri 6 → Fri 13
    await waitFor(() => expect(cell("Consult with counsel", 5).value).toBe("1"));
    expect(cell("Heads of argument", 5).value).toBe("1");
    await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).not.toBeDisabled());

    await clickSave(user);
    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "a1", date: "2026-03-13", durationMinutes: 60, billable: true },
      { taskId: "y1", date: "2026-03-13", durationMinutes: 60, billable: true },
    ]);
  });

  it("the week cannot change while a copy is in flight; the copy lands on the week it was made for", async () => {
    vi.stubGlobal(
      "confirm",
      vi.fn(() => true)
    );
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);

    const release = pendingCopy();
    await user.click(screen.getByRole("button", { name: /Copy Previous Week/ }));
    await waitFor(() => expect(mockFetchPreviousWeekEntries).toHaveBeenCalledWith("2026-03-09"));

    const next = screen.getByRole("button", { name: "Next week" });
    expect(next).toBeDisabled();
    expect(screen.getByRole("button", { name: "Previous week" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "This Week" })).toBeDisabled();
    await user.click(next);

    release([entry("p1", "y1", "2026-03-06", 60)]); // previous Fri 6 → Fri 13
    await waitFor(() => expect(cell("Consult with counsel", 5).value).toBe("1"));
    expect(screen.getByText("Mar 9 – Mar 15, 2026")).toBeInTheDocument();
    expect(mockFetchWeekEntries).not.toHaveBeenCalled();
    expect(totals()[4]).toBe("1h"); // Fri 13
  });
});

describe("Review r1 M1: Add task during an in-flight Copy cannot duplicate a row or a create", () => {
  const assignedRow = {
    id: "a1",
    projectId: "m1",
    projectName: "Khumalo",
    title: "Heads of argument",
  };
  // Assigned to the user but without a row: the page lists at most five empty assigned tasks.
  const hidden = {
    id: "y1",
    projectId: "m3",
    projectName: "Pillay Trust",
    title: "Consult with counsel",
  };

  it("Add task is disabled while copying; the copied task gets one row and Save posts once", async () => {
    const user = userEvent.setup();
    render(
      <WeeklyTimeGrid
        tasks={[assignedRow]}
        existingEntries={[]}
        weekStart="2026-03-09"
        allTasks={[assignedRow, hidden]}
        slug="acme"
      />
    );
    let release: (entries: MyWorkTimeEntryItem[]) => void = () => {};
    mockFetchPreviousWeekEntries.mockImplementationOnce(
      () => new Promise<MyWorkTimeEntryItem[]>((resolve) => (release = resolve))
    );
    mockSaveWeeklyEntries.mockResolvedValue(okBatch(1));

    const addInput = screen.getByPlaceholderText("Add task...");
    await user.type(addInput, "Consult"); // the option is offered before the copy starts
    expect(screen.getByRole("button", { name: /Consult with counsel/ })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Copy Previous Week/ }));
    await waitFor(() => expect(mockFetchPreviousWeekEntries).toHaveBeenCalledTimes(1));

    // While the copy is in flight the task can't be added and the cells can't be edited.
    expect(addInput).toBeDisabled();
    expect(screen.queryByRole("button", { name: /Consult with counsel/ })).toBeNull();
    expect(cell("Heads of argument", 1)).toBeDisabled();

    release([entry("p1", "y1", "2026-03-03", 60)]); // previous Tue 3 → Tue 10
    await waitFor(() => expect(cell("Consult with counsel", 2).value).toBe("1"));
    expect(screen.getAllByText("Consult with counsel")).toHaveLength(1);

    await clickSave(user);
    await waitFor(() => expect(mockSaveWeeklyEntries).toHaveBeenCalledTimes(1));
    expect(mockSaveWeeklyEntries.mock.calls[0][1]).toEqual([
      { taskId: "y1", date: "2026-03-10", durationMinutes: 60, billable: true },
    ]);
  });

  it("cells are disabled while a week loads", async () => {
    const user = userEvent.setup();
    await renderPage("2026-03-09", WEEK_1);
    let release: (entries: MyWorkTimeEntryItem[]) => void = () => {};
    mockFetchWeekEntries.mockImplementationOnce(
      () => new Promise<MyWorkTimeEntryItem[]>((resolve) => (release = resolve))
    );

    await user.click(screen.getByRole("button", { name: "Next week" }));
    await waitFor(() => expect(screen.getByText("Loading...")).toBeInTheDocument());
    expect(cell("Heads of argument", 1)).toBeDisabled();
    expect(screen.getByPlaceholderText("Add task...")).toBeDisabled();

    release(WEEK_2);
    await waitFor(() => expect(cell("Consult with counsel", 2).value).toBe("2"));
    // The lock clears when the week-load transition settles, which can land a tick after the
    // new values render (CI run 46055098219); wait for it rather than asserting immediately.
    await waitFor(() => expect(cell("Consult with counsel", 2)).not.toBeDisabled());
  });
});
