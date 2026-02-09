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
      membersCount: 1,
      pendingInvitationsCount: 0,
      maxAllowedMemberships: 2,
      ...overrides,
    },
    invitations: { revalidate: vi.fn() },
  };
}

describe("InviteMemberForm", () => {
  it("renders form when under member limit", () => {
    mockUseOrganization.mockReturnValue(mockOrg({ membersCount: 1 }));
    render(<InviteMemberForm />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send invite" })).toBeInTheDocument();
  });

  it("shows upgrade message when at member limit", () => {
    mockUseOrganization.mockReturnValue(
      mockOrg({ membersCount: 2, pendingInvitationsCount: 0 }),
    );
    render(<InviteMemberForm />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(
      screen.getByText(/reached its member limit/),
    ).toBeInTheDocument();
  });

  it("counts pending invitations toward the limit", () => {
    mockUseOrganization.mockReturnValue(
      mockOrg({ membersCount: 1, pendingInvitationsCount: 1 }),
    );
    render(<InviteMemberForm />);

    expect(screen.queryByLabelText("Email address")).not.toBeInTheDocument();
    expect(
      screen.getByText(/reached its member limit/),
    ).toBeInTheDocument();
  });

  it("renders form when maxAllowedMemberships is not set (unlimited)", () => {
    mockUseOrganization.mockReturnValue(
      mockOrg({ membersCount: 50, maxAllowedMemberships: undefined }),
    );
    render(<InviteMemberForm />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("renders form when maxAllowedMemberships is 0 (unlimited)", () => {
    mockUseOrganization.mockReturnValue(
      mockOrg({ membersCount: 50, maxAllowedMemberships: 0 }),
    );
    render(<InviteMemberForm />);

    expect(screen.getByLabelText("Email address")).toBeInTheDocument();
  });

  it("shows billing link with correct slug", () => {
    mockUseOrganization.mockReturnValue(
      mockOrg({ membersCount: 2, slug: "my-org" }),
    );
    render(<InviteMemberForm />);

    const link = screen.getByRole("link", { name: "Upgrade your plan" });
    expect(link).toHaveAttribute("href", "/org/my-org/settings/billing");
  });

  it("returns null when organization is not loaded", () => {
    mockUseOrganization.mockReturnValue({
      organization: null,
      invitations: null,
    });
    const { container } = render(<InviteMemberForm />);

    expect(container.innerHTML).toBe("");
  });
});
