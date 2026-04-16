// Core infrastructure
export {
  api,
  apiRequest,
  apiClient,
  ApiError,
  handleApiError,
  isSubscriptionError,
  getAuthFetchOptions,
  API_BASE,
} from "./client";

// Domain modules
export * from "./fields";
export * from "./tags";
export * from "./views";
export * from "./document-templates";
export * from "./settings";
export * from "./generated-documents";

// Existing domain modules
export * from "./capabilities";
export * from "./billing-runs";
export * from "./retainers";
export * from "./information-requests";
export * from "./automations";
export * from "./capacity";
export * from "./email";
export * from "./integrations";
export * from "./org-roles";
export * from "./portal-requests";
export * from "./reports";
export {
  type ScheduleStatus,
  type RecurrenceFrequency,
  type ScheduleResponse,
  type CreateScheduleRequest,
  type UpdateScheduleRequest,
  type ScheduleExecutionResponse,
  type ListSchedulesParams,
  getSchedules,
  getSchedule,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  pauseSchedule,
  resumeSchedule,
  getExecutions,
} from "./schedules";
export * from "./setup-status";
export * from "./templates";
export * from "./packs";
