"use server";

import { api } from "@/lib/api";
import { revalidatePath } from "next/cache";

// ---- Types ----

export type ProposalStatus =
  | "DRAFT"
  | "SENT"
  | "ACCEPTED"
  | "DECLINED"
  | "EXPIRED";
export type FeeModel = "FIXED" | "HOURLY" | "RETAINER";

export interface ProposalResponse {
  id: string;
  proposalNumber: string;
  title: string;
  customerId: string;
  portalContactId: string | null;
  status: ProposalStatus;
  feeModel: FeeModel;
  fixedFeeAmount: number | null;
  fixedFeeCurrency: string | null;
  hourlyRateNote: string | null;
  retainerAmount: number | null;
  retainerCurrency: string | null;
  retainerHoursIncluded: number | null;
  contentJson: Record<string, unknown> | null;
  projectTemplateId: string | null;
  sentAt: string | null;
  expiresAt: string | null;
  acceptedAt: string | null;
  declinedAt: string | null;
  declineReason: string | null;
  createdProjectId: string | null;
  createdRetainerId: string | null;
  createdById: string;
  createdAt: string;
  updatedAt: string;
}

export interface PaginatedProposals {
  content: ProposalResponse[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface ProposalStats {
  totalDraft: number;
  totalSent: number;
  totalAccepted: number;
  totalDeclined: number;
  totalExpired: number;
  conversionRate: number;
  averageDaysToAccept: number;
}

export interface ListProposalsParams {
  status?: ProposalStatus;
  customerId?: string;
  feeModel?: FeeModel;
  createdById?: string;
  page?: number;
  size?: number;
}

export interface CreateProposalData {
  title: string;
  customerId: string;
  feeModel: FeeModel;
  portalContactId?: string;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  hourlyRateNote?: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
  contentJson?: Record<string, unknown>;
  projectTemplateId?: string;
  expiresAt?: string;
}

export interface UpdateProposalData {
  title?: string;
  customerId?: string;
  portalContactId?: string;
  feeModel?: FeeModel;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  hourlyRateNote?: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
  contentJson?: Record<string, unknown>;
  projectTemplateId?: string;
  expiresAt?: string;
}

// ---- Additional Types for 237A ----

export interface MilestoneData {
  description: string;
  percentage: number;
  relativeDueDays: number;
}

export interface TeamMemberData {
  memberId: string;
  role: string;
}

export interface MilestoneResponse {
  id: string;
  description: string;
  percentage: number;
  relativeDueDays: number;
  sortOrder: number;
  invoiceId: string | null;
}

export interface TeamMemberResponse {
  id: string;
  memberId: string;
  memberName?: string;
  role: string | null;
  sortOrder: number;
}

export interface ProposalDetailResponse extends ProposalResponse {
  customerName?: string;
  portalContactName?: string;
  projectTemplateName?: string;
  milestones: MilestoneResponse[];
  teamMembers: TeamMemberResponse[];
  createdByName?: string;
}

export interface PortalContactSummary {
  id: string;
  displayName: string;
  email: string;
}

// ---- Actions ----

export async function listProposals(
  params?: ListProposalsParams,
): Promise<PaginatedProposals> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.customerId) qs.set("customerId", params.customerId);
  if (params?.feeModel) qs.set("feeModel", params.feeModel);
  if (params?.createdById) qs.set("createdById", params.createdById);
  if (params?.page !== undefined) qs.set("page", String(params.page));
  if (params?.size !== undefined) qs.set("size", String(params.size));
  const query = qs.toString();
  return api.get<PaginatedProposals>(
    `/api/proposals${query ? `?${query}` : ""}`,
  );
}

export async function getProposal(id: string): Promise<ProposalResponse> {
  return api.get<ProposalResponse>(`/api/proposals/${id}`);
}

export async function createProposal(
  data: CreateProposalData,
): Promise<ProposalResponse> {
  const result = await api.post<ProposalResponse>("/api/proposals", data);
  revalidatePath("/", "layout");
  return result;
}

export async function updateProposal(
  id: string,
  data: UpdateProposalData,
): Promise<ProposalResponse> {
  const result = await api.put<ProposalResponse>(
    `/api/proposals/${id}`,
    data,
  );
  revalidatePath("/", "layout");
  return result;
}

export async function deleteProposal(id: string): Promise<void> {
  await api.delete<void>(`/api/proposals/${id}`);
  revalidatePath("/", "layout");
}

export async function sendProposal(
  id: string,
  portalContactId: string,
): Promise<ProposalResponse> {
  const result = await api.post<ProposalResponse>(
    `/api/proposals/${id}/send`,
    { portalContactId },
  );
  revalidatePath("/", "layout");
  return result;
}

export async function withdrawProposal(
  id: string,
): Promise<ProposalResponse> {
  const result = await api.post<ProposalResponse>(
    `/api/proposals/${id}/withdraw`,
  );
  revalidatePath("/", "layout");
  return result;
}

export async function getProposalStats(): Promise<ProposalStats> {
  return api.get<ProposalStats>("/api/proposals/stats");
}

export async function listCustomerProposals(
  customerId: string,
  page?: number,
  size?: number,
): Promise<PaginatedProposals> {
  const qs = new URLSearchParams();
  if (page !== undefined) qs.set("page", String(page));
  if (size !== undefined) qs.set("size", String(size));
  const query = qs.toString();
  return api.get<PaginatedProposals>(
    `/api/customers/${customerId}/proposals${query ? `?${query}` : ""}`,
  );
}

export async function getProposalDetail(
  id: string,
): Promise<ProposalDetailResponse> {
  return api.get<ProposalDetailResponse>(`/api/proposals/${id}`);
}

export async function replaceMilestones(
  proposalId: string,
  milestones: MilestoneData[],
): Promise<void> {
  await api.put<void>(`/api/proposals/${proposalId}/milestones`, milestones);
  revalidatePath("/", "layout");
}

export async function replaceTeamMembers(
  proposalId: string,
  members: TeamMemberData[],
): Promise<void> {
  await api.put<void>(`/api/proposals/${proposalId}/team`, members);
  revalidatePath("/", "layout");
}

export async function getPortalContacts(
  customerId: string,
): Promise<PortalContactSummary[]> {
  try {
    return await api.get<PortalContactSummary[]>(
      `/api/customers/${customerId}/portal-contacts`,
    );
  } catch {
    return [];
  }
}

export async function fetchOrgMembers(): Promise<
  Array<{
    id: string;
    name: string;
    email: string;
    avatarUrl: string | null;
    orgRole: string;
  }>
> {
  return api.get("/api/members");
}
