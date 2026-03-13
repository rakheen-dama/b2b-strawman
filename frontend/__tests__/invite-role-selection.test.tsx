import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Set auth mode to mock so InviteMemberForm renders MockInviteMemberForm (avoids Clerk hooks)
vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "mock");

// Mock motion/react (Collapsible may use motion internally)
vi.mock("motion/react", () => ({
  motion: {
    div: (props: React.PropsWithChildren<Record<string, unknown>>) => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { children, initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

// Mock server actions
const mockInviteMember = vi.fn();

vi.mock("@/app/(app)/org/[slug]/team/invitation-actions", () => ({
  inviteMember: (...args: unknown[]) => mockInviteMember(...args),
  listInvitations: vi.fn().mockResolvedValue([]),
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
  }: {
    children: React.ReactNode;
    href: string;
  }) => <a href={href}>{children}</a>,
}));

import { InviteMemberForm } from "@/components/team/invite-member-form";
import type { OrgRole } from "@/lib/api/org-roles";

function makeCustomRole(overrides: Partial<OrgRole> = {}): OrgRole {
  return {
    id: "custom-bookkeeper",
    name: "Bookkeeper",
    slug: "bookkeeper",
    description: "Can manage invoices and view financials",
    capabilities: ["FINANCIAL_VISIBILITY", "INVOICING"],
    isSystem: false,
    memberCount: 3,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

const defaultProps = {
  maxMembers: 10,
  currentMembers: 2,
  planTier: "SHARED",
  orgSlug: "acme",
};

describe("Invite Form Role Selection", () => {
  beforeEach(() => {
    mockInviteMember.mockResolvedValue({ success: true });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows custom roles in the dropdown", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    render(
      <InviteMemberForm {...defaultProps} roles={[bookkeeper]} />,
    );

    // Click the role dropdown trigger
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);

    // Should show system roles grouped under "System"
    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Member" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Admin" })).toBeInTheDocument();

    // Should show custom roles grouped under "Custom"
    expect(screen.getByText("Custom")).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Bookkeeper" }),
    ).toBeInTheDocument();
  });

  it("shows capability summary pills when custom role is selected", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    render(
      <InviteMemberForm {...defaultProps} roles={[bookkeeper]} />,
    );

    // Initially no pills shown (system role selected by default)
    expect(screen.queryByText("Financial Visibility")).not.toBeInTheDocument();

    // Select the custom role
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

    // Should show capability pills
    await waitFor(() => {
      expect(screen.getByText("Financial Visibility")).toBeInTheDocument();
      expect(screen.getByText("Invoicing")).toBeInTheDocument();
    });

    // Should not show capabilities not in the role
    expect(screen.queryByText("Project Management")).not.toBeInTheDocument();
  });

  it("hides customization section when Admin is selected", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    render(
      <InviteMemberForm {...defaultProps} roles={[bookkeeper]} />,
    );

    // Select the custom role first to show customization
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

    // "Customize for this user" should be visible
    await waitFor(() => {
      expect(
        screen.getByText("Customize for this user"),
      ).toBeInTheDocument();
    });

    // Now switch to Admin
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Admin" }));

    // "Customize for this user" should be gone
    await waitFor(() => {
      expect(
        screen.queryByText("Customize for this user"),
      ).not.toBeInTheDocument();
    });
  });

  it("sends orgRoleId with custom role on invite", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    render(
      <InviteMemberForm {...defaultProps} roles={[bookkeeper]} />,
    );

    // Fill in email
    await user.type(
      screen.getByPlaceholderText("colleague@company.com"),
      "test@example.com",
    );

    // Select custom role
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

    // Submit the form
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    await waitFor(() => {
      expect(mockInviteMember).toHaveBeenCalledWith(
        "test@example.com",
        "org:member",
        "custom-bookkeeper",
        undefined,
      );
    });
  });
});
