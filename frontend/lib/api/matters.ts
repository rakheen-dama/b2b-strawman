/**
 * Matters API module — typed wrappers for the backend `/api/matters/**` endpoints
 * (Phase 87 S4, slice 610A; ADR-337 wire vocabulary).
 *
 * NOT the HTTP client: `./client.ts` (singular) is the fetch wrapper (`api.get/post/…`,
 * `ApiError`, auth headers) that this module builds on. This file only knows URLs and payload
 * types for the Matters bounded context. Clients live in `./clients.ts` (plural).
 *
 * `matterPath()` / `MATTERS_PATH` are re-exported from `./paths.ts` — the single place the
 * `matters` wire segment is spelled (shared with `tags.ts#entityApiPrefix` and the sibling
 * closure / statements / correspondence / information-requests modules, which build URLs with
 * `matterPath()`). Guarded by `__tests__/lib/api/no-inline-matter-client-literals.test.ts`.
 * The tag / field-group storage key for matters stays `"PROJECT"` (ADR-342).
 */
import "server-only";

import { api } from "./client";
import { getEntityTags, setEntityTags } from "./tags";
import { setEntityFieldGroups } from "./fields";
import { MATTERS_PATH, matterPath } from "./paths";
import { toQuery, type QueryValue } from "./query";
import type {
  ActivityResponse,
  AdversePartyLink,
  BudgetStatusResponse,
  Client,
  ClientMatter,
  Comment,
  CreateExpenseRequest,
  CreateMatterRequest,
  CreateTaskRequest,
  Document,
  ExpenseResponse,
  FieldDefinitionResponse,
  LightweightBudgetStatus,
  Matter,
  MatterListRow,
  MatterMember,
  MatterProfitabilityResponse,
  MatterSetupStatus,
  MatterTimeSummary,
  MatterUpcomingDeadline,
  MemberTimeSummary,
  PaginatedExpenseResponse,
  SetEntityTagsRequest,
  TagResponse,
  Task,
  TaskTimeSummary,
  UnbilledTimeSummary,
  UpdateExpenseRequest,
  UpdateMatterRequest,
  UploadInitRequest,
  UploadInitResponse,
  UpsertBudgetRequest,
} from "@/lib/types";
import type {
  MatterHealthDetail,
  MemberHoursEntry,
  TaskSummaryResponse,
} from "@/lib/dashboard-types";

// ---- Path helpers ----

export { MATTERS_PATH, matterPath } from "./paths";

// ---- Params / request types ----

/**
 * `GET /api/matters` filters. The backend still reads the legacy `customerId` query param
 * (ADR-337 renamed bodies and paths, not query params). Extra keys are passed through as
 * custom-field filters (`allParams` on the controller).
 */
export interface ListMattersParams {
  view?: string;
  status?: string;
  dueBefore?: string;
  customerId?: string;
  size?: number;
  /** Caller is a member of the matter. */
  mine?: boolean | string;
  /** Unbilled billable minutes > 0 (disbursement-only matters excluded — 615A follow-up). */
  hasUnbilled?: boolean | string;
  /** ISO yyyy-MM-dd. Resolve relative tokens ("-90d") first — the backend 400s on them (R-3). */
  inactiveSince?: string;
  /** CSV of tag slugs; all must match. Ignored by the backend on the saved-view path. */
  tags?: string;
  [customFieldFilter: string]: QueryValue;
}

export interface MatterDateRange {
  from?: string;
  to?: string;
}

export interface ListMatterTasksParams {
  status?: string;
  assigneeId?: string;
  priority?: string;
  view?: string;
  recurring?: boolean;
}

export interface ListMatterExpensesParams extends MatterDateRange {
  category?: string;
  memberId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface MatterActivityParams {
  entityType?: string;
  page?: number;
  size?: number;
}

export interface ListMatterCommentsParams {
  entityType: string;
  entityId?: string;
  size?: number;
}

export interface CreateMatterCommentRequest {
  entityType: string;
  entityId: string;
  body: string;
  visibility?: string;
}

export interface UpdateMatterCommentRequest {
  body: string;
  visibility?: string;
}

// ---- Core CRUD ----

export async function listMatters(params?: ListMattersParams): Promise<Matter[]> {
  return api.get<Matter[]>(`${MATTERS_PATH}${toQuery(params)}`);
}

/**
 * Same endpoint as {@link listMatters}, typed as the dense list projection the Matters table
 * renders (615A `MatterListRow`). `listMatters` keeps its `Matter[]` type for its other consumers.
 */
export async function listMatterRows(params?: ListMattersParams): Promise<MatterListRow[]> {
  return api.get<MatterListRow[]>(`${MATTERS_PATH}${toQuery(params)}`);
}

export async function getMatter(matterId: string): Promise<Matter> {
  return api.get<Matter>(matterPath(matterId));
}

export async function createMatter(req: CreateMatterRequest): Promise<Matter> {
  return api.post<Matter>(MATTERS_PATH, req);
}

export async function updateMatter(matterId: string, req: UpdateMatterRequest): Promise<Matter> {
  return api.put<Matter>(matterPath(matterId), req);
}

export async function deleteMatter(matterId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId));
}

