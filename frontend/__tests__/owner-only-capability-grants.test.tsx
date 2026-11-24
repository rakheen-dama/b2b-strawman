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

  // review round 2: additions are locked, removals are not (the backend allows taking one away)
  it("a non-owner may uncheck an owner-only capability the role holds, but not check it again", async () => {
    const user = userEvent.setup();
    mockUpdateRoleAction.mockResolvedValue({ success: true });
    asCaller("admin", <RoleDialog slug="acme" role={partner} open={true} onOpenChange={vi.fn()} />);

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeEnabled();
    expect(signOff).toHaveAttribute("data-state", "checked");
    await user.click(signOff);
    expect(signOff).toHaveAttribute("data-state", "unchecked");
    expect(signOff).toBeDisabled(); // unchecked: re-checking would grant it

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(mockUpdateRoleAction).toHaveBeenCalled());
    expect(mockUpdateRoleAction.mock.calls[0][2].capabilities).toEqual(["VIEW_TRUST"]);
  });

  it("a non-owner cannot check an unchecked owner-only capability", async () => {
    const user = userEvent.setup();
    asCaller(
      "admin",
      <RoleDialog slug="acme" role={bookkeeper} open={true} onOpenChange={vi.fn()} />
    );

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeDisabled();
    await user.click(signOff);
    expect(signOff).toHaveAttribute("data-state", "unchecked");
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

function openPanel(kind: "owner" | "admin", currentRole: string, roles = [bookkeeper, partner]) {
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
      roles={roles}
      slug="acme"
    />
  );
}

// Task C follow-through: the system Admin role holds no owner-only capability, but only the owner
// may assign it (OrgRoleService.requireSystemRoleGrantable), so it is locked for a non-owner too.
const systemAdmin = {
  ...bookkeeper,
  id: "role-system-admin",
  name: "Admin",
  slug: "admin",
  isSystem: true,
  capabilities: ["TEAM_OVERSIGHT"],
};

describe("MemberDetailPanel — the system Admin role", () => {
  it("a non-owner cannot pick the system Admin role, and sees the hint", async () => {
    const user = userEvent.setup();
    openPanel("admin", "Bookkeeper", [systemAdmin, bookkeeper]);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-role-system-admin")).toHaveAttribute(
      "aria-disabled",
      "true"
    );
    expect(screen.getByTestId("owner-only-role-hint-role-system-admin")).toHaveTextContent(HINT);
  });

  it("the owner can pick the system Admin role", async () => {
    const user = userEvent.setup();
    openPanel("owner", "Bookkeeper", [systemAdmin, bookkeeper]);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    await user.click(screen.getByRole("combobox"));
    expect(screen.getByTestId("role-option-role-system-admin")).not.toHaveAttribute(
      "aria-disabled",
      "true"
    );
  });
});

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

// --- review round 1 ---

// The hint must not become part of the option's text: Radix copies an item's ItemText into the
// select trigger, which showed "PartnerOnly the owner can grant this" for a selected locked role.
describe("Role select trigger — a selected locked role shows its name only", () => {
  it("a member already on a Partner role, seen by a non-owner", async () => {
    openPanel("admin", "Partner");
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Partner"));
    expect(screen.getByRole("combobox").textContent).toBe("Partner");
  });

  it("a member on the system Admin role, seen by a non-owner", async () => {
    openPanel("admin", "Admin", [bookkeeper, partner, systemAdmin]);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Admin"));
    expect(screen.getByRole("combobox").textContent).toBe("Admin");
  });
});

// The hint is the disabled checkbox's accessible description (its aria-label names it).
describe("Owner-only hint — tied to its disabled checkbox", () => {
  it("in the role dialog", () => {
    asCaller("admin", <RoleDialog slug="acme" open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByRole("checkbox", { name: SIGN_OFF_LABEL })).toHaveAccessibleDescription(
      HINT
    );
  });

  it("in the member detail panel", async () => {
    openPanel("admin", "Bookkeeper");
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));
    expect(screen.getByRole("checkbox", { name: SIGN_OFF_LABEL })).toHaveAccessibleDescription(
      HINT
    );
  });

  it("in the invite form", async () => {
    const user = userEvent.setup();
    asCaller("admin", <InviteMemberForm {...inviteProps} />);
    await user.click(screen.getByRole("combobox"));
    await user.click(screen.getByTestId("role-option-role-bookkeeper"));
    await user.click(screen.getByText("Customize for this user"));
    expect(
      await screen.findByRole("checkbox", { name: SIGN_OFF_LABEL })
    ).toHaveAccessibleDescription(HINT);
  });

  it("an ordinary capability has no description", () => {
    asCaller("admin", <RoleDialog slug="acme" open={true} onOpenChange={vi.fn()} />);
    expect(screen.getByRole("checkbox", { name: "Billing" })).not.toHaveAttribute(
      "aria-describedby"
    );
  });
});

// review round 2: the member panel follows the backend's effective-capabilities rule — a
// non-owner may remove an owner-only capability (drop a "+X", add a "-X" for an inherited X) but
// not grant one (add a "+X", drop an existing "-X")
function openPanelWith(currentRole: string, overrides: string[]) {
  mockFetchMemberCapabilities.mockResolvedValue({
    memberId: "m1",
    roleName: currentRole,
    roleCapabilities: [],
    overrides,
    effectiveCapabilities: [],
  });
  mockAssignMemberRole.mockResolvedValue({ success: true });
  return asCaller(
    "admin",
    <MemberDetailPanel
      open={true}
      onOpenChange={vi.fn()}
      member={{ id: "m1", name: "Thandi", email: "thandi@test.com", role: "org:member" }}
      roles={[bookkeeper, partner]}
      slug="acme"
    />
  );
}

describe("MemberDetailPanel — a non-owner removes owner-only capabilities, never grants them", () => {
  it("adds a -X for an inherited owner-only capability", async () => {
    const user = userEvent.setup();
    openPanelWith("Partner", []);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Partner"));

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toBeEnabled();
    await user.click(signOff);
    expect(signOff).toHaveAttribute("data-state", "unchecked");
    expect(signOff).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(mockAssignMemberRole).toHaveBeenCalledWith("acme", "m1", "role-partner", [
        `-${SIGN_OFF}`,
      ])
    );
  });

  it("drops a +X override", async () => {
    const user = userEvent.setup();
    openPanelWith("Bookkeeper", [`+${SIGN_OFF}`]);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));

    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toHaveAttribute("data-state", "checked");
    expect(signOff).toBeEnabled();
    await user.click(signOff);

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() =>
      expect(mockAssignMemberRole).toHaveBeenCalledWith("acme", "m1", "role-bookkeeper", [])
    );
  });

  it("cannot add a +X", async () => {
    openPanelWith("Bookkeeper", []);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Bookkeeper"));
    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toHaveAttribute("data-state", "unchecked");
    expect(signOff).toBeDisabled();
  });

  it("cannot drop an existing -X", async () => {
    openPanelWith("Partner", [`-${SIGN_OFF}`]);
    await waitFor(() => expect(screen.getByRole("combobox")).toHaveTextContent("Partner"));
    const signOff = screen.getByRole("checkbox", { name: SIGN_OFF_LABEL });
    expect(signOff).toHaveAttribute("data-state", "unchecked");
    expect(signOff).toBeDisabled();
  });
});
