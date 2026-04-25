"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { ConflictCheck, ConflictCheckResult, ConflictCheckType } from "@/lib/types";

// -- Response types --

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

// -- Conflict check filters --

export interface ConflictCheckFilters {
  result?: ConflictCheckResult;
  checkType?: ConflictCheckType;
  checkedBy?: string;
  dateFrom?: string;
  dateTo?: string;
}

// -- Conflict check actions --

export async function performConflictCheck(data: {
  checkedName: string;
  checkedIdNumber?: string;
  checkedRegistrationNumber?: string;
  checkType: string;
  customerId?: string;
  projectId?: string;
}): Promise<{ success: boolean; data?: ConflictCheck; error?: string }> {
  try {
    const body: Record<string, unknown> = {
      checkedName: data.checkedName,
      checkType: data.checkType,
    };
    if (data.checkedIdNumber) body.checkedIdNumber = data.checkedIdNumber;
    if (data.checkedRegistrationNumber)
      body.checkedRegistrationNumber = data.checkedRegistrationNumber;
    if (data.customerId) body.customerId = data.customerId;
    if (data.projectId) body.projectId = data.projectId;

    const result = await api.post<ConflictCheck>("/api/conflict-checks", body);
    return { success: true, data: result };
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Failed to perform conflict check";
    return { success: false, error: message };
  }
}

export async function fetchConflictChecks(
  filters?: ConflictCheckFilters
): Promise<PaginatedResponse<ConflictCheck>> {
  const params = new URLSearchParams();
  if (filters?.result) params.set("result", filters.result);
  if (filters?.checkType) params.set("checkType", filters.checkType);
  if (filters?.checkedBy) params.set("checkedBy", filters.checkedBy);
  if (filters?.dateFrom) params.set("dateFrom", filters.dateFrom);
  if (filters?.dateTo) params.set("dateTo", filters.dateTo);
  params.set("size", "100");

  return api.get<PaginatedResponse<ConflictCheck>>(`/api/conflict-checks?${params.toString()}`);
}

export async function fetchConflictCheck(id: string): Promise<ConflictCheck> {
  return api.get<ConflictCheck>(`/api/conflict-checks/${id}`);
}

export async function resolveConflict(
  slug: string,
  id: string,
  data: {
    resolution: string;
    resolutionNotes?: string;
    waiverDocumentId?: string;
  }
): Promise<ActionResult> {
  try {
    await api.post(`/api/conflict-checks/${id}/resolve`, data);
    revalidatePath(`/org/${slug}/conflict-check`);
    return { success: true };
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Failed to resolve conflict";
    return { success: false, error: message };
  }
}

// -- Projects & Customers (for form selectors) --
// Both /api/projects and /api/customers return a flat List<...Response> (no
// pagination envelope). We accept both shapes defensively in case those
// endpoints are ever paginated. See backend CustomerController.listCustomers
// and ProjectController.listProjects (both return ResponseEntity<List<...>>).

export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/projects?size=200");
  return Array.isArray(result) ? result : (result?.content ?? []);
}

export async function fetchCustomers(): Promise<{ id: string; name: string }[]> {
  const result = await api.get<
    { id: string; name: string }[] | PaginatedResponse<{ id: string; name: string }>
  >("/api/customers?size=200");
  return Array.isArray(result) ? result : (result?.content ?? []);
}
