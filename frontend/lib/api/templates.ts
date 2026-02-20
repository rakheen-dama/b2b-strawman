import "server-only";

import { api } from "@/lib/api";

// ---- Template Task ----

export interface ProjectTemplateTagResponse {
  id: string;
  name: string;
  color: string | null;
}

export interface TemplateTaskResponse {
  id: string;
  name: string;
  description: string | null;
  estimatedHours: number | null;
  sortOrder: number;
  billable: boolean;
  assigneeRole: "PROJECT_LEAD" | "ANY_MEMBER" | "UNASSIGNED";
}

// ---- Template Response ----

export interface ProjectTemplateResponse {
  id: string;
  name: string;
  namePattern: string;
  description: string | null;
  billableDefault: boolean;
  source: "MANUAL" | "FROM_PROJECT";
  sourceProjectId: string | null;
  active: boolean;
  taskCount: number;
  tagCount: number;
  tasks: TemplateTaskResponse[];
  tags: ProjectTemplateTagResponse[];
  createdAt: string;
  updatedAt: string;
}

// ---- Requests ----

export interface TemplateTaskRequest {
  name: string;
  description?: string;
  estimatedHours?: number;
  sortOrder: number;
  billable: boolean;
  assigneeRole: "PROJECT_LEAD" | "ANY_MEMBER" | "UNASSIGNED";
}

export interface CreateProjectTemplateRequest {
  name: string;
  namePattern: string;
  description?: string;
  billableDefault: boolean;
  tasks: TemplateTaskRequest[];
  tagIds: string[];
}

export interface UpdateProjectTemplateRequest {
  name: string;
  namePattern: string;
  description?: string;
  billableDefault: boolean;
  tasks: TemplateTaskRequest[];
  tagIds: string[];
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
  customerId?: string;
  projectLeadMemberId?: string;
  description?: string;
}

// ---- API Functions ----

export async function getProjectTemplates(): Promise<ProjectTemplateResponse[]> {
  return api.get<ProjectTemplateResponse[]>("/api/project-templates");
}

export async function getProjectTemplate(id: string): Promise<ProjectTemplateResponse> {
  return api.get<ProjectTemplateResponse>(`/api/project-templates/${id}`);
}

export async function createProjectTemplate(
  data: CreateProjectTemplateRequest,
): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>("/api/project-templates", data);
}

export async function updateProjectTemplate(
  id: string,
  data: UpdateProjectTemplateRequest,
): Promise<ProjectTemplateResponse> {
  return api.put<ProjectTemplateResponse>(`/api/project-templates/${id}`, data);
}

export async function deleteProjectTemplate(id: string): Promise<void> {
  return api.delete<void>(`/api/project-templates/${id}`);
}

export async function duplicateProjectTemplate(id: string): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>(`/api/project-templates/${id}/duplicate`);
}

export async function saveProjectFromTemplate(
  projectId: string,
  data: SaveFromProjectRequest,
): Promise<ProjectTemplateResponse> {
  return api.post<ProjectTemplateResponse>(
    `/api/project-templates/from-project/${projectId}`,
    data,
  );
}

export async function instantiateProjectTemplate(
  templateId: string,
  data: InstantiateTemplateRequest,
): Promise<unknown> {
  return api.post<unknown>(`/api/project-templates/${templateId}/instantiate`, data);
}
