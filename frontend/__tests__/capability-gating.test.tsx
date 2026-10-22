import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: () => "/org/test-org/dashboard",
  useSearchParams: () => new URLSearchParams(),
}));

// Mock motion/react to avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock SidebarUserFooter (depends on auth provider)
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

// Mock command-palette-provider — DesktopSidebar calls useCommandPalette()
vi.mock("@/components/command-palette-provider", () => ({
  useCommandPalette: vi.fn(() => ({ open: false, setOpen: vi.fn() })),
  CommandPaletteProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import { CapabilityProvider, RequiresCapability } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { DesktopSidebar } from "@/components/desktop-sidebar";

afterEach(() => {
  cleanup();
});

describe("Sidebar capability gating", () => {
  it("hides Fee notes when user lacks INVOICING capability", () => {
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <CapabilityProvider
          capabilities={["PROJECT_MANAGEMENT"]}
          role="Member"
          isAdmin={false}
          isOwner={false}
        >
          <DesktopSidebar slug="test-org" />
        </CapabilityProvider>
      </OrgProfileProvider>
    );

    expect(screen.queryByText("Fee notes")).not.toBeInTheDocument();
  });

  it("shows Fee notes when user has INVOICING capability", async () => {
    const user = userEvent.setup();
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <CapabilityProvider
          capabilities={["INVOICING"]}
          role="Accountant"
          isAdmin={false}
          isOwner={false}
        >
          <DesktopSidebar slug="test-org" />
        </CapabilityProvider>
      </OrgProfileProvider>
    );

    // Fees zone starts collapsed (defaultExpanded: false) — expand it
    await user.click(screen.getByRole("button", { name: "Fees" }));

    expect(screen.getByText("Fee notes")).toBeInTheDocument();
  });

  it("shows all nav items for admin users", async () => {
    const user = userEvent.setup();
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={["bulk_billing"]}
        terminologyNamespace={null}
      >
        <CapabilityProvider capabilities={[]} role="Admin" isAdmin={true} isOwner={false}>
          <DesktopSidebar slug="test-org" />
        </CapabilityProvider>
      </OrgProfileProvider>
    );

    // Expand collapsed zones (Fees and Firm have defaultExpanded: false; Clients is expanded)
    await user.click(screen.getByRole("button", { name: "Fees" }));
    await user.click(screen.getByRole("button", { name: "Firm" }));

    expect(screen.getByText("Fee notes")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Clients" })).toBeInTheDocument();
    expect(screen.getByText("Reports")).toBeInTheDocument();
    expect(screen.getByText("FICA & compliance")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Audit trail" })).toBeInTheDocument();
    // Profitability's single entry is Firm › Reports (Phase 87 612A) — no sidebar item
    expect(screen.queryByText("Profitability")).not.toBeInTheDocument();
    // Documents item removed from sidebar
    expect(screen.queryByRole("link", { name: "Documents" })).not.toBeInTheDocument();
  });
});

describe("Action button capability gating", () => {
  it("hides create project button when user lacks PROJECT_MANAGEMENT", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Member" isAdmin={false} isOwner={false}>
        <RequiresCapability cap="PROJECT_MANAGEMENT">
          <button>Create Project</button>
        </RequiresCapability>
      </CapabilityProvider>
    );

    expect(screen.queryByText("Create Project")).not.toBeInTheDocument();
  });

  it("shows create project button when user has PROJECT_MANAGEMENT", () => {
    render(
      <CapabilityProvider
        capabilities={["PROJECT_MANAGEMENT"]}
        role="Manager"
        isAdmin={false}
        isOwner={false}
      >
        <RequiresCapability cap="PROJECT_MANAGEMENT">
          <button>Create Project</button>
        </RequiresCapability>
      </CapabilityProvider>
    );

    expect(screen.getByText("Create Project")).toBeInTheDocument();
  });

  it("hides create customer button when user lacks CUSTOMER_MANAGEMENT", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Member" isAdmin={false} isOwner={false}>
        <RequiresCapability cap="CUSTOMER_MANAGEMENT">
          <button>Create Customer</button>
        </RequiresCapability>
      </CapabilityProvider>
    );

    expect(screen.queryByText("Create Customer")).not.toBeInTheDocument();
  });
});
