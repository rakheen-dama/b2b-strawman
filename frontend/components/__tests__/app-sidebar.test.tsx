import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { mobile, mockSetPaletteOpen } = vi.hoisted(() => ({
  mobile: { value: false },
  mockSetPaletteOpen: vi.fn(),
}));

vi.mock("@/hooks/use-mobile", () => ({ useIsMobile: () => mobile.value }));

vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useSearchParams: vi.fn(() => new URLSearchParams()),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));

// SidebarUserFooter uses auth hooks
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

vi.mock("@/components/command-palette-provider", () => ({
  useCommandPalette: () => ({ open: false, setOpen: mockSetPaletteOpen }),
}));

import { usePathname, useSearchParams } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { SidebarProvider, SidebarTrigger } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/shell/app-sidebar";
import { NAV_GROUPS } from "@/lib/nav-items";

const mockUsePathname = vi.mocked(usePathname);
const mockUseSearchParams = vi.mocked(useSearchParams);

// All capabilities + legal modules — every zone and item renders (Trust needs VIEW_TRUST +
// trust_accounting).
const ALL_CAPABILITIES = [
  "FINANCIAL_VISIBILITY",
  "INVOICING",
  "PROJECT_MANAGEMENT",
  "TEAM_OVERSIGHT",
  "CUSTOMER_MANAGEMENT",
  "AUTOMATIONS",
  "VIEW_TRUST",
  "AI_REVIEW",
];
const LEGAL_MODULES = [
  "court_calendar",
  "conflict_check",
  "information_requests",
  "bulk_billing",
  "disbursements",
  "lssa_tariff",
  "trust_accounting",
];

const ZONE_LABELS = NAV_GROUPS.map((g) => g.label);
const ALL_ITEM_LABELS = NAV_GROUPS.flatMap((g) => g.items.map((i) => i.label));

interface RenderOptions {
  capabilities?: string[];
  isAdmin?: boolean;
  isOwner?: boolean;
  modules?: string[];
  defaultOpen?: boolean;
  brandColor?: string;
  /** Rendered inside the provider; stands in for a focused editor that handles a key itself. */
  extra?: React.ReactNode;
}

