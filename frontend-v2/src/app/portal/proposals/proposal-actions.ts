const TOKEN_KEY = "portal_token";

export type PortalProposalStatus = "SENT" | "ACCEPTED" | "DECLINED" | "EXPIRED";

export interface PortalProposalSummary {
  id: string;
  proposalNumber: string;
  title: string;
  status: PortalProposalStatus;
  feeModel: string;
  feeAmount: number | null;
  feeCurrency: string | null;
  sentAt: string | null;
}

export interface PortalProposalDetail {
  id: string;
  proposalNumber: string;
  title: string;
  status: PortalProposalStatus;
  feeModel: string;
  feeAmount: number | null;
  feeCurrency: string | null;
  contentHtml: string | null;
  milestonesJson: string | null;
  sentAt: string | null;
  expiresAt: string | null;
  orgName: string | null;
  orgLogoUrl: string | null;
  orgBrandColor: string | null;
}

export interface PortalAcceptResponse {
  proposalId: string;
  status: string;
  acceptedAt: string;
  projectName: string | null;
  message: string;
}

export interface PortalDeclineResponse {
  proposalId: string;
  status: string;
  declinedAt: string;
}

function getBackendUrl(): string {
  return process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
}

function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export async function listPortalProposals(): Promise<PortalProposalSummary[]> {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");

  const res = await fetch(`${getBackendUrl()}/portal/api/proposals`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      throw new Error("Session expired. Please sign in again.");
    }
    throw new Error("Failed to load proposals.");
  }

  return res.json();
}

export async function getPortalProposal(
  id: string,
): Promise<PortalProposalDetail> {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");

  const res = await fetch(`${getBackendUrl()}/portal/api/proposals/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      throw new Error("Session expired. Please sign in again.");
    }
    throw new Error("Failed to load proposal.");
  }

  return res.json();
}

export async function acceptProposal(
  id: string,
): Promise<PortalAcceptResponse> {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");

  const res = await fetch(
    `${getBackendUrl()}/portal/api/proposals/${id}/accept`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    },
  );

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      throw new Error("Session expired. Please sign in again.");
    }
    throw new Error("Failed to accept proposal.");
  }

  return res.json();
}

export async function declineProposal(
  id: string,
  reason?: string,
): Promise<PortalDeclineResponse> {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");

  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
  };

  let body: string | undefined;
  if (reason) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify({ reason });
  }

  const res = await fetch(
    `${getBackendUrl()}/portal/api/proposals/${id}/decline`,
    {
      method: "POST",
      headers,
      body,
    },
  );

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      throw new Error("Session expired. Please sign in again.");
    }
    throw new Error("Failed to decline proposal.");
  }

  return res.json();
}
