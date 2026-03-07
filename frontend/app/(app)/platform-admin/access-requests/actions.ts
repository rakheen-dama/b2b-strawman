"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";

export interface AccessRequest {
  id: string;
  email: string;
  fullName: string;
  organizationName: string;
  country: string;
  industry: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "PENDING_VERIFICATION";
  otpVerifiedAt: string | null;
  createdAt: string;
}

interface AccessRequestsResponse {
  content: AccessRequest[];
}

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function listAccessRequests(
  status?: string,
): Promise<{ success: boolean; data?: AccessRequest[]; error?: string }> {
  try {
    const endpoint = status
      ? `/api/platform-admin/access-requests?status=${status}`
      : "/api/platform-admin/access-requests";
    const response = await api.get<AccessRequestsResponse>(endpoint);
    return { success: true, data: response.content };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function approveAccessRequest(id: string): Promise<ActionResult> {
  try {
    await api.post(`/api/platform-admin/access-requests/${id}/approve`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath("/platform-admin/access-requests");
  return { success: true };
}

export async function rejectAccessRequest(id: string): Promise<ActionResult> {
  try {
    await api.post(`/api/platform-admin/access-requests/${id}/reject`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath("/platform-admin/access-requests");
  return { success: true };
}
