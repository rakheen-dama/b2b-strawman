"use client";

import useSWR, { type SWRConfiguration } from "swr";
import { getTakeOnReport } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import type { TakeOnReport } from "@/lib/types";

/** The one SWR key of a take-on report: the summary and the downloads share a single read. */
export function takeOnReportKey(reportId: string) {
  return `take-on-report-${reportId}`;
}

async function fetchTakeOnReport(reportId: string): Promise<TakeOnReport> {
  const result = await getTakeOnReport(reportId);
  if (!result.success || !result.data) {
    throw new Error(result.error ?? "The take-on report could not be loaded.");
  }
  return result.data;
}

/**
 * `GET /api/take-on/reports/{id}` under one SWR key (657D): the Results step's summary and its
 * report downloads read the same entry, so the report is fetched once on mount. `reportId` null
 * reads nothing.
 */
export function useTakeOnReport(
  reportId: string | null,
  config?: SWRConfiguration<TakeOnReport, Error>
) {
  return useSWR<TakeOnReport, Error>(
    reportId ? takeOnReportKey(reportId) : null,
    () => fetchTakeOnReport(reportId as string),
    config
  );
}
