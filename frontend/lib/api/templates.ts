import "server-only";

import { api } from "./client";

// ---- Template Task ----

export interface ProjectTemplateTagResponse {
  id: string;
  name: string;
  color: string | null;
}

export interface TemplateTaskItemResponse {
  id: string;
  title: string;
  sortOrder: number;
}

export interface TemplateTaskResponse {
  id: string;
  name: string;
  description: string | null;
  estimatedHours: number | null;
  sortOrder: number;
  billable: boolean;
  assigneeRole: "PROJECT_LEAD" | "ANY_MEMBER" | "UNASSIGNED";
  items: TemplateTaskItemResponse[];
}

// ---- Template Response ----

export interface ProjectTemplateResponse {
  id: string;
  name: string;
  namePattern: string;
  description: string | null;
  billableDefault: boolean;
  source: "MANUAL" | "FROM_PROJECT" | "SEEDER";
  sourceMatterId: string | null;
  active: boolean;
  taskCount: number;
  tagCount: number;
  tasks: TemplateTaskResponse[];
  tags: ProjectTemplateTagResponse[];
  requiredCustomerFieldIds?: string[];
  requestTemplateId?: string | null;
  /** Work type matters created from this template inherit when none is supplied. */
  workType?: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---- Requests ----

export interface TemplateTaskItemRequest {
  title: string;
  sortOrder: number;
}

export interface TemplateTaskRequest {
  name: string;
  description?: string;
  estimatedHours?: number;
  sortOrder: number;
  billable: boolean;
  assigneeRole: "PROJECT_LEAD" | "ANY_MEMBER" | "UNASSIGNED";
  items?: TemplateTaskItemRequest[];
}

export interface CreateProjectTemplateRequest {
  name: string;
  namePattern: string;
  description?: string;
  billableDefault: boolean;
  tasks: TemplateTaskRequest[];
  tagIds: string[];
  requestTemplateId?: string | null;
  workType?: string | null;
}

export interface UpdateProjectTemplateRequest {
  name: string;
  namePattern: string;
  description?: string;
  billableDefault: boolean;
  tasks: TemplateTaskRequest[];
  tagIds: string[];
  requestTemplateId?: string | null;
  workType?: string | null;
}

export interface SaveFromProjectRequest {
  name: string;
  namePattern: string;
  description?: string;
  taskIds: string[];
  tagIds: string[];
  taskRoles: Record<string, string>;
}

export interface InstantiateTemplateRequest {
  name?: string;
  clientId?: string;
  matterLeadMemberId?: string;
  description?: string;
  referenceNumber?: string;
  priority?: string;
  workType?: string;
}

// ---- API Functions ----

export async function getProjectTemplates(): Promise<ProjectTemplateResponse[]> {
  return api.get<ProjectTemplateResponse[]>("/api/matter-templates");
}

export async function getProjectTemplate(id: string): Promise<ProjectTemplateResponse> {
  return api.get<ProjectTemplateResponse>(`/api/matter-templates/${id}`);
}

export async function createProjectTemplate(
  data: CreateProjectTemplateRequest
): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>("/api/matter-templates", data);
}

export async function updateProjectTemplate(
  id: string,
  data: UpdateProjectTemplateRequest
): Promise<ProjectTemplateResponse> {
  return api.put<ProjectTemplateResponse>(`/api/matter-templates/${id}`, data);
}

export async function deleteProjectTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/matter-templates/${id}`);
}

export async function duplicateProjectTemplate(id: string): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>(`/api/matter-templates/${id}/duplicate`);
}

export async function saveProjectFromTemplate(
  projectId: string,
  data: SaveFromProjectRequest
): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>(`/api/matter-templates/from-matter/${projectId}`, data);
}

export interface InstantiateTemplateResponse {
  id: string;
  name: string;
}

export async function instantiateProjectTemplate(
  templateId: string,
  data: InstantiateTemplateRequest
): Promise<InstantiateTemplateResponse> {
  return api.post<InstantiateTemplateResponse>(
    `/api/matter-templates/${templateId}/instantiate`,
    data
  );
}

export async function updateTemplateRequiredCustomerFields(
  id: string,
  fieldDefinitionIds: string[]
): Promise<void> {
  return api.put<void>(`/api/matter-templates/${id}/required-client-fields`, {
    fieldDefinitionIds,
  });
}
