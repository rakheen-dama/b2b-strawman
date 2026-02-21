"use server";

import { auth } from "@clerk/nextjs/server";
import { executeReport, type ReportExecutionResponse } from "@/lib/api/reports";
import { ApiError } from "@/lib/api";

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
  await auth();

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
  const { getToken } = await auth();
  const token = await getToken();

  if (!token) {
    return { data: null, error: "Not authenticated" };
  }

  const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
  const queryParams = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value != null && value !== "") {
      queryParams.set(key, String(value));
    }
  }

  try {
    const response = await fetch(
      `${backendUrl}/api/report-definitions/${slug}/export/csv?${queryParams.toString()}`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    );

    if (!response.ok) {
      return { data: null, error: `Export failed: ${response.statusText}` };
    }

    const text = await response.text();
    return { data: text };
  } catch {
    return { data: null, error: "Failed to export CSV." };
  }
}

export async function exportReportPdfAction(
  slug: string,
  parameters: Record<string, unknown>,
): Promise<ActionResult<string>> {
  const { getToken } = await auth();
  const token = await getToken();

  if (!token) {
    return { data: null, error: "Not authenticated" };
  }

  const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
  const queryParams = new URLSearchParams();
  for (const [key, value] of Object.entries(parameters)) {
    if (value != null && value !== "") {
      queryParams.set(key, String(value));
    }
  }

  try {
    const response = await fetch(
      `${backendUrl}/api/report-definitions/${slug}/export/pdf?${queryParams.toString()}`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    );

    if (!response.ok) {
      return { data: null, error: `Export failed: ${response.statusText}` };
    }

    const buffer = await response.arrayBuffer();
    const base64 = Buffer.from(buffer).toString("base64");
    return { data: base64 };
  } catch {
    return { data: null, error: "Failed to export PDF." };
  }
}
