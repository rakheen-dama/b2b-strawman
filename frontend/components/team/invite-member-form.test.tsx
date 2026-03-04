import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InviteMemberForm } from "./invite-member-form";

const mockUseOrganization = vi.fn();
vi.mock("@clerk/nextjs", () => ({
  useOrganization: () => mockUseOrganization(),
}));

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
const mockListInvitations = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/actions", () => ({
  inviteMember: (...args: unknown[]) => mockInviteMember(...args),
  listInvitations: () => mockListInvitations(),
}));

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

function mockOrg(overrides: Record<string, unknown> = {}) {
  return {
    organization: {
      slug: "acme",
      pendingInvitationsCount: 0,
      ...overrides,
    },
    invitations: { revalidate: vi.fn() },
  };
}

describe("InviteMemberForm", () => {
  it("renders form when under member limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={2} currentMembers={1} planTier="SHARED" orgSlug="acme" />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send Invite" })).toBeInTheDocument();
  });

  it("shows upgrade message when at member limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={2} currentMembers={2} planTier="SHARED" orgSlug="acme" />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(screen.getByText(/Member limit reached/)).toBeInTheDocument();
  });

  it("counts pending invitations toward the limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg({ pendingInvitationsCount: 1 }));
    render(<InviteMemberForm maxMembers={2} currentMembers={1} planTier="SHARED" orgSlug="acme" />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(screen.getByText(/Member limit reached/)).toBeInTheDocument();
  });

  it("renders form when maxMembers is 0 (unlimited)", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={0} currentMembers={50} planTier="DEDICATED" orgSlug="acme" />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("shows billing link with correct slug", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={2} currentMembers={2} planTier="SHARED" orgSlug="my-org" />);

    const link = screen.getByRole("link", { name: "Upgrade" });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/billing");
  });

  it("returns null when organization is not loaded", () => {
    mockUseOrganization.mockReturnValue({
      organization: null,
      invitations: null,
    });
    const { container } = render(
      <InviteMemberForm maxMembers={2} currentMembers={1} planTier="SHARED" orgSlug="acme" />
    );

    expect(container.innerHTML).toBe("");
  });

  it("shows progress bar with member count", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={10} currentMembers={3} planTier="SHARED" orgSlug="acme" />);

    expect(screen.getByText("3 of 10 members")).toBeInTheDocument();
  });

  it("submits invite and shows success message", async () => {
    const user = userEvent.setup();
    mockUseOrganization.mockReturnValue(mockOrg());
    mockInviteMember.mockResolvedValue({ success: true });

    render(<InviteMemberForm maxMembers={10} currentMembers={1} planTier="SHARED" orgSlug="acme" />);

    await user.type(screen.getByLabelText("Email address"), "test@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    await waitFor(() => {
      expect(mockInviteMember).toHaveBeenCalledWith("test@example.com", "org:member");
    });
    expect(await screen.findByText(/Invitation sent to test@example.com/)).toBeInTheDocument();
  });

  it("shows error when invite fails", async () => {
    const user = userEvent.setup();
    mockUseOrganization.mockReturnValue(mockOrg());
    mockInviteMember.mockResolvedValue({ success: false, error: "Already invited" });

    render(<InviteMemberForm maxMembers={10} currentMembers={1} planTier="SHARED" orgSlug="acme" />);

    await user.type(screen.getByLabelText("Email address"), "dup@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    expect(await screen.findByText("Already invited")).toBeInTheDocument();
  });
});
