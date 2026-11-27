import { describe, it, expect } from "vitest";
import { planWeekSave } from "@/components/time-tracking/timesheet-save-plan";
import type { MyWorkTimeEntryItem } from "@/lib/types";

const DATES = [
  "2026-03-09",
  "2026-03-10",
  "2026-03-11",
  "2026-03-12",
  "2026-03-13",
  "2026-03-14",
  "2026-03-15",
];

function entry(id: string, taskId: string, date: string, minutes: number): MyWorkTimeEntryItem {
  return {
    id,
    taskId,
    taskTitle: taskId,
    matterId: `m-${taskId}`,
    matterName: "Matter",
    date,
    durationMinutes: minutes,
    billable: true,
    description: null,
  };
}

// t1: Mon 1h → 1.5h (create 30), Tue 1h → 0.5h (update), Wed 1h → 0 (delete),
//     Thu 0.5h + 0.5h → 0.5h (ambiguous); t2: Fri 0 → 2h (create 120).
const baseline = [
  entry("e1", "t1", DATES[0], 60),
  entry("e2", "t1", DATES[1], 60),
  entry("e3", "t1", DATES[2], 60),
  entry("e4", "t1", DATES[3], 30),
  entry("e5", "t1", DATES[3], 30),
];
const cells: Record<string, number> = {
  "t1|0": 1.5,
  "t1|1": 0.5,
  "t1|2": 0,
  "t1|3": 0.5,
  "t2|4": 2,
};
const hoursFor = (taskId: string, day: number) => cells[`${taskId}|${day}`] ?? 0;

describe("planWeekSave — duplicate row ids (review r1 M1 backstop)", () => {
  it("plans each task once even when its id is passed several times", () => {
    const once = planWeekSave(["t1", "t2"], DATES, hoursFor, baseline);
    const dup = planWeekSave(["t1", "t2", "t1", "t2", "t2"], DATES, hoursFor, baseline);
    expect(dup).toEqual(once);
  });

  it("leaves the F-668A-R1 plan itself unchanged", () => {
    expect(planWeekSave(["t1", "t2"], DATES, hoursFor, baseline)).toEqual({
      creates: [
        { taskId: "t1", dayIndex: 0, date: DATES[0], durationMinutes: 30 },
        { taskId: "t2", dayIndex: 4, date: DATES[4], durationMinutes: 120 },
      ],
      updates: [
        { taskId: "t1", dayIndex: 1, entryId: "e2", matterId: "m-t1", durationMinutes: 30 },
      ],
      deletes: [{ taskId: "t1", dayIndex: 2, entryId: "e3", matterId: "m-t1" }],
      ambiguous: [{ taskId: "t1", dayIndex: 3, entryCount: 2 }],
    });
  });
});
