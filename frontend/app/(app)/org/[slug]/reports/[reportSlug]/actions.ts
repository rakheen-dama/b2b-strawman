"use server";

import { unstable_rethrow } from "next/navigation";
import {
  executeReport,
  exportReportCsv,
  exportReportPdf,
  type ReportExecutionResponse,
} from "@/lib/api/reports";
import { api, ApiError } from "@/lib/api";
import { listMatters } from "@/lib/api/matters";
import { listClients } from "@/lib/api/clients";
import type { OrgMember, TrustAccount } from "@/lib/types";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function executeReportAction(
  slug: string,
  parameters: Record<string, unknown>,
  page: number,
  size: number
): Promise<ActionResult<ReportExecutionResponse>> {
  try {
    const data = await executeReport(slug, parameters, page, size);
    return { data };
  } catch (error) {
    // Next control flow (e.g. the re-login redirect on an expired session) must propagate.
    unstable_rethrow(error);
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function exportReportCsvAction(
  slug: string,
  parameters: Record<string, unknown>
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportCsv(slug, parameters);
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

export async function exportReportPdfAction(
  slug: string,
  parameters: Record<string, unknown>
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportPdf(slug, parameters);
    return { data };
  } catch (error) {
    // Next control flow (e.g. the re-login redirect on an expired session) must propagate.
    unstable_rethrow(error);
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export PDF." };
  }
}

// ---- Entity List Actions (for report parameter pickers) ----

export interface EntityOption {
  id: string;
  label: string;
  secondaryLabel?: string;
}

export async function fetchEntityOptionsAction(
  entityType: string
): Promise<ActionResult<EntityOption[]>> {
  try {
    const type = entityType.toLowerCase();
    if (type === "project") {
      const projects = await listMatters();
      return {
        data: projects.map((p) => ({ id: p.id, label: p.name })),
      };
    }
    if (type === "member") {
      const members = await api.get<OrgMember[]>("/api/members");
      return {
        data: members.map((m) => ({
          id: m.id,
          label: m.name,
          secondaryLabel: m.email,
        })),
      };
    }
    if (type === "customer") {
      const customers = await listClients();
      return {
        data: customers.map((c) => ({ id: c.id, label: c.name })),
      };
    }
    if (type === "trustaccount") {
      // Section 35 trust_account_id parameter (630A); GET /api/trust-accounts is VIEW_TRUST.
      const accounts = await api.get<TrustAccount[]>("/api/trust-accounts");
      return {
        data: accounts.map((a) => {
          // Name + bank need not be unique, so the label carries a masked account-number suffix
          // (the picker trigger shows only `label`). Never expose the full number.
          const accountNumber = a.accountNumber?.trim() ?? "";
          const maskedSuffix = accountNumber.length > 4 ? ` ••••${accountNumber.slice(-4)}` : "";
          return {
            id: a.id,
            label: `${a.accountName}${maskedSuffix}`,
            secondaryLabel: a.bankName,
          };
        }),
      };
    }
    return { data: null, error: `Unknown entity type: ${entityType}` };
  } catch (error) {
    // Next control flow (e.g. the re-login redirect on an expired session) must propagate.
    unstable_rethrow(error);
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to fetch entities." };
  }
}
