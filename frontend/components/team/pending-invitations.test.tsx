import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Set auth mode to keycloak so PendingInvitations renders KeycloakBffPendingInvitations
vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");

// --- Mocks ---

const mockListInvitations = vi.fn();
const mockRevokeInvitation = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/invitation-actions", () => ({
  listInvitations: () => mockListInvitations(),
  revokeInvitation: (id: string) => mockRevokeInvitation(id),
}));

// DataTable calls useRouter() unconditionally (Next 16 throws without the App Router context).
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    refresh: vi.fn(),
    back: vi.fn(),
    forward: vi.fn(),
    prefetch: vi.fn(),
  }),
  usePathname: () => "/org/acme/team",
  useSearchParams: () => new URLSearchParams(),
}));

// Must import after mocks and env stubs are set up
import { PendingInvitations } from "./pending-invitations";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

describe("PendingInvitations (keycloak mode)", () => {
  it("shows loading state initially", () => {
    mockListInvitations.mockReturnValue(new Promise(() => {})); // never resolves

    render(<PendingInvitations isAdmin={true} />);
    expect(screen.getByText("Loading invitations...")).toBeInTheDocument();
  });

  it("shows empty state when no invitations", async () => {
    mockListInvitations.mockResolvedValue([]);

    render(<PendingInvitations isAdmin={true} />);
    await waitFor(() => {
      expect(screen.getByText("No pending invitations")).toBeInTheDocument();
    });
    // The page-level tab panel's empty state (669B): default variant, no table.
    expect(
      screen.getByText("No pending invitations").closest('[data-slot="empty-state"]')
    ).toHaveAttribute("data-variant", "default");
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("renders a DataTable with today's headers, a pending-invite-row per invitation and SA dates (669B)", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "alice@example.com",
        role: "org:admin",
        status: "PENDING",
        createdAt: "2026-01-15T10:00:00Z",
      },
      {
        id: "inv-2",
        emailAddress: "bob@example.com",
        role: "org:member",
        status: "PENDING",
        createdAt: "",
      },
    ]);

    render(<PendingInvitations isAdmin={true} />);
    await screen.findByText("alice@example.com");

    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Email",
      "Role",
      "Invited",
      "Actions",
    ]);
    const rows = screen.getAllByTestId("pending-invite-row");
    expect(rows).toHaveLength(2);
    expect(rows.every((row) => row.tagName === "TR")).toBe(true);
    // formatDateInSA (kit DateCell); a missing date reads "—".
    expect(within(rows[0]).getByText("15 Jan 2026")).toBeInTheDocument();
    expect(within(rows[1]).getByText("\u2014")).toBeInTheDocument();
    expect(within(rows[0]).getByRole("button", { name: "Revoke" })).toBeEnabled();
  });

  it("has no Actions column for non-admins (669B)", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "alice@example.com",
        role: "org:admin",
        status: "PENDING",
        createdAt: "2026-01-15T10:00:00Z",
      },
    ]);

    render(<PendingInvitations isAdmin={false} />);
    await screen.findByText("alice@example.com");

    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Email",
      "Role",
      "Invited",
    ]);
  });

  it("renders invitation rows", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "alice@example.com",
        role: "org:admin",
        status: "PENDING",
        createdAt: "2026-01-15",
      },
    ]);

    render(<PendingInvitations isAdmin={true} />);
    await waitFor(() => {
      expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    });
    expect(screen.getByText("Admin")).toBeInTheDocument();
  });

  it("hides revoke button for non-admins", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "bob@example.com",
        role: "org:member",
        status: "PENDING",
        createdAt: "2026-01-15",
      },
    ]);

    render(<PendingInvitations isAdmin={false} />);
    await waitFor(() => {
      expect(screen.getByText("bob@example.com")).toBeInTheDocument();
    });
    expect(screen.queryByText("Revoke")).not.toBeInTheDocument();
  });

  it("calls revokeInvitation and refreshes list on revoke", async () => {
    const user = userEvent.setup();
    mockListInvitations
      .mockResolvedValueOnce([
        {
          id: "inv-1",
          emailAddress: "carol@example.com",
          role: "org:member",
          status: "PENDING",
          createdAt: "2026-01-15",
        },
      ])
      .mockResolvedValue([]); // after revoke, list is empty

    mockRevokeInvitation.mockResolvedValue({ success: true });

    render(<PendingInvitations isAdmin={true} />);
    await waitFor(() => {
      expect(screen.getByText("carol@example.com")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Revoke"));

    await waitFor(() => {
      expect(mockRevokeInvitation).toHaveBeenCalledWith("inv-1");
    });
    expect(await screen.findByText("No pending invitations")).toBeInTheDocument();
    expect(screen.queryByTestId("revoke-invitation-error")).not.toBeInTheDocument();
  });

  // FR-8: the backend refuses a revoke it cannot make safe — 400 "Invitation not revoked" when
  // Keycloak fails (fail closed). The action returns the ProblemDetail detail; the admin must see
  // it.
  const NOT_REVOKED =
    "The invitation email for 'dave@example.com' could not be cancelled in Keycloak, so its link" +
    " may still work. Nothing was changed. Please try again.";
  const dave = {
    id: "inv-2",
    emailAddress: "dave@example.com",
    role: "org:member",
    status: "PENDING",
    createdAt: "2026-01-15",
  };

  it("shows the refusal detail when the revoke is refused, and keeps the row", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([dave]);
    mockRevokeInvitation.mockResolvedValue({ success: false, error: NOT_REVOKED });

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));

    const alert = await screen.findByTestId("revoke-invitation-error");
    expect(alert).toHaveAttribute("role", "alert");
    expect(alert).toHaveTextContent(NOT_REVOKED);
    expect(screen.getByText("dave@example.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Revoke" })).toBeEnabled();
  });

  it("shows a generic message when the refusal carries no detail", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([dave]);
    mockRevokeInvitation.mockResolvedValue({ success: false });

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));

    expect(await screen.findByTestId("revoke-invitation-error")).toHaveTextContent(
      "Could not revoke the invitation. Please try again."
    );
  });

  it("shows a generic message when the revoke throws", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([dave]);
    mockRevokeInvitation.mockRejectedValue(new Error("network down"));

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));

    expect(await screen.findByTestId("revoke-invitation-error")).toHaveTextContent(
      "Could not revoke the invitation. Please try again."
    );
    expect(screen.getByText("dave@example.com")).toBeInTheDocument();
  });

  // CodeRabbit (minor): a revoke that succeeded must never be reported as failed because the
  // list refresh afterwards failed
  it("a successful revoke whose list refresh fails drops the row, with no failure message", async () => {
    const user = userEvent.setup();
    mockListInvitations
      .mockResolvedValueOnce([dave])
      .mockRejectedValueOnce(new Error("refresh failed"));
    mockRevokeInvitation.mockResolvedValue({ success: true });

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));

    await waitFor(() => expect(screen.queryByText("dave@example.com")).not.toBeInTheDocument());
    expect(screen.queryByTestId("revoke-invitation-error")).not.toBeInTheDocument();
    expect(screen.queryByText(/Could not revoke/)).not.toBeInTheDocument();
  });

  it("clears the refusal on the next attempt", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([dave]);
    mockRevokeInvitation
      .mockResolvedValueOnce({ success: false, error: "Invitation not revoked" })
      .mockReturnValueOnce(new Promise(() => {})); // second attempt still in flight

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));
    expect(await screen.findByTestId("revoke-invitation-error")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Revoke" }));
    await waitFor(() =>
      expect(screen.queryByTestId("revoke-invitation-error")).not.toBeInTheDocument()
    );
  });
});
