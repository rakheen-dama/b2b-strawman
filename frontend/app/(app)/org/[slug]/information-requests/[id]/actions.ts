"use server";

import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  acceptItem,
  rejectItem,
  cancelRequest,
  resendNotification,
  type InformationRequestResponse,
} from "@/lib/api/information-requests";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: InformationRequestResponse;
}

interface VoidActionResult {
  success: boolean;
  error?: string;
}

export async function acceptItemAction(
  slug: string,
  requestId: string,
  itemId: string
): Promise<ActionResult> {
  try {
    const data = await acceptItem(requestId, itemId);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to accept item." };
  }
}

export async function rejectItemAction(
  slug: string,
  requestId: string,
  itemId: string,
  reason: string
): Promise<ActionResult> {
  try {
    const data = await rejectItem(requestId, itemId, reason);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to reject item." };
  }
}

export async function cancelRequestAction(slug: string, requestId: string): Promise<ActionResult> {
  try {
    const data = await cancelRequest(requestId);
    revalidatePath(`/org/${slug}/information-requests/${requestId}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to cancel request." };
  }
}

export async function resendNotificationAction(requestId: string): Promise<VoidActionResult> {
  try {
    await resendNotification(requestId);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to resend notification." };
  }
}
