import "server-only";

import { api } from "./client";

// ---- Types ----

export interface AiProfileResponse {
  id: string;
  practiceAreas: string[];
  jurisdiction: string;
  riskCalibration: string;
  houseStyleNotes: string | null;
  ficaRequirements: Record<string, unknown> | null;
  feeEstimationNotes: string | null;
  preferredModel: string;
  monthlyBudgetCents: number | null;
  profileVersion: number;
  coldStartCompleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateAiProfileRequest {
  practiceAreas: string[];
  jurisdiction: string;
  riskCalibration: string;
  houseStyleNotes?: string | null;
  ficaRequirements?: Record<string, unknown> | null;
  feeEstimationNotes?: string | null;
  preferredModel: string;
  monthlyBudgetCents?: number | null;
  coldStartCompleted?: boolean;
}

export interface AiCostSummaryResponse {
  currentMonthSpentCents: number;
  monthlyBudgetCents: number | null;
  invocationCount: number;
  remainingBudgetCents: number | null;
  periodStart: string;
  periodEnd: string;
}

// ---- Gate Types ----

export interface AiGateListItem {
  id: string;
  gateType: string;
  status: string;
  aiReasoning: string;
  expiresAt: string;
  createdAt: string;
  executionId: string;
}

export interface AiGateDetail {
  id: string;
  gateType: string;
  status: string;
  proposedAction: Record<string, unknown>;
  aiReasoning: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  expiresAt: string;
  createdAt: string;
  executionId: string;
}

export interface PaginatedGatesResponse {
  content: AiGateListItem[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// ---- Execution Types ----

export interface AiExecutionListItem {
  id: string;
  skillId: string;
  entityType: string;
  entityId: string;
  status: string;
  inputSummary: string | null;
  model: string;
  inputTokens: number;
  outputTokens: number;
  costCents: number;
  durationMs: number | null;
  createdAt: string;
}

export interface AiExecutionDetail {
  id: string;
  skillId: string;
  entityType: string;
  entityId: string;
  status: string;
  inputSummary: string | null;
  outputContent: string | null;
  model: string;
  inputTokens: number;
  outputTokens: number;
  cacheReadInputTokens: number;
  cacheCreationInputTokens: number;
  costCents: number;
  durationMs: number | null;
  invokedBy: string;
  firmProfileVersion: number | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface PaginatedExecutionsResponse {
  content: AiExecutionListItem[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

// ---- API Functions ----

export async function getAiProfile(): Promise<AiProfileResponse> {
  return api.get<AiProfileResponse>("/api/ai/profile");
}

export async function updateAiProfile(data: UpdateAiProfileRequest): Promise<AiProfileResponse> {
  return api.put<AiProfileResponse>("/api/ai/profile", data);
}

export async function getAiCostSummary(): Promise<AiCostSummaryResponse> {
  return api.get<AiCostSummaryResponse>("/api/ai/cost-summary");
}

// ---- Gate API Functions ----

export async function getAiGates(params: {
  status?: string;
  gateType?: string;
  page?: number;
  size?: number;
}): Promise<PaginatedGatesResponse> {
  const searchParams = new URLSearchParams();
  if (params.status) searchParams.set("status", params.status);
  if (params.gateType) searchParams.set("gateType", params.gateType);
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedGatesResponse>(`/api/ai/gates${qs ? `?${qs}` : ""}`);
}

export async function getAiGate(id: string): Promise<AiGateDetail> {
  return api.get<AiGateDetail>(`/api/ai/gates/${id}`);
}

export async function approveAiGate(id: string, notes?: string): Promise<AiGateDetail> {
  return api.post<AiGateDetail>(`/api/ai/gates/${id}/approve`, notes ? { notes } : {});
}

export async function rejectAiGate(id: string, notes?: string): Promise<AiGateDetail> {
  return api.post<AiGateDetail>(`/api/ai/gates/${id}/reject`, notes ? { notes } : {});
}

// ---- Batch Approve Types ----

export interface GateDisposition {
  gateId: string;
  outcome: string; // "APPROVED_EXECUTED" | "FAILED"
  error: string | null;
}

export interface BatchApproveResponse {
  results: GateDisposition[];
}

export async function batchApproveAiGates(
  gateIds: string[],
  notes?: string
): Promise<BatchApproveResponse> {
  return api.post<BatchApproveResponse>(
    "/api/ai/gates/batch-approve",
    notes ? { gateIds, notes } : { gateIds }
  );
}

// ---- Execution API Functions ----

export async function getAiExecutions(params: {
  skillId?: string;
  status?: string;
  page?: number;
  size?: number;
}): Promise<PaginatedExecutionsResponse> {
  const searchParams = new URLSearchParams();
  if (params.skillId) searchParams.set("skillId", params.skillId);
  if (params.status) searchParams.set("status", params.status);
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  const qs = searchParams.toString();
  return api.get<PaginatedExecutionsResponse>(`/api/ai/executions${qs ? `?${qs}` : ""}`);
}

export async function getAiExecution(id: string): Promise<AiExecutionDetail> {
  return api.get<AiExecutionDetail>(`/api/ai/executions/${id}`);
}

// ---- FICA Verification Types ----

export interface ChecklistReviewItem {
  checklistItemId: string;
  itemName: string;
  status: "SATISFIED" | "UNSATISFIED" | "PARTIAL" | "REQUIRES_REVIEW";
  evidenceDocument: string | null;
  reasoning: string;
  flags: string[];
}

export interface RecommendedAction {
  action: "MARK_ITEMS_COMPLETE" | "REQUEST_ADDITIONAL_DOCUMENT";
  items: string[];
  reasoning: string;
}

export interface FicaVerificationOutput {
  overallAssessment: "COMPLETE" | "INCOMPLETE" | "NEEDS_REVIEW";
  riskLevel: "LOW" | "MEDIUM" | "HIGH";
  checklistReview: ChecklistReviewItem[];
  missingDocuments: string[];
  riskFlags: string[];
  recommendedActions: RecommendedAction[];
}

export interface FicaVerificationResponse {
  executionId: string;
  status: "COMPLETED" | "FAILED";
  output: FicaVerificationOutput | null;
  gates: AiGateListItem[];
  costCents: number;
  model: string;
  durationMs: number;
}

// ---- FICA Verification API Function ----

export async function invokeFicaVerification(
  customerId: string
): Promise<FicaVerificationResponse> {
  return api.post<FicaVerificationResponse>("/api/ai/skills/fica-verification", { customerId });
}

// ---- Matter Intake Types ----

export interface MatterClassification {
  recommendedType: string;
  confidence: number;
  reasoning: string;
}

export interface TemplateRecommendation {
  templateId: string;
  templateName: string;
  reasoning: string;
  customisationNotes: string | null;
}

export interface RequiredDocument {
  documentType: string;
  reasoning: string;
  priority: "HIGH" | "MEDIUM" | "LOW";
}

export interface FeeEstimate {
  tariffBasis: string;
  estimatedRangeMinCents: number;
  estimatedRangeMaxCents: number;
  reasoning: string;
  assumptions: string[];
}

export interface ConflictMatch {
  existingMatterName: string;
  customerName: string;
  matchType: string;
  reasoning: string;
}

export interface ConflictScreening {
  status: "CLEAR" | "POTENTIAL_CONFLICT" | "CONFLICT_DETECTED";
  matches: ConflictMatch[];
}

export interface MatterIntakeOutput {
  matterClassification: MatterClassification;
  templateRecommendation: TemplateRecommendation | null;
  requiredDocuments: RequiredDocument[];
  feeEstimate: FeeEstimate | null;
  conflictScreening: ConflictScreening;
  riskFlags: string[];
}

export interface MatterIntakeResponse {
  executionId: string;
  status: "COMPLETED" | "FAILED";
  output: MatterIntakeOutput | null;
  gates: AiGateListItem[];
  costCents: number;
  model: string;
  durationMs: number;
}

// ---- Matter Intake API Function ----

export async function invokeMatterIntake(
  customerId: string,
  description: string
): Promise<MatterIntakeResponse> {
  return api.post<MatterIntakeResponse>("/api/ai/skills/matter-intake", {
    customerId,
    description,
  });
}

// ---- Drafting Types ----

export interface VariableFill {
  variableName: string;
  value: string | null;
  source: string;
  confidence: "HIGH" | "MEDIUM" | "LOW" | "UNDETERMINED";
  flag: string | null;
}

export interface NarrativeSection {
  sectionName: string;
  content: string;
  notes: string | null;
}

export interface ClauseRecommendation {
  clauseId: string;
  clauseName: string;
  reasoning: string;
}

export interface DraftingRecommendedAction {
  action: string;
  reasoning: string;
}

export interface DraftingOutput {
  templateId: string;
  variableFills: VariableFill[];
  narrativeSections: NarrativeSection[];
  clauseRecommendations: ClauseRecommendation[];
  warnings: string[];
  recommendedActions: DraftingRecommendedAction[];
}

export interface DraftingResponse {
  executionId: string;
  status: "COMPLETED" | "FAILED";
  output: DraftingOutput | null;
  gates: AiGateListItem[];
  costCents: number;
  model: string;
  durationMs: number;
}

// ---- Contract Review Types ----

export interface DocumentClassification {
  type: string;
  subtype: string;
  partiesIdentified: string[];
}

export interface ContractReviewFinding {
  severity: "HIGH" | "MEDIUM" | "LOW";
  category: string;
  clauseReference: string | null;
  title: string;
  description: string;
  riskExplanation: string | null;
  recommendation: string | null;
  statutoryReference: string | null;
}

export interface MissingProtection {
  protection: string;
  reasoning: string;
  recommendation: string | null;
  priority: string | null;
}

export interface ContractReviewRecommendedAction {
  action: string;
  reasoning: string;
}

export interface ContractReviewOutput {
  documentClassification: DocumentClassification;
  executiveSummary: string;
  findings: ContractReviewFinding[];
  missingProtections: MissingProtection[];
  overallRiskAssessment: string;
  recommendedActions: ContractReviewRecommendedAction[];
}

export interface ContractReviewResponse {
  executionId: string;
  status: "COMPLETED" | "FAILED";
  output: ContractReviewOutput | null;
  gates: AiGateListItem[];
  costCents: number;
  model: string;
  durationMs: number;
}

// ---- Contract Review API Function ----

export async function invokeContractReview(
  documentId: string,
  projectId: string
): Promise<ContractReviewResponse> {
  // The backend returns `output` as a parsed JSON object (the markdown fence is stripped
  // server-side), so no client-side JSON.parse is needed — consistent with the other skills.
  return api.post<ContractReviewResponse>("/api/ai/skills/contract-review", {
    documentId,
    projectId,
  });
}

// ---- Drafting API Function ----

export async function invokeDrafting(
  templateId: string,
  projectId: string
): Promise<DraftingResponse> {
  // The backend returns `output` as a parsed JSON object (the markdown fence is stripped
  // server-side), so no client-side JSON.parse is needed — consistent with the other skills.
  return api.post<DraftingResponse>("/api/ai/skills/drafting", {
    templateId,
    projectId,
  });
}
