"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  anonymizeClient,
  exportClientData,
  getClientAnonymizationPreview,
} from "@/lib/api/clients";
import { revalidatePath } from "next/cache";
import type {
  StandaloneExportResult,
  StandaloneAnonymizationResult,
  AnonymizationPreview,
} from "@/lib/types/data-protection";

interface ActionResult<T = undefined> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function triggerDataExport(
  customerId: string
): Promise<ActionResult<StandaloneExportResult>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can export client data." };
  }

  try {
    const result = await exportClientData(customerId);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchAnonymizationPreview(
  customerId: string
): Promise<ActionResult<AnonymizationPreview>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: "Only owners can preview anonymization." };
  }

  try {
    const result = await getClientAnonymizationPreview(customerId);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function executeAnonymization(
  slug: string,
  customerId: string,
  confirmationName: string,
  reason: string
): Promise<ActionResult<StandaloneAnonymizationResult>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: "Only owners can anonymize client data." };
  }

  try {
    const result = await anonymizeClient(customerId, { confirmationName, reason });

    revalidatePath(`/org/${slug}/clients`);
    revalidatePath(`/org/${slug}/clients/${customerId}`);

    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
