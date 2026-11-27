import { describe, it, expect } from "vitest";
import {
  mergeRows,
  rowsForWeek,
  type GridTaskRow,
} from "@/components/time-tracking/timesheet-rows";
import type { MyWorkTimeEntryItem } from "@/lib/types";

const row = (id: string, matter = "Matter A"): GridTaskRow => ({
  id,
  projectId: `m-${id}`,
  projectName: matter,
  title: `Task ${id}`,
});

function entry(taskId: string, matterName: string, title: string): MyWorkTimeEntryItem {
  return {
    id: `e-${taskId}-${Math.random()}`,
    taskId,
    taskTitle: title,
    matterId: `m-${taskId}`,
    matterName,
    date: "2026-03-09",
    durationMinutes: 60,
    billable: true,
    description: null,
  };
}

describe("rowsForWeek (K-88)", () => {
  // Seven assigned tasks; a3 and a6 have time this week; x1/x2 are not assigned but have time.
  const assigned = ["a1", "a2", "a3", "a4", "a5", "a6", "a7"].map((id) => row(id));
  const entries = [
    entry("x2", "Zulu Estate", "Draft will"),
    entry("a6", "Matter A", "Task a6"),
    entry("x1", "Botha", "Consult"),
    entry("a3", "Matter A", "Task a3"),
    entry("x2", "Zulu Estate", "Draft will"), // second entry on x2: still one row
    entry("a6", "Matter A", "Task a6"),
  ];

  it("orders assigned-with-time, then other logged tasks by matter, then 5 empty assigned", () => {
    expect(rowsForWeek(assigned, entries).map((r) => r.id)).toEqual([
      "a3",
      "a6",
      "x1",
      "x2",
      "a1",
      "a2",
      "a4",
      "a5",
      "a7",
    ]);
  });

  it("caps empty assigned rows at five", () => {
    expect(rowsForWeek(assigned, []).map((r) => r.id)).toEqual(["a1", "a2", "a3", "a4", "a5"]);
  });

  it("builds logged-only rows from the entry and lists each task once", () => {
    const rows = rowsForWeek([...assigned, row("a3")], entries);
    expect(rows.filter((r) => r.id === "a3")).toHaveLength(1);
    expect(rows.filter((r) => r.id === "x2")).toEqual([
      { id: "x2", projectId: "m-x2", projectName: "Zulu Estate", title: "Draft will" },
    ]);
  });

  it("mergeRows keeps the current order and adds only new ids", () => {
    expect(
      mergeRows([row("a"), row("b")], [row("b"), row("c"), row("c")]).map((r) => r.id)
    ).toEqual(["a", "b", "c"]);
  });
});
