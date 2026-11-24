import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

/**
 * FR-4 (founder ruling 2026-11-24): only the owner may put an owner-only capability into a role or
 * into a member's capabilities (the backend refuses anyone else with 403 "Owner-only capability").
 * For a non-owner every place that grants capabilities shows an owner-only capability — or a role
 * holding one — but disabled, with the hint "Only the owner can grant this", and keeps its current
 * state. For the owner nothing changes.
 */

vi.mock("server-only", () => ({}));
vi.stubEnv("NEXT_PUBLIC_AUTH_MODE", "mock");

vi.mock("motion/react", () => ({
  motion: {
    div: (props: React.PropsWithChildren<Record<string, unknown>>) => {
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      const { children, initial, animate, transition, ...rest } = props;
      return <div {...rest}>{children}</div>;
    },
  },
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const mockCreateRoleAction = vi.fn();
const mockUpdateRoleAction = vi.fn();
vi.mock("@/app/(app)/org/[slug]/settings/firm/_tabs/roles-actions", () => ({
  createRoleAction: (...args: unknown[]) => mockCreateRoleAction(...args),
  updateRoleAction: (...args: unknown[]) => mockUpdateRoleAction(...args),
  deleteRoleAction: vi.fn(),
}));

const mockAssignMemberRole = vi.fn();
const mockFetchMemberCapabilities = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/member-actions", () => ({
  assignMemberRole: (...args: unknown[]) => mockAssignMemberRole(...args),
  fetchMemberCapabilities: (...args: unknown[]) => mockFetchMemberCapabilities(...args),
  listMembers: vi.fn().mockResolvedValue([]),
}));

const mockInviteMember = vi.fn();
vi.mock("@/app/(app)/org/[slug]/team/invitation-actions", () => ({
  inviteMember: (...args: unknown[]) => mockInviteMember(...args),
  listInvitations: vi.fn().mockResolvedValue([]),
}));

import { CapabilityProvider } from "@/lib/capabilities";
import { RoleDialog } from "@/components/roles/role-dialog";
import { MemberDetailPanel } from "@/components/team/member-detail-panel";
import { InviteMemberForm } from "@/components/team/invite-member-form";
import type { OrgRole } from "@/lib/api/org-roles";

const SIGN_OFF = "SIGN_OFF_TRUST_TAKE_ON";
const SIGN_OFF_LABEL = "Sign off trust take-on";
const HINT = "Only the owner can grant this";

