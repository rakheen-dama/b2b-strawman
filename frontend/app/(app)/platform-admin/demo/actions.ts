"use server";

import { api, apiRequest, ApiError } from "@/lib/api";
import { type DemoProvisionFormData } from "@/lib/schemas/demo-provision";
import { revalidatePath } from "next/cache";
import type { AdminTenantBilling } from "@/app/(app)/platform-admin/billing/actions";

export interface DemoProvisionResponse {
  organizationId: string;
  organizationSlug: string;
  organizationName: string;
  verticalProfile: string;
  loginUrl: string;
  demoDataSeeded: boolean;
  adminNote: string | null;
}

export interface DemoCleanupResponse {
  organizationId: string;
  organizationName: string;
  keycloakCleaned: boolean;
  schemaCleaned: boolean;
  publicRecordsCleaned: boolean;
  s3Cleaned: boolean;
  errors: string[];
}

export interface DemoReseedResponse {
  organizationId: string;
  organizationName: string;
  verticalProfile: string;
  seededAt: string;
}

interface ActionResult<T = void> {
  success: boolean;
  data?: T;
  error?: string;
}

export async function provisionDemo(
  data: DemoProvisionFormData
): Promise<ActionResult<DemoProvisionResponse>> {
  try {
    const response = await api.post<DemoProvisionResponse>(
      "/api/platform-admin/demo/provision",
      data
    );
    revalidatePath("/platform-admin/demo");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function listDemoTenants(): Promise<ActionResult<AdminTenantBilling[]>> {
  try {
    // TODO: The backend billingMethod filter accepts a single value. Fetch PILOT
    // and COMPLIMENTARY separately to avoid pulling the full tenant list.
    // Tracked as a backend enhancement for multi-value billingMethod filtering.
    const [pilotResult, compResult] = await Promise.all([
      api.get<AdminTenantBilling[]>("/api/platform-admin/billing/tenants?billingMethod=PILOT"),
      api.get<AdminTenantBilling[]>(
        "/api/platform-admin/billing/tenants?billingMethod=COMPLIMENTARY"
      ),
    ]);
    const demoTenants = [...pilotResult, ...compResult];
    return { success: true, data: demoTenants };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deleteDemoTenant(
  orgId: string,
  confirmName: string
): Promise<ActionResult<DemoCleanupResponse>> {
  try {
    const response = await apiRequest<DemoCleanupResponse>(`/api/platform-admin/demo/${orgId}`, {
      method: "DELETE",
      body: { confirmOrganizationName: confirmName },
    });
    revalidatePath("/platform-admin/demo");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function reseedDemoTenant(orgId: string): Promise<ActionResult<DemoReseedResponse>> {
  try {
    const response = await api.post<DemoReseedResponse>(`/api/platform-admin/demo/${orgId}/reseed`);
    revalidatePath("/platform-admin/demo");
    return { success: true, data: response };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
