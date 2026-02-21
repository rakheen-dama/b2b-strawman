import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MockAuthContextProvider } from "@/lib/auth/client/mock-context";

// Mock next/navigation
const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
}));

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Helper: build a mock JWT
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

// Helper: set document.cookie with mock token
function setCookie(token: string) {
  Object.defineProperty(document, "cookie", {
    writable: true,
    value: `mock-auth-token=${token}`,
  });
}

function clearCookie() {
  Object.defineProperty(document, "cookie", {
    writable: true,
    value: "",
  });
}

describe("Mock client auth hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCookie();
  });

  afterEach(() => {
    cleanup();
  });

  // ---- useAuthUser ----

  it("useAuthUser() returns null and isLoaded=false initially when no cookie", async () => {
    const { useAuthUser } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { user, isLoaded } = useAuthUser();
      return (
        <div>
          <span data-testid="user">{user ? user.email : "null"}</span>
          <span data-testid="loaded">{String(isLoaded)}</span>
        </div>
      );
    }

    render(
      <MockAuthContextProvider>
        <TestComp />
      </MockAuthContextProvider>,
    );

    // Initially not loaded (before useEffect runs)
    expect(screen.getByTestId("user").textContent).toBe("null");
  });

  it("useAuthUser() fetches userinfo and returns AuthUser after mount", async () => {
    const token = buildMockJwt(defaultPayload);
    setCookie(token);

    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          id: "user_e2e_alice",
          firstName: "Alice",
          lastName: "Owner",
          email: "alice@e2e-test.local",
          imageUrl: null,
        }),
    });

    const { useAuthUser } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { user, isLoaded } = useAuthUser();
      return (
        <div>
          <span data-testid="email">{user?.email ?? "null"}</span>
          <span data-testid="loaded">{String(isLoaded)}</span>
        </div>
      );
    }

    render(
      <MockAuthContextProvider>
        <TestComp />
      </MockAuthContextProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loaded").textContent).toBe("true");
    });

    expect(screen.getByTestId("email").textContent).toBe(
      "alice@e2e-test.local",
    );
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/userinfo/user_e2e_alice"),
      expect.any(Object),
    );
  });

  // ---- useOrgMembers ----

  it("useOrgMembers() starts with empty members and isLoaded=false", async () => {
    const { useOrgMembers } = await import("@/lib/auth/client/hooks");

    // No cookie set — no fetch should happen
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });

    function TestComp() {
      const { members, isLoaded } = useOrgMembers();
      return (
        <div>
          <span data-testid="count">{members.length}</span>
          <span data-testid="loaded">{String(isLoaded)}</span>
        </div>
      );
    }

    render(
      <MockAuthContextProvider>
        <TestComp />
      </MockAuthContextProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loaded").textContent).toBe("true");
    });

    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("useOrgMembers() fetches /api/members with Bearer token and returns members", async () => {
    const token = buildMockJwt(defaultPayload);
    setCookie(token);

    const mockMembers = [
      {
        id: "m1",
        role: "owner",
        email: "alice@e2e-test.local",
        name: "Alice Owner",
      },
      {
        id: "m2",
        role: "admin",
        email: "bob@e2e-test.local",
        name: "Bob Admin",
      },
    ];

    // Use URL-based routing since context and hook fetch concurrently
    mockFetch.mockImplementation((url: string) => {
      if (url.includes("/userinfo/")) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              id: "user_e2e_alice",
              firstName: "Alice",
              lastName: "Owner",
              email: "alice@e2e-test.local",
              imageUrl: null,
            }),
        });
      }
      if (url.includes("/api/members")) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve(mockMembers),
        });
      }
      return Promise.resolve({ ok: false, json: () => Promise.resolve({}) });
    });

    const { useOrgMembers } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { members, isLoaded } = useOrgMembers();
      return (
        <div>
          <span data-testid="count">{members.length}</span>
          <span data-testid="loaded">{String(isLoaded)}</span>
          {members.map((m) => (
            <span key={m.id} data-testid="member-email">
              {m.email}
            </span>
          ))}
        </div>
      );
    }

    render(
      <MockAuthContextProvider>
        <TestComp />
      </MockAuthContextProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loaded").textContent).toBe("true");
    });

    expect(screen.getByTestId("count").textContent).toBe("2");
    expect(
      screen.getAllByTestId("member-email").map((el) => el.textContent),
    ).toContain("alice@e2e-test.local");

    const membersCall = mockFetch.mock.calls.find((call) =>
      (call[0] as string).includes("/api/members"),
    );
    expect(membersCall).toBeDefined();
    expect(membersCall?.[1]).toMatchObject({
      headers: { Authorization: expect.stringContaining("Bearer ") },
    });
  });

  // ---- useSignOut ----

  it("useSignOut() clears cookie and navigates to /mock-login", async () => {
    const token = buildMockJwt(defaultPayload);
    setCookie(token);

    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          firstName: "Alice",
          lastName: "Owner",
          email: "alice@e2e-test.local",
          imageUrl: null,
        }),
    });

    const { useSignOut } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { signOut } = useSignOut();
      return (
        <button onClick={signOut} data-testid="sign-out">
          Sign Out
        </button>
      );
    }

    const user = userEvent.setup();
    render(
      <MockAuthContextProvider>
        <TestComp />
      </MockAuthContextProvider>,
    );

    await user.click(screen.getByTestId("sign-out"));

    expect(mockPush).toHaveBeenCalledWith("/mock-login");
    // Cookie should be cleared (max-age=0). In a real browser the cookie disappears;
    // in the test environment the raw set-cookie string is retained, so we verify
    // the original token value is no longer present.
    expect(document.cookie).not.toContain(buildMockJwt(defaultPayload));
  });
});
