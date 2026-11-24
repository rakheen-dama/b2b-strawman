import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Set auth mode to mock so InviteMemberForm renders MockInviteMemberForm (avoids auth provider hooks)
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
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

import { InviteMemberForm } from "@/components/team/invite-member-form";
import { CapabilityProvider } from "@/lib/capabilities";
import type { OrgRole } from "@/lib/api/org-roles";

/** As the owner: only the owner may invite into the Admin role (Task C). */
function renderAsOwner(ui: React.ReactNode) {
  return render(
    <CapabilityProvider capabilities={[]} role="owner" isAdmin={false} isOwner={true}>
      {ui}
    </CapabilityProvider>
  );
}

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

    renderAsOwner(<InviteMemberForm {...defaultProps} roles={[bookkeeper]} />);

    // Click the role dropdown trigger
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);

    // Should show system roles grouped under "System"
    expect(screen.getByText("System")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Member" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Admin" })).toBeInTheDocument();

    // Should show custom roles grouped under "Custom"
    expect(screen.getByText("Custom")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Bookkeeper" })).toBeInTheDocument();
  });

  it("shows capability summary pills when custom role is selected", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    renderAsOwner(<InviteMemberForm {...defaultProps} roles={[bookkeeper]} />);

    // Initially no pills shown (system role selected by default)
    expect(screen.queryByText("Financial Visibility")).not.toBeInTheDocument();

    // Select the custom role
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

    // Should show capability pills
    await waitFor(() => {
      expect(screen.getByText("Financial Visibility")).toBeInTheDocument();
      expect(screen.getByText("Billing")).toBeInTheDocument();
    });

    // Should not show capabilities not in the role
    expect(screen.queryByText("Matter Management")).not.toBeInTheDocument();
  });

  // FR-7 (founder ruling 2026-11-24): capabilities are not customised at invite time — they are
  // changed on the Team page after the person joins
  it.each([
    ["owner", true],
    ["admin", false],
  ] as const)(
    "never offers 'Customize for this user' at invite time (%s)",
    async (role, isOwner) => {
      const user = userEvent.setup();
      const bookkeeper = makeCustomRole();

      render(
        <CapabilityProvider
          capabilities={isOwner ? [] : ["TEAM_OVERSIGHT"]}
          role={role}
          isAdmin={!isOwner}
          isOwner={isOwner}
        >
          <InviteMemberForm {...defaultProps} roles={[bookkeeper]} />
        </CapabilityProvider>
      );

      await user.click(screen.getByRole("combobox"));
      await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

      // the role's capability summary still shows ...
      await waitFor(() => expect(screen.getByText("Financial Visibility")).toBeInTheDocument());
      // ... but there is nothing to customise
      expect(screen.queryByText("Customize for this user")).not.toBeInTheDocument();
      expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
      expect(screen.queryByTestId(/^invite-capability-/)).not.toBeInTheDocument();
    }
  );

  it("sends orgRoleId with custom role on invite", async () => {
    const user = userEvent.setup();
    const bookkeeper = makeCustomRole();

    renderAsOwner(<InviteMemberForm {...defaultProps} roles={[bookkeeper]} />);

    // Fill in email
    await user.type(screen.getByPlaceholderText("colleague@company.com"), "test@example.com");

    // Select custom role
    const trigger = screen.getByRole("combobox");
    await user.click(trigger);
    await user.click(screen.getByRole("option", { name: "Bookkeeper" }));

    // Submit the form
    await user.click(screen.getByRole("button", { name: "Send Invite" }));

    await waitFor(() => {
      // no capability overrides travel with an invitation (FR-7)
      expect(mockInviteMember).toHaveBeenCalledWith(
        "test@example.com",
        "org:member",
        "custom-bookkeeper"
      );
    });
  });
});
