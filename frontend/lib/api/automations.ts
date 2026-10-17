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
  | "INFORMATION_REQUEST_COMPLETED"
  | "FIELD_DATE_APPROACHING"
  | "SCHEDULED";

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

export type ActionExecutionStatus = "PENDING" | "COMPLETED" | "FAILED" | "SKIPPED";

export type RuleSource = "MANUAL" | "TEMPLATE";

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
// Phase 86 (ADR-334): the rule editor and its write endpoints are gone; only the always-on
// list / toggle / execution reads remain.

// -- Rules --

export async function listRules(params?: {
  enabled?: boolean;
  triggerType?: TriggerType;
}): Promise<AutomationRuleResponse[]> {
  const searchParams = new URLSearchParams();
  if (params?.enabled !== undefined) searchParams.set("enabled", String(params.enabled));
  if (params?.triggerType) searchParams.set("triggerType", params.triggerType);
  const qs = searchParams.toString();
  return api.get<AutomationRuleResponse[]>(`/api/automation-rules${qs ? `?${qs}` : ""}`);
}

export async function toggleRule(id: string): Promise<AutomationRuleResponse> {
  return api.post<AutomationRuleResponse>(`/api/automation-rules/${id}/toggle`);
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
  if (params?.page !== undefined) searchParams.set("page", String(params.page));
  if (params?.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedResponse<AutomationExecutionResponse>>(
    `/api/automation-executions${qs ? `?${qs}` : ""}`
  );
}
