"use server";

import {
  executeReport,
  exportReportCsv,
  exportReportPdf,
  type ReportExecutionResponse,
} from "@/lib/api/reports";
import { api, ApiError } from "@/lib/api";
import type { Project, OrgMember, Customer } from "@/lib/types";

interface ActionResult<T> {
  data: T | null;
  error?: string;
}

export async function executeReportAction(
  slug: string,
  parameters: Record<string, unknown>,
  page: number,
  size: number,
): Promise<ActionResult<ReportExecutionResponse>> {
  try {
    const data = await executeReport(slug, parameters, page, size);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "An unexpected error occurred." };
  }
}

export async function exportReportCsvAction(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportCsv(slug, parameters);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export CSV." };
  }
}

export async function exportReportPdfAction(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<ActionResult<string>> {
  try {
    const data = await exportReportPdf(slug, parameters);
    return { data };
  } catch (error) {
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
  entityType: string,
): Promise<ActionResult<EntityOption[]>> {
  try {
    const type = entityType.toLowerCase();
    if (type === "project") {
      const projects = await api.get<Project[]>("/api/projects");
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
      const customers = await api.get<Customer[]>("/api/customers");
      return {
        data: customers.map((c) => ({ id: c.id, label: c.name })),
      };
    }
    return { data: null, error: `Unknown entity type: ${entityType}` };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to fetch entities." };
  }
}
