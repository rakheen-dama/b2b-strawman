import "server-only";

import { api } from "@/lib/api";
import type { EntityType } from "@/lib/types";
import type {
  PrerequisiteCheck,
  PrerequisiteContext,
  IntakeFieldGroupsResponse,
} from "@/components/prerequisite/types";

export async function checkPrerequisites(
  context: PrerequisiteContext,
  entityType: EntityType,
  entityId: string,
): Promise<PrerequisiteCheck> {
  const params = new URLSearchParams({
    context,
    entityType,
    entityId,
  });
  return api.get<PrerequisiteCheck>(
    `/api/prerequisites/check?${params.toString()}`,
  );
}

export async function fetchIntakeFields(
  entityType: EntityType,
): Promise<IntakeFieldGroupsResponse> {
  return api.get<IntakeFieldGroupsResponse>(
    `/api/field-definitions/intake?entityType=${encodeURIComponent(entityType)}`,
  );
}

export async function checkEngagementPrerequisites(
  templateId: string,
  customerId: string,
): Promise<PrerequisiteCheck> {
  return api.get<PrerequisiteCheck>(
    `/api/project-templates/${templateId}/prerequisite-check?customerId=${encodeURIComponent(customerId)}`,
  );
}
