import "server-only";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY;

export class InternalApiError extends Error {
  constructor(
    public status: number,
    public statusText: string,
    public body?: string
  ) {
    super(`Internal API request failed: ${status} ${statusText}`);
    this.name = "InternalApiError";
  }
}

export interface ProvisionOrgRequest {
  clerkOrgId: string;
  orgName: string;
}

export interface ProvisionOrgResponse {
  clerkOrgId: string;
  schemaName: string;
  status: string;
}

export interface UpdateOrgRequest {
  clerkOrgId: string;
  orgName: string;
  updatedAt: number;
}

export interface SyncMemberRequest {
  clerkOrgId: string;
  clerkUserId: string;
  email: string;
  name?: string;
  avatarUrl?: string;
  orgRole: string;
}

/**
 * Server-only API client for Spring Boot internal endpoints.
 * Authenticates via X-API-KEY header (not JWT).
 * Use only in server contexts (Route Handlers, Server Actions).
 */
export async function internalApiClient<T>(
  endpoint: string,
  options: {
    method?: "GET" | "POST" | "PUT" | "DELETE";
    body?: unknown;
  } = {}
): Promise<T> {
  if (!INTERNAL_API_KEY) {
    throw new Error("INTERNAL_API_KEY environment variable is not set");
  }

  const response = await fetch(`${BACKEND_URL}${endpoint}`, {
    method: options.method || "POST",
    headers: {
      "Content-Type": "application/json",
      "X-API-KEY": INTERNAL_API_KEY,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    const body = await response.text().catch(() => undefined);
    throw new InternalApiError(response.status, response.statusText, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
