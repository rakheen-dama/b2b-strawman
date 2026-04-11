import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

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
import RolesSettingsPage from "@/app/(app)/org/[slug]/settings/roles/page";
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
  const result = await RolesSettingsPage({
    params: Promise.resolve({ slug: "acme" }),
  });
  render(result);
}

describe("RolesSettingsPage", () => {
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

    // Capability pills (underscores replaced with spaces)
    // "FINANCIAL VISIBILITY" appears on Owner, Admin, and Bookkeeper cards (3 total)
    const financialBadges = screen.getAllByText("FINANCIAL VISIBILITY");
    expect(financialBadges).toHaveLength(3);
    // "INVOICING" appears on Owner, Admin, and Bookkeeper cards (3 total)
    const invoicingBadges = screen.getAllByText("INVOICING");
    expect(invoicingBadges).toHaveLength(3);
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
});
