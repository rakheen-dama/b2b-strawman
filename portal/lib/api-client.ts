import { getJwt, clearAuth } from "@/lib/auth";

const BASE_URL = process.env.NEXT_PUBLIC_PORTAL_API_URL ?? "http://localhost:8080";

/**
 * Portal API fetch wrapper. Injects the JWT as a Bearer token.
 * On 401, clears auth (triggers useAuth re-render) and redirects to /login.
 */
export async function portalFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const jwt = getJwt();

  const headers = new Headers(options.headers);
  if (jwt) {
    headers.set("Authorization", `Bearer ${jwt}`);
  }
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    // clearAuth() emits change so useAuth hook updates immediately
    clearAuth();
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
    throw new Error("Unauthorized");
  }

  return response;
}

/**
 * Convenience helper: GET request that parses JSON response.
 * Throws on non-ok responses.
 */
export async function portalGet<T>(path: string): Promise<T> {
  const response = await portalFetch(path);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API error: ${response.status} ${body}`);
  }
  return response.json() as Promise<T>;
}

/**
 * Convenience helper: POST request with JSON body.
 * Returns the parsed JSON response.
 */
export async function portalPost<T>(path: string, body: unknown): Promise<T> {
  const response = await portalFetch(path, {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const respBody = await response.text();
    throw new Error(`API error: ${response.status} ${respBody}`);
  }
  return response.json() as Promise<T>;
}

/**
 * Unauthenticated fetch for public endpoints (branding, auth).
 * Does NOT inject JWT. Does NOT redirect on 401.
 */
export async function publicFetch(
  path: string,
  options: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }

  return fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });
}
