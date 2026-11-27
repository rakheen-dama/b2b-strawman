"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface BatchTimeEntryItem {
  taskId: string;
  date: string; // "YYYY-MM-DD"
  durationMinutes: number;
  description?: string;
  billable: boolean;
}

export interface CreatedEntry {
  id: string;
  taskId: string;
  date: string;
}

export interface EntryError {
  index: number;
  taskId: string;
  message: string;
}

export interface BatchSaveResult {
  created: CreatedEntry[];
  errors: EntryError[];
  totalCreated: number;
  totalErrors: number;
}

export async function saveWeeklyEntries(
  slug: string,
  entries: BatchTimeEntryItem[]
): Promise<{ success: boolean; result?: BatchSaveResult; error?: string }> {
  try {
    const result = await api.post<BatchSaveResult>("/api/time-entries/batch", {
      entries,
    });
    revalidatePath(`/org/${slug}/my-work`);
    revalidatePath(`/org/${slug}/my-work/timesheet`);
    return { success: true, result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

/**
 * The week's entries for the current member. Returns `null` (not `[]`) on failure: the timesheet
 * grid diffs its cells against these entries on Save (F-668A-R1), so an empty list it cannot trust
 * must not pass for "no time logged".
 */
export async function fetchWeekEntries(
  from: string,
  to: string
): Promise<import("@/lib/types").MyWorkTimeEntryItem[] | null> {
  try {
    return await api.get(`/api/my-work/time-entries?from=${from}&to=${to}`);
  } catch (error) {
    console.error("Failed to fetch week entries:", error);
    return null;
  }
}

/**
 * Fetches time entries for the previous week (weekStart - 7 to weekStart - 1).
 * Used by Copy Previous Week feature to pre-fill the current week's grid.
 */
export async function fetchPreviousWeekEntries(
  weekStart: string
): Promise<import("@/lib/types").MyWorkTimeEntryItem[]> {
  // weekStart is a Monday (YYYY-MM-DD). Previous week = weekStart-7 to weekStart-1
  const [y, m, d] = weekStart.split("-").map(Number);
  const monday = new Date(y, m - 1, d);
  const prevMonday = new Date(monday);
  prevMonday.setDate(monday.getDate() - 7);
  const prevSunday = new Date(monday);
  prevSunday.setDate(monday.getDate() - 1);

  const from = prevMonday.toLocaleDateString("en-CA");
  const to = prevSunday.toLocaleDateString("en-CA");

  try {
    return await api.get(`/api/my-work/time-entries?from=${from}&to=${to}`);
  } catch (error) {
    console.error("Failed to fetch previous week entries:", error);
    return [];
  }
}
