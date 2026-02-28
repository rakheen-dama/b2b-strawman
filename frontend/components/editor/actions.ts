"use server";

import { api } from "@/lib/api";

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
  entityType: string,
): Promise<VariableMetadataResponse> {
  return api.get<VariableMetadataResponse>(
    `/api/templates/variables?entityType=${entityType}`,
  );
}
