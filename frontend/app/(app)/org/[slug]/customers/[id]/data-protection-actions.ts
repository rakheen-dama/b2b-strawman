"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { StandaloneExportResult, StandaloneAnonymizationResult, AnonymizationPreview } from "@/lib/types/data-protection";

interface ActionResult<T = undefined> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function triggerDataExport(
  customerId: string,
): Promise<ActionResult<StandaloneExportResult>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { success: false, error: "Only admins and owners can export customer data." };
  }

  try {
    const result = await api.post<StandaloneExportResult>(
      `/api/customers/${customerId}/data-export`,
    );
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchAnonymizationPreview(
  customerId: string,
): Promise<ActionResult<AnonymizationPreview>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: "Only owners can preview anonymization." };
  }

  try {
    const result = await api.get<AnonymizationPreview>(
      `/api/customers/${customerId}/anonymize/preview`,
    );
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
  reason: string,
): Promise<ActionResult<StandaloneAnonymizationResult>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isOwner) {
    return { success: false, error: "Only owners can anonymize customer data." };
  }

  try {
    const result = await api.post<StandaloneAnonymizationResult>(
      `/api/customers/${customerId}/anonymize`,
      { confirmationName, reason },
    );

    revalidatePath(`/org/${slug}/customers`);
    revalidatePath(`/org/${slug}/customers/${customerId}`);

    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
