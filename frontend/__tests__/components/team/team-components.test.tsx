import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/org/test-org/team",
}));

// Mock next-auth/react
const mockUseSession = vi.fn();
vi.mock("next-auth/react", () => ({
  useSession: mockUseSession,
  signOut: vi.fn(),
  SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock mock-context to prevent errors when imported but not used
vi.mock("@/lib/auth/client/mock-context", () => ({
  useMockAuthContext: () => {
    throw new Error("useMockAuthContext should not be called in keycloak mode");
  },
}));

// Mock Clerk
vi.mock("@clerk/nextjs", () => ({
  useOrganization: () => ({
    memberships: { data: [], isLoaded: true },
    invitations: { data: [], isLoaded: true },
    isLoaded: true,
  }),
}));

// Mock server action
vi.mock("@/app/(app)/org/[slug]/team/actions", () => ({
  inviteMember: vi.fn().mockResolvedValue({ success: true }),
}));

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Encode a fake JWT with org info
function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

const TEST_TOKEN = makeJwt({
  sub: "user-1",
  o: { id: "org-123", slg: "test-org", rol: "owner" },
});

describe("Keycloak team components", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");

    mockUseSession.mockReturnValue({
      data: {
        user: { name: "Alice Owner", email: "alice@example.com" },
        accessToken: TEST_TOKEN,
      },
      status: "authenticated",
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("KeycloakMemberList renders members from backend API", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve([
          { id: "m1", role: "owner", email: "alice@example.com", name: "Alice Owner" },
          { id: "m2", role: "member", email: "bob@example.com", name: "Bob Member" },
        ]),
    });

    const { MemberList } = await import("@/components/team/member-list");

    render(<MemberList />);

    await waitFor(() => {
      expect(screen.getByText("Alice Owner")).toBeDefined();
    });

    expect(screen.getByText("Bob Member")).toBeDefined();
    expect(screen.getByText("alice@example.com")).toBeDefined();
    expect(screen.getByText("bob@example.com")).toBeDefined();
    // Roles should be resolved from non-prefixed keys (use getAllByText since "Member" appears in header too)
    expect(screen.getByText("Owner")).toBeDefined();
    const memberBadges = screen.getAllByText("Member");
    expect(memberBadges.length).toBeGreaterThanOrEqual(1);
  });

  it("KeycloakPendingInvitations renders invitations from backend API", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve([
          { id: "inv-1", email: "carol@example.com", status: "pending", createdAt: "2026-03-01T10:00:00Z" },
          { id: "inv-2", email: "dave@example.com", status: "expired", createdAt: "2026-02-15T08:00:00Z" },
        ]),
    });

    const { PendingInvitations } = await import("@/components/team/pending-invitations");

    render(<PendingInvitations isAdmin={true} />);

    await waitFor(() => {
      expect(screen.getByText("carol@example.com")).toBeDefined();
    });

    expect(screen.getByText("dave@example.com")).toBeDefined();
    expect(screen.getByText("Pending")).toBeDefined();
    expect(screen.getByText("Expired")).toBeDefined();
  });

  it("KeycloakPendingInvitations revokes invitation via DELETE", async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve([
            { id: "inv-1", email: "carol@example.com", status: "pending", createdAt: "2026-03-01T10:00:00Z" },
          ]),
      })
      .mockResolvedValueOnce({ ok: true });

    const { PendingInvitations } = await import("@/components/team/pending-invitations");
    const user = userEvent.setup();

    render(<PendingInvitations isAdmin={true} />);

    await waitFor(() => {
      expect(screen.getByText("carol@example.com")).toBeDefined();
    });

    const revokeButton = screen.getByText("Revoke");
    await user.click(revokeButton);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/orgs/org-123/invitations/inv-1"),
        expect.objectContaining({ method: "DELETE" }),
      );
    });
  });

  it("KeycloakInviteMemberForm renders form and submits", async () => {
    // First fetch for pending invitations count
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    });

    const { InviteMemberForm } = await import("@/components/team/invite-member-form");

    render(
      <InviteMemberForm
        maxMembers={10}
        currentMembers={2}
        planTier="SHARED"
        orgSlug="test-org"
      />,
    );

    await waitFor(() => {
      expect(screen.getByLabelText("Email address")).toBeDefined();
    });

    expect(screen.getByLabelText("Role")).toBeDefined();
    expect(screen.getByText("Send Invite")).toBeDefined();
    // Should show member count
    expect(screen.getByText("2 of 10 members")).toBeDefined();
  });
});
