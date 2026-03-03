import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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

// Mock next-auth/react
const mockUseSession = vi.fn();
const mockSignOut = vi.fn();
vi.mock("next-auth/react", () => ({
  useSession: mockUseSession,
  signOut: mockSignOut,
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock mock-context to prevent errors when imported but not used
vi.mock("@/lib/auth/client/mock-context", () => ({
  useMockAuthContext: () => {
    throw new Error("useMockAuthContext should not be called in keycloak mode");
  },
}));

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Keycloak client auth hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");
  });

  afterEach(() => {
    cleanup();
  });

  // ---- useAuthUser ----

  it("useAuthUser() returns user data from next-auth session", async () => {
    mockUseSession.mockReturnValue({
      data: {
        user: {
          name: "Alice Owner",
          email: "alice@example.com",
          image: "https://example.com/alice.jpg",
        },
        accessToken: "mock-access-token",
      },
      status: "authenticated",
    });

    const { useAuthUser } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { user, isLoaded } = useAuthUser();
      return (
        <div>
          <span data-testid="email">{user?.email ?? "null"}</span>
          <span data-testid="first-name">{user?.firstName ?? "null"}</span>
          <span data-testid="last-name">{user?.lastName ?? "null"}</span>
          <span data-testid="image">{user?.imageUrl ?? "null"}</span>
          <span data-testid="loaded">{String(isLoaded)}</span>
        </div>
      );
    }

    render(<TestComp />);

    expect(screen.getByTestId("loaded").textContent).toBe("true");
    expect(screen.getByTestId("email").textContent).toBe("alice@example.com");
    expect(screen.getByTestId("first-name").textContent).toBe("Alice");
    expect(screen.getByTestId("last-name").textContent).toBe("Owner");
    expect(screen.getByTestId("image").textContent).toBe(
      "https://example.com/alice.jpg",
    );
  });

  it("useAuthUser() returns null and isLoaded=false when session is loading", async () => {
    mockUseSession.mockReturnValue({
      data: null,
      status: "loading",
    });

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

    render(<TestComp />);

    expect(screen.getByTestId("user").textContent).toBe("null");
    expect(screen.getByTestId("loaded").textContent).toBe("false");
  });

  it("useAuthUser() returns null and isLoaded=true when unauthenticated", async () => {
    mockUseSession.mockReturnValue({
      data: null,
      status: "unauthenticated",
    });

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

    render(<TestComp />);

    expect(screen.getByTestId("user").textContent).toBe("null");
    expect(screen.getByTestId("loaded").textContent).toBe("true");
  });

  // ---- useSignOut ----

  it("useSignOut() calls next-auth signOut with callbackUrl", async () => {
    mockUseSession.mockReturnValue({
      data: {
        user: { name: "Alice", email: "alice@example.com" },
        accessToken: "mock-token",
      },
      status: "authenticated",
    });

    const { useSignOut } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { signOut } = useSignOut();
      return (
        <button onClick={signOut} data-testid="kc-sign-out">
          Sign Out
        </button>
      );
    }

    const user = userEvent.setup();
    render(<TestComp />);

    await user.click(screen.getByTestId("kc-sign-out"));

    expect(mockSignOut).toHaveBeenCalledWith({ callbackUrl: "/sign-in" });
  });

  // ---- useOrgMembers ----

  it("useOrgMembers() fetches members using session access token", async () => {
    mockUseSession.mockReturnValue({
      data: {
        user: { name: "Alice", email: "alice@example.com" },
        accessToken: "kc-access-token",
      },
      status: "authenticated",
    });

    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve([
          { id: "m1", role: "owner", email: "alice@example.com", name: "Alice" },
          { id: "m2", role: "member", email: "bob@example.com", name: "Bob" },
        ]),
    });

    const { useOrgMembers } = await import("@/lib/auth/client/hooks");

    function TestComp() {
      const { members, isLoaded } = useOrgMembers();
      return (
        <div>
          <span data-testid="kc-member-count">{members.length}</span>
          <span data-testid="kc-members-loaded">{String(isLoaded)}</span>
        </div>
      );
    }

    render(<TestComp />);

    await waitFor(() => {
      expect(screen.getByTestId("kc-members-loaded").textContent).toBe("true");
    });

    expect(screen.getByTestId("kc-member-count").textContent).toBe("2");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/members"),
      expect.objectContaining({
        headers: { Authorization: "Bearer kc-access-token" },
      }),
    );
  });
});
