"use server";

import { createSavedViewAction as createShared, type ViewActionResult } from "@/lib/view-actions";
import type { CreateSavedViewRequest } from "@/lib/types";

export async function createSavedViewAction(
  slug: string,
  req: CreateSavedViewRequest
): Promise<ViewActionResult> {
  return createShared(slug, "pipeline", req);
}
