"use server";

import { revalidatePath } from "next/cache";
import { getAuthContext } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { ApiError } from "@/lib/api";
import {
  createStage,
  updateStage,
  reorderStages,
  archiveStage,
  deleteStage,
  type CreateStageRequest,
  type UpdateStageRequest,
  type ReorderStagesRequest,
  type StageDto,
} from "@/lib/api/crm";

export interface StageActionResult {
  success: boolean;
  error?: string;
  status?: number;
  stage?: StageDto;
  stages?: StageDto[];
}

async function requireManagePipeline(slug: string): Promise<string | null> {
  const { orgSlug } = await getAuthContext();
  if (slug !== orgSlug) return "Organization mismatch.";
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("MANAGE_PIPELINE")) {
    return "You do not have permission to manage the pipeline.";
  }
  return null;
}

function revalidate(slug: string) {
  revalidatePath(`/org/${slug}/settings/pipeline`);
}

export async function createStageAction(
  slug: string,
  req: CreateStageRequest
): Promise<StageActionResult> {
  const denied = await requireManagePipeline(slug);
  if (denied) return { success: false, error: denied };
  try {
    const stage = await createStage(req);
    revalidate(slug);
    return { success: true, stage };
  } catch (error) {
    if (error instanceof ApiError)
      return { success: false, error: error.message, status: error.status };
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function updateStageAction(
  slug: string,
  id: string,
  req: UpdateStageRequest
): Promise<StageActionResult> {
  const denied = await requireManagePipeline(slug);
  if (denied) return { success: false, error: denied };
  try {
    const stage = await updateStage(id, req);
    revalidate(slug);
    return { success: true, stage };
  } catch (error) {
    if (error instanceof ApiError)
      return { success: false, error: error.message, status: error.status };
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function reorderStagesAction(
  slug: string,
  req: ReorderStagesRequest
): Promise<StageActionResult> {
  const denied = await requireManagePipeline(slug);
  if (denied) return { success: false, error: denied };
  try {
    const stages = await reorderStages(req);
    revalidate(slug);
    return { success: true, stages };
  } catch (error) {
    if (error instanceof ApiError)
      return { success: false, error: error.message, status: error.status };
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function archiveStageAction(slug: string, id: string): Promise<StageActionResult> {
  const denied = await requireManagePipeline(slug);
  if (denied) return { success: false, error: denied };
  try {
    const stage = await archiveStage(id);
    revalidate(slug);
    return { success: true, stage };
  } catch (error) {
    if (error instanceof ApiError)
      return { success: false, error: error.message, status: error.status };
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function deleteStageAction(slug: string, id: string): Promise<StageActionResult> {
  const denied = await requireManagePipeline(slug);
  if (denied) return { success: false, error: denied };
  try {
    await deleteStage(id);
    revalidate(slug);
    return { success: true };
  } catch (error) {
    if (error instanceof ApiError)
      return { success: false, error: error.message, status: error.status };
    return { success: false, error: "An unexpected error occurred." };
  }
}
