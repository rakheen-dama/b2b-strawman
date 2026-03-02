"use server";

import "server-only";

import { api } from "@/lib/api";
import type {
  PrerequisiteCheck,
  PrerequisiteContext,
} from "@/components/prerequisite/types";

export async function checkPrerequisitesAction(
  context: PrerequisiteContext,
  entityType: string,
  entityId: string,
): Promise<PrerequisiteCheck> {
  const params = new URLSearchParams({ context, entityType, entityId });
  return api.get<PrerequisiteCheck>(
    `/api/prerequisites/check?${params.toString()}`,
  );
}
