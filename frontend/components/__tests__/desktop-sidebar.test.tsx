import { describe, it, expect, vi, afterEach } from "vitest";
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
import { TerminologyProvider } from "@/lib/terminology";
import { OrgProfileProvider } from "@/lib/org-profile";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { CommandPaletteProvider } from "@/components/command-palette-provider";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// All capabilities — lets all nav items render
const ALL_CAPABILITIES = [
  "FINANCIAL_VISIBILITY",
  "INVOICING",
  "PROJECT_MANAGEMENT",
  "TEAM_OVERSIGHT",
  "CUSTOMER_MANAGEMENT",
  "AUTOMATIONS",
  "RESOURCE_PLANNING",
];

function renderSidebar() {
  return render(
    <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
      <TerminologyProvider verticalProfile={null}>
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
      </TerminologyProvider>
    </OrgProfileProvider>
  );
}

describe("DesktopSidebar", () => {
  it("renders all 5 zone headers", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderSidebar();

    expect(screen.getByText("Work")).toBeInTheDocument();
    expect(screen.getAllByText("Projects").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.getByText("Finance")).toBeInTheDocument();
    expect(screen.getAllByText("Team").length).toBeGreaterThanOrEqual(1);
  });

  it("collapses a zone on header click, hides its items", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();

    // Dashboard is in the Work zone (defaultExpanded: true)
    expect(screen.getByText("Dashboard")).toBeInTheDocument();

    // Click the "Work" zone header button
    const workHeader = screen.getByRole("button", { name: /work/i });
    await user.click(workHeader);

    // Work zone items should now be hidden
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("My Work")).not.toBeInTheDocument();
  });

  it("renders utility footer section with Notifications and Settings", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderSidebar();

    expect(screen.getByText("Notifications")).toBeInTheDocument();
    expect(screen.getByText("Settings")).toBeInTheDocument();
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
