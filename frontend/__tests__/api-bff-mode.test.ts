import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only (no-op)
vi.mock("server-only", () => ({}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  redirect: vi.fn(),
  notFound: vi.fn(),
}));

// Mock next/headers
const mockCookieGet = vi.fn();
vi.mock("next/headers", () => ({
  cookies: vi.fn().mockResolvedValue({
    get: (...args: unknown[]) => mockCookieGet(...args),
  }),
}));

// Mock AUTH_MODE as keycloak and stub getAuthToken
vi.mock("@/lib/auth", () => ({
  AUTH_MODE: "keycloak",
  getAuthToken: vi.fn().mockRejectedValue(
    new Error("getAuthToken() is not available in BFF mode"),
  ),
}));

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("API client in BFF (keycloak) mode", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default cookie values
    mockCookieGet.mockImplementation((name: string) => {
      if (name === "SESSION") return { value: "session-xyz" };
      if (name === "XSRF-TOKEN") return { value: "csrf-token-abc" };
      return undefined;
    });
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "content-length": "2" }),
      text: () => Promise.resolve("{}"),
    });
  });

  it("does not include Authorization header in requests", async () => {
    // Dynamic import to pick up mocked AUTH_MODE
    const { api } = await import("@/lib/api");
    await api.get("/api/projects");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers).not.toHaveProperty("Authorization");
  });

  it("sets credentials to include", async () => {
    const { api } = await import("@/lib/api");
    await api.get("/api/projects");

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.credentials).toBe("include");
  });

  it("forwards SESSION cookie as header", async () => {
    const { api } = await import("@/lib/api");
    await api.get("/api/projects");

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers.cookie).toBe("SESSION=session-xyz");
  });

  it("adds X-XSRF-TOKEN header on POST", async () => {
    const { api } = await import("@/lib/api");
    await api.post("/api/projects", { name: "Test" });

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers["X-XSRF-TOKEN"]).toBe("csrf-token-abc");
  });

  it("adds X-XSRF-TOKEN header on PUT", async () => {
    const { api } = await import("@/lib/api");
    await api.put("/api/projects/1", { name: "Updated" });

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers["X-XSRF-TOKEN"]).toBe("csrf-token-abc");
  });

  it("does not add X-XSRF-TOKEN header on GET", async () => {
    const { api } = await import("@/lib/api");
    await api.get("/api/projects");

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers).not.toHaveProperty("X-XSRF-TOKEN");
  });

  it("uses gateway URL as API base", async () => {
    const { api } = await import("@/lib/api");
    await api.get("/api/projects");

    const [url] = mockFetch.mock.calls[0];
    expect(url).toContain("localhost:8443");
    expect(url).not.toContain("localhost:8080");
  });

  it("adds X-XSRF-TOKEN header on DELETE", async () => {
    const { api } = await import("@/lib/api");
    await api.delete("/api/projects/1");

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers["X-XSRF-TOKEN"]).toBe("csrf-token-abc");
  });

  it("adds X-XSRF-TOKEN header on PATCH", async () => {
    const { api } = await import("@/lib/api");
    await api.patch("/api/projects/1", { name: "Patched" });

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers["X-XSRF-TOKEN"]).toBe("csrf-token-abc");
  });

  it("URL-decodes XSRF-TOKEN cookie value", async () => {
    mockCookieGet.mockImplementation((name: string) => {
      if (name === "SESSION") return { value: "session-xyz" };
      if (name === "XSRF-TOKEN") return { value: "token%3Dwith%26special" };
      return undefined;
    });

    const { api } = await import("@/lib/api");
    await api.post("/api/projects", { name: "Test" });

    const [, fetchOptions] = mockFetch.mock.calls[0];
    expect(fetchOptions.headers["X-XSRF-TOKEN"]).toBe("token=with&special");
  });
});
