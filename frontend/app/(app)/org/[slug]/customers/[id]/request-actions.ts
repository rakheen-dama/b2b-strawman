"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createRequest,
  sendRequest,
  getCustomerRequests,
  getProjectRequests,
  listRequestTemplates,
  type InformationRequestResponse,
  type CreateInformationRequestRequest,
  type RequestTemplateResponse,
} from "@/lib/api/information-requests";
import { fetchPortalContacts } from "@/lib/actions/acceptance-actions";
import type { PortalContactSummary } from "@/lib/actions/acceptance-actions";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: InformationRequestResponse;
}

interface RequestListResult {
  success: boolean;
  error?: string;
  data?: InformationRequestResponse[];
}

interface TemplateListResult {
  success: boolean;
  error?: string;
  data?: RequestTemplateResponse[];
}

interface ContactListResult {
  success: boolean;
  error?: string;
  data?: PortalContactSummary[];
}

export async function createRequestAction(
  slug: string,
  customerId: string,
  input: CreateInformationRequestRequest
): Promise<ActionResult> {
  try {
    const data = await createRequest(input);
    revalidatePath(`/org/${slug}/customers/${customerId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can create information requests.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function sendRequestAction(
  slug: string,
  customerId: string,
  requestId: string
): Promise<ActionResult> {
  try {
    const data = await sendRequest(requestId);
    revalidatePath(`/org/${slug}/customers/${customerId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to send request." };
  }
}

export async function fetchCustomerRequestsAction(customerId: string): Promise<RequestListResult> {
  try {
    const data = await getCustomerRequests(customerId);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch requests." };
  }
}

export async function fetchProjectRequestsAction(projectId: string): Promise<RequestListResult> {
  try {
    const data = await getProjectRequests(projectId);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch requests." };
  }
}

export async function fetchActiveTemplatesAction(): Promise<TemplateListResult> {
  try {
    const data = await listRequestTemplates(true);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch templates." };
  }
}

export async function fetchPortalContactsAction(customerId: string): Promise<ContactListResult> {
  try {
    const data = await fetchPortalContacts(customerId);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to fetch contacts." };
  }
}
