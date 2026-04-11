"use client";

import { useState, useTransition } from "react";
import { ChevronLeft, ChevronRight, Copy, Upload } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TimeCell } from "./time-cell";
import {
  saveWeeklyEntries,
  fetchWeekEntries,
  fetchPreviousWeekEntries,
} from "@/app/(app)/org/[slug]/my-work/timesheet/actions";
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
  const [cellValues, setCellValues] = useState<Record<CellKey, number>>(() =>
    buildCellValuesFromEntries(existingEntries, parseIsoDate(initialWeekStart))
  );
  const [cellErrors, setCellErrors] = useState<Record<CellKey, string>>({});
  const [dirty, setDirty] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isNavigating, startNavTransition] = useTransition();
  const [addTaskQuery, setAddTaskQuery] = useState("");

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

  function navigateWeek(direction: -1 | 1) {
    if (dirty && !confirm("You have unsaved changes. Discard?")) return;
    const newMonday = addDays(weekStart, direction * 7);
    setWeekStart(newMonday);
    setCellValues({});
    setCellErrors({});
    setDirty(false);

    const from = toIsoDate(newMonday);
    const to = toIsoDate(addDays(newMonday, 6));
    startNavTransition(async () => {
      const entries = await fetchWeekEntries(from, to);
      setCellValues(buildCellValuesFromEntries(entries, newMonday));
    });
  }

  function goToThisWeek() {
    if (dirty && !confirm("You have unsaved changes. Discard?")) return;
    const monday = getIsoWeekMonday(new Date());
    setWeekStart(monday);
    setCellValues({});
    setCellErrors({});
    setDirty(false);
    const from = toIsoDate(monday);
    const to = toIsoDate(addDays(monday, 6));
    startNavTransition(async () => {
      const entries = await fetchWeekEntries(from, to);
      setCellValues(buildCellValuesFromEntries(entries, monday));
    });
  }

  async function handleSave() {
    const entries: Array<{
      taskId: string;
      dayIndex: number;
      hours: number;
    }> = [];
    for (const task of taskRows) {
      for (const d of DAYS) {
        const hours = cellValues[cellKey(task.id, d)] ?? 0;
        if (hours > 0) {
          entries.push({ taskId: task.id, dayIndex: d, hours });
        }
      }
    }
    if (entries.length === 0) return;

    setIsSaving(true);
    const batchEntries = entries.map((e) => ({
      taskId: e.taskId,
      date: toIsoDate(addDays(weekStart, e.dayIndex)),
      durationMinutes: Math.round(e.hours * 60),
      billable: true as const,
    }));

    try {
      const response = await saveWeeklyEntries(slug, batchEntries);

      if (!response.success) {
        toast.error(response.error ?? "Failed to save entries");
        return;
      }

      const { result } = response;
      if (!result) return;

      if (result.totalErrors === 0) {
        toast.success(
          `Saved ${result.totalCreated} ${result.totalCreated === 1 ? "entry" : "entries"}`
        );
        setDirty(false);
      } else if (result.totalCreated > 0) {
        toast.warning(`Saved ${result.totalCreated} entries, ${result.totalErrors} failed`);
        const newCellErrors = mapErrorsToCells(result.errors, batchEntries);
        setCellErrors(newCellErrors);
      } else {
        toast.error(`Save failed \u2014 ${result.totalErrors} errors`);
        const newCellErrors = mapErrorsToCells(result.errors, batchEntries);
        setCellErrors(newCellErrors);
      }
    } finally {
      setIsSaving(false);
    }
  }

  function mapErrorsToCells(
    errors: Array<{ index: number; taskId: string; message: string }>,
    batchEntries: Array<{ taskId: string; date: string }>
  ): Record<CellKey, string> {
    const newCellErrors: Record<CellKey, string> = {};
    for (const err of errors) {
      const original = batchEntries[err.index];
      if (original) {
        const entryDate = parseIsoDate(original.date);
        const diff = Math.round((entryDate.getTime() - weekStart.getTime()) / 86400000);
        if (diff >= 0 && diff <= 6) {
          newCellErrors[cellKey(original.taskId, diff)] = err.message;
        }
      }
    }
    return newCellErrors;
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
              projectId: entry.projectId,
              projectName: entry.projectName,
              title: entry.taskTitle,
            });
          }
          currentTaskIds.add(entry.taskId);
        }
      }

      if (newTaskRows.length > 0) {
        setTaskRows((prev) => [...prev, ...newTaskRows]);
      }

      // Build cell values from shifted entries
      const newCellValues = buildCellValuesFromEntries(shiftedEntries, weekStart);
      setCellValues(newCellValues);
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
      projectId: row.task.projectId,
      projectName: row.task.projectName,
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
          <Button onClick={handleSave} disabled={!dirty || isSaving || isNavigating}>
            {isSaving ? "Saving..." : "Save"}
          </Button>
        </div>
      </div>

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
