import "server-only";

import { unstable_rethrow } from "next/navigation";
import { getClient } from "@/lib/api/clients";
import { isUuid } from "@/components/audit/filter-params";
import type { ConflictCheckClientDetails } from "./form-defaults";

/**
 * The `customerId` client's name + structural ID number for the conflict-check form prefill. The
 * New Matter link carries only `customerId` — no name or ID number in the URL (personal data,
 * POPIA) — so the page resolves them here. A missing or non-UUID id skips the lookup; a failed
 * lookup (404/403/network) returns null so the page renders on its other defaults; Next control
 * flow (the re-login redirect) still propagates.
 */
export async function resolveClientDetails(
  customerId: string | undefined
): Promise<ConflictCheckClientDetails | null> {
  if (!customerId || !isUuid(customerId)) return null;
  try {
    const client = await getClient(customerId);
    return { name: client.name, idNumber: client.idNumber };
  } catch (error) {
    unstable_rethrow(error);
    console.error("Failed to resolve conflict-check client:", error);
    return null;
  }
}
