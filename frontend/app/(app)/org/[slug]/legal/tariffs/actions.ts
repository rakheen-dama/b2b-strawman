"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { TariffSchedule, TariffItem } from "@/lib/types";

// -- Response types --

interface ActionResult {
  success: boolean;
  error?: string;
}

// -- Tariff Schedule actions --

export async function fetchTariffSchedules(): Promise<TariffSchedule[]> {
  return api.get<TariffSchedule[]>("/api/tariff-schedules");
}

export async function fetchTariffSchedule(
  id: string,
): Promise<TariffSchedule> {
  return api.get<TariffSchedule>(`/api/tariff-schedules/${id}`);
}

export async function fetchActiveSchedule(): Promise<TariffSchedule | null> {
  try {
    return await api.get<TariffSchedule>("/api/tariff-schedules/active");
  } catch {
    return null;
  }
}

export async function createSchedule(
  slug: string,
  data: {
    name: string;
    code: string;
    description?: string;
    effectiveFrom: string;
    effectiveTo?: string;
  },
): Promise<ActionResult> {
  try {
    await api.post("/api/tariff-schedules", data);
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to create tariff schedule";
    return { success: false, error: message };
  }
}

export async function updateSchedule(
  slug: string,
  id: string,
  data: Record<string, unknown>,
): Promise<ActionResult> {
  try {
    await api.put(`/api/tariff-schedules/${id}`, data);
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to update tariff schedule";
    return { success: false, error: message };
  }
}

export async function cloneSchedule(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.post(`/api/tariff-schedules/${id}/clone`, {});
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to clone tariff schedule";
    return { success: false, error: message };
  }
}

// -- Tariff Item actions --

export async function fetchTariffItems(
  scheduleId: string,
  search?: string,
  section?: string,
): Promise<TariffItem[]> {
  const params = new URLSearchParams();
  params.set("scheduleId", scheduleId);
  if (search) params.set("search", search);
  if (section) params.set("section", section);

  return api.get<TariffItem[]>(
    `/api/tariff-items?${params.toString()}`,
  );
}

export async function createItem(
  slug: string,
  scheduleId: string,
  data: {
    itemNumber: string;
    description: string;
    unit: string;
    amount: number;
    notes?: string;
  },
): Promise<ActionResult> {
  try {
    await api.post(`/api/tariff-schedules/${scheduleId}/items`, data);
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to create tariff item";
    return { success: false, error: message };
  }
}

export async function updateItem(
  slug: string,
  id: string,
  data: Record<string, unknown>,
): Promise<ActionResult> {
  try {
    await api.put(`/api/tariff-items/${id}`, data);
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to update tariff item";
    return { success: false, error: message };
  }
}

export async function deleteItem(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/tariff-items/${id}`);
    revalidatePath(`/org/${slug}/legal/tariffs`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to delete tariff item";
    return { success: false, error: message };
  }
}
