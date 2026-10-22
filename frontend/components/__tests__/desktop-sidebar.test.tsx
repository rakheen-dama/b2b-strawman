import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useSearchParams: () => new URLSearchParams(),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
}));

// Mock motion/react — avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock recent-items-provider — CommandPaletteDialog uses useRecentItems
vi.mock("@/components/recent-items-provider", () => ({
  useRecentItems: vi.fn(() => ({ items: [], addItem: vi.fn() })),
}));

// Mock SidebarUserFooter — uses auth hooks
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

import { usePathname } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { CommandPaletteProvider } from "@/components/command-palette-provider";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// All capabilities + legal modules — lets every zone render (Trust needs VIEW_TRUST + trust_accounting)
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

function renderSidebar() {
  return render(
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={LEGAL_MODULES}
      terminologyNamespace={null}
    >
      <CapabilityProvider
        capabilities={ALL_CAPABILITIES}
        role="Owner"
        isAdmin={false}
        isOwner={true}
      >
        <CommandPaletteProvider slug="test-org">
          <DesktopSidebar slug="test-org" />
        </CommandPaletteProvider>
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

describe("DesktopSidebar", () => {
  it("renders the six zone headers (Phase 87 612A, ADR-339)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderSidebar();

    for (const label of ["Today", "Matters", "Clients", "Fees", "Trust", "Firm"]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
    for (const gone of ["Work", "Finance", "Team", "AI"]) {
      expect(screen.queryByRole("button", { name: gone })).not.toBeInTheDocument();
    }
  });

  it("collapses a zone on header click, hides its items", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();

    // Home is in the Today zone (defaultExpanded: true)
    expect(screen.getByRole("link", { name: "Home" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Today" }));

    // Today zone items should now be hidden
    expect(screen.queryByRole("link", { name: "Home" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "My work" })).not.toBeInTheDocument();
  });

  it("renders the utility footer with Notifications only — Settings lives in Firm", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();

    expect(screen.getByRole("link", { name: "Notifications" })).toHaveAttribute(
      "href",
      "/org/test-org/notifications"
    );
    // Firm is collapsed by default, and the footer no longer carries Settings.
    expect(screen.queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Firm" }));
    expect(screen.getAllByRole("link", { name: "Settings" })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
      "href",
      "/org/test-org/settings/firm"
    );
  });

  it("renders search pill with ⌘K badge", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();

    expect(screen.getByText("Search...")).toBeInTheDocument();
    expect(screen.getByText("⌘K")).toBeInTheDocument();
  });

  it("opens command palette when search pill is clicked", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();
    await user.click(screen.getByText("Search..."));
    // CommandPaletteDialog is dynamically imported — wait for it to load
    expect(await screen.findByPlaceholderText("Search pages, settings...")).toBeInTheDocument();
  });
});
