import "server-only";

import { api } from "./client";

// === Dashboard Summary ===

export interface AutomationSummary {
  activeRulesCount: number;
  todayTotal: number;
  todaySucceeded: number;
  todayFailed: number;
}

// === Enums ===

export type TriggerType =
  | "TASK_STATUS_CHANGED"
  | "PROJECT_STATUS_CHANGED"
  | "CUSTOMER_STATUS_CHANGED"
  | "INVOICE_STATUS_CHANGED"
  | "TIME_ENTRY_CREATED"
  | "BUDGET_THRESHOLD_REACHED"
  | "DOCUMENT_ACCEPTED"
  | "INFORMATION_REQUEST_COMPLETED";

export type ExecutionStatus =
  | "TRIGGERED"
  | "ACTIONS_COMPLETED"
  | "ACTIONS_FAILED"
  | "CONDITIONS_NOT_MET";

export type ActionType =
  | "CREATE_TASK"
  | "SEND_NOTIFICATION"
  | "SEND_EMAIL"
  | "UPDATE_STATUS"
  | "CREATE_PROJECT"
  | "ASSIGN_MEMBER";

export type ActionExecutionStatus =
  | "PENDING"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED";

export type RuleSource = "MANUAL" | "TEMPLATE";

export type ConditionOperator =
  | "EQUALS"
  | "NOT_EQUALS"
  | "IN"
  | "NOT_IN"
  | "GREATER_THAN"
  | "LESS_THAN"
  | "CONTAINS"
  | "IS_NULL"
  | "IS_NOT_NULL";

export type DelayUnit = "MINUTES" | "HOURS" | "DAYS";

// === Response DTOs ===

export interface AutomationActionResponse {
  id: string;
  ruleId: string;
  sortOrder: number;
  actionType: ActionType;
  actionConfig: Record<string, unknown>;
  delayDuration: number | null;
  delayUnit: DelayUnit | null;
  createdAt: string;
  updatedAt: string;
}

export interface AutomationRuleResponse {
  id: string;
  name: string;
  description: string | null;
  enabled: boolean;
  triggerType: TriggerType;
  triggerConfig: Record<string, unknown>;
  conditions: Record<string, unknown>[];
  source: RuleSource;
  templateSlug: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  actions: AutomationActionResponse[];
}

