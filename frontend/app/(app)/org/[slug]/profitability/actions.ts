"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
  ProjectProfitabilityResponse,
  CustomerProfitabilityResponse,
} from "@/lib/types";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function getUtilization(
  from: string,
  to: string,
): Promise<ActionResult<UtilizationResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const data = await api.get<UtilizationResponse>(
      `/api/reports/utilization?from=${from}&to=${to}`,
    );
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function getOrgProfitability(
  from?: string,
  to?: string,
  customerId?: string,
  includeProjections?: boolean,
): Promise<ActionResult<OrgProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    if (customerId) params.set("customerId", customerId);
    if (includeProjections) params.set("includeProjections", "true");
    const qs = params.toString();
    const data = await api.get<OrgProfitabilityResponse>(
      `/api/reports/profitability${qs ? `?${qs}` : ""}`,
    );
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function getProjectProfitability(
  projectId: string,
  from?: string,
  to?: string,
): Promise<ActionResult<ProjectProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    const data = await api.get<ProjectProfitabilityResponse>(
      `/api/projects/${projectId}/profitability${qs ? `?${qs}` : ""}`,
    );
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function getCustomerProfitability(
  customerId: string,
  from?: string,
  to?: string,
): Promise<ActionResult<CustomerProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    const data = await api.get<CustomerProfitabilityResponse>(
      `/api/customers/${customerId}/profitability${qs ? `?${qs}` : ""}`,
    );
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}
