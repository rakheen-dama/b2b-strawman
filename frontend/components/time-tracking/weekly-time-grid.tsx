"use client";

import { useRef, useState, useTransition } from "react";
import { ChevronLeft, ChevronRight, Copy, Upload } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@b2mash/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { TimeCell } from "./time-cell";
import { planWeekSave, isEmptyPlan, type PlanCell } from "./timesheet-save-plan";
import {
  saveWeeklyEntries,
  fetchWeekEntries,
  fetchPreviousWeekEntries,
} from "@/app/(app)/org/[slug]/my-work/timesheet/actions";
import {
  updateTimeEntry,
  deleteTimeEntry,
} from "@/app/(app)/org/[slug]/matters/[id]/time-entry-actions";
import { cn } from "@/lib/utils";
import type { MyWorkTimeEntryItem } from "@/lib/types";
import { CsvImportDialog } from "./csv-import-dialog";

// --- Helpers ---

function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function toIsoDate(date: Date): string {
  return date.toLocaleDateString("en-CA");
}

function addDays(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function getIsoWeekMonday(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  d.setDate(d.getDate() + diff);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function formatWeekLabel(monday: Date): string {
  const sunday = addDays(monday, 6);
  const from = monday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
  const to = sunday.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  return `${from} \u2013 ${to}`;
}

function formatDayHeader(monday: Date, dayIndex: number): string {
  const date = addDays(monday, dayIndex);
  const abbr = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][dayIndex];
  return `${abbr} ${date.getDate()}`;
}

type CellKey = string; // `${taskId}-${dayIndex}`
function cellKey(taskId: string, dayIndex: number): CellKey {
  return `${taskId}-${dayIndex}`;
}

function buildCellValuesFromEntries(
  entries: MyWorkTimeEntryItem[],
  monday: Date
): Record<CellKey, number> {
  const values: Record<CellKey, number> = {};
  for (const entry of entries) {
    const entryDate = parseIsoDate(entry.date);
    const diff = Math.round((entryDate.getTime() - monday.getTime()) / 86400000);
    if (diff >= 0 && diff <= 6) {
      const key = cellKey(entry.taskId, diff);
      values[key] = (values[key] ?? 0) + entry.durationMinutes / 60;
    }
  }
  return values;
}

// --- Types ---

export interface GridTaskRow {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
}

interface WeeklyTimeGridProps {
  tasks: GridTaskRow[];
  existingEntries: MyWorkTimeEntryItem[];
  weekStart: string; // "YYYY-MM-DD" Monday
  allTasks: GridTaskRow[];
  slug: string;
}

// --- Component ---

const DAYS = [0, 1, 2, 3, 4, 5, 6] as const;

export function WeeklyTimeGrid({
  tasks: initialTasks,
  existingEntries,
  weekStart: initialWeekStart,
  allTasks,
  slug,
}: WeeklyTimeGridProps) {
  const [taskRows, setTaskRows] = useState<GridTaskRow[]>(initialTasks);
  const [weekStart, setWeekStart] = useState(() => parseIsoDate(initialWeekStart));
  // The week's entries as last loaded from the server. Save sends only the difference between the
  // cells and this baseline, so it never re-creates existing time (F-668A-R1).
  const [baseline, setBaseline] = useState<MyWorkTimeEntryItem[]>(existingEntries);
  const [baselineError, setBaselineError] = useState(false);
  const [cellValues, setCellValues] = useState<Record<CellKey, number>>(() =>
    buildCellValuesFromEntries(existingEntries, parseIsoDate(initialWeekStart))
  );
  const [cellErrors, setCellErrors] = useState<Record<CellKey, string>>({});
  const [saveProblems, setSaveProblems] = useState<string[]>([]);
  const [dirty, setDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isNavigating, startNavTransition] = useTransition();
  const [addTaskQuery, setAddTaskQuery] = useState("");
  // Only the latest week load may set the baseline (fast Previous/Next clicks).
  const loadSeq = useRef(0);

  function handleCellChange(taskId: string, dayIndex: number, hours: number) {
    const key = cellKey(taskId, dayIndex);
    setCellValues((prev) => ({ ...prev, [key]: hours }));
    setCellErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
    setDirty(true);
  }

  function rowTotal(taskId: string): number {
    return DAYS.reduce<number>((sum, d) => sum + (cellValues[cellKey(taskId, d)] ?? 0), 0);
  }

  function columnTotal(dayIndex: number): number {
    return taskRows.reduce((sum, t) => sum + (cellValues[cellKey(t.id, dayIndex)] ?? 0), 0);
  }

  const grandTotal = DAYS.reduce<number>((sum, d) => sum + columnTotal(d), 0);

  /**
   * Loads a week's entries as the new baseline and resets the cells to them. On failure the
   * baseline is unknown, so Save stays disabled until the page is reloaded.
   */
  async function loadWeek(monday: Date): Promise<void> {
    const seq = ++loadSeq.current;
    let entries: MyWorkTimeEntryItem[] | null = null;
    try {
      entries = await fetchWeekEntries(toIsoDate(monday), toIsoDate(addDays(monday, 6)));
    } catch {
      entries = null;
    }
    if (seq !== loadSeq.current) return;
    if (!Array.isArray(entries)) {
      setBaselineError(true);
      return;
    }
    setBaseline(entries);
    setCellValues(buildCellValuesFromEntries(entries, monday));
    setBaselineError(false);
  }

  function switchWeek(monday: Date) {
    if (dirty && !confirm("You have unsaved changes. Discard?")) return;
    setWeekStart(monday);
    setBaseline([]);
    setCellValues({});
    setCellErrors({});
    setSaveProblems([]);
    setDirty(false);
    startNavTransition(() => loadWeek(monday));
  }

  function navigateWeek(direction: -1 | 1) {
    switchWeek(addDays(weekStart, direction * 7));
  }

  function goToThisWeek() {
    switchWeek(getIsoWeekMonday(new Date()));
  }

  function describeCell(cell: PlanCell): string {
    const title = taskRows.find((t) => t.id === cell.taskId)?.title ?? "Task";
    return `${formatDayHeader(weekStart, cell.dayIndex)} \u00b7 ${title}`;
  }

  function withPeriod(message: string): string {
    const trimmed = message.trim();
    return /[.!?]$/.test(trimmed) ? trimmed : `${trimmed}.`;
  }

  async function handleSave() {
    const dayDates = DAYS.map((d) => toIsoDate(addDays(weekStart, d)));
    const plan = planWeekSave(
      taskRows.map((t) => t.id),
      dayDates,
      (taskId, d) => cellValues[cellKey(taskId, d)] ?? 0,
      baseline
    );
    if (isEmptyPlan(plan)) {
      setDirty(false);
      return;
    }

    // `cell` is absent only for a failure that can't be tied to one cell.
    const problems: Array<{ cell?: PlanCell; message: string }> = [];
    const editElsewhere = "Change it from the matter\u2019s Time tab.";
    let saved = 0;

    for (const a of plan.ambiguous) {
      problems.push({
        cell: a,
        message: `${describeCell(a)}: this day has ${a.entryCount} time entries, so the timesheet can\u2019t reduce it. ${editElsewhere}`,
      });
    }

    setIsSaving(true);
    setSaveProblems([]);
    try {
      if (plan.creates.length > 0) {
        const response = await saveWeeklyEntries(
          slug,
          plan.creates.map((c) => ({
            taskId: c.taskId,
            date: c.date,
            durationMinutes: c.durationMinutes,
            billable: true,
          }))
        );
        if (!response.success || !response.result) {
          const reason = withPeriod(response.error ?? "Failed to save");
          for (const c of plan.creates) {
            problems.push({ cell: c, message: `${describeCell(c)}: ${reason}` });
          }
        } else {
          saved += response.result.totalCreated;
          for (const err of response.result.errors) {
            const c = plan.creates[err.index];
            if (c)
              problems.push({ cell: c, message: `${describeCell(c)}: ${withPeriod(err.message)}` });
          }
        }
      }

      for (const u of plan.updates) {
        const result = await updateTimeEntry(slug, u.matterId, u.entryId, {
          durationMinutes: u.durationMinutes,
        });
        if (result.success) saved += 1;
        else
          problems.push({
            cell: u,
            message: `${describeCell(u)}: ${withPeriod(result.error ?? "Could not update the entry")} ${editElsewhere}`,
          });
      }

      for (const del of plan.deletes) {
        const result = await deleteTimeEntry(slug, del.matterId, del.entryId);
        if (result.success) saved += 1;
        else
          problems.push({
            cell: del,
            message: `${describeCell(del)}: ${withPeriod(result.error ?? "Could not delete the entry")} ${editElsewhere}`,
          });
      }
    } catch {
      problems.push({ message: "Save failed part-way. The grid now shows what was saved." });
    } finally {
      // The server is the source of truth: reload the week so the next Save diffs against it.
      await loadWeek(weekStart);
      setDirty(false);
      setIsSaving(false);
    }

    const newCellErrors: Record<CellKey, string> = {};
    for (const p of problems) {
      if (p.cell) newCellErrors[cellKey(p.cell.taskId, p.cell.dayIndex)] = p.message;
    }
    setCellErrors(newCellErrors);
    setSaveProblems(problems.map((p) => p.message));

    const changes = (n: number) => `${n} ${n === 1 ? "change" : "changes"}`;
    if (problems.length === 0) {
      toast.success(`Saved ${changes(saved)}`);
    } else {
      const description = problems.map((p) => p.message).join("\n");
      if (saved > 0) {
        toast.warning(`Saved ${changes(saved)}, ${problems.length} not saved`, { description });
      } else {
        toast.error(`${changes(problems.length)} not saved`, { description });
      }
    }
  }

  // Add-task row filtering
  const availableToAdd = allTasks.filter(
    (t) =>
      !taskRows.some((r) => r.id === t.id) &&
      (addTaskQuery === "" ||
        t.title.toLowerCase().includes(addTaskQuery.toLowerCase()) ||
        t.projectName.toLowerCase().includes(addTaskQuery.toLowerCase()))
  );

  function addTask(task: GridTaskRow) {
    setTaskRows((prev) => [...prev, task]);
    setAddTaskQuery("");
  }

  // --- Copy Previous Week ---
  const [isCopying, setIsCopying] = useState(false);

  async function handleCopyPreviousWeek() {
    // Check if grid has data — confirm before overwriting
    const hasData = Object.values(cellValues).some((v) => v > 0);
    if (hasData) {
      if (
        !confirm(
          "Current week has data. Copy previous week will overwrite existing entries. Continue?"
        )
      ) {
        return;
      }
    }

    setIsCopying(true);
    try {
      const entries = await fetchPreviousWeekEntries(toIsoDate(weekStart));
      if (entries.length === 0) {
        toast.error("No entries found in the previous week");
        return;
      }

      // Shift entries by +7 days so they map to the current week
      const shiftedEntries: MyWorkTimeEntryItem[] = entries.map((entry) => {
        const entryDate = parseIsoDate(entry.date);
        const shifted = addDays(entryDate, 7);
        return { ...entry, date: toIsoDate(shifted) };
      });

      // Add any new task rows that aren't already in the grid
      const currentTaskIds = new Set(taskRows.map((t) => t.id));
      const newTaskRows: GridTaskRow[] = [];

      for (const entry of shiftedEntries) {
        if (!currentTaskIds.has(entry.taskId)) {
          // Check allTasks first
          const fromAllTasks = allTasks.find((t) => t.id === entry.taskId);
          if (fromAllTasks) {
            newTaskRows.push(fromAllTasks);
          } else {
            // Build from entry data
            newTaskRows.push({
              id: entry.taskId,
              projectId: entry.matterId,
              projectName: entry.matterName,
              title: entry.taskTitle,
            });
          }
          currentTaskIds.add(entry.taskId);
        }
      }

      if (newTaskRows.length > 0) {
        setTaskRows((prev) => [...prev, ...newTaskRows]);
      }

      // Overwrite only the cells the previous week had time in. Replacing the whole grid would turn
      // every other existing cell into a 0, which Save now reads as "delete" (F-668A-R1).
      const newCellValues = buildCellValuesFromEntries(shiftedEntries, weekStart);
      setCellValues((prev) => ({ ...prev, ...newCellValues }));
      setCellErrors({});
      setDirty(true);
      toast.success(`Copied ${entries.length} entries from previous week`);
    } catch {
      toast.error("Failed to copy previous week entries");
    } finally {
      setIsCopying(false);
    }
  }

  // --- CSV Import Handler ---
  function handleCsvImport(
    rows: Array<{
      taskId: string;
      task: GridTaskRow;
      date: string;
      hours: number;
      description: string;
      billable: boolean;
    }>
  ) {
    if (rows.length === 0) return;

    const totalRows = rows.length;

    // Pre-filter rows to entries within the current week
    const weekEndMs = addDays(weekStart, 6).getTime();
    const weekStartMs = weekStart.getTime();
    const inWeekRows = rows.filter((row) => {
      const entryMs = parseIsoDate(row.date).getTime();
      return entryMs >= weekStartMs && entryMs <= weekEndMs;
    });

    const skippedCount = totalRows - inWeekRows.length;

    if (inWeekRows.length === 0) {
      toast.warning(`No entries fell within the current week (${totalRows} outside range)`);
      return;
    }

    // Add any new task rows
    const currentTaskIds = new Set(taskRows.map((t) => t.id));
    const newTaskRows: GridTaskRow[] = [];

    for (const row of inWeekRows) {
      if (!currentTaskIds.has(row.taskId)) {
        newTaskRows.push(row.task);
        currentTaskIds.add(row.taskId);
      }
    }

    if (newTaskRows.length > 0) {
      setTaskRows((prev) => [...prev, ...newTaskRows]);
    }

    // Convert imported rows to MyWorkTimeEntryItem format for buildCellValuesFromEntries
    const importedEntries: MyWorkTimeEntryItem[] = inWeekRows.map((row) => ({
      id: "",
      taskId: row.taskId,
      taskTitle: row.task.title,
      matterId: row.task.projectId,
      matterName: row.task.projectName,
      date: row.date,
      durationMinutes: Math.round(row.hours * 60),
      billable: row.billable,
      description: row.description || null,
    }));

    const importedCellValues = buildCellValuesFromEntries(importedEntries, weekStart);

    // Merge with existing cell values (imported values overwrite)
    setCellValues((prev) => ({ ...prev, ...importedCellValues }));
    setCellErrors({});
    setDirty(true);

    if (skippedCount > 0) {
      toast.warning(
        `Imported ${inWeekRows.length} of ${totalRows} entries (${skippedCount} outside current week)`
      );
    } else {
      toast.success(`Imported ${inWeekRows.length} entries from CSV`);
    }
  }

  return (
    <div className="space-y-4">
      {/* Header: week nav + save */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            onClick={() => navigateWeek(-1)}
            disabled={isNavigating}
            aria-label="Previous week"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-[160px] text-center text-sm font-medium text-slate-700 dark:text-slate-300">
            {isNavigating ? "Loading..." : formatWeekLabel(weekStart)}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="size-8"
            onClick={() => navigateWeek(1)}
            disabled={isNavigating}
            aria-label="Next week"
          >
            <ChevronRight className="size-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={goToThisWeek} disabled={isNavigating}>
            This Week
          </Button>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handleCopyPreviousWeek}
            disabled={isCopying || isNavigating}
          >
            <Copy className="mr-1.5 size-3.5" />
            {isCopying ? "Copying..." : "Copy Previous Week"}
          </Button>
          <CsvImportDialog availableTasks={allTasks} onImport={handleCsvImport}>
            <Button variant="outline" size="sm">
              <Upload className="mr-1.5 size-3.5" />
              Import CSV
            </Button>
          </CsvImportDialog>
          <Button
            onClick={handleSave}
            disabled={!dirty || isSaving || isNavigating || baselineError}
          >
            {isSaving ? "Saving..." : "Save"}
          </Button>
        </div>
      </div>

      {baselineError && (
        <Alert variant="destructive">
          <AlertTitle>Couldn&rsquo;t load this week&rsquo;s time entries</AlertTitle>
          <AlertDescription>Reload the page before saving.</AlertDescription>
        </Alert>
      )}

      {saveProblems.length > 0 && (
        <Alert variant="destructive" data-testid="timesheet-save-problems">
          <AlertTitle>Not saved</AlertTitle>
          <AlertDescription>
            <ul className="list-disc pl-4">
              {saveProblems.map((message) => (
                <li key={message}>{message}</li>
              ))}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* Grid */}
      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
        <Table>
          <TableHeader>
            <TableRow className="border-slate-200 hover:bg-transparent dark:border-slate-800">
              <TableHead className="min-w-[200px] text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Task
              </TableHead>
              {DAYS.map((d) => (
                <TableHead
                  key={d}
                  className="w-16 text-center text-xs tracking-wide text-slate-600 uppercase dark:text-slate-400"
                >
                  {formatDayHeader(weekStart, d)}
                </TableHead>
              ))}
              <TableHead className="w-16 text-center text-xs font-semibold tracking-wide text-slate-700 uppercase dark:text-slate-300">
                Total
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {taskRows.map((task) => (
              <TableRow key={task.id} className="border-slate-100 dark:border-slate-800/50">
                <TableCell>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-950 dark:text-slate-50">
                      {task.title}
                    </p>
                    <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                      {task.projectName}
                    </p>
                  </div>
                </TableCell>
                {DAYS.map((d) => {
                  const key = cellKey(task.id, d);
                  const val = cellValues[key] ?? 0;
                  return (
                    <TableCell key={d} className="p-1">
                      <TimeCell
                        key={`${key}-${val}`}
                        taskId={task.id}
                        dayIndex={d}
                        value={val}
                        error={cellErrors[key]}
                        onChange={handleCellChange}
                      />
                    </TableCell>
                  );
                })}
                <TableCell className="text-center text-sm font-medium text-slate-700 dark:text-slate-300">
                  {rowTotal(task.id) > 0 ? `${rowTotal(task.id)}h` : "\u2014"}
                </TableCell>
              </TableRow>
            ))}

            {/* Column Totals Row */}
            <TableRow className="border-t-2 border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/50">
              <TableCell className="text-xs font-semibold text-slate-600 dark:text-slate-400">
                Daily Total
              </TableCell>
              {DAYS.map((d) => (
                <TableCell
                  key={d}
                  className="text-center text-sm font-semibold text-slate-800 dark:text-slate-200"
                >
                  {columnTotal(d) > 0 ? `${columnTotal(d)}h` : "\u2014"}
                </TableCell>
              ))}
              <TableCell className="text-center text-sm font-bold text-slate-900 dark:text-slate-100">
                {grandTotal > 0 ? `${grandTotal}h` : "\u2014"}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>

      {/* Add Task Row */}
      <div className="relative">
        <input
          type="text"
          placeholder="Add task..."
          value={addTaskQuery}
          onChange={(e) => setAddTaskQuery(e.target.value)}
          className={cn(
            "w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm",
            "focus:ring-2 focus:ring-slate-400 focus:outline-none dark:border-slate-700 dark:bg-slate-950"
          )}
        />
        {addTaskQuery.length > 0 && availableToAdd.length > 0 && (
          <div className="absolute z-10 mt-1 w-full rounded-md border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-900">
            {availableToAdd.slice(0, 10).map((task) => (
              <button
                key={task.id}
                type="button"
                onClick={() => addTask(task)}
                className={cn(
                  "flex w-full flex-col px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-800"
                )}
              >
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {task.title}
                </span>
                <span className="text-xs text-slate-500 dark:text-slate-400">
                  {task.projectName}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
