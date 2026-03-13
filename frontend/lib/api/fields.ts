import "server-only";

import { api } from "./client";
import type {
  EntityType,
  FieldDefinitionResponse,
  CreateFieldDefinitionRequest,
  UpdateFieldDefinitionRequest,
  FieldGroupResponse,
  CreateFieldGroupRequest,
  UpdateFieldGroupRequest,
  FieldGroupMemberResponse,
} from "@/lib/types";

// ---- Field Definitions ----

export async function getFieldDefinitions(
  entityType: string,
): Promise<FieldDefinitionResponse[]> {
  return api.get<FieldDefinitionResponse[]>(
    `/api/field-definitions?entityType=${entityType}`,
  );
}

export async function getFieldDefinition(
  id: string,
): Promise<FieldDefinitionResponse> {
  return api.get<FieldDefinitionResponse>(`/api/field-definitions/${id}`);
}

export async function createFieldDefinition(
  req: CreateFieldDefinitionRequest,
): Promise<FieldDefinitionResponse> {
  return api.post<FieldDefinitionResponse>("/api/field-definitions", req);
}

export async function updateFieldDefinition(
  id: string,
  req: UpdateFieldDefinitionRequest,
): Promise<FieldDefinitionResponse> {
  return api.put<FieldDefinitionResponse>(`/api/field-definitions/${id}`, req);
}

export async function deleteFieldDefinition(id: string): Promise<void> {
  return api.delete<void>(`/api/field-definitions/${id}`);
}

// ---- Field Groups ----

export async function getFieldGroups(
  entityType: string,
): Promise<FieldGroupResponse[]> {
  return api.get<FieldGroupResponse[]>(
    `/api/field-groups?entityType=${entityType}`,
  );
}

export async function getFieldGroup(
  id: string,
): Promise<FieldGroupResponse> {
  return api.get<FieldGroupResponse>(`/api/field-groups/${id}`);
}

export async function createFieldGroup(
  req: CreateFieldGroupRequest,
): Promise<FieldGroupResponse> {
  return api.post<FieldGroupResponse>("/api/field-groups", req);
}

export async function updateFieldGroup(
  id: string,
  req: UpdateFieldGroupRequest,
): Promise<FieldGroupResponse> {
  return api.put<FieldGroupResponse>(`/api/field-groups/${id}`, req);
}

export async function deleteFieldGroup(id: string): Promise<void> {
  return api.delete<void>(`/api/field-groups/${id}`);
}

export async function getGroupMembers(
  groupId: string,
): Promise<FieldGroupMemberResponse[]> {
  return api.get<FieldGroupMemberResponse[]>(
    `/api/field-groups/${groupId}/fields`,
  );
}

// ---- Entity Custom Field Groups ----

export async function setEntityFieldGroups(
  entityType: EntityType,
  entityId: string,
  appliedFieldGroups: string[],
): Promise<FieldDefinitionResponse[]> {
  const prefix = entityType.toLowerCase() + "s";
  return api.put<FieldDefinitionResponse[]>(
    `/api/${prefix}/${entityId}/field-groups`,
    { appliedFieldGroups },
  );
}
