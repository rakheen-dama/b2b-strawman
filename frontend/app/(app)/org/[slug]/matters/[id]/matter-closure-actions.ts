"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  closeMatter,
  evaluateClosure,
  ClosureGatesFailedError,
  type CloseMatterRequest,
  type CloseMatterResponse,
  type ClosureReport,
} from "@/lib/api/matter-closure";

export type EvaluateClosureResult =
  | { success: true; report: ClosureReport }
  | { success: false; error: string };

export type CloseMatterActionResult =
  | { success: true; data: CloseMatterResponse }
  | { success: false; kind: "gates_failed"; report: ClosureReport }
  | { success: false; kind: "forbidden"; error: string }
  | { success: false; kind: "error"; error: string };

export async function evaluateClosureAction(projectId: string): Promise<EvaluateClosureResult> {
  try {
    const report = await evaluateClosure(projectId);
    return { success: true, report };
  } catch (err) {
    return {
      success: false,
      error: err instanceof ApiError ? err.message : "Failed to evaluate closure",
    };
  }
}

export async function closeMatterAction(
  slug: string,
  projectId: string,
  req: CloseMatterRequest
): Promise<CloseMatterActionResult> {
  const caps = await fetchMyCapabilities();
  const canClose = caps.isOwner || caps.isAdmin || caps.capabilities.includes("CLOSE_MATTER");
  if (!canClose) {
    return {
      success: false,
      kind: "forbidden",
      error: "You do not have permission to close matters.",
    };
  }
  if (req.override) {
    const canOverride =
      caps.isOwner || caps.isAdmin || caps.capabilities.includes("OVERRIDE_MATTER_CLOSURE");
    if (!canOverride) {
      return {
        success: false,
        kind: "forbidden",
        error: "You do not have permission to override closure gates.",
      };
    }
  }
  try {
    const data = await closeMatter(projectId, req);
    revalidatePath(`/org/${slug}/projects/${projectId}`);
    revalidatePath(`/org/${slug}/projects`);
    return { success: true, data };
  } catch (err) {
    if (err instanceof ClosureGatesFailedError) {
      return { success: false, kind: "gates_failed", report: err.report };
    }
    if (err instanceof ApiError && err.status === 403) {
      return { success: false, kind: "forbidden", error: err.message };
    }
    return {
      success: false,
      kind: "error",
      error: err instanceof ApiError ? err.message : "Failed to close matter",
    };
  }
}
