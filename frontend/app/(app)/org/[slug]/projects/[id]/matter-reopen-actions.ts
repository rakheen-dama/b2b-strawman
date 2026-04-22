"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  reopenMatter,
  RetentionElapsedError,
  type ReopenMatterResponse,
} from "@/lib/api/matter-closure";

export type ReopenMatterActionResult =
  | { success: true; data: ReopenMatterResponse }
  | {
      success: false;
      kind: "retention_elapsed";
      retentionEndedOn: string;
      error: string;
    }
  | { success: false; kind: "forbidden"; error: string }
  | { success: false; kind: "error"; error: string };

export async function reopenMatterAction(
  slug: string,
  projectId: string,
  notes: string
): Promise<ReopenMatterActionResult> {
  const caps = await fetchMyCapabilities();
  const canReopen = caps.isOwner || caps.isAdmin || caps.capabilities.includes("CLOSE_MATTER");
  if (!canReopen) {
    return {
      success: false,
      kind: "forbidden",
      error: "You do not have permission to reopen matters.",
    };
  }
  try {
    const data = await reopenMatter(projectId, notes);
    revalidatePath(`/org/${slug}/projects/${projectId}`);
    revalidatePath(`/org/${slug}/projects`);
    return { success: true, data };
  } catch (err) {
    if (err instanceof RetentionElapsedError) {
      const friendly = err.retentionEndedOn
        ? `Matter retention window expired on ${err.retentionEndedOn}. This matter can no longer be reopened.`
        : "Matter retention window has expired. This matter can no longer be reopened.";
      return {
        success: false,
        kind: "retention_elapsed",
        retentionEndedOn: err.retentionEndedOn,
        error: friendly,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      return { success: false, kind: "forbidden", error: err.message };
    }
    return {
      success: false,
      kind: "error",
      error: err instanceof ApiError ? err.message : "Failed to reopen matter",
    };
  }
}
