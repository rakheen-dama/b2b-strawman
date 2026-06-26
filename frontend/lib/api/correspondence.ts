import "server-only";

import { api } from "./client";

export interface CorrespondenceListItem {
  id: string;
  subject: string | null;
  fromAddress: string;
  receivedAt: string | null;
  attachmentCount: number;
  direction: "INBOUND" | "OUTBOUND";
}

export interface PaginatedCorrespondenceResponse {
  content: CorrespondenceListItem[];
  page: { totalElements: number; totalPages: number; size: number; number: number };
}

function buildQuery(params: { page?: number; size?: number }): string {
  const sp = new URLSearchParams();
  if (params.page !== undefined) sp.set("page", String(params.page));
  if (params.size !== undefined) sp.set("size", String(params.size));
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export async function getProjectCorrespondence(
  projectId: string,
  params: { page?: number; size?: number } = {}
): Promise<PaginatedCorrespondenceResponse> {
  return api.get<PaginatedCorrespondenceResponse>(
    `/api/projects/${projectId}/correspondence${buildQuery(params)}`
  );
}

export async function getCustomerCorrespondence(
  customerId: string,
  params: { page?: number; size?: number } = {}
): Promise<PaginatedCorrespondenceResponse> {
  return api.get<PaginatedCorrespondenceResponse>(
    `/api/customers/${customerId}/correspondence${buildQuery(params)}`
  );
}
