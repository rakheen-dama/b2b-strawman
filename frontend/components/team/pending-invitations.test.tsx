import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
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

vi.mock("@/lib/format", () => ({
  formatDate: (d: string | Date) => (typeof d === "string" ? d : new Date(d).toISOString()),
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

  // FR-8: the backend refuses a revoke it cannot make safe — 409 "Invitation already accepted"
  // (the person already used the email link) or 400 "Invitation not revoked" (Keycloak error).
  // The action returns the ProblemDetail detail; the admin must see it.
  const ALREADY_ACCEPTED =
    "'dave@example.com' has already accepted the invitation email and joins with the invited role" +
    " on their first sign-in, so it can no longer be revoked. Remove them from the team once they" +
    " appear.";
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
    mockRevokeInvitation.mockResolvedValue({ success: false, error: ALREADY_ACCEPTED });

    render(<PendingInvitations isAdmin={true} />);
    await user.click(await screen.findByText("Revoke"));

    const alert = await screen.findByTestId("revoke-invitation-error");
    expect(alert).toHaveAttribute("role", "alert");
    expect(alert).toHaveTextContent(ALREADY_ACCEPTED);
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
