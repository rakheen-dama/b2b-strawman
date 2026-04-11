"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { ProposalResponse } from "@/lib/types/proposal";
import type { Customer } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: ProposalResponse;
}

export interface PortalContactSummary {
  id: string;
  displayName: string;
  email: string;
}

interface CreateProposalRequest {
  title: string;
  customerId: string;
  feeModel: string;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  hourlyRateNote?: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
  contingencyPercent?: number;
  contingencyCapPercent?: number;
  contingencyDescription?: string;
  expiresAt?: string;
}

export async function createProposalAction(
  slug: string,
  data: CreateProposalRequest
): Promise<ActionResult> {
  try {
    const created = await api.post<ProposalResponse>("/api/proposals", data);
    revalidatePath(`/org/${slug}/proposals`);
    return { success: true, data: created };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to create proposals." };
      }
      if (error.status === 400) {
        return { success: false, error: error.message || "Invalid proposal data." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchProposalAction(id: string): Promise<ProposalResponse> {
  return api.get<ProposalResponse>(`/api/proposals/${id}`);
}

export async function sendProposalAction(
  slug: string,
  id: string,
  portalContactId: string
): Promise<ActionResult> {
  try {
    const data = await api.post<ProposalResponse>(`/api/proposals/${id}/send`, { portalContactId });
    revalidatePath(`/org/${slug}/proposals`);
    revalidatePath(`/org/${slug}/proposals/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to send proposals." };
      }
      if (error.status === 409) {
        return { success: false, error: "Proposal cannot be sent in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function withdrawProposalAction(slug: string, id: string): Promise<ActionResult> {
  try {
    const data = await api.post<ProposalResponse>(`/api/proposals/${id}/withdraw`);
    revalidatePath(`/org/${slug}/proposals`);
    revalidatePath(`/org/${slug}/proposals/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to withdraw proposals." };
      }
      if (error.status === 409) {
        return { success: false, error: "Proposal cannot be withdrawn in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchCustomersAction(): Promise<
  Array<{ id: string; name: string; email: string }>
> {
  try {
    const result = await api.get<Customer[]>("/api/customers?size=200");
    // The endpoint may return paginated or flat array
    const customers = Array.isArray(result)
      ? result
      : ((result as unknown as { content: Customer[] }).content ?? []);
    return customers
      .filter((c) => c.lifecycleStatus !== "OFFBOARDED" && c.lifecycleStatus !== "PROSPECT")
      .map((c) => ({ id: c.id, name: c.name, email: c.email }));
  } catch {
    return [];
  }
}

export async function fetchPortalContactsAction(
  customerId: string
): Promise<PortalContactSummary[]> {
  try {
    return await api.get<PortalContactSummary[]>(`/api/customers/${customerId}/portal-contacts`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return [];
    }
    console.error("Failed to fetch portal contacts:", error);
    return [];
  }
}
