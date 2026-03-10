import "server-only";

import { api } from "@/lib/api";

// ---- Response Interfaces ----

export interface MyCapabilitiesResponse {
  capabilities: string[];
  role: string;
  isAdmin: boolean;
  isOwner: boolean;
}

// ---- API Functions ----

export async function fetchMyCapabilities(): Promise<MyCapabilitiesResponse> {
  return api.get<MyCapabilitiesResponse>("/api/me/capabilities");
}
