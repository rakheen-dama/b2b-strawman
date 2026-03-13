import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Set auth mode to keycloak so PendingInvitations renders KeycloakBffPendingInvitations
vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "keycloak");

// --- Mocks ---

const mockListInvitations = vi.fn();
const mockRevokeInvitation = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/actions", () => ({
  listInvitations: () => mockListInvitations(),
  revokeInvitation: (id: string) => mockRevokeInvitation(id),
}));

vi.mock("@/lib/format", () => ({
  formatDate: (d: string | Date) =>
    typeof d === "string" ? d : new Date(d).toISOString(),
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
        status: "pending",
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
        status: "pending",
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
          status: "pending",
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
  });
});