// ---- Lifecycle (PATCH) ----
// Closure (CLOSED, gates, closure letter) and closure-reopen live in `./matter-closure.ts`.

export async function completeMatter(
  matterId: string,
  opts?: { acknowledgeUnbilledTime?: boolean }
): Promise<Matter> {
  const body = opts?.acknowledgeUnbilledTime ? { acknowledgeUnbilledTime: true } : undefined;
  return api.patch<Matter>(matterPath(matterId, "complete"), body);
}

export async function archiveMatter(matterId: string): Promise<Matter> {
  return api.patch<Matter>(matterPath(matterId, "archive"));
}

/**
 * `PATCH /api/matters/{id}/reopen` — COMPLETED/ARCHIVED → ACTIVE. Named `reactivateMatter`
 * because `reopenMatter` is already the closure-reopen in `./matter-closure.ts`.
 */
export async function reactivateMatter(matterId: string): Promise<Matter> {
  return api.patch<Matter>(matterPath(matterId, "reopen"));
}

// ---- Tags + field groups (delegate to the generic EntityType helpers; key "PROJECT", ADR-342) ----

export async function getMatterTags(matterId: string): Promise<TagResponse[]> {
  return getEntityTags("PROJECT", matterId);
}

export async function setMatterTags(
  matterId: string,
  req: SetEntityTagsRequest
): Promise<TagResponse[]> {
  return setEntityTags("PROJECT", matterId, req);
}

export async function setMatterFieldGroups(
  matterId: string,
  appliedFieldGroups: string[]
): Promise<FieldDefinitionResponse[]> {
  return setEntityFieldGroups("PROJECT", matterId, appliedFieldGroups);
}

// ---- Read models ----

export async function getMatterSetupStatus(matterId: string): Promise<MatterSetupStatus> {
  return api.get<MatterSetupStatus>(matterPath(matterId, "setup-status"));
}

export async function getMatterUnbilledSummary(matterId: string): Promise<UnbilledTimeSummary> {
  return api.get<UnbilledTimeSummary>(matterPath(matterId, "unbilled-summary"));
}

export async function getMatterUpcomingDeadlines(
  matterId: string
): Promise<MatterUpcomingDeadline[]> {
  return api.get<MatterUpcomingDeadline[]>(matterPath(matterId, "upcoming-deadlines"));
}

export async function getMatterHealth(matterId: string): Promise<MatterHealthDetail> {
  return api.get<MatterHealthDetail>(matterPath(matterId, "health"));
}

export async function getMatterTaskSummary(matterId: string): Promise<TaskSummaryResponse> {
  return api.get<TaskSummaryResponse>(matterPath(matterId, "task-summary"));
}

export async function getMatterMemberHours(
  matterId: string,
  range: { from: string; to: string }
): Promise<MemberHoursEntry[]> {
  return api.get<MemberHoursEntry[]>(
    `${matterPath(matterId, "member-hours")}${toQuery({ ...range })}`
  );
}

export async function getMatterActivity(
  matterId: string,
  params?: MatterActivityParams
): Promise<ActivityResponse> {
  return api.get<ActivityResponse>(`${matterPath(matterId, "activity")}${toQuery({ ...params })}`);
}

export async function getMatterProfitability(
  matterId: string,
  range?: MatterDateRange
): Promise<MatterProfitabilityResponse> {
  return api.get<MatterProfitabilityResponse>(
    `${matterPath(matterId, "profitability")}${toQuery({ ...range })}`
  );
}

// ---- Time summaries ----

export async function getMatterTimeSummary(
  matterId: string,
  range?: MatterDateRange
): Promise<MatterTimeSummary> {
  return api.get<MatterTimeSummary>(
    `${matterPath(matterId, "time-summary")}${toQuery({ ...range })}`
  );
}

export async function getMatterTimeSummaryByMember(
  matterId: string,
  range?: MatterDateRange
): Promise<MemberTimeSummary[]> {
  return api.get<MemberTimeSummary[]>(
    `${matterPath(matterId, "time-summary/by-member")}${toQuery({ ...range })}`
  );
}

export async function getMatterTimeSummaryByTask(
  matterId: string,
  range?: MatterDateRange
): Promise<TaskTimeSummary[]> {
  return api.get<TaskTimeSummary[]>(
    `${matterPath(matterId, "time-summary/by-task")}${toQuery({ ...range })}`
  );
}

// ---- Nested: tasks ----

export async function listMatterTasks(
  matterId: string,
  params?: ListMatterTasksParams
): Promise<Task[]> {
  return api.get<Task[]>(`${matterPath(matterId, "tasks")}${toQuery({ ...params })}`);
}

