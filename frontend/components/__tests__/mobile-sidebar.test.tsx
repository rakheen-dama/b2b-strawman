import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useSearchParams: () => new URLSearchParams(),
}));

// Mock motion/react — avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock SidebarUserFooter — uses auth hooks
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

import { usePathname } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { MobileSidebar } from "@/components/mobile-sidebar";

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

function renderMobileSidebar(props: { groups?: string[] } = {}) {
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
        <MobileSidebar slug="test-org" {...props} />
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

describe("MobileSidebar", () => {
  it("renders hamburger menu trigger", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    expect(screen.getByRole("button", { name: /toggle menu/i })).toBeInTheDocument();
  });

  it("opens sheet and shows the six zone headers on trigger click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    for (const label of ["Today", "Matters", "Clients", "Fees", "Trust", "Firm"]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("renders the shared utility footer (Notifications only — Settings lives in Firm)", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    expect(screen.getByRole("link", { name: "Notifications" })).toHaveAttribute(
      "href",
      "/org/test-org/notifications"
    );
    // Firm is collapsed by default, and the footer no longer carries Settings.
    expect(screen.queryByRole("link", { name: "Settings" })).not.toBeInTheDocument();
  });

  it("closes sheet on nav item click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderMobileSidebar();

    // Open the sheet
    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    // Sheet should be open — zone headers visible
    expect(screen.getByRole("button", { name: "Today" })).toBeInTheDocument();

    // Click a nav item (Home link)
    await user.click(screen.getByRole("link", { name: "Home" }));

    // Sheet should close — zone headers should no longer be visible
    expect(screen.queryByRole("button", { name: "Today" })).not.toBeInTheDocument();
  });

  it("closes sheet when a utility footer item is clicked (shared SidebarUtilityFooter)", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderMobileSidebar();

    await user.click(screen.getByRole("button", { name: /toggle menu/i }));
    expect(screen.getByRole("button", { name: "Today" })).toBeInTheDocument();

    await user.click(screen.getByRole("link", { name: "Notifications" }));

    expect(screen.queryByRole("button", { name: "Today" })).not.toBeInTheDocument();
  });
});
