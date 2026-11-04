import "server-only";

import { api } from "./client";

// ---- Response Interfaces ----

export interface MyCapabilitiesResponse {
  capabilities: string[];
  role: string;
  isAdmin: boolean;
  isOwner: boolean;
  /** The caller's members.id — what the backend compares for self-approval (630B). */
  memberId: string;
}

// ---- API Functions ----

export async function fetchMyCapabilities(): Promise<MyCapabilitiesResponse> {
  return api.get<MyCapabilitiesResponse>("/api/me/capabilities");
}
