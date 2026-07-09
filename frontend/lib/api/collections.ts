import "server-only";

import { api } from "./client";

// ---- Collections Settings Types (588C) ----
// 591B extends this file with debtors/activities types — keep sections additive.

export interface CollectionsSettingsResponse {
  collectionsEnabled: boolean;
  stage1DaysOverdue: number;
  stage2DaysOverdue: number;
  stage3DaysOverdue: number;
  escalateDaysOverdue: number;
}

export interface UpdateCollectionsSettingsRequest {
  collectionsEnabled: boolean;
  stage1DaysOverdue: number;
  stage2DaysOverdue: number;
  stage3DaysOverdue: number;
  escalateDaysOverdue: number;
}

export interface CollectionsExemptionResponse {
  id: string;
  collectionsExempt: boolean;
}

// ---- Collections Settings API Functions (588C) ----

export async function getCollectionsSettings(): Promise<CollectionsSettingsResponse> {
  return api.get<CollectionsSettingsResponse>("/api/settings/collections");
}

export async function updateCollectionsSettings(
  dto: UpdateCollectionsSettingsRequest
): Promise<CollectionsSettingsResponse> {
  return api.put<CollectionsSettingsResponse>("/api/settings/collections", dto);
}

// ---- Customer Exemption API Functions (588C) ----

export async function setCollectionsExemption(
  customerId: string,
  exempt: boolean
): Promise<CollectionsExemptionResponse> {
  return api.put<CollectionsExemptionResponse>(
    `/api/customers/${customerId}/collections-exemption`,
    { collectionsExempt: exempt }
  );
}

// ---- Debtors / Activities (591B — do not implement here) ----
