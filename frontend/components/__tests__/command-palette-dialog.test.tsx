import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));

// Mock motion/react — avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock recent-items-provider — control recentItems in tests
vi.mock("@/components/recent-items-provider", () => ({
  useRecentItems: vi.fn(() => ({ items: [], addItem: vi.fn() })),
}));

import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CommandPaletteDialog } from "@/components/command-palette-dialog";
import { useRecentItems } from "@/components/recent-items-provider";
import { useRouter } from "next/navigation";

beforeEach(() => {
  vi.mocked(useRecentItems).mockReturnValue({ items: [], addItem: vi.fn() });
});

afterEach(() => {
  cleanup();
});

const ALL_CAPABILITIES = [
  "FINANCIAL_VISIBILITY",
  "INVOICING",
  "PROJECT_MANAGEMENT",
  "TEAM_OVERSIGHT",
  "CUSTOMER_MANAGEMENT",
  "AUTOMATIONS",
];

function renderDialog({
  capabilities = ALL_CAPABILITIES,
  isAdmin = false,
  isOwner = true,
  role = "Owner",
  enabledModules = [] as string[],
}: {
  capabilities?: string[];
  isAdmin?: boolean;
  isOwner?: boolean;
  role?: string;
  enabledModules?: string[];
} = {}) {
  return render(
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={enabledModules}
      terminologyNamespace={null}
    >
      <CapabilityProvider
        capabilities={capabilities}
        role={role}
        isAdmin={isAdmin}
        isOwner={isOwner}
      >
        <CommandPaletteDialog slug="test-org" open={true} onOpenChange={vi.fn()} />
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

describe("CommandPaletteDialog", () => {
  it("renders both group headings", () => {
    renderDialog();
    // Use getAllByText since "Settings" appears as both group heading and nav item
    const pagesHeadings = screen.getAllByText("Pages");
    expect(pagesHeadings.length).toBeGreaterThan(0);
    const settingsHeadings = screen.getAllByText("Settings");
    expect(settingsHeadings.length).toBeGreaterThan(0);
  });

  it("renders a capability-filtered nav item — Fee notes hidden when user has no capabilities", () => {
    renderDialog({ capabilities: [], isAdmin: false, isOwner: false, role: "member" });
    expect(screen.queryByText("Fee notes")).not.toBeInTheDocument();
  });

  it("renders Pages items for user with capabilities — Home is visible", () => {
    renderDialog({ capabilities: ALL_CAPABILITIES, isAdmin: false, isOwner: true });
    expect(screen.getByText("Home")).toBeInTheDocument();
  });

  // Phase 87 S6a (612A, OI-3): the palette applies the same adminOnly rule as the sidebar.
  it("omits the adminOnly Audit trail page for a non-admin member", () => {
    renderDialog({
      capabilities: ALL_CAPABILITIES,
      isAdmin: false,
      isOwner: false,
      role: "member",
    });
    expect(screen.queryByText("Audit trail")).not.toBeInTheDocument();
  });

  it("shows the adminOnly Audit trail page to an owner", () => {
    renderDialog({ capabilities: ALL_CAPABILITIES, isAdmin: false, isOwner: true });
    expect(screen.getByText("Audit trail")).toBeInTheDocument();
  });

  // Phase 87 S6a (612A, OI-2): any-of gate — AIVERIFY-011 members keep New matter (AI intake).
  it("shows New matter to a member with AI_EXECUTE but no PROJECT_MANAGEMENT", () => {
    renderDialog({ capabilities: ["AI_EXECUTE"], isAdmin: false, isOwner: false, role: "member" });
    expect(screen.getByText("New matter")).toBeInTheDocument();
  });

  it("hides New matter from a member with neither PROJECT_MANAGEMENT nor AI_EXECUTE", () => {
    renderDialog({ capabilities: ["INVOICING"], isAdmin: false, isOwner: false, role: "member" });
    expect(screen.queryByText("New matter")).not.toBeInTheDocument();
  });

  it("omits comingSoon settings items — Security is not in DOM", () => {
    renderDialog();
    expect(screen.queryByText("Security")).not.toBeInTheDocument();
  });

  it("shows Recent group heading when recentItems is non-empty", () => {
    vi.mocked(useRecentItems).mockReturnValue({
      items: [{ href: "/org/test-org/matters/abc", label: "Alpha Project" }],
      addItem: vi.fn(),
    });
    renderDialog();
    const recentHeadings = screen.getAllByText("Recent");
    expect(recentHeadings.length).toBeGreaterThan(0);
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
  });

  it("navigates the Matters page item to the renamed /matters route (Phase 87 610E)", async () => {
    const paletteMockPush = vi.fn();
    vi.mocked(useRouter).mockReturnValue({ push: paletteMockPush } as unknown as ReturnType<
      typeof useRouter
    >);
    renderDialog();
    await userEvent.setup().click(screen.getByText("Matters"));
    expect(paletteMockPush).toHaveBeenCalledWith("/org/test-org/matters");
  });

  // Phase 87 612B.1 (ADR-339 §12.6.1): the palette applies the sidebar's module gate to pages.
  it("hides module-gated pages (Disbursements, Trust reports) when their modules are off", () => {
    renderDialog({ enabledModules: [] });
    expect(screen.queryByText("Disbursements")).not.toBeInTheDocument();
    expect(screen.queryByText("Trust reports")).not.toBeInTheDocument();
  });

  it("shows module-gated pages when their modules are on", () => {
    renderDialog({ enabledModules: ["disbursements", "trust_accounting"] });
    expect(screen.getByText("Disbursements")).toBeInTheDocument();
    expect(screen.getByText("Trust reports")).toBeInTheDocument();
  });

  // Phase 87 S9 (616E, ADR-341 §12.7.3): the Settings group is SETTINGS_FAMILIES × tabs, filtered
  // by the same rule as the family pages and the settings rail (lib/nav-filter.ts).
  describe("Settings entries — one nav source (616E)", () => {
    const settingsEntries = () =>
      screen
        .getAllByRole("option")
        .map((o) => o.textContent ?? "")
        .filter((t) => t.startsWith("Settings › "));

    it("labels each entry Settings › {Family} › {Tab}; an owner with no modules sees 29", () => {
      renderDialog({ capabilities: [], isAdmin: false, isOwner: true, enabledModules: [] });
      const entries = settingsEntries();
      expect(entries).toHaveLength(29);
      expect(entries[0]).toBe("Settings › Firm › General");
      expect(entries).toContain("Settings › Firm › Roles");
      expect(entries).not.toContain("Settings › Fees & Trust › Fee runs");
      expect(entries).not.toContain("Settings › Fees & Trust › Trust accounting");
    });

    it("shows the module-gated tabs to an owner with bulk_billing + trust_accounting (31)", () => {
      renderDialog({
        capabilities: [],
        isAdmin: false,
        isOwner: true,
        enabledModules: ["bulk_billing", "trust_accounting"],
      });
      const entries = settingsEntries();
      expect(entries).toHaveLength(31);
      expect(entries).toContain("Settings › Fees & Trust › Fee runs");
      expect(entries).toContain("Settings › Fees & Trust › Trust accounting");
    });

    it("shows a plain member exactly the 9 ungated tabs, in family/tab order", () => {
      renderDialog({
        capabilities: [],
        isAdmin: false,
        isOwner: false,
        role: "member",
        enabledModules: ["bulk_billing", "trust_accounting"],
      });
      expect(settingsEntries()).toEqual([
        "Settings › Firm › General",
        "Settings › Firm › Subscription",
        "Settings › Matters › Templates",
        "Settings › Matters › Tags",
        "Settings › Documents › Templates",
        "Settings › Documents › Clauses",
        "Settings › Fees & Trust › Rates",
        "Settings › Connections › Integrations",
        "Settings › Connections › Notifications",
      ]);
    });

    it("hides admin-only Fee runs from a member even with bulk_billing on (owner sees it)", () => {
      renderDialog({
        capabilities: ALL_CAPABILITIES,
        isAdmin: false,
        isOwner: false,
        role: "member",
        enabledModules: ["bulk_billing"],
      });
      expect(screen.queryByText("Settings › Fees & Trust › Fee runs")).not.toBeInTheDocument();
      cleanup();
      renderDialog({ isAdmin: false, isOwner: true, enabledModules: ["bulk_billing"] });
      expect(screen.getByText("Settings › Fees & Trust › Fee runs")).toBeInTheDocument();
    });

    it("shows admin-only tabs to an owner who is not flagged admin — Settings › Firm › Roles", () => {
      renderDialog({ capabilities: ALL_CAPABILITIES, isAdmin: false, isOwner: true });
      expect(screen.getByText("Settings › Firm › Roles")).toBeInTheDocument();
    });

    it("shows Settings › Firm › Audit trail to a member holding TEAM_OVERSIGHT (D5)", () => {
      renderDialog({
        capabilities: ["TEAM_OVERSIGHT"],
        isAdmin: false,
        isOwner: false,
        role: "member",
      });
      expect(screen.getByText("Settings › Firm › Audit trail")).toBeInTheDocument();
      cleanup();
      renderDialog({ capabilities: [], isAdmin: false, isOwner: false, role: "member" });
      expect(screen.queryByText("Settings › Firm › Audit trail")).not.toBeInTheDocument();
    });

    it("navigates an entry to its family ?tab= URL", async () => {
      const settingsMockPush = vi.fn();
      vi.mocked(useRouter).mockReturnValue({ push: settingsMockPush } as unknown as ReturnType<
        typeof useRouter
      >);
      renderDialog();
      await userEvent.setup().click(screen.getByText("Settings › Fees & Trust › Rates"));
      expect(settingsMockPush).toHaveBeenCalledWith("/org/test-org/settings/fees?tab=tariffs");
    });

    it("disambiguates the two Templates tabs by family", async () => {
      const templatesMockPush = vi.fn();
      vi.mocked(useRouter).mockReturnValue({ push: templatesMockPush } as unknown as ReturnType<
        typeof useRouter
      >);
      renderDialog();
      const user = userEvent.setup();
      await user.click(screen.getByText("Settings › Matters › Templates"));
      expect(templatesMockPush).toHaveBeenLastCalledWith(
        "/org/test-org/settings/matters?tab=templates"
      );
      await user.click(screen.getByText("Settings › Documents › Templates"));
      expect(templatesMockPush).toHaveBeenLastCalledWith(
        "/org/test-org/settings/documents?tab=templates"
      );
    });

    it("never lists Security or a Coming soon entry", () => {
      renderDialog({ enabledModules: ["bulk_billing", "trust_accounting"] });
      expect(settingsEntries().some((t) => /security|coming soon/i.test(t))).toBe(false);
    });
  });
});
