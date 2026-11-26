import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// Mock capabilities API
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: [],
    role: "Admin",
    isAdmin: true,
    isOwner: false,
  }),
}));

// Mock org-roles API
const mockFetchOrgRoles = vi.fn();

vi.mock("@/lib/api/org-roles", () => ({
  fetchOrgRoles: (...args: unknown[]) => mockFetchOrgRoles(...args),
}));

// Mock next/link to render plain <a> tags
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Mock next/navigation
vi.mock("next/navigation", () => ({
  notFound: vi.fn(),
}));

// Must import page AFTER vi.mock declarations
import { renderRolesTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/roles";
import type { OrgRole } from "@/lib/api/org-roles";

function makeSystemRole(name: string, memberCount: number, capabilities: string[] = []): OrgRole {
  return {
    id: `sys-${name.toLowerCase()}`,
    name,
    slug: name.toLowerCase(),
    description: null,
    capabilities,
    isSystem: true,
    memberCount,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

function makeCustomRole(overrides: Partial<OrgRole> = {}): OrgRole {
  return {
    id: "custom-1",
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

function defaultRoles(): OrgRole[] {
  return [
    makeSystemRole("Owner", 1),
    makeSystemRole("Admin", 2),
    makeSystemRole("Member", 5),
    makeCustomRole(),
  ];
}

async function renderPage(roles: OrgRole[] = defaultRoles()) {
  mockFetchOrgRoles.mockResolvedValue(roles);
  const result = await renderRolesTab({ slug: "acme", searchParams: {} });
  render(result);
}

describe("Firm › Roles tab loader", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders system roles as read-only without edit/delete buttons", async () => {
    await renderPage();

    // System roles are displayed
    expect(screen.getByText("Owner")).toBeInTheDocument();
    expect(screen.getByText("Admin")).toBeInTheDocument();
    expect(screen.getByText("Member")).toBeInTheDocument();

    // System badges are shown
    const systemBadges = screen.getAllByText("System");
    expect(systemBadges).toHaveLength(3);

    // No edit/delete buttons on system roles
    expect(screen.queryByLabelText("Edit Owner")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Delete Owner")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Edit Admin")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Delete Admin")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Edit Member")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Delete Member")).not.toBeInTheDocument();
  });

  it("renders custom roles with capability pills", async () => {
    await renderPage();

    expect(screen.getByText("Bookkeeper")).toBeInTheDocument();
    expect(screen.getByText("Can manage invoices and view financials")).toBeInTheDocument();

    // Capability pills show the CAPABILITY_META label, not the enum value (628A).
    // "Financial Visibility" appears on Owner, Admin, and Bookkeeper cards (3 total)
    const financialBadges = screen.getAllByText("Financial Visibility");
    expect(financialBadges).toHaveLength(3);
    // INVOICING → "Billing" on Owner, Admin, and Bookkeeper cards (3 total)
    const invoicingBadges = screen.getAllByText("Billing");
    expect(invoicingBadges).toHaveLength(3);
    // PROJECT_MANAGEMENT / CUSTOMER_MANAGEMENT → legal labels on Owner and Admin (2 each)
    expect(screen.getAllByText("Matter Management")).toHaveLength(2);
    expect(screen.getAllByText("Client Management")).toHaveLength(2);
    expect(screen.queryByText("INVOICING")).not.toBeInTheDocument();
    expect(screen.queryByText("PROJECT MANAGEMENT")).not.toBeInTheDocument();
  });

  it("custom role card falls back to the spaced enum for an unknown capability", async () => {
    await renderPage([makeCustomRole({ capabilities: ["PROJECT_MANAGEMENT", "SOME_NEW_CAP"] })]);

    expect(screen.getByText("Matter Management")).toBeInTheDocument();
    expect(screen.getByText("SOME NEW CAP")).toBeInTheDocument();
  });

  it("renders member count per role", async () => {
    await renderPage();

    // System roles show member counts
    expect(screen.getByText("1 member")).toBeInTheDocument();
    expect(screen.getByText("2 members")).toBeInTheDocument();
    expect(screen.getByText("5 members")).toBeInTheDocument();

    // Custom role shows member count
    expect(screen.getByText("3 members")).toBeInTheDocument();
  });

  it("renders New Role button", async () => {
    await renderPage();

    expect(screen.getByRole("button", { name: /new role/i })).toBeInTheDocument();
  });

  it("renders edit and delete actions on custom roles", async () => {
    await renderPage();

    // Edit and Delete buttons exist on the custom role card
    expect(screen.getByLabelText("Edit Bookkeeper")).toBeInTheDocument();
    expect(screen.getByLabelText("Delete Bookkeeper")).toBeInTheDocument();
  });

  it("665A: one h1 'Roles & Permissions' via PageHeader; System Roles and Custom Roles are h2 sections", async () => {
    await renderPage();

    const h1s = screen.getAllByRole("heading", { level: 1 });
    expect(h1s).toHaveLength(1);
    expect(h1s[0]).toHaveTextContent("Roles & Permissions");
    expect(h1s[0].closest('[data-slot="page-header"]')).not.toBeNull();

    const system = screen
      .getByRole("heading", { level: 2, name: "System Roles" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    expect(system).not.toBeNull();
    expect(
      within(system).getByText("Built-in roles that cannot be modified or deleted.")
    ).toBeVisible();
    for (const name of ["Owner", "Admin", "Member"]) {
      expect(within(system).getByRole("heading", { level: 3, name })).toBeVisible();
    }
    // Only one heading carries "System Roles" (e2e matches it as a substring).
    expect(screen.getAllByRole("heading", { name: /System Roles/ })).toHaveLength(1);

    const custom = screen
      .getByRole("heading", { level: 2, name: "Custom Roles" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    expect(custom).not.toBeNull();
    const newRole = within(custom).getByRole("button", { name: "New Role" });
    expect(newRole.closest('[data-slot="card-action"]')).not.toBeNull();
    expect(within(custom).getByRole("heading", { level: 3, name: "Bookkeeper" })).toBeVisible();

    // The capability reference sits in an untitled section; its trigger is unchanged.
    const reference = screen.getByRole("button", { name: "Capability Reference" });
    expect(reference.closest('[data-slot="settings-section"]')).not.toBeNull();
  });

  it("665A K-104: labels the three display-only capabilities and titles every badge with its full label", async () => {
    await renderPage([
      makeSystemRole("Owner", 1),
      makeSystemRole("Admin", 1),
      makeSystemRole("Member", 5, [
        "GENERATE_STATEMENT_OF_ACCOUNT",
        "VIEW_LEGAL",
        "MANAGE_DISBURSEMENTS",
      ]),
    ]);

    const memberCard = screen
      .getByRole("heading", { level: 3, name: "Member" })
      .closest("div.rounded-lg") as HTMLElement;
    for (const label of [
      "Generate statement of account",
      "View legal registers",
      "Manage disbursements",
    ]) {
      const text = within(memberCard).getByText(label);
      const badge = text.closest('[data-slot="badge"]') as HTMLElement;
      expect(badge).toHaveAttribute("title", label);
    }
    expect(within(memberCard).queryByText("GENERATE STATEMENT OF ACCOUNT")).not.toBeInTheDocument();
    expect(within(memberCard).queryByText("VIEW LEGAL")).not.toBeInTheDocument();
  });

  it("665A K-104: custom role badges carry their label as the title too", async () => {
    await renderPage();

    const custom = screen
      .getByRole("heading", { level: 2, name: "Custom Roles" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    const badge = within(custom).getByText("Financial Visibility").closest('[data-slot="badge"]');
    expect(badge).toHaveAttribute("title", "Financial Visibility");
  });
});