export interface ActionExecutionResponse {
  id: string;
  actionId: string;
  actionType: ActionType;
  status: ActionExecutionStatus;
  scheduledFor: string | null;
  executedAt: string | null;
  resultData: Record<string, unknown> | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface AutomationExecutionResponse {
  id: string;
  ruleId: string;
  ruleName: string;
  triggerEventType: string;
  triggerEventData: Record<string, unknown>;
  conditionsMet: boolean;
  status: ExecutionStatus;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
  createdAt: string;
  actionExecutions: ActionExecutionResponse[];
}

export interface TemplateDefinitionResponse {
  slug: string;
  name: string;
  description: string;
  category: string;
  triggerType: string;
  triggerConfig: Record<string, unknown>;
  actionCount: number;
}

// === Request DTOs ===

export interface ActionRequest {
  actionType: ActionType;
  actionConfig: Record<string, unknown>;
  sortOrder: number;
  delayDuration?: number | null;
  delayUnit?: DelayUnit | null;
}

export interface CreateRuleRequest {
  name: string;
  description?: string;
  triggerType: TriggerType;
  triggerConfig: Record<string, unknown>;
  conditions?: Record<string, unknown>[];
  actions?: ActionRequest[];
}

export interface UpdateRuleRequest {
  name: string;
  description?: string;
  triggerType: TriggerType;
  triggerConfig: Record<string, unknown>;
  conditions?: Record<string, unknown>[];
  actions?: ActionRequest[];
}

export interface TestRuleRequest {
  sampleEventData: Record<string, unknown>;
}

export interface TestRuleResponse {
  conditionsMet: boolean;
  evaluationDetails: string[];
}

// === Paginated Response ===

export interface PaginatedResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

// === API Functions ===

// -- Rules --

export async function listRules(params?: {
  enabled?: boolean;
  triggerType?: TriggerType;
}): Promise<AutomationRuleResponse[]> {
  const searchParams = new URLSearchParams();
  if (params?.enabled !== undefined)
    searchParams.set("enabled", String(params.enabled));
  if (params?.triggerType) searchParams.set("triggerType", params.triggerType);
  const qs = searchParams.toString();
  return api.get<AutomationRuleResponse[]>(
    `/api/automation-rules${qs ? `?${qs}` : ""}`,
  );
}

export async function getRule(id: string): Promise<AutomationRuleResponse> {
  return api.get<AutomationRuleResponse>(`/api/automation-rules/${id}`);
}

export async function createRule(
  data: CreateRuleRequest,
): Promise<AutomationRuleResponse> {
  return api.post<AutomationRuleResponse>("/api/automation-rules", data);
}

export async function updateRule(
  id: string,
  data: UpdateRuleRequest,
): Promise<AutomationRuleResponse> {
  return api.put<AutomationRuleResponse>(`/api/automation-rules/${id}`, data);
}

export async function deleteRule(id: string): Promise<void> {
  return api.delete<void>(`/api/automation-rules/${id}`);
}

export async function toggleRule(
  id: string,
): Promise<AutomationRuleResponse> {
  return api.post<AutomationRuleResponse>(
    `/api/automation-rules/${id}/toggle`,
  );
}

export async function duplicateRule(
  id: string,
): Promise<AutomationRuleResponse> {
  return api.post<AutomationRuleResponse>(
    `/api/automation-rules/${id}/duplicate`,
  );
}

export async function testRule(
  id: string,
  sampleData: TestRuleRequest,
): Promise<TestRuleResponse> {
  return api.post<TestRuleResponse>(
    `/api/automation-rules/${id}/test`,
    sampleData,
  );
}

// -- Templates --

export async function listTemplates(): Promise<TemplateDefinitionResponse[]> {
  return api.get<TemplateDefinitionResponse[]>("/api/automation-templates");
}

export async function activateTemplate(
  slug: string,
): Promise<AutomationRuleResponse> {
  return api.post<AutomationRuleResponse>(
    `/api/automation-templates/${slug}/activate`,
  );
}

// -- Executions --

export async function listExecutions(params?: {
  ruleId?: string;
  status?: ExecutionStatus;
  page?: number;
  size?: number;
}): Promise<PaginatedResponse<AutomationExecutionResponse>> {
  const searchParams = new URLSearchParams();
  if (params?.ruleId) searchParams.set("ruleId", params.ruleId);
  if (params?.status) searchParams.set("status", params.status);
  if (params?.page !== undefined)
    searchParams.set("page", String(params.page));
  if (params?.size !== undefined)
    searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedResponse<AutomationExecutionResponse>>(
    `/api/automation-executions${qs ? `?${qs}` : ""}`,
  );
}

export async function getExecution(
  id: string,
): Promise<AutomationExecutionResponse> {
  return api.get<AutomationExecutionResponse>(
    `/api/automation-executions/${id}`,
  );
}

export async function getRuleExecutions(
  ruleId: string,
  params?: { page?: number; size?: number },
): Promise<PaginatedResponse<AutomationExecutionResponse>> {
  const searchParams = new URLSearchParams();
  if (params?.page !== undefined)
    searchParams.set("page", String(params.page));
  if (params?.size !== undefined)
    searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedResponse<AutomationExecutionResponse>>(
    `/api/automation-rules/${ruleId}/executions${qs ? `?${qs}` : ""}`,
  );
}

// -- Actions (stubs for future Epic 286+) --

export async function addAction(
  ruleId: string,
  data: Record<string, unknown>,
): Promise<AutomationActionResponse> {
  return api.post<AutomationActionResponse>(
    `/api/automation-rules/${ruleId}/actions`,
    data,
  );
}

export async function updateAction(
  ruleId: string,
  actionId: string,
  data: Record<string, unknown>,
): Promise<AutomationActionResponse> {
  return api.put<AutomationActionResponse>(
    `/api/automation-rules/${ruleId}/actions/${actionId}`,
    data,
  );
}

export async function deleteAction(
  ruleId: string,
  actionId: string,
): Promise<void> {
  return api.delete<void>(
    `/api/automation-rules/${ruleId}/actions/${actionId}`,
  );
}

export async function reorderActions(
  ruleId: string,
  actionIds: string[],
): Promise<void> {
  return api.post<void>(
    `/api/automation-rules/${ruleId}/actions/reorder`,
    { actionIds },
  );
}
