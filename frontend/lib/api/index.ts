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
export * from "./audit-events";
export * from "./capabilities";
export * from "./billing-runs";
export * from "./information-requests";
export * from "./automations";
export * from "./email";
export * from "./integrations";
export * from "./org-roles";
export * from "./reports";
export * from "./setup-status";
export * from "./templates";
export * from "./packs";
export * from "./legal-disbursements";
export * from "./statement-of-account";
export * from "./ai";
export * from "./collections";
