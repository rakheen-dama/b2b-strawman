"use server";

import { api } from "@/lib/api";
import type { PaginatedExpenseResponse } from "@/lib/types";

/**
 * Fetch expenses for the current user (non-project-scoped).
 * Hits GET /api/expenses/mine.
 */
export async function getMyExpenses(params?: {
  page?: number;
  size?: number;
}): Promise<PaginatedExpenseResponse> {
  const searchParams = new URLSearchParams();
  if (params?.page != null) searchParams.set("page", String(params.page));
  if (params?.size != null) searchParams.set("size", String(params.size));
  searchParams.set("sort", "date,desc");

  const qs = searchParams.toString();
  const url = `/api/expenses/mine${qs ? `?${qs}` : ""}`;
  return api.get<PaginatedExpenseResponse>(url);
}
