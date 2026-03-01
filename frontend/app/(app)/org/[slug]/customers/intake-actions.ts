"use server";

import { fetchIntakeFieldsAction } from "@/lib/actions/prerequisite-actions";
import type { EntityType } from "@/lib/types";
import type { IntakeFieldGroupsResponse } from "@/components/prerequisite/types";

export async function fetchIntakeFields(
  entityType: EntityType,
): Promise<IntakeFieldGroupsResponse> {
  return fetchIntakeFieldsAction(entityType);
}
