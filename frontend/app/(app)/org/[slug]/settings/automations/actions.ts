"use server";

import { ApiError } from "@/lib/api";
import {
  toggleRule,
  deleteRule,
  activateTemplate,
  updateRule,
  duplicateRule,
  listExecutions,
} from "@/lib/api/automations";
import type {
  UpdateRuleRequest,
  AutomationRuleResponse,
  ExecutionStatus,
  AutomationExecutionResponse,
  PaginatedResponse,
} from "@/lib/api/automations";
import { revalidatePath } from "next/cache";

export interface ActionResult {
  success: boolean;
  error?: string;
  data?: AutomationRuleResponse;
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

  revalidatePath(`/org/${slug}/settings/automations`);
  return { success: true };
}

export async function deleteRuleAction(slug: string, ruleId: string): Promise<ActionResult> {
  try {
    await deleteRule(ruleId);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/automations`);
  return { success: true };
}

export async function activateTemplateAction(
  slug: string,
  templateSlug: string
): Promise<ActionResult> {
  try {
    await activateTemplate(templateSlug);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to activate automation templates.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/settings/automations`);
  return { success: true };
}

export async function updateRuleAction(
  slug: string,
  id: string,
  data: UpdateRuleRequest
): Promise<ActionResult> {
  try {
    const updated = await updateRule(id, data);
    revalidatePath(`/org/${slug}/settings/automations`);
    revalidatePath(`/org/${slug}/settings/automations/${id}`);
    return { success: true, data: updated };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to update automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}

export async function fetchExecutionsAction(params?: {
  ruleId?: string;
  status?: ExecutionStatus;
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<AutomationExecutionResponse>> {
  return listExecutions(params);
}

export async function duplicateRuleAction(slug: string, id: string): Promise<ActionResult> {
  try {
    const duplicated = await duplicateRule(id);
    revalidatePath(`/org/${slug}/settings/automations`);
    return { success: true, data: duplicated };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to duplicate automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
}
