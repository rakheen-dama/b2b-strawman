"use server";

import {
  countAuditEvents,
  exportAuditCsv,
  exportAuditPdf,
  type AuditEventFilter,
} from "@/lib/api/audit-events";
import { ApiError } from "@/lib/api/client";

export interface ActionResult<T> {
  data: T | null;
  error?: string;
  detail?: { rowCount?: number; cap?: number };
}

export async function exportAuditCsvAction(
  filter: AuditEventFilter
): Promise<ActionResult<string>> {
  try {
    const data = await exportAuditCsv(filter);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to export CSV." };
  }
}

export async function exportAuditPdfAction(
  filter: AuditEventFilter
): Promise<ActionResult<string>> {
  try {
    const data = await exportAuditPdf(filter);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      const detail = error.detail as
        | { rowCount?: number; cap?: number }
        | undefined;
      return { data: null, error: error.message, detail };
    }
    return { data: null, error: "Failed to export PDF." };
  }
}

export async function countAuditEventsAction(
  filter: AuditEventFilter
): Promise<ActionResult<number>> {
  try {
    const data = await countAuditEvents(filter);
    return { data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { data: null, error: error.message };
    }
    return { data: null, error: "Failed to count events." };
  }
}
