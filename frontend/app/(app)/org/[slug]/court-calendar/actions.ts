"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  CourtDate,
  CourtDateStatus,
  CourtDateType,
  PrescriptionTracker,
} from "@/lib/types";

// ── Response types ─────────────────────────────────────────────────

interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

interface ActionResult {
  success: boolean;
  error?: string;
}

// ── Court Date filters ─────────────────────────────────────────────

export interface CourtDateFilters {
  from?: string;
  to?: string;
  status?: CourtDateStatus;
  dateType?: CourtDateType;
  customerId?: string;
  projectId?: string;
}

// ── Court Date actions ─────────────────────────────────────────────

export async function fetchCourtDates(
  filters?: CourtDateFilters
): Promise<PaginatedResponse<CourtDate>> {
  const params = new URLSearchParams();
  if (filters?.from) params.set("from", filters.from);
  if (filters?.to) params.set("to", filters.to);
  if (filters?.status) params.set("status", filters.status);
  if (filters?.dateType) params.set("dateType", filters.dateType);
  if (filters?.customerId) params.set("customerId", filters.customerId);
  if (filters?.projectId) params.set("projectId", filters.projectId);
  params.set("size", "100");

  return api.get<PaginatedResponse<CourtDate>>(
    `/api/court-dates?${params.toString()}`
  );
}

export async function fetchCourtDate(id: string): Promise<CourtDate> {
  return api.get<CourtDate>(`/api/court-dates/${id}`);
}

export async function createCourtDate(
  slug: string,
  data: {
    projectId: string;
    dateType: string;
    scheduledDate: string;
    scheduledTime?: string;
    courtName: string;
    courtReference?: string;
    judgeMagistrate?: string;
    description?: string;
    reminderDays?: number;
  }
): Promise<ActionResult> {
  try {
    await api.post("/api/court-dates", data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to create court date";
    return { success: false, error: message };
  }
}

export async function updateCourtDate(
  slug: string,
  id: string,
  data: Record<string, unknown>
): Promise<ActionResult> {
  try {
    await api.put(`/api/court-dates/${id}`, data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to update court date";
    return { success: false, error: message };
  }
}

export async function postponeCourtDate(
  slug: string,
  id: string,
  data: { newDate: string; reason: string }
): Promise<ActionResult> {
  try {
    await api.post(`/api/court-dates/${id}/postpone`, data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to postpone court date";
    return { success: false, error: message };
  }
}

export async function cancelCourtDate(
  slug: string,
  id: string,
  data: { reason: string }
): Promise<ActionResult> {
  try {
    await api.post(`/api/court-dates/${id}/cancel`, data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to cancel court date";
    return { success: false, error: message };
  }
}

export async function recordOutcome(
  slug: string,
  id: string,
  data: { outcome: string }
): Promise<ActionResult> {
  try {
    await api.post(`/api/court-dates/${id}/outcome`, data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to record outcome";
    return { success: false, error: message };
  }
}

// ── Prescription Tracker actions ───────────────────────────────────

export async function fetchPrescriptionTrackers(
  filters?: { customerId?: string }
): Promise<PaginatedResponse<PrescriptionTracker>> {
  const params = new URLSearchParams();
  if (filters?.customerId) params.set("customerId", filters.customerId);
  params.set("size", "100");

  return api.get<PaginatedResponse<PrescriptionTracker>>(
    `/api/prescription-trackers?${params.toString()}`
  );
}

export async function createPrescriptionTracker(
  slug: string,
  data: {
    projectId: string;
    causeOfActionDate: string;
    prescriptionType: string;
    customYears?: number;
    notes?: string;
  }
): Promise<ActionResult> {
  try {
    await api.post("/api/prescription-trackers", data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to create prescription tracker";
    return { success: false, error: message };
  }
}

export async function interruptPrescription(
  slug: string,
  id: string,
  data: { interruptionDate: string; interruptionReason: string }
): Promise<ActionResult> {
  try {
    await api.post(`/api/prescription-trackers/${id}/interrupt`, data);
    revalidatePath(`/org/${slug}/court-calendar`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to interrupt prescription";
    return { success: false, error: message };
  }
}

// ── Projects (for dialogs) ─────────────────────────────────────────

export async function fetchProjects(): Promise<
  { id: string; name: string }[]
> {
  const result = await api.get<PaginatedResponse<{ id: string; name: string }>>(
    "/api/projects?size=200"
  );
  return result.content;
}

// ── Dashboard ──────────────────────────────────────────────────────

export interface UpcomingResponse {
  courtDates: CourtDate[];
  prescriptionWarnings: PrescriptionTracker[];
}

export async function fetchUpcoming(): Promise<UpcomingResponse> {
  return api.get<UpcomingResponse>("/api/court-calendar/upcoming");
}
