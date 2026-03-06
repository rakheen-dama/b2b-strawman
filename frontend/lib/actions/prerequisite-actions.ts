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

// Wrapper for custom-fields action — "use server" files cannot re-export,
// only async function declarations are allowed.
import { updateEntityCustomFieldsAction as _updateEntityCustomFields } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";

export async function updateEntityCustomFieldsAction(
  slug: string,
  entityType: EntityType,
  entityId: string,
  customFields: Record<string, unknown>,
) {
  return _updateEntityCustomFields(slug, entityType, entityId, customFields);
}
