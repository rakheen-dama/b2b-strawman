"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import {
  createAllocation,
  updateAllocation,
  deleteAllocation,
  bulkUpsertAllocations,
  listAllocations,
} from "@/lib/api/capacity";
import type {
  CreateAllocationRequest,
  UpdateAllocationRequest,
  BulkAllocationRequest,
  AllocationResponse,
  BulkAllocationResponse,
} from "@/lib/api/capacity";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

interface AllocationActionResult extends ActionResult {
  allocation?: AllocationResponse;
}

interface BulkActionResult extends ActionResult {
  data?: BulkAllocationResponse;
}

interface AllocationListResult extends ActionResult {
  allocations?: AllocationResponse[];
}

export async function createAllocationAction(
  slug: string,
  data: CreateAllocationRequest,
): Promise<AllocationActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage allocations.",
    };
  }

  try {
    const allocation = await createAllocation(data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, allocation };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create allocation." };
  }
}

export async function updateAllocationAction(
  slug: string,
  id: string,
  data: UpdateAllocationRequest,
): Promise<AllocationActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage allocations.",
    };
  }

  try {
    const allocation = await updateAllocation(id, data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, allocation };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update allocation." };
  }
}

export async function deleteAllocationAction(
  slug: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage allocations.",
    };
  }

  try {
    await deleteAllocation(id);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete allocation." };
  }
}

export async function listAllocationsAction(
  _slug: string,
  memberId: string,
): Promise<AllocationListResult> {
  await getAuthContext();
  try {
    const allocations = await listAllocations({ memberId });
    return { success: true, allocations };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to list allocations." };
  }
}

export async function bulkUpsertAction(
  slug: string,
  data: BulkAllocationRequest,
): Promise<BulkActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage allocations.",
    };
  }

  try {
    const result = await bulkUpsertAllocations(data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to bulk upsert allocations." };
  }
}
