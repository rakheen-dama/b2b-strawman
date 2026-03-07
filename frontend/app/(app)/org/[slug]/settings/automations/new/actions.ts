"use server";

import { ApiError } from "@/lib/api";
import { createRule } from "@/lib/api/automations";
import type { CreateRuleRequest } from "@/lib/api/automations";
import { revalidatePath } from "next/cache";
import type { ActionResult } from "../actions";

export async function createRuleAction(
  slug: string,
  data: CreateRuleRequest,
): Promise<ActionResult> {
  try {
    const created = await createRule(data);
    revalidatePath(`/org/${slug}/settings/automations`);
    return { success: true, data: created };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to create automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
