"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import {
  createRetainer,
  pauseRetainer,
  resumeRetainer,
  terminateRetainer,
} from "@/lib/api/retainers";
import type { RetainerResponse, CreateRetainerRequest } from "@/lib/api/retainers";

interface ActionResult {
  success: boolean;
  error?: string;
  data?: RetainerResponse;
}

export async function createRetainerAction(
  slug: string,
  data: CreateRetainerRequest,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You do not have permission to perform this action." };
  }

  try {
    const created = await createRetainer(data);
    revalidatePath(`/org/${slug}/retainers`);
    return { success: true, data: created };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to create retainers." };
      }
      if (error.status === 409) {
        return { success: false, error: "This customer already has an active or paused retainer." };
      }
      if (error.status === 400) {
        return { success: false, error: error.message || "Invalid retainer data." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function pauseRetainerAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You do not have permission to perform this action." };
  }

  try {
    const data = await pauseRetainer(id);
    revalidatePath(`/org/${slug}/retainers`);
    revalidatePath(`/org/${slug}/retainers/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to pause retainers." };
      }
      if (error.status === 409) {
        return { success: false, error: "Retainer cannot be paused in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function resumeRetainerAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You do not have permission to perform this action." };
  }

  try {
    const data = await resumeRetainer(id);
    revalidatePath(`/org/${slug}/retainers`);
    revalidatePath(`/org/${slug}/retainers/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to resume retainers." };
      }
      if (error.status === 409) {
        return { success: false, error: "Retainer cannot be resumed in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function terminateRetainerAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "You do not have permission to perform this action." };
  }

  try {
    const data = await terminateRetainer(id);
    revalidatePath(`/org/${slug}/retainers`);
    revalidatePath(`/org/${slug}/retainers/${id}`);
    return { success: true, data };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to terminate retainers." };
      }
      if (error.status === 409) {
        return { success: false, error: "Retainer cannot be terminated in its current state." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
