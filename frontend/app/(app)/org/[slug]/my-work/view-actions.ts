"use server";

import {
  createSavedViewAction as createShared,
  type ViewActionResult,
} from "@/lib/view-actions";
import type { CreateSavedViewRequest } from "@/lib/types";

export async function createMyWorkViewAction(
  slug: string,
  req: CreateSavedViewRequest,
): Promise<ViewActionResult> {
  return createShared(slug, "my-work", req);
}
