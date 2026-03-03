import { describe, it, expect, vi, beforeAll, beforeEach, afterAll, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next-auth/react
const mockSignIn = vi.fn();
const mockNextAuthSignOut = vi.fn();
const mockUseSession = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
  signIn: (...args: unknown[]) => mockSignIn(...args),
  signOut: (...args: unknown[]) => mockNextAuthSignOut(...args),
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: () => "/org/acme-corp/dashboard",
  useRouter: () => ({ push: vi.fn() }),
}));

// Mock Clerk (to avoid import errors in non-keycloak branches)
vi.mock("@clerk/nextjs", () => ({
  UserButton: () => null,
  OrganizationSwitcher: () => null,
  useUser: () => ({ user: null }),
}));

// Mock the mock-context (to avoid import errors)
vi.mock("@/lib/auth/client/mock-context", () => ({
  useMockAuthContext: () => ({
    authUser: null,
    isLoaded: true,
    orgSlug: null,
    token: null,
  }),
}));

describe("Keycloak Header Controls", () => {
  beforeAll(() => {
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");
  });

  afterAll(() => {
    vi.unstubAllEnvs();
  });

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSession.mockReturnValue({
      data: {
        user: {
          name: "Jane Doe",
          email: "jane@example.com",
          image: null,
        },
        accessToken: "mock-access-token",
      },
      status: "authenticated",
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("KeycloakUserButton renders user initials and name", async () => {
    const { AuthHeaderControls } = await import(
      "@/components/auth-header-controls"
    );

    render(<AuthHeaderControls />);

    // Should show initials button
    const initialsButton = screen.getByText("JD");
    expect(initialsButton).toBeDefined();

    // Click to open dropdown
    const user = userEvent.setup();
    await user.click(initialsButton);

    // Should show name and email in dropdown
    expect(screen.getByText("Jane Doe")).toBeDefined();
    expect(screen.getByText("jane@example.com")).toBeDefined();
  });

  it("KeycloakOrgSwitcher renders org list from mocked fetch", async () => {
    const mockOrgs = [
      { id: "1", name: "Acme Corp", slug: "acme-corp", role: "owner" },
      { id: "2", name: "Other Org", slug: "other-org", role: "member" },
    ];

    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve(mockOrgs),
    } as Response);

    const { AuthHeaderControls } = await import(
      "@/components/auth-header-controls"
    );

    render(<AuthHeaderControls />);

    // Wait for orgs to load and show current org name
    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeDefined();
    });

    // Click to open org dropdown
    const user = userEvent.setup();
    await user.click(screen.getByText("Acme Corp"));

    // Should show both orgs in dropdown
    await waitFor(() => {
      expect(screen.getByText("Other Org")).toBeDefined();
      expect(screen.getByText("Current")).toBeDefined();
    });

    // Select other org triggers re-auth
    await user.click(screen.getByText("Other Org"));
    expect(mockSignIn).toHaveBeenCalledWith("keycloak", {
      kc_org: "other-org",
    });
  });

  it("Sign-out button triggers next-auth signOut", async () => {
    const { AuthHeaderControls } = await import(
      "@/components/auth-header-controls"
    );

    render(<AuthHeaderControls />);

    // Click initials to open user dropdown
    const user = userEvent.setup();
    const initialsButton = screen.getByText("JD");
    await user.click(initialsButton);

    // Click sign out
    const signOutButton = screen.getByText("Sign out");
    await user.click(signOutButton);

    // Should trigger next-auth signOut
    expect(mockNextAuthSignOut).toHaveBeenCalledWith({
      callbackUrl: "/sign-in",
    });
  });
});
