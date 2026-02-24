import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Mock auth module
vi.mock("@/lib/auth", () => ({
  getJwt: vi.fn(),
  clearAuth: vi.fn(),
}));

import { getJwt, clearAuth } from "@/lib/auth";
const mockGetJwt = vi.mocked(getJwt);
const mockClearAuth = vi.mocked(clearAuth);

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Import after mocks are set up
import { portalFetch, portalGet, publicFetch } from "@/lib/api-client";

describe("api-client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetJwt.mockReturnValue(null);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("portalFetch adds Authorization header when JWT exists", async () => {
    mockGetJwt.mockReturnValue("test-jwt-token");
    mockFetch.mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));

    await portalFetch("/portal/projects");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, options] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8080/portal/projects");
    const headers = options.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer test-jwt-token");
  });

  it("portalFetch prepends base URL to path", async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));

    await portalFetch("/portal/projects");

    const [url] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8080/portal/projects");
  });

  it("portalFetch clears auth and redirects on 401", async () => {
    mockGetJwt.mockReturnValue("expired-token");
    mockFetch.mockResolvedValue(new Response("Unauthorized", { status: 401 }));

    // Mock window.location
    const originalLocation = window.location;
    Object.defineProperty(window, "location", {
      writable: true,
      value: { ...originalLocation, href: "" },
    });

    await expect(portalFetch("/portal/projects")).rejects.toThrow("Unauthorized");
    expect(mockClearAuth).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe("/login");

    // Restore
    Object.defineProperty(window, "location", {
      writable: true,
      value: originalLocation,
    });
  });

  it("publicFetch calls fetch without Authorization header", async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));

    await publicFetch("/portal/branding?orgId=org_abc");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, options] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:8080/portal/branding?orgId=org_abc");
    const headers = options.headers as Headers;
    expect(headers.has("Authorization")).toBe(false);
  });

  it("publicFetch sets Content-Type for requests with body", async () => {
    mockFetch.mockResolvedValue(new Response(JSON.stringify({}), { status: 200 }));

    await publicFetch("/portal/auth/request-link", {
      method: "POST",
      body: JSON.stringify({ email: "test@example.com" }),
    });

    const [, options] = mockFetch.mock.calls[0];
    const headers = options.headers as Headers;
    expect(headers.get("Content-Type")).toBe("application/json");
  });

  it("portalGet parses JSON response", async () => {
    mockGetJwt.mockReturnValue("valid-token");
    const payload = { id: "1", name: "Test Project" };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const result = await portalGet<{ id: string; name: string }>("/portal/projects/1");

    expect(result).toEqual(payload);
  });
});
