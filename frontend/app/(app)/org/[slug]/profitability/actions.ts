"use server";

import { unstable_rethrow } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";
import { exportProfitabilityCsv } from "@/lib/api/reports";
import { getMatterProfitability } from "@/lib/api/matters";
import { getClientProfitability } from "@/lib/api/clients";
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
    const data = await getMatterProfitability(projectId, { from, to });
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
    const data = await getClientProfitability(customerId, { from, to });
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

/**
 * CSV of the Matter Profitability card's rows for its own date range. No frontend capability
 * check: the backend's FINANCIAL_VISIBILITY gate is the authority (the page itself renders only
 * for admin / owner / FINANCIAL_VISIBILITY), and a denial comes back as the 403 message.
 */
export async function exportProfitabilityCsvAction(
  from?: string,
  to?: string
): Promise<ActionResult<string>> {
  try {
    const data = await exportProfitabilityCsv(from || undefined, to || undefined);
    return { data };
  } catch (error) {
    // Next control flow (e.g. the re-login redirect on an expired session) must propagate.
    unstable_rethrow(error);
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export CSV." };
  }
}
