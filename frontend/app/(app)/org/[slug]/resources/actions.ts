"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import {
  createAllocation,
  updateAllocation,
  deleteAllocation,
  bulkUpsertAllocations,
  createLeaveBlock,
  updateLeaveBlock,
  deleteLeaveBlock,
  listLeaveForMember,
  createCapacityRecord,
  updateCapacityRecord,
  deleteCapacityRecord,
  listCapacityRecords,
  listAllocations,
} from "@/lib/api/capacity";
import type {
  CreateAllocationRequest,
  UpdateAllocationRequest,
  BulkAllocationRequest,
  AllocationResponse,
  BulkAllocationResponse,
  CreateLeaveRequest,
  UpdateLeaveRequest,
  LeaveBlockResponse,
  CreateCapacityRequest,
  UpdateCapacityRequest,
  MemberCapacityResponse,
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

// ---- Leave Actions ----

interface LeaveActionResult extends ActionResult {
  leave?: LeaveBlockResponse;
}

interface LeaveListResult extends ActionResult {
  blocks?: LeaveBlockResponse[];
}

export async function createLeaveAction(
  slug: string,
  memberId: string,
  data: CreateLeaveRequest,
): Promise<LeaveActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage leave." };
  }

  try {
    const leave = await createLeaveBlock(memberId, data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, leave };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create leave block." };
  }
}

export async function updateLeaveAction(
  slug: string,
  memberId: string,
  id: string,
  data: UpdateLeaveRequest,
): Promise<LeaveActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage leave." };
  }

  try {
    const leave = await updateLeaveBlock(memberId, id, data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, leave };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update leave block." };
  }
}

export async function deleteLeaveAction(
  slug: string,
  memberId: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return { success: false, error: "Only admins and owners can manage leave." };
  }

  try {
    await deleteLeaveBlock(memberId, id);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete leave block." };
  }
}

export async function listLeaveAction(
  _slug: string,
  memberId: string,
): Promise<LeaveListResult> {
  try {
    const blocks = await listLeaveForMember(memberId);
    return { success: true, blocks };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to list leave blocks." };
  }
}

// ---- Capacity Actions ----

interface CapacityActionResult extends ActionResult {
  capacity?: MemberCapacityResponse;
}

interface CapacityListResult extends ActionResult {
  records?: MemberCapacityResponse[];
}

export async function createCapacityRecordAction(
  slug: string,
  memberId: string,
  data: CreateCapacityRequest,
): Promise<CapacityActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage capacity.",
    };
  }

  try {
    const capacity = await createCapacityRecord(memberId, data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, capacity };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to create capacity record." };
  }
}

export async function updateCapacityRecordAction(
  slug: string,
  memberId: string,
  id: string,
  data: UpdateCapacityRequest,
): Promise<CapacityActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage capacity.",
    };
  }

  try {
    const capacity = await updateCapacityRecord(memberId, id, data);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true, capacity };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to update capacity record." };
  }
}

export async function deleteCapacityRecordAction(
  slug: string,
  memberId: string,
  id: string,
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();
  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "Only admins and owners can manage capacity.",
    };
  }

  try {
    await deleteCapacityRecord(memberId, id);
    revalidatePath(`/org/${slug}/resources`);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to delete capacity record." };
  }
}

export async function listCapacityRecordsAction(
  _slug: string,
  memberId: string,
): Promise<CapacityListResult> {
  try {
    const records = await listCapacityRecords(memberId);
    return { success: true, records };
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "Failed to list capacity records." };
  }
}

// ---- Allocation List Action ----

interface AllocationListResult extends ActionResult {
  allocations?: AllocationResponse[];
}

export async function listAllocationsAction(
  _slug: string,
  memberId: string,
): Promise<AllocationListResult> {
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