export async function createMatterTask(matterId: string, req: CreateTaskRequest): Promise<Task> {
  return api.post<Task>(matterPath(matterId, "tasks"), req);
}

// ---- Nested: documents ----

export async function listMatterDocuments(matterId: string): Promise<Document[]> {
  return api.get<Document[]>(matterPath(matterId, "documents"));
}

export async function initMatterDocumentUpload(
  matterId: string,
  req: UploadInitRequest
): Promise<UploadInitResponse> {
  return api.post<UploadInitResponse>(matterPath(matterId, "documents/upload-init"), req);
}

// ---- Nested: members ----

export async function listMatterMembers(matterId: string): Promise<MatterMember[]> {
  return api.get<MatterMember[]>(matterPath(matterId, "members"));
}

export async function addMatterMember(matterId: string, memberId: string): Promise<void> {
  return api.post<void>(matterPath(matterId, "members"), { memberId });
}

export async function removeMatterMember(matterId: string, memberId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId, `members/${memberId}`));
}

export async function transferMatterLead(matterId: string, memberId: string): Promise<void> {
  return api.put<void>(matterPath(matterId, `members/${memberId}/role`), { role: "lead" });
}

// ---- Nested: linked clients ----

export async function listMatterClients(matterId: string): Promise<Client[]> {
  return api.get<Client[]>(matterPath(matterId, "clients"));
}

export async function linkMatterClient(matterId: string, clientId: string): Promise<ClientMatter> {
  return api.post<ClientMatter>(matterPath(matterId, `clients/${clientId}`));
}

export async function unlinkMatterClient(matterId: string, clientId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId, `clients/${clientId}`));
}

// ---- Nested: budget ----

export async function getMatterBudget(matterId: string): Promise<BudgetStatusResponse> {
  return api.get<BudgetStatusResponse>(matterPath(matterId, "budget"));
}

export async function getMatterBudgetStatus(matterId: string): Promise<LightweightBudgetStatus> {
  return api.get<LightweightBudgetStatus>(matterPath(matterId, "budget/status"));
}

export async function upsertMatterBudget(
  matterId: string,
  req: UpsertBudgetRequest
): Promise<BudgetStatusResponse> {
  return api.put<BudgetStatusResponse>(matterPath(matterId, "budget"), req);
}

export async function deleteMatterBudget(matterId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId, "budget"));
}

// ---- Nested: comments ----

export async function listMatterComments(
  matterId: string,
  params: ListMatterCommentsParams
): Promise<Comment[]> {
  return api.get<Comment[]>(`${matterPath(matterId, "comments")}${toQuery({ ...params })}`);
}

export async function createMatterComment(
  matterId: string,
  req: CreateMatterCommentRequest
): Promise<Comment> {
  return api.post<Comment>(matterPath(matterId, "comments"), req);
}

export async function updateMatterComment(
  matterId: string,
  commentId: string,
  req: UpdateMatterCommentRequest
): Promise<Comment> {
  return api.put<Comment>(matterPath(matterId, `comments/${commentId}`), req);
}

export async function deleteMatterComment(matterId: string, commentId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId, `comments/${commentId}`));
}

// ---- Nested: expenses (page removed in 615E, ADR-340; endpoints still live) ----

export async function listMatterExpenses(
  matterId: string,
  params?: ListMatterExpensesParams
): Promise<PaginatedExpenseResponse> {
  return api.get<PaginatedExpenseResponse>(
    `${matterPath(matterId, "expenses")}${toQuery({ ...params })}`
  );
}

export async function createMatterExpense(
  matterId: string,
  req: CreateExpenseRequest
): Promise<ExpenseResponse> {
  return api.post<ExpenseResponse>(matterPath(matterId, "expenses"), req);
}

export async function updateMatterExpense(
  matterId: string,
  expenseId: string,
  req: UpdateExpenseRequest
): Promise<ExpenseResponse> {
  return api.put<ExpenseResponse>(matterPath(matterId, `expenses/${expenseId}`), req);
}

export async function deleteMatterExpense(matterId: string, expenseId: string): Promise<void> {
  return api.delete<void>(matterPath(matterId, `expenses/${expenseId}`));
}

export async function writeOffMatterExpense(
  matterId: string,
  expenseId: string
): Promise<ExpenseResponse> {
  return api.patch<ExpenseResponse>(matterPath(matterId, `expenses/${expenseId}/write-off`), {});
}

export async function restoreMatterExpense(
  matterId: string,
  expenseId: string
): Promise<ExpenseResponse> {
  return api.patch<ExpenseResponse>(matterPath(matterId, `expenses/${expenseId}/restore`), {});
}

// ---- Nested: legal ----

export async function listMatterAdverseParties(matterId: string): Promise<AdversePartyLink[]> {
  return api.get<AdversePartyLink[]>(matterPath(matterId, "adverse-parties"));
}
