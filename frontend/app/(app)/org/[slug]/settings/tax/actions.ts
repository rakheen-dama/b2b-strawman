"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  UpdateTaxSettingsRequest,
  TaxRateResponse,
  CreateTaxRateRequest,
  UpdateTaxRateRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateTaxSettings(
  slug: string,
  data: UpdateTaxSettingsRequest,
): Promise<ActionResult> {
  try {
    await api.patch("/api/settings/tax", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404 || error.status === 405) {
        return { success: false, error: "Tax settings API not available yet." };
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update tax settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  revalidatePath(`/org/${slug}/settings/tax`);
  return { success: true };
}

// ---- Tax Rate CRUD Actions ----

interface CreateTaxRateActionResult {
  success: boolean;
  error?: string;
  taxRate?: TaxRateResponse;
}

export async function createTaxRate(
  slug: string,
  req: CreateTaxRateRequest,
): Promise<CreateTaxRateActionResult> {
  let taxRate: TaxRateResponse;
  try {
    taxRate = await api.post<TaxRateResponse>("/api/tax-rates", req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create tax rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A tax rate with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tax`);
  return { success: true, taxRate };
}

export async function updateTaxRate(
  slug: string,
  id: string,
  req: UpdateTaxRateRequest,
): Promise<ActionResult> {
  try {
    await api.put(`/api/tax-rates/${id}`, req);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update tax rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error: "A tax rate with this name already exists.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tax`);
  return { success: true };
}

export async function deactivateTaxRate(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/tax-rates/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to deactivate tax rates.",
        };
      }
      if (error.status === 400 || error.status === 409) {
        return {
          success: false,
          error:
            error.detail?.detail ??
            error.message ??
            "Cannot deactivate this tax rate.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/tax`);
  return { success: true };
}
