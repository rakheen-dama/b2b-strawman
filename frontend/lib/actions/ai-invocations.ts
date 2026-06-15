"use server";

import { ApiError } from "@/lib/api";
import {
  approveInvocation,
  listInvocations,
  rejectInvocation,
  type InvocationPage,
} from "@/lib/api/ai-invocations";

export interface ActionResult {
  success: boolean;
  error?: string;
}

/**
 * Server action wrapping the `server-only` AI invocations client so client
 * components (e.g. PendingSuggestionsWidget) can read pending suggestions
 * without a same-origin browser fetch that nothing proxies to the gateway.
 *
 * Returns an empty page when there are no pending invocations — the authorized
 * empty case is a normal `content: []`, not an error.
 */
export async function listPendingInvocationsAction(
  contextEntityType: string,
  contextEntityId: string
): Promise<InvocationPage> {
  try {
    return await listInvocations({
      contextEntityType,
      contextEntityId,
      status: "PENDING_APPROVAL",
      size: 10,
    });
  } catch (error) {
    // Degrade gracefully: the widget renders nothing on an empty page, so a
    // transient backend/auth failure simply hides the widget rather than
    // surfacing an error on every entity-detail page. (The original symptom
    // was a hard 404; this fix must never re-introduce a page-level error.)
    console.error("listPendingInvocationsAction failed", error);
    return { content: [], page: { totalElements: 0, totalPages: 0, size: 10, number: 0 } };
  }
}

export async function approveInvocationAction(id: string): Promise<ActionResult> {
  try {
    await approveInvocation(id);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}

export async function rejectInvocationAction(
  id: string,
  rejectReason: string
): Promise<ActionResult> {
  try {
    await rejectInvocation(id, rejectReason);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}
