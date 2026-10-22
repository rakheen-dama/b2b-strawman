"use server";

import { ApiError } from "@/lib/api";
import { toggleRule, listExecutions } from "@/lib/api/automations";
import type {
  ExecutionStatus,
  AutomationExecutionResponse,
  PaginatedResponse,
} from "@/lib/api/automations";
import { revalidatePath } from "next/cache";

export interface ActionResult {
  success: boolean;
  error?: string;
}

export async function toggleRuleAction(slug: string, ruleId: string): Promise<ActionResult> {
  try {
    await toggleRule(ruleId);
  } catch (error) {
    console.error("[toggleRuleAction] Failed to toggle rule:", ruleId, error);
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to toggle automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/connections`);
  return { success: true };
}

export async function fetchExecutionsAction(params?: {
  ruleId?: string;
  status?: ExecutionStatus;
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<AutomationExecutionResponse>> {
  return listExecutions(params);
}
