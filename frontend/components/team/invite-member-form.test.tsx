import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockInviteMember = vi.fn();
const mockListInvitations = vi.fn().mockResolvedValue([]);
vi.mock("@/app/(app)/org/[slug]/team/invitation-actions", () => ({
  inviteMember: (...args: unknown[]) => mockInviteMember(...args),
  listInvitations: () => mockListInvitations(),
}));

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

describe("InviteMemberForm", () => {
  // Tests use default auth mode (keycloak), which renders KeycloakBffInviteMemberForm.
  // That component calls listInvitations() in useEffect to get pending count.

  async function renderForm(props: {
    maxMembers: number;
    currentMembers: number;
    orgSlug: string;
  }) {
    const { InviteMemberForm } = await import("./invite-member-form");
    const result = render(<InviteMemberForm {...props} roles={[]} />);
    // Wait for listInvitations useEffect to settle
    await waitFor(() => {});
    return result;
  }

  it("renders form when under member limit", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 1, orgSlug: "acme" });

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send Invite" })).toBeInTheDocument();
  });

  it("shows upgrade message when at member limit", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 2, orgSlug: "acme" });

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(screen.getByText(/Member limit reached/)).toBeInTheDocument();
  });

  it("renders form when maxMembers is 0 (unlimited)", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 0, currentMembers: 50, orgSlug: "acme" });

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("shows billing link with correct slug", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 2, orgSlug: "my-org" });

    const link = screen.getByRole("link", { name: "Upgrade" });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/billing");
  });

  it("shows member count without plan-tier ceiling", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 10, currentMembers: 3, orgSlug: "acme" });

    // Unlimited members — ceiling display removed (GAP-L-20)
    expect(screen.getByText("3 members · 0 pending invitations")).toBeInTheDocument();
    expect(screen.queryByText(/of 10 members/)).not.toBeInTheDocument();
  });

  // Phase 88 G-D12: pending invitations are not members — the seat line counts them separately.
  it("counts pending invitations separately from members", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "a@example.com",
        role: "org:member",
        status: "PENDING",
        createdAt: "2026-10-28T08:00:00Z",
      },
      {
        id: "inv-2",
        emailAddress: "b@example.com",
        role: "org:admin",
        status: "PENDING",
        createdAt: "2026-10-28T08:00:00Z",
      },
    ]);
    await renderForm({ maxMembers: 0, currentMembers: 3, orgSlug: "acme" });

    expect(await screen.findByText("3 members · 2 pending invitations")).toBeInTheDocument();
  });

  it("uses singulars for one member and one pending invitation", async () => {
    mockListInvitations.mockResolvedValue([
      {
        id: "inv-1",
        emailAddress: "a@example.com",
        role: "org:member",
        status: "PENDING",
        createdAt: "2026-10-28T08:00:00Z",
      },
    ]);
    await renderForm({ maxMembers: 0, currentMembers: 1, orgSlug: "acme" });

    expect(await screen.findByText("1 member · 1 pending invitation")).toBeInTheDocument();
  });

  it("submits invite and shows success message", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([]);
    mockInviteMember.mockResolvedValue({ success: true });

    await renderForm({ maxMembers: 10, currentMembers: 1, orgSlug: "acme" });

    await user.type(screen.getByLabelText("Email address"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    await waitFor(() => {
      expect(mockInviteMember).toHaveBeenCalledWith(
        "test@example.com",
        "org:member",
        undefined,
        undefined
      );
    });
    expect(await screen.findByText(/Invitation sent to test@example.com/)).toBeInTheDocument();
  });

  it("shows error when invite fails", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([]);
    mockInviteMember.mockResolvedValue({ success: false, error: "Already invited" });

    await renderForm({ maxMembers: 10, currentMembers: 1, orgSlug: "acme" });

    await user.type(screen.getByLabelText("Email address"), "dup@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    expect(await screen.findByText("Already invited")).toBeInTheDocument();
  });
});
