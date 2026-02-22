"use server";

import { ApiError } from "@/lib/api";
import {
  upsertIntegration,
  setApiKey,
  deleteApiKey,
  toggleIntegration,
  testConnection,
} from "@/lib/api/integrations";
import { revalidatePath } from "next/cache";
import type {
  IntegrationDomain,
  UpsertIntegrationRequest,
  SetApiKeyRequest,
  ToggleIntegrationRequest,
  ConnectionTestResult,
} from "@/lib/types";

interface ActionResult<T = undefined> {
  success: boolean;
  error?: string;
  data?: T;
}

export async function upsertIntegrationAction(
  slug: string,
  domain: IntegrationDomain,
  data: UpsertIntegrationRequest,
): Promise<ActionResult> {
  try {
    await upsertIntegration(domain, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/integrations`);
  return { success: true };
}

export async function setApiKeyAction(
  slug: string,
  domain: IntegrationDomain,
  data: SetApiKeyRequest,
): Promise<ActionResult> {
  try {
    await setApiKey(domain, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/integrations`);
  return { success: true };
}

export async function deleteApiKeyAction(
  slug: string,
  domain: IntegrationDomain,
): Promise<ActionResult> {
  try {
    await deleteApiKey(domain);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/integrations`);
  return { success: true };
}

export async function toggleIntegrationAction(
  slug: string,
  domain: IntegrationDomain,
  data: ToggleIntegrationRequest,
): Promise<ActionResult> {
  try {
    await toggleIntegration(domain, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/integrations`);
  return { success: true };
}

export async function testConnectionAction(
  slug: string,
  domain: IntegrationDomain,
): Promise<ActionResult<ConnectionTestResult>> {
  try {
    const result = await testConnection(domain);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "Permission denied." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
