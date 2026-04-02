import "server-only";

import { getAuthToken, AUTH_MODE } from "@/lib/auth";
import { redirect } from "next/navigation";
import { notFound } from "next/navigation";
import type { ProblemDetail } from "@/lib/types";

const GATEWAY_URL = process.env.GATEWAY_URL || "http://localhost:8443";
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
export const API_BASE = AUTH_MODE === "keycloak" ? GATEWAY_URL : BACKEND_URL;

/**
 * Get auth headers and fetch options for the current auth mode.
 * In BFF mode: forwards SESSION cookie, adds CSRF token for mutations.
 * In mock mode: adds Bearer token.
 */
export async function getAuthFetchOptions(method: string = "GET"): Promise<{
  headers: Record<string, string>;
  credentials?: RequestCredentials;
}> {
  if (AUTH_MODE === "keycloak") {
    const headers: Record<string, string> = {};
    // Server-side: forward cookies
    const { cookies } = await import("next/headers");
    const cookieStore = await cookies();
    const sessionCookie = cookieStore.get("SESSION");
    if (sessionCookie) {
      headers["cookie"] = `SESSION=${sessionCookie.value}`;
    }
    // Add CSRF for mutations
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
      const csrfCookie = cookieStore.get("XSRF-TOKEN");
      if (csrfCookie) {
        headers["X-XSRF-TOKEN"] = decodeURIComponent(csrfCookie.value);
      }
    }
    return { headers, credentials: "include" as RequestCredentials };
  }

  // Mock mode: Bearer token
  const token = await getAuthToken();
  return {
    headers: { Authorization: `Bearer ${token}` },
  };
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detail?: ProblemDetail
  ) {
    super(message);
    this.name = "ApiError";
  }
}

interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  body?: unknown;
  cache?: RequestCache;
  next?: NextFetchRequestConfig;
  headers?: Record<string, string>;
}

/**
 * Server-side API client for Spring Boot backend (or gateway in BFF mode).
 * In Mock mode: attaches JWT as Bearer token.
 * In keycloak (BFF) mode: forwards SESSION cookie, adds CSRF for mutations.
 * Use only in Server Components, Server Actions, or Route Handlers.
 */
export async function apiRequest<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  const method = options.method || "GET";

  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions(method);
  } catch {
    redirect("/sign-in");
  }

  const response = await fetch(`${API_BASE}${endpoint}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...authOptions.headers,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
    cache: options.cache,
    next: options.next,
    credentials: authOptions.credentials,
    redirect: "manual",
  });

  // Detect redirect responses (e.g., gateway redirecting to login)
  if (response.status >= 300 && response.status < 400) {
    throw new ApiError(401, "Authentication session expired");
  }

  if (!response.ok) {
    let detail: ProblemDetail | undefined;
    let message = response.statusText;

    try {
      const contentType = response.headers.get("content-type");
      if (
        contentType?.includes("application/json") ||
        contentType?.includes("application/problem+json")
      ) {
        detail = await response.json();
        message = detail?.detail || detail?.title || message;
      } else {
        message = (await response.text()) || message;
      }
    } catch {
      // Failed to parse error body — use statusText
    }

    throw new ApiError(response.status, message, detail);
  }

  const contentLength = response.headers.get("content-length");
  if (response.status === 204 || contentLength === "0") {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

/**
 * Detects whether an ApiError is a subscription-related 403.
 * Returns "required" when the org needs a subscription to perform the action,
 * "locked" when the subscription is fully locked (e.g. SUSPENDED/LOCKED),
 * or null for regular permission-based 403s.
 *
 * Extracted as a pure function for testability (client.ts is server-only).
 */
export function isSubscriptionError(
  error: unknown,
): "required" | "locked" | null {
  if (
    error instanceof ApiError &&
    error.status === 403 &&
    error.detail?.type
  ) {
    if (error.detail.type === "subscription_required") return "required";
    if (error.detail.type === "subscription_locked") return "locked";
  }
  return null;
}

/**
 * Handles ApiError by performing appropriate navigation for
 * known HTTP error statuses. Call this in catch blocks of
 * server components and server actions.
 *
 * - 401 → redirect to sign-in
 * - 403 with subscription_required/subscription_locked → re-throw with
 *   descriptive message (client-side error handlers detect via detail.type)
 * - 404 → trigger Next.js not-found page
 * - All others → re-throws the error
 */
export function handleApiError(error: unknown): never {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      redirect("/sign-in");
    }

    // Subscription-related 403s: re-throw with a user-friendly message
    // while preserving detail.type for client-side detection
    const subscriptionKind = isSubscriptionError(error);
    if (subscriptionKind === "locked") {
      throw new ApiError(
        403,
        "Your subscription is inactive. Please subscribe to regain access.",
        error.detail,
      );
    }
    if (subscriptionKind === "required") {
      throw new ApiError(
        403,
        "A subscription is required to perform this action.",
        error.detail,
      );
    }

    if (error.status === 404) {
      notFound();
    }
  }
  throw error;
}

export const api = {
  get: <T>(endpoint: string, options?: Omit<ApiRequestOptions, "method" | "body">) =>
    apiRequest<T>(endpoint, { ...options, method: "GET" }),

  post: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "POST", body }),

  put: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "PUT", body }),

  patch: <T>(
    endpoint: string,
    body?: unknown,
    options?: Omit<ApiRequestOptions, "method" | "body">
  ) => apiRequest<T>(endpoint, { ...options, method: "PATCH", body }),

  delete: <T>(endpoint: string, options?: Omit<ApiRequestOptions, "method" | "body">) =>
    apiRequest<T>(endpoint, { ...options, method: "DELETE" }),
};

// Backward-compatible alias for existing code
export { apiRequest as apiClient };
