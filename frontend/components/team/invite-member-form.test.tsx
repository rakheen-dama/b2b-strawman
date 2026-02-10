import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
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

vi.mock("@/app/(app)/org/[slug]/team/actions", () => ({
  inviteMember: vi.fn(),
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
    render(<InviteMemberForm maxMembers={2} currentMembers={1} />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send invite" })).toBeInTheDocument();
  });

  it("shows upgrade message when at member limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={2} currentMembers={2} />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(
      screen.getByText(/reached its member limit/),
    ).toBeInTheDocument();
  });

  it("counts pending invitations toward the limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg({ pendingInvitationsCount: 1 }));
    render(<InviteMemberForm maxMembers={2} currentMembers={1} />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(
      screen.getByText(/reached its member limit/),
    ).toBeInTheDocument();
  });

  it("renders form when maxMembers is 0 (unlimited)", () => {
    mockUseOrganization.mockReturnValue(mockOrg());
    render(<InviteMemberForm maxMembers={0} currentMembers={50} />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("shows billing link with correct slug", () => {
    mockUseOrganization.mockReturnValue(mockOrg({ slug: "my-org" }));
    render(<InviteMemberForm maxMembers={2} currentMembers={2} />);

    const link = screen.getByRole("link", { name: "Upgrade your plan" });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/billing");
  });

  it("returns null when organization is not loaded", () => {
    mockUseOrganization.mockReturnValue({
      organization: null,
      invitations: null,
    });
    const { container } = render(<InviteMemberForm maxMembers={2} currentMembers={1} />);

    expect(container.innerHTML).toBe("");
  });
});
