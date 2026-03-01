"use server";

/**
 * Route-group-local re-export of intake field fetching.
 * Provides a consistent import point for CreateCustomerDialog (246B) alongside
 * other customer server actions in this directory (e.g. customer-actions.ts).
 */
import { fetchIntakeFieldsAction } from "@/lib/actions/prerequisite-actions";
import type { EntityType } from "@/lib/types";
import type { IntakeFieldGroupsResponse } from "@/components/prerequisite/types";

export async function fetchIntakeFields(
  entityType: EntityType,
): Promise<IntakeFieldGroupsResponse> {
  return fetchIntakeFieldsAction(entityType);
}
