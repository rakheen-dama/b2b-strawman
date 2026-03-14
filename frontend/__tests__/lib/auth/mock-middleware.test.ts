import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest, NextResponse } from "next/server";

// Helper: build a mock JWT with the given payload
function buildMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

const defaultPayload = {
  sub: "user_e2e_alice",
  o: { id: "org_e2e_test", slg: "e2e-test-org", rol: "owner" },
  iss: "http://mock-idp:8090",
  aud: "docteams-e2e",
  iat: 1708000000,
  exp: 1708086400,
  v: 2,
};

function createMockRequest(
  pathname: string,
  cookieToken?: string,
): NextRequest {
  const url = `http://localhost:3000${pathname}`;
  const request = new NextRequest(url);
  if (cookieToken) {
    request.cookies.set("mock-auth-token", cookieToken);
  }
  return request;
}

describe("Mock auth middleware", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  async function loadMiddleware(authMode: string) {
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", authMode);
    const mod = await import("@/lib/auth/middleware");
    return mod.createAuthMiddleware();
  }

  it("redirects to /mock-login when no cookie on protected route", async () => {
    const middleware = await loadMiddleware("mock");
    const request = createMockRequest("/org/e2e-test-org/dashboard");

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toContain("/mock-login");
    expect(location).toContain("redirect=%2Forg%2Fe2e-test-org%2Fdashboard");
  });

  it("passes through when valid cookie on protected route", async () => {
    const middleware = await loadMiddleware("mock");
    const token = buildMockJwt(defaultPayload);
    const request = createMockRequest(
      "/org/e2e-test-org/dashboard",
      token,
    );

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    // NextResponse.next() returns a 200
    expect(response.status).toBe(200);
  });

  it("passes through on public routes without cookie", async () => {
    const middleware = await loadMiddleware("mock");

    const publicPaths = ["/", "/sign-in", "/mock-login"];
    for (const path of publicPaths) {
      const request = createMockRequest(path);
      const response = (await middleware(
        request,
        {} as never,
      )) as NextResponse;

      expect(response.status).toBe(200);
    }
  });

  it("defaults to keycloak middleware when AUTH_MODE is not set", async () => {
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "");
    const mod = await import("@/lib/auth/middleware");
    const middleware = mod.createAuthMiddleware();

    // Protected route without SESSION cookie should redirect to gateway
    const request = createMockRequest("/org/acme-corp/dashboard");
    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toContain("/oauth2/authorization/keycloak");
  });

  it("redirects /dashboard to org-scoped dashboard with valid cookie", async () => {
    const middleware = await loadMiddleware("mock");
    const token = buildMockJwt(defaultPayload);
    const request = createMockRequest("/dashboard", token);

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toContain("/org/e2e-test-org/dashboard");
  });
});
