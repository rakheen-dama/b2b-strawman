"use server";

import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
  MatterProfitabilityResponse,
  ClientProfitabilityResponse,
} from "@/lib/types";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function getUtilization(
  from: string,
  to: string
): Promise<ActionResult<UtilizationResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const data = await api.get<UtilizationResponse>(
      `/api/reports/utilization?from=${from}&to=${to}`
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
  customerId?: string
): Promise<ActionResult<OrgProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    // D4 (Phase 87): backend /api/reports/profitability still takes customerId (ReportController:66) — do not rename until the backend moves.
    if (customerId) params.set("customerId", customerId);
    const qs = params.toString();
    const data = await api.get<OrgProfitabilityResponse>(
      `/api/reports/profitability${qs ? `?${qs}` : ""}`
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
  to?: string
): Promise<ActionResult<MatterProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    const data = await api.get<MatterProfitabilityResponse>(
      `/api/matters/${projectId}/profitability${qs ? `?${qs}` : ""}`
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
  to?: string
): Promise<ActionResult<ClientProfitabilityResponse>> {
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
    return { data: null, error: "Forbidden" };
  }

  try {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    const data = await api.get<ClientProfitabilityResponse>(
      `/api/clients/${customerId}/profitability${qs ? `?${qs}` : ""}`
    );
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}
