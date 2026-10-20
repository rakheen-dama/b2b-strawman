import "server-only";

import { api } from "./client";
import type { TemplateReadiness } from "@/lib/types";

/**
 * Template readiness for the setup-guidance cards. The matter/client setup read models
 * (setup-status, readiness, unbilled-summary, completeness-summary) are owned by
 * `./matters.ts` / `./clients.ts` (Phase 87 610C; 610A review H1).
 */
export async function fetchTemplateReadiness(
  entityType: "PROJECT" | "CUSTOMER",
  entityId: string
): Promise<TemplateReadiness[]> {
  return api.get<TemplateReadiness[]>(
    `/api/templates/readiness?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`
  );
}
