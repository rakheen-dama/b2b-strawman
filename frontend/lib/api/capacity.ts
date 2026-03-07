import "server-only";

import { api } from "@/lib/api";

// ---- Grid Types ----

export interface TeamCapacityGrid {
  members: MemberRow[];
  weekSummaries: WeekSummary[];
}

export interface MemberRow {
  memberId: string;
  memberName: string;
  avatarUrl: string | null;
  weeks: WeekCell[];
  totalAllocated: number;
  totalCapacity: number;
  avgUtilizationPct: number;
}

export interface WeekCell {
  weekStart: string;
  allocations: AllocationSlot[];
  totalAllocated: number;
  effectiveCapacity: number;
  remainingCapacity: number;
  utilizationPct: number;
  overAllocated: boolean;
  leaveDays: number;
}

export interface AllocationSlot {
  projectId: string;
  projectName: string;
  hours: number;
}

export interface WeekSummary {
  weekStart: string;
  teamTotalAllocated: number;
  teamTotalCapacity: number;
  teamUtilizationPct: number;
}

export interface ProjectStaffingResponse {
  projectId: string;
  projectName: string;
  members: StaffingMemberRow[];
  totalPlannedHours: number;
  budgetHours: number | null;
  budgetUsedPct: number | null;
}

export interface StaffingMemberRow {
  memberId: string;
  memberName: string;
  weeks: StaffingWeekCell[];
  totalAllocatedHours: number;
}

export interface StaffingWeekCell {
  weekStart: string;
  allocatedHours: number;
}

// ---- Allocation Types ----

export interface CreateAllocationRequest {
  memberId: string;
  projectId: string;
  weekStart: string;
  allocatedHours: number;
  note?: string;
}

export interface UpdateAllocationRequest {
  allocatedHours: number;
  note?: string;
}

export interface AllocationResponse {
  id: string;
  memberId: string;
  projectId: string;
  weekStart: string;
  allocatedHours: number;
  note: string | null;
  overAllocated: boolean;
  overageHours: number;
  createdAt: string;
}

export interface BulkAllocationRequest {
  allocations: CreateAllocationRequest[];
}

export interface BulkAllocationResponse {
  results: AllocationResultItem[];
}

export interface AllocationResultItem {
  allocation: AllocationResponse;
  created: boolean;
}

// ---- Capacity Types ----

export interface CreateCapacityRequest {
  weeklyHours: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
  note?: string;
}

export interface UpdateCapacityRequest {
  weeklyHours: number;
  effectiveTo?: string | null;
  note?: string;
}

export interface MemberCapacityResponse {
  id: string;
  memberId: string;
  weeklyHours: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  note: string | null;
  createdAt: string;
}

// ---- Leave Types ----

export interface CreateLeaveRequest {
  startDate: string;
  endDate: string;
  note?: string;
}

export interface UpdateLeaveRequest {
  startDate: string;
  endDate: string;
  note?: string;
}

export interface LeaveBlockResponse {
  id: string;
  memberId: string;
  startDate: string;
  endDate: string;
  note: string | null;
  createdBy: string;
  createdAt: string;
}

// ---- Utilization Types ----

export interface TeamUtilizationResponse {
  members: MemberUtilizationSummary[];
  teamAverages: TeamAverages;
}

export interface MemberUtilizationSummary {
  memberId: string;
  memberName: string;
  weeklyCapacity: number;
  totalPlannedHours: number;
  totalActualHours: number;
  totalBillableHours: number;
  avgPlannedUtilizationPct: number;
  avgActualUtilizationPct: number;
  avgBillableUtilizationPct: number;
  overAllocatedWeeks: number;
  weeks: WeekUtilization[];
}

export interface WeekUtilization {
  weekStart: string;
  effectiveCapacity: number;
  plannedHours: number;
  actualHours: number;
  billableActualHours: number;
  plannedUtilizationPct: number;
  actualUtilizationPct: number;
  billableUtilizationPct: number;
}

export interface TeamAverages {
  avgPlannedUtilizationPct: number;
  avgActualUtilizationPct: number;
  avgBillableUtilizationPct: number;
}

// ---- API Functions ----

export async function getTeamCapacityGrid(
  weekStart: string,
  weekEnd: string,
): Promise<TeamCapacityGrid> {
  return api.get<TeamCapacityGrid>(
    `/api/capacity/team?weekStart=${weekStart}&weekEnd=${weekEnd}`,
  );
}

export async function getMemberCapacityDetail(
  memberId: string,
  weekStart: string,
  weekEnd: string,
): Promise<MemberRow> {
  return api.get<MemberRow>(
    `/api/capacity/members/${memberId}?weekStart=${weekStart}&weekEnd=${weekEnd}`,
  );
}

