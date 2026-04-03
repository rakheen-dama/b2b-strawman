"use server";

import { api, ApiError } from "@/lib/api";
import type { BillingMethod } from "@/lib/internal-api";
import { revalidatePath } from "next/cache";

export interface AdminTenantBilling {
  organizationId: string;
  organizationName: string;
  verticalProfile: string;
  subscriptionStatus: string;
  billingMethod: BillingMethod;
  trialEndsAt: string | null;
  currentPeriodEnd: string | null;
  graceEndsAt: string | null;
  createdAt: string;
  memberCount: number;
  adminNote: string | null;
  isDemoTenant: boolean;
}

export interface AdminBillingOverride {
  status?: string | null;
  billingMethod?: string | null;
  currentPeriodEnd?: string | null;
  adminNote: string;
}

interface ActionResult<T = void> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function listBillingTenants(
  status?: string,
  billingMethod?: string,
  profile?: string,
  search?: string,
): Promise<ActionResult<AdminTenantBilling[]>> {
  try {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (billingMethod) params.set("billingMethod", billingMethod);
    if (profile) params.set("profile", profile);
    if (search) params.set("search", search);
    const qs = params.toString();
    const endpoint = `/api/platform-admin/billing/tenants${qs ? `?${qs}` : ""}`;
    const response = await api.get<AdminTenantBilling[]>(endpoint);
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

function isValidOrgId(orgId: string): boolean {
  return /^[\w-]+$/.test(orgId);
}

export async function getBillingTenant(
  orgId: string,
): Promise<ActionResult<AdminTenantBilling>> {
  if (!isValidOrgId(orgId)) {
    return { success: false, error: "Invalid organization ID" };
  }
  try {
    const response = await api.get<AdminTenantBilling>(
      `/api/platform-admin/billing/tenants/${orgId}`,
    );
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function overrideBilling(
  orgId: string,
  data: AdminBillingOverride,
): Promise<ActionResult<AdminTenantBilling>> {
  if (!isValidOrgId(orgId)) {
    return { success: false, error: "Invalid organization ID" };
  }
  try {
    const response = await api.put<AdminTenantBilling>(
      `/api/platform-admin/billing/tenants/${orgId}/status`,
      data,
    );
    revalidatePath("/platform-admin/billing");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function extendTrial(
  orgId: string,
  days: number,
): Promise<ActionResult<AdminTenantBilling>> {
  if (!isValidOrgId(orgId)) {
    return { success: false, error: "Invalid organization ID" };
  }
  try {
    const response = await api.post<AdminTenantBilling>(
      `/api/platform-admin/billing/tenants/${orgId}/extend-trial`,
      { days },
    );
    revalidatePath("/platform-admin/billing");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
