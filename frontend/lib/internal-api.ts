const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY;

export class InternalApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "InternalApiError";
  }
}

/**
 * Server-side API client for internal Spring Boot endpoints (/internal/*).
 * Uses X-API-KEY header instead of Bearer JWT.
 * Use only in API route handlers (e.g., webhook handler).
 */
export async function internalApiClient<T>(
  endpoint: string,
  options: {
    method?: "GET" | "POST" | "PUT" | "DELETE";
    body?: unknown;
    headers?: Record<string, string>;
  } = {},
): Promise<T> {
  if (!INTERNAL_API_KEY) {
    throw new InternalApiError(500, "INTERNAL_API_KEY not configured");
  }

  const response = await fetch(`${BACKEND_URL}${endpoint}`, {
    method: options.method || "POST",
    headers: {
      "Content-Type": "application/json",
      "X-API-KEY": INTERNAL_API_KEY,
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    throw new InternalApiError(
      response.status,
      `Internal API request failed: ${response.statusText}`,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
