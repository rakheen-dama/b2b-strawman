import { publicFetch } from "@/lib/api-client";
import type {
  AcceptancePageData,
  AcceptanceResponse,
  DeclineResponse,
} from "@/lib/types";

/**
 * Fetches acceptance page data for the given token.
 * Uses publicFetch (no JWT auth — token-based access).
 */
export async function getAcceptancePageData(
  token: string,
): Promise<AcceptancePageData> {
  const response = await publicFetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}`,
  );
  if (!response.ok) {
    throw new Error("Failed to load acceptance data. Please try again.");
  }
  return response.json() as Promise<AcceptancePageData>;
}

/**
 * Submits an acceptance for the given token with the acceptor's name.
 */
export async function submitAcceptance(
  token: string,
  name: string,
): Promise<AcceptanceResponse> {
  const response = await publicFetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}/accept`,
    {
      method: "POST",
      body: JSON.stringify({ name }),
    },
  );
  if (!response.ok) {
    throw new Error("Failed to submit acceptance. Please try again.");
  }
  return response.json() as Promise<AcceptanceResponse>;
}

/** 409 from the decline endpoint — the page re-fetches so the true state renders. */
export class DeclineConflictError extends Error {
  constructor() {
    super("This request can no longer be declined.");
    this.name = "DeclineConflictError";
  }
}

/**
 * Declines the document for the given token (Phase 88 U23). The backend requires a
 * JSON body: `{}` without a reason, `{reason}` with one (≤ 2000 chars; blank → null).
 * 409 (no longer SENT/VIEWED-and-unexpired) throws {@link DeclineConflictError}.
 */
export async function declineAcceptance(
  token: string,
  reason = "",
): Promise<DeclineResponse> {
  const trimmed = reason.trim();
  const response = await publicFetch(
    `/api/portal/acceptance/${encodeURIComponent(token)}/decline`,
    {
      method: "POST",
      body: JSON.stringify(trimmed ? { reason: trimmed } : {}),
    },
  );
  if (response.status === 409) {
    throw new DeclineConflictError();
  }
  if (!response.ok) {
    throw new Error("Failed to decline. Please try again.");
  }
  return response.json() as Promise<DeclineResponse>;
}

/**
 * Constructs the URL for the acceptance PDF stream.
 * Used as the iframe src for displaying the document.
 */
export function getAcceptancePdfUrl(token: string): string {
  const baseUrl =
    process.env.NEXT_PUBLIC_PORTAL_API_URL ?? "http://localhost:8080";
  return `${baseUrl}/api/portal/acceptance/${encodeURIComponent(token)}/pdf`;
}
