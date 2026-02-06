import { auth } from "@clerk/nextjs/server";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

export interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  body?: unknown;
  cache?: RequestCache;
  headers?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Server-side API client for Spring Boot backend.
 * Automatically attaches Clerk JWT as Bearer token.
 * Use only in Server Components, Server Actions, or Route Handlers.
 */
export async function apiClient<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  const { getToken } = await auth();
  const token = await getToken();

  if (!token) {
    throw new ApiError(401, "No authentication token available");
  }

  const response = await fetch(`${BACKEND_URL}${endpoint}`, {
    method: options.method || "GET",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: options.cache,
  });

  if (!response.ok) {
    throw new ApiError(response.status, `API request failed: ${response.statusText}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
