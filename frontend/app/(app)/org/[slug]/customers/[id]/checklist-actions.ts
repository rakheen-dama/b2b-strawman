"use server";

import { auth } from "@clerk/nextjs/server";
import { ApiError } from "@/lib/api";
import {
  getCustomerChecklists,
  completeItem,
  skipItem,
  reopenItem,
  instantiateChecklist as apiInstantiate,
} from "@/lib/checklist-api";
import { revalidatePath } from "next/cache";
import type { ChecklistInstanceResponse } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function fetchCustomerChecklists(
  customerId: string,
): Promise<ChecklistInstanceResponse[]> {
  const { orgRole } = await auth();
  if (!orgRole) {
    return [];
  }

  try {
    return await getCustomerChecklists(customerId);
  } catch {
    return [];
  }
}

export async function completeChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
  notes: string,
  documentId?: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage checklist items." };
  }

  try {
    await completeItem(itemId, { notes: notes || undefined, documentId });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function skipChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
  reason: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage checklist items." };
  }

  try {
    await skipItem(itemId, { reason });
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function reopenChecklistItem(
  slug: string,
  customerId: string,
  itemId: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage checklist items." };
  }

  try {
    await reopenItem(itemId);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}

export async function instantiateChecklist(
  customerId: string,
  templateId: string,
  slug: string,
): Promise<ActionResult> {
  const { orgRole } = await auth();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can instantiate checklists." };
  }

  try {
    await apiInstantiate(customerId, templateId);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/customers/${customerId}`);
  return { success: true };
}
