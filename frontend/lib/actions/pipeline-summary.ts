"use server";

import { pipelineSummary, type PipelineSummaryResponse } from "@/lib/api/crm";

/**
 * SWR fetcher for the dashboard pipeline-summary widget. Wraps the server-only
 * `pipelineSummary()` crm client so a `"use client"` SWR widget can reach it
 * across the server boundary. Degrades to `null` on failure (the widget renders
 * an error state) — matching the `try/catch → null` convention in
 * `lib/actions/dashboard.ts`.
 */
export async function fetchPipelineSummary(): Promise<PipelineSummaryResponse | null> {
  try {
    return await pipelineSummary();
  } catch {
    return null;
  }
}
