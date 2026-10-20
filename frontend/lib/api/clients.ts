/**
 * Clients API module — typed wrappers for the backend `/api/clients/**` endpoints
 * (Phase 87 S4, slice 610A; ADR-337 wire vocabulary).
 *
 * NOT the HTTP client: `./client.ts` (singular) is the fetch wrapper (`api.get/post/…`,
 * `ApiError`, auth headers) that every lib/api module builds on. This file (plural) only knows
 * URLs and payload types for the Clients bounded context (Open Question #16).
 *
 * `clientPath()` / `CLIENTS_PATH` are re-exported from `./paths.ts` — the single place the
 * `clients` wire segment is spelled (shared with `tags.ts#entityApiPrefix`); other lib/api modules
 * (checklists, collections, correspondence, information-requests) switch to it in 610C.
 * The tag / field-group storage key for clients stays `"CUSTOMER"` (ADR-342).
 */
import "server-only";

import { api } from "./client";
import { getEntityTags, setEntityTags } from "./tags";
import { setEntityFieldGroups } from "./fields";
import { CLIENTS_PATH, clientPath } from "./paths";
import { toQuery, type QueryValue } from "./query";
import type {
  AggregatedCompletenessResponse,
  AnonymizationPreview,
  Client,
  ClientMatter,
  ClientProfitabilityResponse,
  ClientReadiness,
  CompletenessScore,
  CreateClientRequest,
  FicaStatus,
  FieldDefinitionResponse,
  LifecycleHistoryEntry,
  LifecycleStatus,
  Matter,
  PortalContactSummary,
  SetEntityTagsRequest,
  StandaloneAnonymizationResult,
  StandaloneExportResult,
  TagResponse,
  TransitionResponse,
  UnbilledTimeResponse,
  UnbilledTimeSummary,
  UpdateClientRequest,
  UploadInitRequest,
  UploadInitResponse,
} from "@/lib/types";

// ---- Path helpers ----

export { CLIENTS_PATH, clientPath } from "./paths";

// ---- Params / response types ----

/** `GET /api/clients` filters. Extra keys pass through (`allParams` on the controller). */
export interface ListClientsParams {
  view?: string;
  lifecycleStatus?: LifecycleStatus;
  status?: string;
  size?: number;
  [filter: string]: QueryValue;
}

export interface ClientDateRange {
  from?: string;
  to?: string;
}

export interface DormancyCheckResult {
  thresholdDays: number;
  candidates: Array<{
    clientId: string;
    clientName: string;
    lastActivityDate: string | null;
    daysSinceActivity: number;
  }>;
}

export interface AnonymizeClientRequest {
  confirmationName: string;
  reason: string;
}

// ---- Core CRUD ----

export async function listClients(params?: ListClientsParams): Promise<Client[]> {
  return api.get<Client[]>(`${CLIENTS_PATH}${toQuery(params)}`);
}

export async function getClient(clientId: string): Promise<Client> {
  return api.get<Client>(clientPath(clientId));
}

export async function createClient(req: CreateClientRequest): Promise<Client> {
  return api.post<Client>(CLIENTS_PATH, req);
}

export async function updateClient(clientId: string, req: UpdateClientRequest): Promise<Client> {
  return api.put<Client>(clientPath(clientId), req);
}

// ---- Lifecycle ----

/** `DELETE /api/clients/{id}` archives (soft) and returns the archived client. */
export async function archiveClient(clientId: string): Promise<Client> {
  return api.delete<Client>(clientPath(clientId));
}

export async function unarchiveClient(clientId: string): Promise<Client> {
  return api.post<Client>(clientPath(clientId, "unarchive"));
}

export async function transitionClientLifecycle(
  clientId: string,
  targetStatus: string,
  notes?: string
): Promise<TransitionResponse> {
  return api.post<TransitionResponse>(clientPath(clientId, "transition"), {
    targetStatus,
    notes: notes ?? null,
  });
}

export async function getClientLifecycleHistory(
  clientId: string
): Promise<LifecycleHistoryEntry[]> {
  return api.get<LifecycleHistoryEntry[]>(clientPath(clientId, "lifecycle"));
}

export async function getClientLifecycleSummary(): Promise<Record<string, number>> {
  return api.get<Record<string, number>>(`${CLIENTS_PATH}/lifecycle-summary`);
}

