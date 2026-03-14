import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
  }: {
    children: React.ReactNode;
    href: string;
  }) => <a href={href}>{children}</a>,
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
    planTier: string;
    orgSlug: string;
  }) {
    const { InviteMemberForm } = await import("./invite-member-form");
    const result = render(
      <InviteMemberForm {...props} roles={[]} />,
    );
    // Wait for listInvitations useEffect to settle
    await waitFor(() => {});
    return result;
  }

  it("renders form when under member limit", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 1, planTier: "SHARED", orgSlug: "acme" });

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send Invite" })).toBeInTheDocument();
  });

  it("shows upgrade message when at member limit", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 2, planTier: "SHARED", orgSlug: "acme" });

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(screen.getByText(/Member limit reached/)).toBeInTheDocument();
  });

  it("renders form when maxMembers is 0 (unlimited)", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 0, currentMembers: 50, planTier: "DEDICATED", orgSlug: "acme" });

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("shows billing link with correct slug", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 2, currentMembers: 2, planTier: "SHARED", orgSlug: "my-org" });

    const link = screen.getByRole("link", { name: "Upgrade" });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/billing");
  });

  it("shows progress bar with member count", async () => {
    mockListInvitations.mockResolvedValue([]);
    await renderForm({ maxMembers: 10, currentMembers: 3, planTier: "SHARED", orgSlug: "acme" });

    expect(screen.getByText("3 of 10 members")).toBeInTheDocument();
  });

  it("submits invite and shows success message", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([]);
    mockInviteMember.mockResolvedValue({ success: true });

    await renderForm({ maxMembers: 10, currentMembers: 1, planTier: "SHARED", orgSlug: "acme" });

    await user.type(screen.getByLabelText("Email address"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    await waitFor(() => {
      expect(mockInviteMember).toHaveBeenCalledWith("test@example.com", "org:member", undefined, undefined);
    });
    expect(await screen.findByText(/Invitation sent to test@example.com/)).toBeInTheDocument();
  });

  it("shows error when invite fails", async () => {
    const user = userEvent.setup();
    mockListInvitations.mockResolvedValue([]);
    mockInviteMember.mockResolvedValue({ success: false, error: "Already invited" });

    await renderForm({ maxMembers: 10, currentMembers: 1, planTier: "SHARED", orgSlug: "acme" });

    await user.type(screen.getByLabelText("Email address"), "dup@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    expect(await screen.findByText("Already invited")).toBeInTheDocument();
  });
});
