import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// G89-F65: after a role change in the member panel the Team list kept the old role until a reload,
// and after the reload a custom role showed its raw slug ("org:associate-no-trust") instead of its
// name ("Associate (no trust)").

vi.mock("server-only", () => ({}));

vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) => {
      const { initial, animate, transition, ...rest } = props;
      void initial;
      void animate;
      void transition;
      return <div {...rest}>{children}</div>;
    },
  },
}));

vi.mock("@/lib/auth/client", () => ({
  useOrgMembers: vi.fn().mockReturnValue({ members: [], isLoaded: true }),
}));

const mockAssignMemberRole = vi.fn();
const mockFetchMemberCapabilities = vi.fn();
const mockListMembers = vi.fn();

vi.mock("@/app/(app)/org/[slug]/team/member-actions", () => ({
  assignMemberRole: (...args: unknown[]) => mockAssignMemberRole(...args),
  fetchMemberCapabilities: (...args: unknown[]) => mockFetchMemberCapabilities(...args),
  listMembers: (...args: unknown[]) => mockListMembers(...args),
}));

import { MemberList } from "@/components/team/member-list";
import type { OrgRole } from "@/lib/api/org-roles";

function role(id: string, name: string, slug: string, isSystem: boolean): OrgRole {
  return {
    id,
    name,
    slug,
    description: null,
    capabilities: ["VIEW_LEGAL"],
    isSystem,
    memberCount: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

const roles: OrgRole[] = [
  role("sys-owner", "Owner", "owner", true),
  role("sys-admin", "Admin", "admin", true),
  role("sys-member", "Member", "member", true),
  role("custom-ant", "Associate (no trust)", "associate-no-trust", false),
];

const carol = { id: "m-carol", name: "Carol Mokoena", email: "carol@test.com" };

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("Team member list roles (G89-F65)", () => {
  it("shows a custom role's display name, not its slug", async () => {
    mockListMembers.mockResolvedValue([{ ...carol, role: "org:associate-no-trust" }]);

    render(<MemberList isAdmin={true} roles={roles} slug="acme" />);

    const row = await screen.findByTestId("member-row-carol@test.com");
    expect(within(row).getByTestId("member-role-badge")).toHaveTextContent("Associate (no trust)");
    expect(row.textContent).not.toContain("associate-no-trust");
  });

  it("reloads the list after a role change is saved", async () => {
    mockListMembers
      .mockResolvedValueOnce([{ ...carol, role: "org:member" }])
      .mockResolvedValue([{ ...carol, role: "org:associate-no-trust" }]);
    mockFetchMemberCapabilities.mockResolvedValue({
      memberId: carol.id,
      roleName: "Member",
      roleCapabilities: ["VIEW_LEGAL"],
      overrides: [],
      effectiveCapabilities: ["VIEW_LEGAL"],
    });
    mockAssignMemberRole.mockResolvedValue({ success: true });
    const user = userEvent.setup();

    render(<MemberList isAdmin={true} roles={roles} slug="acme" />);

    const row = await screen.findByTestId("member-row-carol@test.com");
    expect(within(row).getByTestId("member-role-badge")).toHaveTextContent("Member");

    await user.click(row);
    const save = await screen.findByRole("button", { name: "Save" });
    await waitFor(() => expect(save).toBeEnabled());
    await user.click(save);

    await waitFor(() => expect(mockListMembers).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(
        within(screen.getByTestId("member-row-carol@test.com")).getByTestId("member-role-badge")
      ).toHaveTextContent("Associate (no trust)")
    );
  });
});
