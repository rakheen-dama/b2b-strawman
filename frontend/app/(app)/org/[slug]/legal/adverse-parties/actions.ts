"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  AdverseParty,
  AdversePartyLink,
  AdversePartyType,
} from "@/lib/types";

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

// -- Adverse party detail with links --

export interface AdversePartyDetail extends AdverseParty {
  links?: AdversePartyLink[];
}

// -- Adverse party actions --

export async function fetchAdverseParties(
  search?: string,
  partyType?: AdversePartyType,
  page?: number
): Promise<PaginatedResponse<AdverseParty>> {
  const params = new URLSearchParams();
  if (search) params.set("search", search);
  if (partyType) params.set("partyType", partyType);
  params.set("page", String(page ?? 0));
  params.set("size", "20");

  return api.get<PaginatedResponse<AdverseParty>>(
    `/api/adverse-parties?${params.toString()}`
  );
}

export async function fetchAdverseParty(
  id: string
): Promise<AdversePartyDetail> {
  return api.get<AdversePartyDetail>(`/api/adverse-parties/${id}`);
}

export async function createAdverseParty(
  slug: string,
  data: {
    name: string;
    idNumber?: string;
    registrationNumber?: string;
    partyType: string;
    aliases?: string;
    notes?: string;
  }
): Promise<ActionResult> {
  try {
    await api.post("/api/adverse-parties", data);
    revalidatePath(`/org/${slug}/legal/adverse-parties`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to create adverse party";
    return { success: false, error: message };
  }
}

export async function updateAdverseParty(
  slug: string,
  id: string,
  data: Record<string, unknown>
): Promise<ActionResult> {
  try {
    await api.put(`/api/adverse-parties/${id}`, data);
    revalidatePath(`/org/${slug}/legal/adverse-parties`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to update adverse party";
    return { success: false, error: message };
  }
}

export async function deleteAdverseParty(
  slug: string,
  id: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/adverse-parties/${id}`);
    revalidatePath(`/org/${slug}/legal/adverse-parties`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to delete adverse party";
    return { success: false, error: message };
  }
}

export async function linkAdverseParty(
  slug: string,
  id: string,
  data: {
    projectId: string;
    customerId: string;
    relationship: string;
    description?: string;
  }
): Promise<ActionResult> {
  try {
    await api.post(`/api/adverse-parties/${id}/links`, data);
    revalidatePath(`/org/${slug}/legal/adverse-parties`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to link adverse party";
    return { success: false, error: message };
  }
}

export async function unlinkAdverseParty(
  slug: string,
  linkId: string
): Promise<ActionResult> {
  try {
    await api.delete(`/api/adverse-party-links/${linkId}`);
    revalidatePath(`/org/${slug}/legal/adverse-parties`);
    return { success: true };
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Failed to unlink adverse party";
    return { success: false, error: message };
  }
}

export async function fetchProjectAdverseParties(
  projectId: string
): Promise<AdversePartyLink[]> {
  return api.get<AdversePartyLink[]>(
    `/api/projects/${projectId}/adverse-parties`
  );
}

// -- Projects & Customers (for form selectors) --

export async function fetchProjects(): Promise<
  { id: string; name: string }[]
> {
  const result = await api.get<
    PaginatedResponse<{ id: string; name: string }>
  >("/api/projects?size=200");
  return result.content;
}

export async function fetchCustomers(): Promise<
  { id: string; name: string }[]
> {
  const result = await api.get<
    PaginatedResponse<{ id: string; name: string }>
  >("/api/customers?size=200");
  return result.content;
}
