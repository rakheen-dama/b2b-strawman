import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest, NextResponse } from "next/server";

// Mock @clerk/nextjs/server before any imports that use it
const mockClerkMiddleware = vi.fn(() => vi.fn());
const mockCreateRouteMatcher = vi.fn((routes: string[]) => {
  // Simple public route matcher for testing
  return (request: NextRequest) => {
    const pathname = request.nextUrl.pathname;
    return routes.some((route) => {
      const pattern = route.replace("(.*)", ".*");
      return new RegExp(`^${pattern}$`).test(pathname);
    });
  };
});

vi.mock("@clerk/nextjs/server", () => ({
  clerkMiddleware: mockClerkMiddleware,
  createRouteMatcher: mockCreateRouteMatcher,
}));

function createMockRequest(
  pathname: string,
  cookies?: Record<string, string>,
): NextRequest {
  const url = `http://localhost:3000${pathname}`;
  const request = new NextRequest(url);
  if (cookies) {
    for (const [name, value] of Object.entries(cookies)) {
      request.cookies.set(name, value);
    }
  }
  return request;
}

describe("Keycloak auth middleware", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  async function loadMiddleware(authMode: string) {
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", authMode);
    const mod = await import("@/lib/auth/middleware");
    return mod.createAuthMiddleware();
  }

  it("redirects to /api/auth/signin when no session cookie on protected route", async () => {
    const middleware = await loadMiddleware("keycloak");
    const request = createMockRequest("/org/my-org/dashboard");

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toContain("/api/auth/signin");
    expect(location).toContain(
      "callbackUrl=%2Forg%2Fmy-org%2Fdashboard",
    );
  });

  it("passes through when next-auth.session-token cookie exists on protected route", async () => {
    const middleware = await loadMiddleware("keycloak");
    const request = createMockRequest("/org/my-org/dashboard", {
      "next-auth.session-token": "encrypted-session-value",
    });

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(200);
  });

  it("passes through when __Secure-next-auth.session-token cookie exists", async () => {
    const middleware = await loadMiddleware("keycloak");
    const request = createMockRequest("/org/my-org/dashboard", {
      "__Secure-next-auth.session-token": "encrypted-session-value",
    });

    const response = (await middleware(
      request,
      {} as never,
    )) as NextResponse;

    expect(response.status).toBe(200);
  });

  it("passes through on public routes without cookie", async () => {
    const middleware = await loadMiddleware("keycloak");

    const publicPaths = ["/", "/sign-in", "/portal/something"];
    for (const path of publicPaths) {
      const request = createMockRequest(path);
      const response = (await middleware(
        request,
        {} as never,
      )) as NextResponse;

      expect(response.status).toBe(200);
    }
  });

  it("passes through on /api/auth routes without cookie", async () => {
    const middleware = await loadMiddleware("keycloak");

    const authPaths = [
      "/api/auth/signin",
      "/api/auth/callback/keycloak",
      "/api/auth/signout",
    ];
    for (const path of authPaths) {
      const request = createMockRequest(path);
      const response = (await middleware(
        request,
        {} as never,
      )) as NextResponse;

      expect(response.status).toBe(200);
    }
  });

  it("returns keycloak middleware when AUTH_MODE is keycloak", async () => {
    const middleware = await loadMiddleware("keycloak");

    // Should NOT be the Clerk middleware
    expect(mockClerkMiddleware).not.toHaveBeenCalled();

    // Should be a function (the keycloak middleware handler)
    expect(typeof middleware).toBe("function");
  });
});
