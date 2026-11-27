import type { MyWorkTimeEntryItem } from "@/lib/types";

/**
 * Works out what a weekly-timesheet Save must send (F-668A-R1).
 *
 * The grid pre-fills each cell with the sum of the week's existing entries, so a cell's value is
 * compared with that baseline and only the change is sent:
 * - increased → one new entry for the difference (billable, as the grid has always created);
 * - decreased or cleared, one existing entry → update its duration, or delete it at 0;
 * - decreased, several existing entries → ambiguous: nothing is sent for the cell;
 * - unchanged → nothing.
 */

export interface PlanCell {
  taskId: string;
  dayIndex: number; // 0 = Mon … 6 = Sun
}

export interface PlannedCreate extends PlanCell {
  date: string;
  durationMinutes: number;
}

export interface PlannedUpdate extends PlanCell {
  entryId: string;
  matterId: string;
  durationMinutes: number;
}

export interface PlannedDelete extends PlanCell {
  entryId: string;
  matterId: string;
}

export interface PlannedAmbiguous extends PlanCell {
  entryCount: number;
}

export interface WeekSavePlan {
  creates: PlannedCreate[];
  updates: PlannedUpdate[];
  deletes: PlannedDelete[];
  ambiguous: PlannedAmbiguous[];
}

export function isEmptyPlan(plan: WeekSavePlan): boolean {
  return (
    plan.creates.length === 0 &&
    plan.updates.length === 0 &&
    plan.deletes.length === 0 &&
    plan.ambiguous.length === 0
  );
}

/**
 * @param taskIds   the grid's rows, in display order (a repeated id is planned once)
 * @param dayDates  the week's seven ISO dates, Monday first
 * @param hoursFor  the cell's current value in decimal hours (0 when empty)
 * @param baseline  the week's entries as last loaded from the server
 */
export function planWeekSave(
  taskIds: string[],
  dayDates: string[],
  hoursFor: (taskId: string, dayIndex: number) => number,
  baseline: MyWorkTimeEntryItem[]
): WeekSavePlan {
  const byCell = new Map<string, MyWorkTimeEntryItem[]>();
  for (const entry of baseline) {
    const key = `${entry.taskId}|${entry.date}`;
    const list = byCell.get(key);
    if (list) list.push(entry);
    else byCell.set(key, [entry]);
  }

  const plan: WeekSavePlan = { creates: [], updates: [], deletes: [], ambiguous: [] };
  // A task listed twice must not post its creates twice (review r1 M1 backstop).
  for (const taskId of new Set(taskIds)) {
    dayDates.forEach((date, dayIndex) => {
      const existing = byCell.get(`${taskId}|${date}`) ?? [];
      const existingMinutes = existing.reduce((sum, e) => sum + e.durationMinutes, 0);
      const newMinutes = Math.round(hoursFor(taskId, dayIndex) * 60);

      if (newMinutes === existingMinutes) return;

      if (newMinutes > existingMinutes) {
        plan.creates.push({
          taskId,
          dayIndex,
          date,
          durationMinutes: newMinutes - existingMinutes,
        });
        return;
      }

      if (existing.length === 1) {
        const only = existing[0];
        if (newMinutes === 0) {
          plan.deletes.push({ taskId, dayIndex, entryId: only.id, matterId: only.matterId });
        } else {
          plan.updates.push({
            taskId,
            dayIndex,
            entryId: only.id,
            matterId: only.matterId,
            durationMinutes: newMinutes,
          });
        }
        return;
      }

      plan.ambiguous.push({ taskId, dayIndex, entryCount: existing.length });
    });
  }
  return plan;
}
