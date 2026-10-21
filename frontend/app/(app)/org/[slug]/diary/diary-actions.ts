"use server";

import {
  api,
  API_BASE,
  ApiError,
  getAuthFetchOptions,
  resolveServerReturnTo,
} from "@/lib/api/client";
import { listMatters } from "@/lib/api/matters";
import { redirectToReLogin } from "@/lib/auth/expiry";
import type { OrgMember, ProblemDetail } from "@/lib/types";
import {
  buildDiaryQuery,
  parseTypesHeader,
  type DiaryEventResponse,
  type DiaryMatterOption,
  type DiaryMemberOption,
  type DiaryQueryParams,
  type DiaryResult,
} from "./diary-types";

/**
 * GET /api/diary (613A). Raw fetch rather than api.get because the X-Diary-Types response
 * header is part of the contract and apiRequest returns the body only. Error handling mirrors
 * apiRequest (lib/api/client.ts): manual redirect → 401, ProblemDetail → ApiError(detail).
 */
export async function getDiary(params: DiaryQueryParams): Promise<DiaryResult> {
  let auth: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    auth = await getAuthFetchOptions("GET");
  } catch {
    redirectToReLogin(await resolveServerReturnTo(), "expired");
  }

  const response = await fetch(`${API_BASE}/api/diary?${buildDiaryQuery(params)}`, {
    method: "GET",
    headers: { "Content-Type": "application/json", ...auth.headers },
    credentials: auth.credentials,
    redirect: "manual",
  });

  if (response.status >= 300 && response.status < 400) {
    throw new ApiError(401, "Authentication session expired");
  }
  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    let message = response.statusText;
    try {
      detail = (await response.json()) as ProblemDetail;
      message = detail?.detail || detail?.title || message;
    } catch {
      // Non-JSON error body — keep statusText.
    }
    throw new ApiError(response.status, message, detail);
  }

  const events = (await response.json()) as DiaryEventResponse[];
  return { events, typesServed: parseTypesHeader(response.headers.get("X-Diary-Types")) };
}

/** Org members for the fee-earner Select (same endpoint as the calendar's member list). */
export async function getDiaryMembers(): Promise<DiaryMemberOption[]> {
  const members = await api.get<OrgMember[]>("/api/members");
  return members.map((m) => ({ id: m.id, name: m.name, email: m.email }));
}

/**
 * Matters for the matter combobox — the access-scoped list; the combobox search narrows it
 * client-side.
 */
export async function getDiaryMatters(): Promise<DiaryMatterOption[]> {
  // TODO(615A): switch to listMatters({ mine: true }) short list once GET /api/matters?mine=true exists.
  const matters = await listMatters();
  return matters
    .map((m) => ({ id: m.id, name: m.name, referenceNumber: m.referenceNumber }))
    .sort((a, b) => a.name.localeCompare(b.name));
}
