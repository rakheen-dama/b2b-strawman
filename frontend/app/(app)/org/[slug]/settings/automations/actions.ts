"use server";

import { ApiError } from "@/lib/api";
import {
  toggleRule,
  deleteRule,
  activateTemplate,
} from "@/lib/api/automations";
import { revalidatePath } from "next/cache";

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function toggleRuleAction(
  slug: string,
  ruleId: string,
): Promise<ActionResult> {
  try {
    await toggleRule(ruleId);
  } catch (error) {
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

export async function deleteRuleAction(
  slug: string,
  ruleId: string,
): Promise<ActionResult> {
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
  templateSlug: string,
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