export async function runClientDormancyCheck(): Promise<DormancyCheckResult> {
  return api.post<DormancyCheckResult>(`${CLIENTS_PATH}/dormancy-check`);
}

// ---- Readiness / compliance read models ----

export async function getClientReadiness(clientId: string): Promise<ClientReadiness> {
  return api.get<ClientReadiness>(clientPath(clientId, "readiness"));
}

export async function getClientFicaStatus(clientId: string): Promise<FicaStatus> {
  return api.get<FicaStatus>(clientPath(clientId, "fica-status"));
}

export async function getClientCompletenessSummary(
  clientIds: string[]
): Promise<Record<string, CompletenessScore>> {
  const search = new URLSearchParams();
  clientIds.forEach((id) => search.append("clientIds", id));
  return api.get<Record<string, CompletenessScore>>(
    `${CLIENTS_PATH}/completeness-summary?${search.toString()}`
  );
}

export async function getAggregatedClientCompleteness(): Promise<AggregatedCompletenessResponse> {
  return api.get<AggregatedCompletenessResponse>(`${CLIENTS_PATH}/completeness-summary/aggregated`);
}

export async function listClientPortalContacts(clientId: string): Promise<PortalContactSummary[]> {
  return api.get<PortalContactSummary[]>(clientPath(clientId, "portal-contacts"));
}

// ---- Nested: linked matters ----

export async function listClientMatters(clientId: string): Promise<Matter[]> {
  return api.get<Matter[]>(clientPath(clientId, "matters"));
}

export async function linkClientMatter(clientId: string, matterId: string): Promise<ClientMatter> {
  return api.post<ClientMatter>(clientPath(clientId, `matters/${matterId}`));
}

export async function unlinkClientMatter(clientId: string, matterId: string): Promise<void> {
  return api.delete<void>(clientPath(clientId, `matters/${matterId}`));
}

// ---- Tags + field groups (delegate to the generic EntityType helpers; key "CUSTOMER", ADR-342) ----

export async function getClientTags(clientId: string): Promise<TagResponse[]> {
  return getEntityTags("CUSTOMER", clientId);
}

export async function setClientTags(
  clientId: string,
  req: SetEntityTagsRequest
): Promise<TagResponse[]> {
  return setEntityTags("CUSTOMER", clientId, req);
}

export async function setClientFieldGroups(
  clientId: string,
  appliedFieldGroups: string[]
): Promise<FieldDefinitionResponse[]> {
  return setEntityFieldGroups("CUSTOMER", clientId, appliedFieldGroups);
}

// ---- Billing read models ----

export async function getClientUnbilledSummary(clientId: string): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(clientPath(clientId, "unbilled-summary"));
}

export async function getClientUnbilledTime(
  clientId: string,
  range?: ClientDateRange
): Promise<UnbilledTimeResponse> {
  return api.get<UnbilledTimeResponse>(
    `${clientPath(clientId, "unbilled-time")}${toQuery({ ...range })}`
  );
}

export async function getClientProfitability(
  clientId: string,
  range?: ClientDateRange
): Promise<ClientProfitabilityResponse> {
  return api.get<ClientProfitabilityResponse>(
    `${clientPath(clientId, "profitability")}${toQuery({ ...range })}`
  );
}

// ---- Nested: documents ----

export async function initClientDocumentUpload(
  clientId: string,
  req: UploadInitRequest
): Promise<UploadInitResponse> {
  return api.post<UploadInitResponse>(clientPath(clientId, "documents/upload-init"), req);
}

// ---- Data protection (POPIA) ----

export async function exportClientData(clientId: string): Promise<StandaloneExportResult> {
  return api.post<StandaloneExportResult>(clientPath(clientId, "data-export"));
}

export async function getClientAnonymizationPreview(
  clientId: string
): Promise<AnonymizationPreview> {
  return api.get<AnonymizationPreview>(clientPath(clientId, "anonymize/preview"));
}

export async function anonymizeClient(
  clientId: string,
  req: AnonymizeClientRequest
): Promise<StandaloneAnonymizationResult> {
  return api.post<StandaloneAnonymizationResult>(clientPath(clientId, "anonymize"), req);
}
