"use server";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import {
  createLeaveBlock,
  updateLeaveBlock,
  deleteLeaveBlock,
  listLeaveForMember,
  createCapacityRecord,
  updateCapacityRecord,
  deleteCapacityRecord,
  listCapacityRecords,
} from "@/lib/api/capacity";
import type {
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
  await getAuthContext();
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
  await getAuthContext();
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
