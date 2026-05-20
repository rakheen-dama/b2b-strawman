// ---- OrgSettings (from OrgSettingsController.java) ----

import type { CompliancePackEntry } from "./customer";

export interface OrgSettings {
  orgName?: string | null;
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
  // data protection settings (Phase 50 — Epic 379A)
  dataProtectionJurisdiction?: string | null;
  retentionPolicyEnabled?: boolean;
  defaultRetentionMonths?: number | null;
  financialRetentionMonths?: number;
  informationOfficerName?: string | null;
  informationOfficerEmail?: string | null;
  // portal settings (Epic 496A / 498A / 498C — surfaced by firm settings UI)
  portalDigestCadence?: PortalDigestCadence;
  portalRetainerMemberDisplay?: PortalRetainerMemberDisplay;
}

// ---- Portal settings enums (shared between server actions + UI) ----

export type PortalDigestCadence = "WEEKLY" | "BIWEEKLY" | "OFF";
export type PortalRetainerMemberDisplay =
  | "FULL_NAME"
  | "FIRST_NAME_ROLE"
  | "ROLE_ONLY"
  | "ANONYMISED";

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

export type IntegrationDomain =
  | "ACCOUNTING"
  | "AI"
  | "DOCUMENT_SIGNING"
  | "EMAIL"
  | "KYC_VERIFICATION"
  | "PAYMENT";

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

// ---- AI Model Info (from IntegrationController /ai/models) ----

export interface ModelInfo {
  id: string;
  name: string;
  recommended: boolean;
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

// ---- Xero Integration (from XeroIntegrationController.java) ----

export type XeroConnectionStatus = "CONNECTED" | "TOKEN_EXPIRED" | "REVOKED";

export interface XeroConnectionResponse {
  id: string;
  xeroOrgName: string;
  status: XeroConnectionStatus;
  connectedAt: string;
  lastTokenRefreshAt: string | null;
  accessTokenExpiresAt: string;
  scope: string;
  lastPollAt: string | null;
}

export interface XeroConnectResponse {
  authorizationUrl: string;
  state: string;
}

export interface XeroTaxMapping {
  id: string;
  kaziTaxMode: string;
  externalTaxCode: string | null;
  displayLabel: string | null;
  provider: string;
}

export interface UpdateXeroTaxMappingRequest {
  externalTaxCode: string;
  displayLabel: string;
}

export interface XeroTaxRate {
  taxType: string;
  name: string;
  effectiveRate: number;
}

export interface XeroCustomerImportResult {
  created: number;
  skippedDuplicate: number;
  skippedNoEmail: number;
  total: number;
}

export type XeroPushTrigger = "APPROVED" | "SENT";

export interface XeroSettingsResponse {
  paymentPollIntervalMinutes: number;
  pushTrigger: XeroPushTrigger;
  autoSyncEnabled: boolean;
}

export interface UpdateXeroSettingsRequest {
  paymentPollIntervalMinutes: number;
  pushTrigger: XeroPushTrigger;
  autoSyncEnabled: boolean;
}

// ---- Sync (from AccountingSyncController.java) ----

export interface SyncSummaryResponse {
  pending: number;
  inFlight: number;
  completedLast24h: number;
  failedRetrying: number;
  deadLetter: number;
  blockedTrustBoundary: number;
  reconcileDrift: number;
  oldestPendingAt: string | null;
  lastCompletedAt: string | null;
}

export type SyncEntityType = "INVOICE" | "CUSTOMER";
export type SyncDirection = "PUSH" | "PULL";
export type SyncState =
  | "PENDING"
  | "IN_FLIGHT"
  | "COMPLETED"
  | "FAILED_RETRYING"
  | "DEAD_LETTER"
  | "BLOCKED_TRUST_BOUNDARY"
  | "RECONCILE_DRIFT";
export type SyncTrigger = "EVENT" | "MANUAL" | "FORCE_RESYNC" | "POLL";

export interface SyncEntryResponse {
  id: string;
  entityType: SyncEntityType;
  entityId: string;
  providerId: string;
  direction: SyncDirection;
  state: SyncState;
  attemptCount: number;
  externalReference: string | null;
  externalId: string | null;
  lastErrorCode: string | null;
  lastErrorDetail: string | null;
  trigger: SyncTrigger;
  createdAt: string;
  completedAt: string | null;
}
