"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { TimeEntry, CreateTimeEntryRequest, UpdateTimeEntryRequest, ResolvedRate } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchTimeEntries(taskId: string): Promise<TimeEntry[]> {
  return api.get<TimeEntry[]>(`/api/tasks/${taskId}/time-entries`);
}

export async function createTimeEntry(
  slug: string,
  projectId: string,
  taskId: string,
  formData: FormData
): Promise<ActionResult> {
  const date = formData.get("date")?.toString().trim() ?? "";
  if (!date) {
    return { success: false, error: "Date is required." };
  }

  const hoursStr = formData.get("hours")?.toString().trim() ?? "0";
  const minutesStr = formData.get("minutes")?.toString().trim() ?? "0";
  const hours = parseInt(hoursStr, 10) || 0;
  const minutes = parseInt(minutesStr, 10) || 0;
  const durationMinutes = hours * 60 + minutes;

  if (durationMinutes <= 0) {
    return { success: false, error: "Duration must be greater than 0." };
  }

  const billableStr = formData.get("billable")?.toString();
  const billable = billableStr === "on" || billableStr === "true";
  const description = formData.get("description")?.toString().trim() || undefined;

  const body: CreateTimeEntryRequest = {
    date,
    durationMinutes,
    billable,
    description,
  };

  try {
    await api.post<TimeEntry>(`/api/tasks/${taskId}/time-entries`, body);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  // Also revalidate customer pages so retainer summaries refresh (BUG-004)
  revalidatePath(`/org/${slug}/customers`, "layout");

  return { success: true };
}

export async function updateTimeEntry(
  slug: string,
  projectId: string,
  timeEntryId: string,
  data: UpdateTimeEntryRequest
): Promise<ActionResult> {
  try {
    await api.put<TimeEntry>(`/api/time-entries/${timeEntryId}`, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to edit this time entry.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  revalidatePath(`/org/${slug}/customers`, "layout");

  return { success: true };
}

export async function deleteTimeEntry(
  slug: string,
  projectId: string,
  timeEntryId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/time-entries/${timeEntryId}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete this time entry.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/projects/${projectId}`);
  revalidatePath(`/org/${slug}/customers`, "layout");

  return { success: true };
}

export async function resolveRate(
  memberId: string,
  projectId: string,
  date: string
): Promise<ResolvedRate | null> {
  try {
    return await api.get<ResolvedRate>(
      `/api/billing-rates/resolve?memberId=${encodeURIComponent(memberId)}&projectId=${encodeURIComponent(projectId)}&date=${encodeURIComponent(date)}`
    );
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 404)) {
      console.error("Failed to resolve rate:", error);
    }
    return null;
  }
}
