"use server";

import { auth } from "@clerk/nextjs/server";
import { api, ApiError } from "@/lib/api";
import {
  createDataRequestApi,
  updateDataRequestStatus,
  generateDataExport,
  getExportDownloadUrl,
  executeDataDeletion,
} from "@/lib/compliance-api";
import { revalidatePath } from "next/cache";
import type { DataRequestResponse, AnonymizationResult, Customer } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface DataRequestActionResult {
  success: boolean;
  error?: string;
  request?: DataRequestResponse;
}

interface ExportDownloadResult {
  success: boolean;
  error?: string;
  url?: string;
  expiresInSeconds?: number;
}

interface DeletionResult {
  success: boolean;
  error?: string;
  summary?: AnonymizationResult["anonymizationSummary"];
}

export async function fetchCustomersForSelector(): Promise<Customer[]> {
  return api.get<Customer[]>("/api/customers");
}

function revalidateRequestPaths(slug: string, requestId?: string) {
  revalidatePath(`/org/${slug}/compliance/requests`);
  if (requestId) {
    revalidatePath(`/org/${slug}/compliance/requests/${requestId}`);
  }
}

export async function createDataRequest(
  slug: string,
  customerId: string,
  requestType: string,
  description: string,
): Promise<DataRequestActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can create data requests." };
  }

  try {
    const request = await createDataRequestApi({ customerId, requestType, description });
    revalidateRequestPaths(slug);
    return { success: true, request };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create data request." };
  }
}

export async function updateRequestStatus(
  slug: string,
  id: string,
  action: string,
  reason?: string,
): Promise<DataRequestActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can update data requests." };
  }

  try {
    const request = await updateDataRequestStatus(id, action, reason);
    revalidateRequestPaths(slug, id);
    return { success: true, request };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update request status." };
  }
}

export async function generateExport(slug: string, id: string): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can generate exports." };
  }

  try {
    await generateDataExport(id);
    revalidateRequestPaths(slug, id);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to generate export." };
  }
}

export async function getExportUrl(id: string): Promise<ExportDownloadResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can download exports." };
  }

  try {
    const result = await getExportDownloadUrl(id);
    return { success: true, url: result.url, expiresInSeconds: result.expiresInSeconds };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to get export download URL." };
  }
}

export async function executeDeletion(
  slug: string,
  id: string,
  confirmCustomerName: string,
): Promise<DeletionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can execute data deletions." };
  }

  try {
    const result = await executeDataDeletion(id, confirmCustomerName);
    revalidateRequestPaths(slug, id);
    return { success: true, summary: result.anonymizationSummary };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to execute data deletion." };
  }
}
