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
  entries: BatchTimeEntryItem[],
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

export async function fetchWeekEntries(
  from: string,
  to: string,
): Promise<import("@/lib/types").MyWorkTimeEntryItem[]> {
  try {
    return await api.get(`/api/my-work/time-entries?from=${from}&to=${to}`);
  } catch (error) {
    console.error("Failed to fetch week entries:", error);
    return [];
  }
}
