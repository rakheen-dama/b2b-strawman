import "server-only";

import { api } from "./client";

// ---- Enums ----

export type DealStatus = "OPEN" | "WON" | "LOST";
export type StageType = "OPEN" | "WON" | "LOST";
export type ProposalStatus = "DRAFT" | "SENT" | "ACCEPTED" | "DECLINED" | "EXPIRED";
export type FeeModel = "FIXED" | "HOURLY" | "RETAINER" | "CONTINGENCY";

// ---- Stage DTOs ----

export interface StageDto {
  id: string;
  name: string;
  position: number;
  defaultProbabilityPct: number;
  stageType: StageType;
  archived: boolean;
}

export interface CreateStageRequest {
  name: string;
  position: number;
  defaultProbabilityPct: number;
  stageType: StageType;
}

export interface UpdateStageRequest {
  name: string;
  defaultProbabilityPct: number;
  stageType: StageType;
}

export interface ReorderStagesRequest {
  positions: { id: string; position: number }[];
}

// ---- Deal DTOs ----

export interface DealResponse {
  id: string;
  dealNumber: string;
  customerId: string;
  title: string;
  stageId: string;
  stageName: string | null;
  status: DealStatus;
  valueAmount: number | null;
  valueCurrency: string;
  probabilityPct: number | null;
  effectiveProbabilityPct: number;
  weightedValue: number | null;
  expectedCloseDate: string | null;
  ownerId: string | null;
  source: string | null;
  wonAt: string | null;
  lostAt: string | null;
  lostReason: string | null;
  customFields: Record<string, unknown> | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDealRequest {
  customerId: string;
  title: string;
  stageId?: string;
  valueAmount?: number;
  ownerId?: string;
  source?: string;
  expectedCloseDate?: string;
}

export interface IntakeRequest {
  customerId?: string;
  customer?: { name: string; email?: string; phone?: string };
  title: string;
  stageId?: string;
  valueAmount?: number;
  ownerId?: string;
  source?: string;
  expectedCloseDate?: string;
}

export interface DealUpdateRequest {
  title?: string;
  valueAmount?: number;
  valueCurrency?: string;
  ownerId?: string;
  expectedCloseDate?: string;
  probabilityOverride?: number;
  source?: string;
  customFields?: Record<string, unknown>;
}

export interface TransitionRequest {
  targetStageId: string;
  probabilityOverride?: number;
  lostReason?: string;
}

export interface CreateDealProposalRequest {
  title: string;
  feeModel: FeeModel;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
}

export interface LinkedProposalDto {
  id: string;
  proposalNumber: string;
  status: ProposalStatus;
  amount: number | null;
}

// ---- Pipeline summary ----

export interface PipelineStageBreakdown {
  stageId: string;
  stageName: string;
  dealCount: number;
  totalValue: number;
  weightedValue: number;
}

export interface PipelineSummaryResponse {
  openWeightedValue: number;
  currency: string;
  winRate: number;
  windowFrom: string;
  windowTo: string;
  averageDealSize: number;
  averageDaysToClose: number | null;
  stages: PipelineStageBreakdown[];
}

// ---- List / pagination ----

export interface ListDealsParams {
  stageId?: string;
  ownerId?: string;
  customerId?: string;
  status?: DealStatus;
  source?: string;
  fromDate?: string;
  toDate?: string;
  tags?: string[]; // tag slugs — backend ANDs them (deal must carry ALL)
  view?: string; // saved-view UUID
  page?: number;
  size?: number;
  sort?: string;
}

export interface Page<T> {
  content: T[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// ---- Pipeline stage config functions ----

export async function getStages(): Promise<StageDto[]> {
  return api.get<StageDto[]>("/api/pipeline/stages");
}

export async function createStage(req: CreateStageRequest): Promise<StageDto> {
  return api.post<StageDto>("/api/pipeline/stages", req);
}

export async function updateStage(id: string, req: UpdateStageRequest): Promise<StageDto> {
  return api.put<StageDto>(`/api/pipeline/stages/${id}`, req);
}

export async function reorderStages(req: ReorderStagesRequest): Promise<StageDto[]> {
  return api.put<StageDto[]>("/api/pipeline/stages/reorder", req);
}

export async function archiveStage(id: string): Promise<StageDto> {
  return api.post<StageDto>(`/api/pipeline/stages/${id}/archive`);
}

export async function deleteStage(id: string): Promise<void> {
  await api.delete(`/api/pipeline/stages/${id}`);
}

// ---- Deal functions ----

export async function listDeals(params: ListDealsParams = {}): Promise<Page<DealResponse>> {
  const qs = new URLSearchParams();
  if (params.stageId) qs.set("stageId", params.stageId);
  if (params.ownerId) qs.set("ownerId", params.ownerId);
  if (params.customerId) qs.set("customerId", params.customerId);
  if (params.status) qs.set("status", params.status);
  if (params.source) qs.set("source", params.source);
  if (params.fromDate) qs.set("fromDate", params.fromDate);
  if (params.toDate) qs.set("toDate", params.toDate);
  if (params.tags && params.tags.length > 0) qs.set("tags", params.tags.join(","));
  if (params.view) qs.set("view", params.view);
  if (params.page != null) qs.set("page", String(params.page));
  if (params.size != null) qs.set("size", String(params.size));
  if (params.sort) qs.set("sort", params.sort);
  const q = qs.toString();
  return api.get<Page<DealResponse>>(`/api/deals${q ? `?${q}` : ""}`);
}

export async function getDeal(id: string): Promise<DealResponse> {
  return api.get<DealResponse>(`/api/deals/${id}`);
}

export async function createDeal(req: CreateDealRequest): Promise<DealResponse> {
  return api.post<DealResponse>("/api/deals", req);
}

export async function intakeDeal(req: IntakeRequest): Promise<DealResponse> {
  return api.post<DealResponse>("/api/deals/intake", req);
}

export async function updateDeal(id: string, req: DealUpdateRequest): Promise<DealResponse> {
  return api.put<DealResponse>(`/api/deals/${id}`, req);
}

export async function deleteDeal(id: string): Promise<void> {
  await api.delete(`/api/deals/${id}`);
}

export async function transitionDeal(id: string, req: TransitionRequest): Promise<DealResponse> {
  return api.post<DealResponse>(`/api/deals/${id}/transition`, req);
}

// ---- Pipeline summary function ----

export async function pipelineSummary(
  params: { from?: string; to?: string; ownerId?: string } = {}
): Promise<PipelineSummaryResponse> {
  const qs = new URLSearchParams();
  if (params.from) qs.set("from", params.from);
  if (params.to) qs.set("to", params.to);
  if (params.ownerId) qs.set("ownerId", params.ownerId);
  const q = qs.toString();
  return api.get<PipelineSummaryResponse>(`/api/dashboard/pipeline-summary${q ? `?${q}` : ""}`);
}

// ---- Deal ↔ proposal functions ----

export async function listDealProposals(dealId: string): Promise<LinkedProposalDto[]> {
  return api.get<LinkedProposalDto[]>(`/api/deals/${dealId}/proposals`);
}

export async function createDealProposal(
  dealId: string,
  req: CreateDealProposalRequest
): Promise<LinkedProposalDto> {
  return api.post<LinkedProposalDto>(`/api/deals/${dealId}/proposals`, req);
}

export async function linkDealProposal(dealId: string, proposalId: string): Promise<void> {
  await api.post(`/api/deals/${dealId}/proposals/${proposalId}/link`);
}