export async function getProjectStaffing(
  projectId: string,
  weekStart: string,
  weekEnd: string,
): Promise<ProjectStaffingResponse> {
  return api.get<ProjectStaffingResponse>(
    `/api/capacity/projects/${projectId}?weekStart=${weekStart}&weekEnd=${weekEnd}`,
  );
}

export async function getTeamUtilization(
  weekStart: string,
  weekEnd: string,
): Promise<TeamUtilizationResponse> {
  return api.get<TeamUtilizationResponse>(
    `/api/utilization/team?weekStart=${weekStart}&weekEnd=${weekEnd}`,
  );
}

export async function getMemberUtilization(
  memberId: string,
  weekStart: string,
  weekEnd: string,
): Promise<WeekUtilization[]> {
  return api.get<WeekUtilization[]>(
    `/api/utilization/members/${memberId}?weekStart=${weekStart}&weekEnd=${weekEnd}`,
  );
}

export async function listCapacityRecords(
  memberId: string,
): Promise<MemberCapacityResponse[]> {
  return api.get<MemberCapacityResponse[]>(
    `/api/members/${memberId}/capacity`,
  );
}

export async function createCapacityRecord(
  memberId: string,
  data: CreateCapacityRequest,
): Promise<MemberCapacityResponse> {
  return api.post<MemberCapacityResponse>(
    `/api/members/${memberId}/capacity`,
    data,
  );
}

export async function updateCapacityRecord(
  memberId: string,
  id: string,
  data: UpdateCapacityRequest,
): Promise<MemberCapacityResponse> {
  return api.put<MemberCapacityResponse>(
    `/api/members/${memberId}/capacity/${id}`,
    data,
  );
}

export async function deleteCapacityRecord(
  memberId: string,
  id: string,
): Promise<void> {
  await api.delete(`/api/members/${memberId}/capacity/${id}`);
}

export async function listAllocations(params: {
  memberId?: string;
  projectId?: string;
  weekStart?: string;
  weekEnd?: string;
}): Promise<AllocationResponse[]> {
  const searchParams = new URLSearchParams();
  if (params.memberId) searchParams.set("memberId", params.memberId);
  if (params.projectId) searchParams.set("projectId", params.projectId);
  if (params.weekStart) searchParams.set("weekStart", params.weekStart);
  if (params.weekEnd) searchParams.set("weekEnd", params.weekEnd);
  const qs = searchParams.toString();
  return api.get<AllocationResponse[]>(
    `/api/resource-allocations${qs ? `?${qs}` : ""}`,
  );
}

export async function createAllocation(
  data: CreateAllocationRequest,
): Promise<AllocationResponse> {
  return api.post<AllocationResponse>("/api/resource-allocations", data);
}

export async function updateAllocation(
  id: string,
  data: UpdateAllocationRequest,
): Promise<AllocationResponse> {
  return api.put<AllocationResponse>(`/api/resource-allocations/${id}`, data);
}

export async function deleteAllocation(id: string): Promise<void> {
  await api.delete(`/api/resource-allocations/${id}`);
}

export async function bulkUpsertAllocations(
  data: BulkAllocationRequest,
): Promise<BulkAllocationResponse> {
  return api.post<BulkAllocationResponse>(
    "/api/resource-allocations/bulk",
    data,
  );
}

export async function listLeaveForMember(
  memberId: string,
): Promise<LeaveBlockResponse[]> {
  return api.get<LeaveBlockResponse[]>(`/api/members/${memberId}/leave`);
}

export async function createLeaveBlock(
  memberId: string,
  data: CreateLeaveRequest,
): Promise<LeaveBlockResponse> {
  return api.post<LeaveBlockResponse>(`/api/members/${memberId}/leave`, data);
}

export async function updateLeaveBlock(
  memberId: string,
  id: string,
  data: UpdateLeaveRequest,
): Promise<LeaveBlockResponse> {
  return api.put<LeaveBlockResponse>(
    `/api/members/${memberId}/leave/${id}`,
    data,
  );
}

export async function deleteLeaveBlock(
  memberId: string,
  id: string,
): Promise<void> {
  await api.delete(`/api/members/${memberId}/leave/${id}`);
}

export async function listAllLeave(
  startDate: string,
  endDate: string,
): Promise<LeaveBlockResponse[]> {
  return api.get<LeaveBlockResponse[]>(
    `/api/leave?startDate=${startDate}&endDate=${endDate}`,
  );
}
