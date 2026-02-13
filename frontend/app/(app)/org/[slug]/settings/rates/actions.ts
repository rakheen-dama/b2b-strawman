"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  OrgSettings,
  UpdateOrgSettingsRequest,
  BillingRate,
  CreateBillingRateRequest,
  UpdateBillingRateRequest,
  CostRate,
  CreateCostRateRequest,
  UpdateCostRateRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateDefaultCurrency(
  slug: string,
  currency: string,
): Promise<ActionResult> {
  const body: UpdateOrgSettingsRequest = { defaultCurrency: currency };

  try {
    await api.put<OrgSettings>("/api/settings", body);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "Only admins and owners can update currency settings.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function createBillingRate(
  slug: string,
  data: CreateBillingRateRequest,
): Promise<ActionResult> {
  try {
    await api.post<BillingRate>("/api/billing-rates", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create billing rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            "A billing rate already exists for this period. Please adjust the dates to avoid overlap.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function updateBillingRate(
  slug: string,
  id: string,
  data: UpdateBillingRateRequest,
): Promise<ActionResult> {
  try {
    await api.put<BillingRate>(`/api/billing-rates/${id}`, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update billing rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            "A billing rate already exists for this period. Please adjust the dates to avoid overlap.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function deleteBillingRate(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/billing-rates/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete billing rates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function createCostRate(
  slug: string,
  data: CreateCostRateRequest,
): Promise<ActionResult> {
  try {
    await api.post<CostRate>("/api/cost-rates", data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create cost rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            "A cost rate already exists for this period. Please adjust the dates to avoid overlap.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function updateCostRate(
  slug: string,
  id: string,
  data: UpdateCostRateRequest,
): Promise<ActionResult> {
  try {
    await api.put<CostRate>(`/api/cost-rates/${id}`, data);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update cost rates.",
        };
      }
      if (error.status === 409) {
        return {
          success: false,
          error:
            "A cost rate already exists for this period. Please adjust the dates to avoid overlap.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}

export async function deleteCostRate(
  slug: string,
  id: string,
): Promise<ActionResult> {
  try {
    await api.delete(`/api/cost-rates/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete cost rates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/rates`);
  return { success: true };
}
