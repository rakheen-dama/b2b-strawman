"use server";

import {
  createSavedViewAction as createShared,
  updateSavedViewAction as updateShared,
  deleteSavedViewAction as deleteShared,
  type ViewActionResult,
} from "@/lib/view-actions";
import type {
  CreateSavedViewRequest,
  UpdateSavedViewRequest,
} from "@/lib/types";

export async function createSavedViewAction(
  slug: string,
  req: CreateSavedViewRequest,
): Promise<ViewActionResult> {
  return createShared(slug, "customers", req);
}

export async function updateSavedViewAction(
  slug: string,
  viewId: string,
  req: UpdateSavedViewRequest,
): Promise<ViewActionResult> {
  return updateShared(slug, "customers", viewId, req);
}

export async function deleteSavedViewAction(
  slug: string,
  viewId: string,
): Promise<ViewActionResult> {
  return deleteShared(slug, "customers", viewId);
}