function renderSidebar({
  capabilities = ALL_CAPABILITIES,
  isAdmin = false,
  isOwner = true,
  modules = LEGAL_MODULES,
  defaultOpen = true,
  brandColor,
  extra,
}: RenderOptions = {}) {
  return render(
    <OrgProfileProvider verticalProfile={null} enabledModules={modules} terminologyNamespace={null}>
      <CapabilityProvider
        capabilities={capabilities}
        role={isOwner ? "Owner" : isAdmin ? "Admin" : "Member"}
        isAdmin={isAdmin}
        isOwner={isOwner}
      >
        <SidebarProvider defaultOpen={defaultOpen}>
          <AppSidebar slug="test-org" orgName="Mathebula & Partners" brandColor={brandColor} />
          <SidebarTrigger />
          {extra}
        </SidebarProvider>
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

const nav = () => screen.getByRole("navigation", { name: "Main navigation" });
const navLinkLabels = () =>
  within(nav())
    .getAllByRole("link")
    .map((link) => link.textContent);
const sidebarState = () =>
  document.querySelector('[data-slot="sidebar"]')?.getAttribute("data-state");

async function expandCollapsedZones(user: ReturnType<typeof userEvent.setup>) {
  for (const zone of NAV_GROUPS.filter((g) => !g.defaultExpanded)) {
    await user.click(within(nav()).getByRole("button", { name: zone.label }));
  }
}

beforeEach(() => {
  mobile.value = false;
  mockSetPaletteOpen.mockClear();
  mockUsePathname.mockReturnValue("/org/test-org/other");
  mockUseSearchParams.mockReturnValue(
    new URLSearchParams() as unknown as ReturnType<typeof useSearchParams>
  );
});

afterEach(() => {
  cleanup();
});

describe("AppSidebar — zones and the Main navigation nav", () => {
  it("renders the zone triggers inside the nav in NAV_GROUPS order, each with aria-expanded", () => {
    renderSidebar();
    const triggers = within(nav()).getAllByRole("button");
    expect(triggers.map((b) => b.textContent)).toEqual(ZONE_LABELS);
    for (const trigger of triggers) {
      expect(trigger).toHaveAttribute("aria-expanded");
    }
    // nav.locator("button[aria-expanded]") in sidebar-navigation.spec.ts: nothing else carries it.
    expect(nav().querySelectorAll("[aria-expanded]")).toHaveLength(ZONE_LABELS.length);
  });

  it("opens zones per NAV_GROUPS defaultExpanded", () => {
    renderSidebar();
    for (const zone of NAV_GROUPS) {
      expect(within(nav()).getByRole("button", { name: zone.label })).toHaveAttribute(
        "aria-expanded",
        String(zone.defaultExpanded)
      );
    }
  });

  it("holds exactly the ordered NAV_GROUPS links — no search, utility or admin entry", async () => {
    const user = userEvent.setup();
    renderSidebar();
    await expandCollapsedZones(user);

    expect(navLinkLabels()).toEqual(ALL_ITEM_LABELS);
    expect(
      within(nav()).queryByRole("button", { name: "Search, Command K" })
    ).not.toBeInTheDocument();
    expect(within(nav()).queryByRole("link", { name: "Notifications" })).not.toBeInTheDocument();
  });

  it("toggles a zone's aria-expanded and its items on trigger click", async () => {
    const user = userEvent.setup();
    renderSidebar();
    const today = within(nav()).getByRole("button", { name: "Today" });

    expect(today).toHaveAttribute("aria-expanded", "true");
    expect(within(nav()).getByRole("link", { name: "Home" })).toBeInTheDocument();

    await user.click(today);
    expect(today).toHaveAttribute("aria-expanded", "false");
    expect(within(nav()).queryByRole("link", { name: "Home" })).not.toBeInTheDocument();

    await user.click(today);
    expect(today).toHaveAttribute("aria-expanded", "true");
    expect(within(nav()).getByRole("link", { name: "Home" })).toBeInTheDocument();
  });
});

describe("AppSidebar — gating", () => {
  it("hides a zone whose items all lack the required capability; honours any-of gates", () => {
    renderSidebar({ capabilities: ["PROJECT_MANAGEMENT"], isOwner: false, modules: [] });
    expect(within(nav()).queryByRole("button", { name: "Clients" })).not.toBeInTheDocument();
    expect(within(nav()).queryByRole("link", { name: "Clients" })).not.toBeInTheDocument();
    cleanup();

    renderSidebar({ capabilities: ["AI_EXECUTE"], isOwner: false, modules: [] });
    expect(within(nav()).getByRole("link", { name: "New matter" })).toBeInTheDocument();
  });

  it("hides requiredModule items until the module is enabled", () => {
    renderSidebar({ modules: [] });
    expect(within(nav()).queryByRole("link", { name: "Conflict check" })).not.toBeInTheDocument();
    expect(within(nav()).queryByRole("link", { name: "Adverse parties" })).not.toBeInTheDocument();
    cleanup();

    renderSidebar({ modules: ["conflict_check"] });
    expect(within(nav()).getByRole("link", { name: "Conflict check" })).toBeInTheDocument();
    expect(within(nav()).getByRole("link", { name: "Adverse parties" })).toBeInTheDocument();
  });

  it("shows adminOnly items to admins only", async () => {
    const user = userEvent.setup();
    renderSidebar({ capabilities: ["FINANCIAL_VISIBILITY"], isOwner: false });
    await user.click(within(nav()).getByRole("button", { name: "Firm" }));
    expect(within(nav()).getByRole("link", { name: "Settings" })).toBeInTheDocument();
    expect(within(nav()).queryByRole("link", { name: "Audit trail" })).not.toBeInTheDocument();
    cleanup();

    renderSidebar({ capabilities: ["FINANCIAL_VISIBILITY"], isOwner: false, isAdmin: true });
    await user.click(within(nav()).getByRole("button", { name: "Firm" }));
    expect(within(nav()).getByRole("link", { name: "Audit trail" })).toBeInTheDocument();
  });
});

describe("AppSidebar — active item (longest match → aria-current)", () => {
  const activeLinks = () =>
    within(nav())
      .getAllByRole("link")
      .filter((link) => link.getAttribute("aria-current") === "page")
      .map((link) => link.textContent);

  it.each([
    ["/org/test-org/invoices/abc", "", ["Fee notes"]],
    ["/org/test-org/invoices/billing-runs/x", "", ["Fee runs"]],
    ["/org/test-org/invoices/collections", "", ["Debtors"]],
    ["/org/test-org/invoices/unbilled", "", ["Unbilled work"]],
    ["/org/test-org/matters/new", "", ["New matter"]],
    ["/org/test-org/matters/123", "", ["Matters"]],
    ["/org/test-org/matters-archive", "", []],
    ["/org/test-org/settings/firm", "tab=audit-trail", ["Audit trail"]],
    ["/org/test-org/settings/firm", "tab=audit-trail&severities=CRITICAL", ["Audit trail"]],
    ["/org/test-org/settings/firm", "tab=general", ["Settings"]],
    ["/org/test-org/settings/firm", "", ["Settings"]],
  ])("%s?%s → %j", async (pathname, search, expected) => {
    mockUsePathname.mockReturnValue(pathname);
    mockUseSearchParams.mockReturnValue(
      new URLSearchParams(search) as unknown as ReturnType<typeof useSearchParams>
    );
    const user = userEvent.setup();
    renderSidebar({ isAdmin: true });
    await expandCollapsedZones(user);

    expect(activeLinks()).toEqual(expected);
  });
});

describe("AppSidebar — collapsed icon rail", () => {
  it("keeps every item's accessible name and shows every zone's items", () => {
    renderSidebar({ defaultOpen: false });
    expect(sidebarState()).toBe("collapsed");

    // Fees/Trust/Firm default closed, but the rail shows their items (zone headers → separators).
    expect(within(nav()).getByRole("link", { name: "Home" })).toBeInTheDocument();
    expect(within(nav()).getByRole("link", { name: "Fee notes" })).toBeInTheDocument();
    expect(navLinkLabels()).toEqual(ALL_ITEM_LABELS);
  });

  it("restores the expanded-mode zone state when the rail expands again", async () => {
    const user = userEvent.setup();
    renderSidebar({ defaultOpen: false });

    await user.click(screen.getByRole("button", { name: "Toggle menu" }));
    expect(sidebarState()).toBe("expanded");
    expect(within(nav()).getByRole("button", { name: "Fees" })).toHaveAttribute(
      "aria-expanded",
      "false"
    );
    expect(within(nav()).queryByRole("link", { name: "Fee notes" })).not.toBeInTheDocument();
  });
});

describe("AppSidebar — ⌘/Ctrl+B", () => {
  it("toggles on a plain ⌘B / Ctrl+B and ignores one an editor already handled", () => {
    renderSidebar({
      extra: (
        // Stands in for Tiptap/ProseMirror, which preventDefault()s Mod-b (bold).
        <div data-testid="editor" tabIndex={0} onKeyDown={(e) => e.preventDefault()} />
      ),
    });
    expect(sidebarState()).toBe("expanded");

    fireEvent.keyDown(screen.getByTestId("editor"), { key: "b", metaKey: true });
    expect(sidebarState()).toBe("expanded");

    fireEvent.keyDown(window, { key: "b", metaKey: true });
    expect(sidebarState()).toBe("collapsed");

    fireEvent.keyDown(window, { key: "b", ctrlKey: true });
    expect(sidebarState()).toBe("expanded");
  });
});

describe("AppSidebar — mobile Sheet", () => {
  it('opens via the "Toggle menu" trigger and closes on navigation', async () => {
    mobile.value = true;
    const user = userEvent.setup();
    renderSidebar();

    expect(screen.queryByRole("navigation", { name: "Main navigation" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Toggle menu" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("navigation", { name: "Main navigation" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("link", { name: "Home" }));
    await waitFor(() =>
      expect(screen.queryByRole("navigation", { name: "Main navigation" })).not.toBeInTheDocument()
    );
  });

  it("closes on a utility footer link click", async () => {
    mobile.value = true;
    const user = userEvent.setup();
    renderSidebar();

    await user.click(screen.getByRole("button", { name: "Toggle menu" }));
    await user.click(
      within(screen.getByRole("dialog")).getByRole("link", { name: "Notifications" })
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });
});

describe("AppSidebar — header and footer", () => {
  it("renders the Notifications utility link in the footer, outside the nav", () => {
    renderSidebar();
    expect(screen.getByRole("link", { name: "Notifications" })).toHaveAttribute(
      "href",
      "/org/test-org/notifications"
    );
    expect(within(nav()).queryByRole("link", { name: "Notifications" })).not.toBeInTheDocument();
  });

  it("opens the command palette from the search pill (outside the nav)", async () => {
    const user = userEvent.setup();
    renderSidebar();
    const pill = screen.getByRole("button", { name: "Search, Command K" });
    expect(nav()).not.toContainElement(pill);

    await user.click(pill);
    expect(mockSetPaletteOpen).toHaveBeenCalledWith(true);
  });

  it("renders the org name with the brand accent bar and injects --brand-color on <html>", () => {
    const { unmount } = renderSidebar({ brandColor: "#1B3A4B" });
    expect(screen.getByText("Mathebula & Partners")).toBeInTheDocument();

    const accent = screen.getByTestId("org-brand-accent");
    expect(accent).toHaveAttribute("aria-hidden", "true");
    expect(accent.style.backgroundColor).toBe("var(--brand-color)");
    expect(document.documentElement.style.getPropertyValue("--brand-color")).toBe("#1B3A4B");

    unmount();
    expect(document.documentElement.style.getPropertyValue("--brand-color")).toBe("");
  });
});
