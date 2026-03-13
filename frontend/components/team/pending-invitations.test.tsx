import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PendingInvitations } from "./pending-invitations";

// --- Mocks ---

const mockUseOrganization = vi.fn();
vi.mock("@clerk/nextjs", () => ({
  useOrganization: () => mockUseOrganization(),
}));

const mockListInvitations = vi.fn();
const mockRevokeInvitation = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/invitation-actions", () => ({
  listInvitations: () => mockListInvitations(),
  revokeInvitation: (id: string) => mockRevokeInvitation(id),
}));

vi.mock("@/lib/format", () => ({
  formatDate: (d: string | Date) =>
    typeof d === "string" ? d : new Date(d).toISOString(),
}));

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

describe("PendingInvitations (Clerk mode)", () => {
  it("shows loading state", () => {
    mockUseOrganization.mockReturnValue({
      invitations: null,
      memberships: null,
      isLoaded: false,
    });

    render(<PendingInvitations isAdmin={true} />);
    expect(screen.getByText("Loading invitations...")).toBeInTheDocument();
  });

  it("shows empty state when no invitations", () => {
    mockUseOrganization.mockReturnValue({
      invitations: { data: [] },
      memberships: { data: [] },
      isLoaded: true,
    });

    render(<PendingInvitations isAdmin={true} />);
    expect(screen.getByText("No pending invitations")).toBeInTheDocument();
  });

  it("renders invitation rows", () => {
    mockUseOrganization.mockReturnValue({
      invitations: {
        data: [
          {
            id: "inv-1",
            emailAddress: "alice@example.com",
            role: "org:admin",
            createdAt: "2026-01-15",
            revoke: vi.fn(),
          },
        ],
        hasPreviousPage: false,
        hasNextPage: false,
      },
      memberships: { data: [] },
      isLoaded: true,
    });

    render(<PendingInvitations isAdmin={true} />);
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(screen.getByText("Admin")).toBeInTheDocument();
  });

  it("hides revoke button for non-admins", () => {
    mockUseOrganization.mockReturnValue({
      invitations: {
        data: [
          {
            id: "inv-1",
            emailAddress: "bob@example.com",
            role: "org:member",
            createdAt: "2026-01-15",
            revoke: vi.fn(),
          },
        ],
        hasPreviousPage: false,
        hasNextPage: false,
      },
      memberships: { data: [] },
      isLoaded: true,
    });

    render(<PendingInvitations isAdmin={false} />);
    expect(screen.getByText("bob@example.com")).toBeInTheDocument();
    expect(screen.queryByText("Revoke")).not.toBeInTheDocument();
  });
});
