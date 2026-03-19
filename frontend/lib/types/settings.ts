// ---- OrgSettings (from OrgSettingsController.java) ----

import type { CompliancePackEntry } from "./customer";

export interface OrgSettings {
  defaultCurrency: string;
  logoUrl?: string;
  brandColor?: string;
  documentFooterText?: string;
  // compliance fields (may be absent from API response until backend adds them)
  dormancyThresholdDays?: number;
  dataRequestDeadlineDays?: number;
  compliancePackStatus?: CompliancePackEntry[];
  // integration feature flags
  accountingEnabled?: boolean;
  aiEnabled?: boolean;
  documentSigningEnabled?: boolean;
  // tax fields
  taxRegistrationNumber?: string;
  taxRegistrationLabel?: string;
  taxLabel?: string;
  taxInclusive?: boolean;
  // acceptance fields
  acceptanceExpiryDays?: number;
  // time tracking reminder fields
  timeReminderEnabled?: boolean;
  timeReminderDays?: string;
  timeReminderTime?: string;
  timeReminderMinHours?: number;
  // expense defaults
  defaultExpenseMarkupPercent?: number | null;
  // information request defaults
  defaultRequestReminderDays?: number;
  // capacity defaults
  defaultWeeklyCapacityHours?: number;
  // batch billing defaults
  billingBatchAsyncThreshold?: number;
  billingEmailRateLimit?: number;
  defaultBillingRunCurrency?: string | null;
  // project naming
  projectNamingPattern?: string | null;
  // vertical profile for terminology overrides
  verticalProfile?: string | null;
  // vertical architecture: module gating + i18n namespace
  enabledModules?: string[];
  terminologyNamespace?: string | null;
}

export interface UpdateBatchBillingSettingsRequest {
  billingBatchAsyncThreshold: number;
  billingEmailRateLimit: number;
  defaultBillingRunCurrency?: string | null;
}

export interface UpdateTaxSettingsRequest {
  taxRegistrationNumber?: string;
  taxRegistrationLabel?: string;
  taxLabel?: string;
  taxInclusive: boolean;
}

export interface UpdateOrgSettingsRequest {
  defaultCurrency: string;
  brandColor?: string;
  documentFooterText?: string;
}

export interface UpdateTimeTrackingSettingsRequest {
  timeReminderEnabled: boolean;
  timeReminderDays: string;
  timeReminderTime: string;
  timeReminderMinHours: number;
  defaultExpenseMarkupPercent?: number | null;
}

// ---- Integrations (from IntegrationController.java) ----

export type IntegrationDomain = "ACCOUNTING" | "AI" | "DOCUMENT_SIGNING" | "EMAIL" | "PAYMENT";

export interface OrgIntegration {
  domain: IntegrationDomain;
  providerSlug: string | null;
  enabled: boolean;
  keySuffix: string | null;
  configJson: string | null;
  updatedAt: string | null;
}

export interface ConnectionTestResult {
  success: boolean;
  providerName: string;
  errorMessage: string | null;
}

export interface UpsertIntegrationRequest {
  providerSlug: string;
  configJson?: string;
}

export interface SetApiKeyRequest {
  apiKey: string;
}

export interface ToggleIntegrationRequest {
  enabled: boolean;
}

// ---- Payment Provider Configs (parsed from OrgIntegration.configJson) ----

export interface StripePaymentConfig {
  webhookSigningSecret?: string;
}

export interface PayFastPaymentConfig {
  merchantId?: string;
  merchantKey?: string;
  sandbox?: boolean;
}
