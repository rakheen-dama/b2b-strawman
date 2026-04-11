/**
 * Client-side API client for portal proposals.
 * Uses portal JWT (from magic link exchange), NOT org auth.
 */

import { portalApi } from "@/lib/portal-api";
import type {
  PortalProposalSummary,
  PortalProposalDetail,
  PortalAcceptResponse,
  PortalDeclineResponse,
} from "@/lib/types/document";

// --- API Functions ---

export function listPortalProposals(): Promise<PortalProposalSummary[]> {
  return portalApi.get<PortalProposalSummary[]>("/portal/api/proposals");
}

export function getPortalProposal(id: string): Promise<PortalProposalDetail> {
  return portalApi.get<PortalProposalDetail>(`/portal/api/proposals/${id}`);
}

export function acceptProposal(id: string): Promise<PortalAcceptResponse> {
  return portalApi.post<PortalAcceptResponse>(`/portal/api/proposals/${id}/accept`);
}

export function declineProposal(id: string, reason?: string): Promise<PortalDeclineResponse> {
  return portalApi.post<PortalDeclineResponse>(
    `/portal/api/proposals/${id}/decline`,
    reason ? { reason } : undefined
  );
}
