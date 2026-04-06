"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type { KycVerifyResponse, KycIntegrationStatus } from "@/lib/types";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

export async function verifyKycAction(data: {
  customerId: string;
  checklistInstanceItemId: string;
  idNumber: string;
  fullName: string;
  idDocumentType?: string;
  consentAcknowledged: boolean;
}): Promise<ActionResult<KycVerifyResponse>> {
  try {
    const result = await api.post<KycVerifyResponse>("/api/kyc/verify", data);
    revalidatePath(`/org/[slug]/customers/${data.customerId}`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function getKycStatusAction(): Promise<KycIntegrationStatus> {
  try {
    return await api.get<KycIntegrationStatus>("/api/integrations/kyc/status");
  } catch {
    return { configured: false, provider: null };
  }
}

export async function getKycResultAction(
  reference: string,
): Promise<ActionResult<KycVerifyResponse>> {
  try {
    const result = await api.get<KycVerifyResponse>(
      `/api/kyc/result/${encodeURIComponent(reference)}`,
    );
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
