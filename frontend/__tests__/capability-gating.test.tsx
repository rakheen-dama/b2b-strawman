import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: () => "/org/test-org/dashboard",
}));

// Mock motion/react to avoid animation issues in tests
vi.mock("motion/react", () => ({
  motion: {
    div: (props: React.ComponentProps<"div">) => <div {...props} />,
  },
}));

// Mock server-only
vi.mock("server-only", () => ({}));

// Mock SidebarUserFooter (depends on Clerk)
vi.mock("@/components/sidebar-user-footer", () => ({
  SidebarUserFooter: () => <div data-testid="sidebar-user-footer" />,
}));

import { CapabilityProvider, RequiresCapability } from "@/lib/capabilities";
import { DesktopSidebar } from "@/components/desktop-sidebar";

afterEach(() => {
  cleanup();
});

describe("Sidebar capability gating", () => {
  it("hides Invoices when user lacks INVOICING capability", () => {
    render(
      <CapabilityProvider
        capabilities={["PROJECT_MANAGEMENT"]}
        role="Member"
        isAdmin={false}
        isOwner={false}
      >
        <DesktopSidebar slug="test-org" />
      </CapabilityProvider>,
    );

    expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
  });

  it("shows Invoices when user has INVOICING capability", () => {
    render(
      <CapabilityProvider
        capabilities={["INVOICING"]}
        role="Accountant"
        isAdmin={false}
        isOwner={false}
      >
        <DesktopSidebar slug="test-org" />
      </CapabilityProvider>,
    );

    expect(screen.getByText("Invoices")).toBeInTheDocument();
  });

  it("shows all nav items for admin users", () => {
    render(
      <CapabilityProvider
        capabilities={[]}
        role="Admin"
        isAdmin={true}
        isOwner={false}
      >
        <DesktopSidebar slug="test-org" />
      </CapabilityProvider>,
    );

    expect(screen.getByText("Invoices")).toBeInTheDocument();
    expect(screen.getByText("Customers")).toBeInTheDocument();
    expect(screen.getByText("Profitability")).toBeInTheDocument();
    expect(screen.getByText("Resources")).toBeInTheDocument();
    expect(screen.getByText("Reports")).toBeInTheDocument();
    expect(screen.getByText("Compliance")).toBeInTheDocument();
    expect(screen.getByText("Retainers")).toBeInTheDocument();
    expect(screen.getByText("Recurring Schedules")).toBeInTheDocument();
  });
});

describe("Action button capability gating", () => {
  it("hides create project button when user lacks PROJECT_MANAGEMENT", () => {
    render(
      <CapabilityProvider
        capabilities={[]}
        role="Member"
        isAdmin={false}
        isOwner={false}
      >
        <RequiresCapability cap="PROJECT_MANAGEMENT">
          <button>Create Project</button>
        </RequiresCapability>
      </CapabilityProvider>,
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
      </CapabilityProvider>,
    );

    expect(screen.getByText("Create Project")).toBeInTheDocument();
  });

  it("hides create customer button when user lacks CUSTOMER_MANAGEMENT", () => {
    render(
      <CapabilityProvider
        capabilities={[]}
        role="Member"
        isAdmin={false}
        isOwner={false}
      >
        <RequiresCapability cap="CUSTOMER_MANAGEMENT">
          <button>Create Customer</button>
        </RequiresCapability>
      </CapabilityProvider>,
    );

    expect(screen.queryByText("Create Customer")).not.toBeInTheDocument();
  });
});
