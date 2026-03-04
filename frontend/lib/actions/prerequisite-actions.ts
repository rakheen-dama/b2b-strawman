"use server";

import type { EntityType } from "@/lib/types";
import type {
  PrerequisiteCheck,
  PrerequisiteContext,
  IntakeFieldGroupsResponse,
} from "@/components/prerequisite/types";
import {
  checkPrerequisites,
  fetchIntakeFields,
  checkEngagementPrerequisites,
} from "@/lib/prerequisites";
import { api, ApiError, setEntityFieldGroups } from "@/lib/api";
import { revalidatePath } from "next/cache";

export async function checkPrerequisitesAction(
  context: PrerequisiteContext,
  entityType: EntityType,
  entityId: string,
): Promise<PrerequisiteCheck> {
  return checkPrerequisites(context, entityType, entityId);
}

export async function fetchIntakeFieldsAction(
  entityType: EntityType,
): Promise<IntakeFieldGroupsResponse> {
  return fetchIntakeFields(entityType);
}

export async function checkEngagementPrerequisitesAction(
  templateId: string,
  customerId: string,
): Promise<PrerequisiteCheck> {
  return checkEngagementPrerequisites(templateId, customerId);
}

interface ActionResult {
  success: boolean;
  error?: string;
}

export async function updateEntityCustomFieldsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  customFields: Record<string, unknown>,
): Promise<ActionResult> {
  const prefix = entityType.toLowerCase() + "s";
  try {
    if (entityType === "INVOICE") {
      await api.put(`/api/${prefix}/${entityId}/custom-fields`, { customFields });
    } else {
      const current = await api.get<Record<string, unknown>>(`/api/${prefix}/${entityId}`);
      await api.put(`/api/${prefix}/${entityId}`, { ...current, customFields });
    }
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to update custom fields." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/${prefix}/${entityId}`);
  revalidatePath(`/org/${slug}/${prefix}`);
  return { success: true };
}

export async function setEntityFieldGroupsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  appliedFieldGroups: string[],
): Promise<ActionResult> {
  const prefix = entityType.toLowerCase() + "s";
  try {
    await setEntityFieldGroups(entityType, entityId, appliedFieldGroups);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return { success: false, error: "You do not have permission to manage field groups." };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/${prefix}/${entityId}`);
  revalidatePath(`/org/${slug}/${prefix}`);
  return { success: true };
}
