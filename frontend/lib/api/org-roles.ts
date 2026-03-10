import "server-only";

import { api } from "@/lib/api";

// ---- TypeScript Interfaces ----

export interface OrgRole {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  capabilities: string[];
  isSystem: boolean;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrgRoleRequest {
  name: string;
  description?: string;
  capabilities: string[];
}

export interface UpdateOrgRoleRequest {
  name?: string;
  description?: string;
  capabilities?: string[];
}

// ---- API Functions ----

export async function fetchOrgRoles(): Promise<OrgRole[]> {
  return api.get<OrgRole[]>("/api/org-roles");
}

export async function fetchOrgRole(id: string): Promise<OrgRole> {
  return api.get<OrgRole>(`/api/org-roles/${id}`);
}

export async function createOrgRole(
  data: CreateOrgRoleRequest,
): Promise<OrgRole> {
  return api.post<OrgRole>("/api/org-roles", data);
}

export async function updateOrgRole(
  id: string,
  data: UpdateOrgRoleRequest,
): Promise<OrgRole> {
  return api.put<OrgRole>(`/api/org-roles/${id}`, data);
}

export async function deleteOrgRole(id: string): Promise<void> {
  return api.delete<void>(`/api/org-roles/${id}`);
}
