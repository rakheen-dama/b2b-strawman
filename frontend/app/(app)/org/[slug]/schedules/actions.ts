"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createSchedule,
  updateSchedule,
  deleteSchedule,
  pauseSchedule,
  resumeSchedule,
} from "@/lib/api/schedules";
import type {
  ScheduleResponse,
  CreateScheduleRequest,
  UpdateScheduleRequest,
} from "@/lib/api/schedules";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: ScheduleResponse;
}

export async function createScheduleAction(
  slug: string,
  data: CreateScheduleRequest,
): Promise<ActionResult> {
  try {
    const created = await createSchedule(data);
    revalidatePath(`/org/${slug}/schedules`);
    return { success: true, data: created };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to create schedules." };
      }
      if (error.status === 400) {
        return { success: false, error: error.message || "Invalid schedule data." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateScheduleAction(
  slug: string,
  id: string,
  data: UpdateScheduleRequest,
): Promise<ActionResult> {
  try {
    const updated = await updateSchedule(id, data);
    revalidatePath(`/org/${slug}/schedules`);
    revalidatePath(`/org/${slug}/schedules/${id}`);
    return { success: true, data: updated };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to update schedules." };
      }
      if (error.status === 400) {
        return { success: false, error: error.message || "Invalid schedule data." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deleteScheduleAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await deleteSchedule(id);
    revalidatePath(`/org/${slug}/schedules`);
    revalidatePath(`/org/${slug}/schedules/${id}`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to delete schedules." };
      }
      if (error.status === 404) {
        return { success: false, error: "Schedule not found." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function pauseScheduleAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    const data = await pauseSchedule(id);
    revalidatePath(`/org/${slug}/schedules`);
    revalidatePath(`/org/${slug}/schedules/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to pause schedules." };
      }
      if (error.status === 409) {
        return { success: false, error: "Schedule cannot be paused in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function resumeScheduleAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    const data = await resumeSchedule(id);
    revalidatePath(`/org/${slug}/schedules`);
    revalidatePath(`/org/${slug}/schedules/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to resume schedules." };
      }
      if (error.status === 409) {
        return { success: false, error: "Schedule cannot be resumed in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
