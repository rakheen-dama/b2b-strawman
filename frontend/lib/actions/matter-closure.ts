"use server";

import { listClosureLog } from "@/lib/api/matter-closure";
import type { ClosureLogEntry } from "@/lib/api/matter-closure";

/**
 * Server-action wrapper around `listClosureLog` so that `"use client"`
 * components (e.g. `<ClosureHistorySection>`) can fetch the closure log via
 * SWR without importing the `server-only` API module directly.
 *
 * Capability/authorization is enforced server-side by the underlying REST
 * endpoint — this action is a thin pass-through.
 */
export async function fetchClosureLog(projectId: string): Promise<ClosureLogEntry[]> {
  return listClosureLog(projectId);
}
