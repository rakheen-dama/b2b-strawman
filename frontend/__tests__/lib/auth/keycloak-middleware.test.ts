import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest, NextResponse } from "next/server";

function createRequest(
  pathname: string,
  sessionCookie?: string,
): NextRequest {
  const url = `http://localhost:3000${pathname}`;
  const request = new NextRequest(url);
  if (sessionCookie) {
    request.cookies.set("SESSION", sessionCookie);
  }
  return request;
}

describe("Keycloak BFF middleware", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_GATEWAY_URL", "http://localhost:8443");
  });

  async function loadMiddleware() {
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");
    const mod = await import("@/lib/auth/middleware");
    return mod.createAuthMiddleware();
  }

  it("redirects to gateway login when no SESSION cookie on protected route", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/org/acme-corp/dashboard");

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toBe(
      "http://localhost:8443/oauth2/authorization/keycloak",
    );
  });

  it("passes through when SESSION cookie exists on protected route", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest(
      "/org/acme-corp/dashboard",
      "session-abc-123",
    );

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    // NextResponse.next() returns a 200
    expect(response.status).toBe(200);
  });

  it("passes through on public routes without SESSION cookie", async () => {
    const middleware = await loadMiddleware();

    const publicPaths = ["/", "/sign-in", "/api/webhooks/clerk"];
    for (const path of publicPaths) {
      const request = createRequest(path);
      const response = (await middleware(
        request,
        {} as never,
      )) as NextResponse;

      expect(response.status).toBe(200);
    }
  });

  it("passes through /dashboard with SESSION cookie (server component resolves slug)", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/dashboard", "session-abc-123");

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    // Should pass through — no redirect from middleware
    expect(response.status).toBe(200);
  });

  it("passes through on portal routes without SESSION cookie", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/portal/some-token");

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(200);
  });
});
