"use server";

import { api } from "@/lib/api";
import type { TemplateEntityType } from "@/lib/types";

export interface VariableInfo {
  key: string;
  label: string;
  type: string;
}

export interface VariableGroup {
  label: string;
  prefix: string;
  variables: VariableInfo[];
}

export interface LoopSource {
  key: string;
  label: string;
  entityTypes: string[];
  fields: string[];
}

export interface VariableMetadataResponse {
  groups: VariableGroup[];
  loopSources: LoopSource[];
}

export async function fetchVariableMetadata(
  entityType: TemplateEntityType
): Promise<VariableMetadataResponse> {
  return api.get<VariableMetadataResponse>(
    `/api/templates/variables?entityType=${encodeURIComponent(entityType)}`
  );
}

export async function fetchAllVariableMetadata(): Promise<VariableMetadataResponse> {
  const entityTypes: TemplateEntityType[] = ["PROJECT", "CUSTOMER", "INVOICE"];
  const responses = await Promise.all(entityTypes.map((et) => fetchVariableMetadata(et)));

  // Merge groups, de-duplicating by prefix (keep first occurrence, merge variables)
  const groupMap = new Map<string, VariableGroup>();
  for (const response of responses) {
    for (const group of response.groups) {
      const existing = groupMap.get(group.prefix);
      if (existing) {
        const existingKeys = new Set(existing.variables.map((v) => v.key));
        const newVars = group.variables.filter((v) => !existingKeys.has(v.key));
        groupMap.set(group.prefix, {
          ...existing,
          variables: [...existing.variables, ...newVars],
        });
      } else {
        groupMap.set(group.prefix, group);
      }
    }
  }

  return {
    groups: Array.from(groupMap.values()),
    loopSources: [], // Intentionally empty — clause scope doesn't use loop tables
  };
}
