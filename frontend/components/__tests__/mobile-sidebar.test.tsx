import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
}));

// Mock motion/react — avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => (
      <div {...props}>{children}</div>
    ),
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => (
    <>{children}</>
  ),
}));

// Mock SidebarUserFooter — uses Clerk hooks
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

import { usePathname } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { MobileSidebar } from "@/components/mobile-sidebar";

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

function renderMobileSidebar(props: { groups?: string[] } = {}) {
  return render(
    <CapabilityProvider
      capabilities={ALL_CAPABILITIES}
      role="Owner"
      isAdmin={false}
      isOwner={true}
    >
      <MobileSidebar slug="test-org" {...props} />
    </CapabilityProvider>,
  );
}

describe("MobileSidebar", () => {
  it("renders hamburger menu trigger", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    expect(screen.getByRole("button", { name: /toggle menu/i })).toBeInTheDocument();
  });

  it("opens sheet and shows zone headers on trigger click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    expect(screen.getByText("Work")).toBeInTheDocument();
    expect(screen.getByText("Delivery")).toBeInTheDocument();
    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.getByText("Finance")).toBeInTheDocument();
    expect(screen.getByText("Team & Resources")).toBeInTheDocument();
  });

  it("renders utility footer items (Notifications, Settings)", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");
    renderMobileSidebar();

    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    expect(screen.getByText("Notifications")).toBeInTheDocument();
    expect(screen.getByText("Settings")).toBeInTheDocument();
  });

  it("closes sheet on nav item click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderMobileSidebar();

    // Open the sheet
    await user.click(screen.getByRole("button", { name: /toggle menu/i }));

    // Sheet should be open — zone headers visible
    expect(screen.getByText("Work")).toBeInTheDocument();

    // Click a nav item (Dashboard link)
    await user.click(screen.getByText("Dashboard"));

    // Sheet should close — zone headers should no longer be visible
    expect(screen.queryByText("Work")).not.toBeInTheDocument();
  });
});
