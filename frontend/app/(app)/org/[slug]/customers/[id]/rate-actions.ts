"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  BillingRate,
  CreateBillingRateRequest,
  UpdateBillingRateRequest,
} from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function createCustomerBillingRate(
  slug: string,
  customerId: string,
  data: CreateBillingRateRequest,
): Promise<ActionResult> {
  try {
    await api.post<BillingRate>("/api/billing-rates", {
      ...data,
      customerId,
    });
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

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function updateCustomerBillingRate(
  slug: string,
  customerId: string,
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

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function deleteCustomerBillingRate(
  slug: string,
  customerId: string,
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

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}
