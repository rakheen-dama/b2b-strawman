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

import { usePathname } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { NavZone } from "@/components/nav-zone";
import type { NavGroup } from "@/lib/nav-items";
import { LayoutDashboard, FolderOpen, Receipt } from "lucide-react";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

// ---- Test fixtures ----

const workZone: NavGroup = {
  id: "work",
  label: "Work",
  defaultExpanded: true,
  items: [
    {
      label: "Dashboard",
      href: (slug) => `/org/${slug}/dashboard`,
      icon: LayoutDashboard,
      exact: true,
    },
    {
      label: "Projects",
      href: (slug) => `/org/${slug}/projects`,
      icon: FolderOpen,
    },
  ],
};

const financeZone: NavGroup = {
  id: "finance",
  label: "Finance",
  defaultExpanded: true,
  items: [
    {
      label: "Invoices",
      href: (slug) => `/org/${slug}/invoices`,
      icon: Receipt,
      requiredCapability: "INVOICING",
    },
  ],
};

function renderWithCapabilities(
  ui: React.ReactElement,
  {
    capabilities = [],
    isAdmin = false,
    isOwner = false,
  }: { capabilities?: string[]; isAdmin?: boolean; isOwner?: boolean } = {},
) {
  return render(
    <CapabilityProvider
      capabilities={capabilities}
      role="Member"
      isAdmin={isAdmin}
      isOwner={isOwner}
    >
      {ui}
    </CapabilityProvider>,
  );
}

// ---- Tests ----

describe("NavZone", () => {
  it("renders zone label", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithCapabilities(
      <NavZone zone={workZone} slug="test-org" />,
    );

    expect(screen.getByText("Work")).toBeInTheDocument();
  });

  it("renders items when expanded", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithCapabilities(
      <NavZone zone={workZone} slug="test-org" />,
    );

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Projects")).toBeInTheDocument();
  });

  it("collapses items on zone header click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithCapabilities(
      <NavZone zone={workZone} slug="test-org" />,
    );

    // Items should be visible initially (defaultExpanded: true)
    expect(screen.getByText("Dashboard")).toBeInTheDocument();

    // Click the zone header button
    const header = screen.getByRole("button");
    await user.click(header);

    // Items should be hidden after collapse
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("Projects")).not.toBeInTheDocument();
  });

  it("hides zone entirely when all items are capability-gated and user lacks capability", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithCapabilities(
      <NavZone zone={financeZone} slug="test-org" />,
      { capabilities: [], isAdmin: false, isOwner: false },
    );

    // Zone label should not be rendered
    expect(screen.queryByText("Finance")).not.toBeInTheDocument();
    // Items should not be rendered
    expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
  });

  it("shows active indicator on current path", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");

    renderWithCapabilities(
      <NavZone zone={workZone} slug="test-org" />,
    );

    // Dashboard link should have active class
    const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
    expect(dashboardLink).toHaveClass("bg-white/5");
    expect(dashboardLink).toHaveClass("text-white");

    // Projects link should not have active class
    const projectsLink = screen.getByRole("link", { name: /projects/i });
    expect(projectsLink).toHaveClass("text-white/60");
    expect(projectsLink).not.toHaveClass("bg-white/5");
  });
});