function role(overrides: Partial<OrgRole> = {}): OrgRole {
  return {
    id: "role-bookkeeper",
    name: "Bookkeeper",
    slug: "bookkeeper",
    description: null,
    capabilities: ["INVOICING"],
    isSystem: false,
    memberCount: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

const bookkeeper = role();
const partner = role({
  id: "role-partner",
  name: "Partner",
  slug: "partner",
  capabilities: [SIGN_OFF, "VIEW_TRUST"],
});

function asCaller(kind: "owner" | "admin", ui: React.ReactNode) {
  return render(
    <CapabilityProvider
      capabilities={kind === "owner" ? [] : ["TEAM_OVERSIGHT"]}
      role={kind}
      isAdmin={kind === "admin"}
      isOwner={kind === "owner"}
    >
      {ui}
    </CapabilityProvider>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// --- the role dialog ---

describe("RoleDialog — owner-only capabilities", () => {
  it("a non-owner sees the owner-only capability disabled, with the hint", () => {
    asCaller("admin", <RoleDialog slug="acme" open={true} onOpenChange={vi.fn()} />);

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeDisabled();
    expect(signOff).toHaveAttribute("data-testid", `role-capability-${SIGN_OFF}`);
    expect(screen.getByTestId(`owner-only-hint-${SIGN_OFF}`)).toHaveTextContent(HINT);
    // an ordinary capability stays open
    expect(screen.getByRole("checkbox", { name: "Billing" })).toBeEnabled();
  });

  it("a non-owner editing an owner-made role keeps the owner-only capability unchanged", async () => {
    const user = userEvent.setup();
    mockUpdateRoleAction.mockResolvedValue({ success: true });
    asCaller("admin", <RoleDialog slug="acme" role={partner} open={true} onOpenChange={vi.fn()} />);

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeDisabled();
    expect(signOff).toHaveAttribute("data-state", "checked");
    await user.click(signOff);
    expect(signOff).toHaveAttribute("data-state", "checked");

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(mockUpdateRoleAction).toHaveBeenCalled());
    expect(mockUpdateRoleAction.mock.calls[0][2].capabilities).toEqual(
      expect.arrayContaining([SIGN_OFF, "VIEW_TRUST"])
    );
  });

  it("the owner can grant the owner-only capability; no hint", async () => {
    const user = userEvent.setup();
    mockCreateRoleAction.mockResolvedValue({ success: true });
    asCaller("owner", <RoleDialog slug="acme" open={true} onOpenChange={vi.fn()} />);

    expect(screen.queryByTestId(`owner-only-hint-${SIGN_OFF}`)).not.toBeInTheDocument();
    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeEnabled();

    await user.type(screen.getByLabelText("Name"), "Partner");
    await user.click(signOff);
    await user.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() =>
      expect(mockCreateRoleAction).toHaveBeenCalledWith("acme", {
        name: "Partner",
        description: undefined,
        capabilities: [SIGN_OFF],
      })
    );
  });
});

// --- the member detail panel (role assignment + overrides) ---

function openPanel(kind: "owner" | "admin", currentRole: string) {
  mockFetchMemberCapabilities.mockResolvedValue({
    memberId: "m1",
    roleName: currentRole,
    roleCapabilities: [],
    overrides: [],
    effectiveCapabilities: [],
  });
  return asCaller(
    kind,
    <MemberDetailPanel
      open={true}
      onOpenChange={vi.fn()}
      member={{ id: "m1", name: "Thandi", email: "thandi@test.com", role: "org:member" }}
      roles={[bookkeeper, partner]}
      slug="acme"
    />
  );
}

describe("MemberDetailPanel — owner-only capabilities", () => {
  it("a non-owner cannot pick a role holding an owner-only capability, and sees the hint", async () => {
    const user = userEvent.setup();
    openPanel("admin", "Bookkeeper");
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    await user.click(screen.getByRole("combobox"));
    const partnerOption = screen.getByTestId("role-option-role-partner");
    expect(partnerOption).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByTestId("owner-only-role-hint-role-partner")).toHaveTextContent(HINT);
    expect(screen.getByTestId("role-option-role-bookkeeper")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
  });

  it("a non-owner cannot toggle an owner-only override, and sees the hint", async () => {
    openPanel("admin", "Bookkeeper");
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeDisabled();
    expect(signOff).toHaveAttribute("data-testid", `member-capability-${SIGN_OFF}`);
    expect(screen.getByTestId(`owner-only-hint-${SIGN_OFF}`)).toHaveTextContent(HINT);
    expect(screen.getByRole("checkbox", { name: "View Trust" })).toBeEnabled();
  });

  it("the owner can pick the role and toggle the override; no hint", async () => {
    const user = userEvent.setup();
    openPanel("owner", "Bookkeeper");
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    expect(screen.getByRole("checkbox", { name: SIGN_OFF_LABEL })).toBeEnabled();
    expect(screen.queryByTestId(`owner-only-hint-${SIGN_OFF}`)).not.toBeInTheDocument();
    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-role-partner")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(screen.queryByTestId("owner-only-role-hint-role-partner")).not.toBeInTheDocument();
  });
});

// --- the invite form (role + "customize for this user") ---

const inviteProps = {
  maxMembers: 10,
  currentMembers: 2,
  orgSlug: "acme",
  roles: [bookkeeper, partner],
};

describe("InviteMemberForm — owner-only capabilities", () => {
  it("a non-owner cannot pick a role holding an owner-only capability, and sees the hint", async () => {
    const user = userEvent.setup();
    asCaller("admin", <InviteMemberForm {...inviteProps} />);

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-role-partner")).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByTestId("owner-only-role-hint-role-partner")).toHaveTextContent(HINT);
    expect(screen.getByTestId("role-option-role-bookkeeper")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
  });

  it("a non-owner cannot add an owner-only capability when customizing", async () => {
    const user = userEvent.setup();
    asCaller("admin", <InviteMemberForm {...inviteProps} />);

    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByTestId("role-option-role-bookkeeper"));
    await user.click(screen.getByText("Customize for this user"));

    const signOff = await screen.findByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeDisabled();
    expect(signOff).toHaveAttribute("data-testid", `invite-capability-${SIGN_OFF}`);
    expect(screen.getByTestId(`owner-only-hint-${SIGN_OFF}`)).toHaveTextContent(HINT);
  });

  it("the owner can pick the role; no hint", async () => {
    const user = userEvent.setup();
    asCaller("owner", <InviteMemberForm {...inviteProps} />);

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-role-partner")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(screen.queryByTestId("owner-only-role-hint-role-partner")).not.toBeInTheDocument();
  });

  // Task C: the system Admin role is invited by the owner only; the Owner role is never offered
  it("a non-owner sees the Admin role disabled with the hint; no Owner role", async () => {
    const user = userEvent.setup();
    asCaller("admin", <InviteMemberForm {...inviteProps} />);

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-system-admin")).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByTestId("owner-only-role-hint-system-admin")).toHaveTextContent(HINT);
    expect(screen.getByRole("option", { name: "Member" })).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(screen.queryByRole("option", { name: /Owner/ })).not.toBeInTheDocument();
  });

  it("the owner can pick the Admin role; no hint; no Owner role", async () => {
    const user = userEvent.setup();
    asCaller("owner", <InviteMemberForm {...inviteProps} />);

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-system-admin")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(screen.queryByTestId("owner-only-role-hint-system-admin")).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Owner/ })).not.toBeInTheDocument();
  });
});
