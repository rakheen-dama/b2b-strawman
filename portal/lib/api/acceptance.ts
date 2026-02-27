import { publicFetch } from "@/lib/api-client";
import type { AcceptancePageData, AcceptanceResponse } from "@/lib/types";

const BASE_URL =
  process.env.NEXT_PUBLIC_PORTAL_API_URL ?? "http://localhost:8080";

/**
 * Fetches acceptance page data for the given token.
 * Uses publicFetch (no JWT auth — token-based access).
 */
export async function getAcceptancePageData(
  token: string,
): Promise<AcceptancePageData> {
  const response = await publicFetch(`/api/portal/acceptance/${token}`);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Failed to load acceptance data: ${response.status} ${body}`);
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
  const response = await publicFetch(`/api/portal/acceptance/${token}/accept`, {
    method: "POST",
    body: JSON.stringify({ name }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Failed to submit acceptance: ${response.status} ${body}`);
  }
  return response.json() as Promise<AcceptanceResponse>;
}

/**
 * Constructs the URL for the acceptance PDF stream.
 * Used as the iframe src for displaying the document.
 */
export function getAcceptancePdfUrl(token: string): string {
  return `${BASE_URL}/api/portal/acceptance/${token}/pdf`;
}
