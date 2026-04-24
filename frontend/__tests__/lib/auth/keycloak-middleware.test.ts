import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { NextRequest, NextResponse } from "next/server";

function createRequest(
  pathname: string,
  sessionCookie?: string,
  extraCookies: Record<string, string> = {}
): NextRequest {
  const url = `http://localhost:3000${pathname}`;
  const request = new NextRequest(url);
  if (sessionCookie) {
    request.cookies.set("SESSION", sessionCookie);
  }
  for (const [name, value] of Object.entries(extraCookies)) {
    request.cookies.set(name, value);
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

    const response = (await middleware(request, {} as never)) as NextResponse;

    expect(response.status).toBe(307);
    const location = response.headers.get("location");
    expect(location).toBe("http://localhost:8443/oauth2/authorization/keycloak");
  });

  it("passes through when SESSION cookie exists on protected route", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/org/acme-corp/dashboard", "session-abc-123");

    const response = (await middleware(request, {} as never)) as NextResponse;

    // NextResponse.next() returns a 200
    expect(response.status).toBe(200);
  });

  it("passes through on public routes without SESSION cookie", async () => {
    const middleware = await loadMiddleware();

    const publicPaths = ["/", "/sign-in", "/api/webhooks"];
    for (const path of publicPaths) {
      const request = createRequest(path);
      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(200);
    }
  });

  it("passes through /dashboard with SESSION cookie (server component resolves slug)", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/dashboard", "session-abc-123");

    const response = (await middleware(request, {} as never)) as NextResponse;

    // Should pass through — no redirect from middleware
    expect(response.status).toBe(200);
  });

  it("passes through on portal routes without SESSION cookie", async () => {
    const middleware = await loadMiddleware();
    const request = createRequest("/portal/some-token");

    const response = (await middleware(request, {} as never)) as NextResponse;

    expect(response.status).toBe(200);
  });

  describe("GAP-L-22 — post-registration session handoff", () => {
    const originalFetch = globalThis.fetch;

    afterEach(() => {
      globalThis.fetch = originalFetch;
    });

    function mockBffMe(body: unknown, ok = true) {
      globalThis.fetch = vi.fn(
        async () => new Response(JSON.stringify(body), { status: ok ? 200 : 500 })
      ) as unknown as typeof fetch;
    }

    it("redirects to KC login with prompt=login when SESSION principal mismatches the just-authenticated user", async () => {
      mockBffMe({ authenticated: true, userId: "stale-padmin-sub" });
      const middleware = await loadMiddleware();
      const request = createRequest("/dashboard", "session-abc-123", {
        KC_LAST_LOGIN_SUB: "fresh-thandi-sub",
      });

      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(307);
      const location = response.headers.get("location")!;
      expect(location).toContain("/oauth2/authorization/keycloak");
      expect(location).toContain("prompt=login");

      // SESSION cookie must be cleared so the browser doesn't re-present the
      // stale id on the follow-up request.
      const setCookieHeader = response.headers.get("set-cookie") ?? "";
      expect(setCookieHeader).toMatch(/SESSION=;/i);
      // The one-shot signal cookie must also be cleared.
      expect(setCookieHeader).toMatch(/KC_LAST_LOGIN_SUB=;/i);
    });

    it("passes through when SESSION principal matches the just-authenticated user", async () => {
      mockBffMe({ authenticated: true, userId: "fresh-thandi-sub" });
      const middleware = await loadMiddleware();
      const request = createRequest("/dashboard", "session-abc-123", {
        KC_LAST_LOGIN_SUB: "fresh-thandi-sub",
      });

      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(200);
      // The one-shot signal cookie must be consumed (cleared) on success too.
      const setCookieHeader = response.headers.get("set-cookie") ?? "";
      expect(setCookieHeader).toMatch(/KC_LAST_LOGIN_SUB=;/i);
    });

    it("fails open and passes through when /bff/me is unreachable", async () => {
      globalThis.fetch = vi.fn(async () => {
        throw new Error("network down");
      }) as unknown as typeof fetch;
      const middleware = await loadMiddleware();
      const request = createRequest("/dashboard", "session-abc-123", {
        KC_LAST_LOGIN_SUB: "fresh-thandi-sub",
      });

      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(200);
    });

    it("redirects to KC login when /bff/me reports unauthenticated despite fresh-login signal", async () => {
      mockBffMe({ authenticated: false, userId: null });
      const middleware = await loadMiddleware();
      const request = createRequest("/dashboard", "session-abc-123", {
        KC_LAST_LOGIN_SUB: "fresh-thandi-sub",
      });

      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(307);
      const location = response.headers.get("location")!;
      expect(location).toContain("prompt=login");
    });

    it("does not call /bff/me when the KC_LAST_LOGIN_SUB cookie is absent (zero overhead on normal navigation)", async () => {
      const fetchSpy = vi.fn(
        async () =>
          new Response(JSON.stringify({ authenticated: true, userId: "x" }), { status: 200 })
      ) as unknown as typeof fetch;
      globalThis.fetch = fetchSpy;
      const middleware = await loadMiddleware();
      const request = createRequest("/org/acme-corp/dashboard", "session-abc-123");

      const response = (await middleware(request, {} as never)) as NextResponse;

      expect(response.status).toBe(200);
      expect(fetchSpy).not.toHaveBeenCalled();
    });
  });
});
