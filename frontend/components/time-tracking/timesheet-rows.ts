import type { MyWorkTimeEntryItem } from "@/lib/types";

export interface GridTaskRow {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
}

/** Assigned tasks without time in the week that are shown as empty rows to type into. */
const EMPTY_ASSIGNED_ROWS = 5;

/**
 * The weekly timesheet's rows for one week (K-88). Every task the user logged time on that week
 * gets a row, whether or not it is assigned to them, so no logged time drops out of the rows or the
 * totals. Order: assigned tasks with time (assigned order), then the other tasks with time (by
 * matter, then title, from the entry), then up to five assigned tasks without time. A task with
 * both an assignment and time appears once.
 *
 * @param assigned the tasks assigned to the user
 * @param entries  the user's entries in the week
 */
export function rowsForWeek(
  assigned: GridTaskRow[],
  entries: MyWorkTimeEntryItem[]
): GridTaskRow[] {
  const withTime = new Set(entries.map((e) => e.taskId));
  const assignedIds = new Set(assigned.map((t) => t.id));

  const entryOnly = new Map<string, GridTaskRow>();
  for (const e of entries) {
    if (assignedIds.has(e.taskId) || entryOnly.has(e.taskId)) continue;
    entryOnly.set(e.taskId, {
      id: e.taskId,
      projectId: e.matterId,
      projectName: e.matterName,
      title: e.taskTitle,
    });
  }
  const byMatterThenTitle = (a: GridTaskRow, b: GridTaskRow) =>
    (a.projectName ?? "").localeCompare(b.projectName ?? "") ||
    (a.title ?? "").localeCompare(b.title ?? "");

  return mergeRows(
    [],
    [
      ...assigned.filter((t) => withTime.has(t.id)),
      ...[...entryOnly.values()].sort(byMatterThenTitle),
      ...assigned.filter((t) => !withTime.has(t.id)).slice(0, EMPTY_ASSIGNED_ROWS),
    ]
  );
}

/** `current` in its order, followed by the rows of `next` it does not already have (one per id). */
export function mergeRows(current: GridTaskRow[], next: GridTaskRow[]): GridTaskRow[] {
  const ids = new Set(current.map((t) => t.id));
  const added: GridTaskRow[] = [];
  for (const t of next) {
    if (ids.has(t.id)) continue;
    ids.add(t.id);
    added.push(t);
  }
  return [...current, ...added];
}
