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
    div: ({ children, ...props }: React.ComponentProps<"div">) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

import { usePathname } from "next/navigation";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { NavZone } from "@/components/nav-zone";
import { NAV_GROUPS, type NavGroup } from "@/lib/nav-items";
import { LayoutDashboard, FolderOpen, Receipt, Scale, ScrollText, Plus } from "lucide-react";

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
      label: "Matters",
      href: (slug) => `/org/${slug}/matters`,
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
      label: "Fee notes",
      href: (slug) => `/org/${slug}/invoices`,
      icon: Receipt,
      requiredCapability: "INVOICING",
    },
  ],
};

const legalFinanceZone: NavGroup = {
  id: "finance",
  label: "Finance",
  defaultExpanded: true,
  items: [
    {
      label: "Trust Accounting",
      href: (slug) => `/org/${slug}/trust-accounting`,
      icon: Scale,
      exact: true,
      requiredCapability: "FINANCIAL_VISIBILITY",
      requiredModule: "trust_accounting",
    },
  ],
};

function renderWithProviders(
  ui: React.ReactElement,
  {
    capabilities = [],
    isAdmin = false,
    isOwner = false,
    enabledModules = [],
  }: {
    capabilities?: string[];
    isAdmin?: boolean;
    isOwner?: boolean;
    enabledModules?: string[];
  } = {}
) {
  return render(
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={enabledModules}
      terminologyNamespace={null}
    >
      <CapabilityProvider
        capabilities={capabilities}
        role="Member"
        isAdmin={isAdmin}
        isOwner={isOwner}
      >
        {ui}
      </CapabilityProvider>
    </OrgProfileProvider>
  );
}

// ---- Tests ----

describe("NavZone", () => {
  it("renders zone label", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={workZone} slug="test-org" />);

    expect(screen.getByText("Work")).toBeInTheDocument();
  });

  it("renders items when expanded", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={workZone} slug="test-org" />);

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Matters")).toBeInTheDocument();
  });

  it("collapses items on zone header click", async () => {
    const user = userEvent.setup();
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={workZone} slug="test-org" />);

    // Items should be visible initially (defaultExpanded: true)
    expect(screen.getByText("Dashboard")).toBeInTheDocument();

    // Click the zone header button
    const header = screen.getByRole("button");
    await user.click(header);

    // Items should be hidden after collapse
    expect(screen.queryByText("Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByText("Matters")).not.toBeInTheDocument();
  });

  it("hides zone entirely when all items are capability-gated and user lacks capability", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={financeZone} slug="test-org" />, {
      capabilities: [],
      isAdmin: false,
      isOwner: false,
    });

    // Zone label should not be rendered
    expect(screen.queryByText("Finance")).not.toBeInTheDocument();
    // Items should not be rendered
    expect(screen.queryByText("Fee notes")).not.toBeInTheDocument();
  });

  it("shows active indicator on current path", () => {
    mockUsePathname.mockReturnValue("/org/test-org/dashboard");

    renderWithProviders(<NavZone zone={workZone} slug="test-org" />);

    // Dashboard link should have active class
    const dashboardLink = screen.getByRole("link", { name: /dashboard/i });
    expect(dashboardLink).toHaveClass("bg-white/5");
    expect(dashboardLink).toHaveClass("text-white");

    // Matters link should not have active class
    const mattersLink = screen.getByRole("link", { name: /matters/i });
    expect(mattersLink).toHaveClass("text-white/60");
    expect(mattersLink).not.toHaveClass("bg-white/5");
  });

  // 370.8 Test 1: module-gated item visible when module enabled
  it("shows nav item with requiredModule when module is enabled", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={legalFinanceZone} slug="test-org" />, {
      capabilities: ["FINANCIAL_VISIBILITY"],
      enabledModules: ["trust_accounting"],
    });

    expect(screen.getByText("Trust Accounting")).toBeInTheDocument();
  });

  // 370.8 Test 2: module-gated item hidden when module not enabled
  it("hides nav item with requiredModule when module is not enabled", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={legalFinanceZone} slug="test-org" />, {
      capabilities: ["FINANCIAL_VISIBILITY"],
      enabledModules: [],
    });

    expect(screen.queryByText("Trust Accounting")).not.toBeInTheDocument();
  });

  it("renders labels verbatim — no runtime substitution (Phase 87 611B)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");

    renderWithProviders(<NavZone zone={financeZone} slug="test-org" />, {
      capabilities: ["INVOICING"],
    });

    expect(screen.getByText("Finance")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Fee notes" })).toBeInTheDocument();
    expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
  });
});

// Phase 87 S6a (612A): Firm › Audit trail is admin-only; New matter is an any-of capability gate.
const firmZone: NavGroup = {
  id: "firm",
  label: "Firm",
  defaultExpanded: true,
  items: [
    {
      label: "Audit trail",
      href: (slug) => `/org/${slug}/settings/audit-log`,
      icon: ScrollText,
      adminOnly: true,
    },
    {
      label: "Settings",
      href: (slug) => `/org/${slug}/settings/general`,
      icon: LayoutDashboard,
    },
  ],
};

const anyOfZone: NavGroup = {
  id: "matters",
  label: "Matters",
  defaultExpanded: true,
  items: [
    {
      label: "New matter",
      href: (slug) => `/org/${slug}/matters/new`,
      icon: Plus,
      exact: true,
      requiredCapability: ["PROJECT_MANAGEMENT", "AI_EXECUTE"],
    },
  ],
};

describe("NavZone adminOnly + any-of gates (Phase 87 612A)", () => {
  it("hides an adminOnly item from a member", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderWithProviders(<NavZone zone={firmZone} slug="test-org" />, {
      capabilities: ["FINANCIAL_VISIBILITY", "INVOICING"],
    });
    expect(screen.queryByText("Audit trail")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Settings" })).toBeInTheDocument();
  });

  it("shows an adminOnly item to an admin", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderWithProviders(<NavZone zone={firmZone} slug="test-org" />, { isAdmin: true });
    expect(screen.getByRole("link", { name: "Audit trail" })).toHaveAttribute(
      "href",
      "/org/test-org/settings/audit-log"
    );
  });

  it("shows an adminOnly item to an owner", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderWithProviders(<NavZone zone={firmZone} slug="test-org" />, { isOwner: true });
    expect(screen.getByRole("link", { name: "Audit trail" })).toBeInTheDocument();
  });

  it("shows an any-of item to a member holding only the second capability (AIVERIFY-011)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderWithProviders(<NavZone zone={anyOfZone} slug="test-org" />, {
      capabilities: ["AI_EXECUTE"],
    });
    expect(screen.getByRole("link", { name: "New matter" })).toBeInTheDocument();
  });

  it("hides an any-of item from a member holding none of the capabilities", () => {
    mockUsePathname.mockReturnValue("/org/test-org/other");
    renderWithProviders(<NavZone zone={anyOfZone} slug="test-org" />, {
      capabilities: ["INVOICING"],
    });
    expect(screen.queryByText("New matter")).not.toBeInTheDocument();
  });
});

describe("NavZone active state on the renamed routes (Phase 87 610E)", () => {
  it("highlights the real Matters item on a /matters detail page and links /matters", () => {
    mockUsePathname.mockReturnValue("/org/test-org/matters/abc");
    const mattersZone = NAV_GROUPS.find((g) => g.id === "matters")!;
    renderWithProviders(<NavZone zone={mattersZone} slug="test-org" />);
    const link = screen.getByRole("link", { name: "Matters" });
    expect(link).toHaveAttribute("href", "/org/test-org/matters");
    expect(link).toHaveClass("bg-white/5");
  });

  it("highlights the real Clients item on a /clients detail page and links /clients", () => {
    mockUsePathname.mockReturnValue("/org/test-org/clients/c-1");
    const clientsZone = NAV_GROUPS.find((g) => g.id === "clients")!;
    renderWithProviders(<NavZone zone={clientsZone} slug="test-org" />, {
      capabilities: ["CUSTOMER_MANAGEMENT"],
    });
    const link = screen.getByRole("link", { name: "Clients" });
    expect(link).toHaveAttribute("href", "/org/test-org/clients");
    expect(link).toHaveClass("bg-white/5");
  });
});

// Phase 87 S6a (612A review): one active item per zone — the longest matching href wins.
describe("NavZone longest-match active item (Phase 87 612A)", () => {
  const ALL = ["PROJECT_MANAGEMENT", "AI_EXECUTE", "AI_REVIEW", "INVOICING", "CUSTOMER_MANAGEMENT"];

  function activeLinks(): string[] {
    return screen
      .getAllByRole("link")
      .filter((link) => link.classList.contains("bg-white/5"))
      .map((link) => link.textContent ?? "");
  }

  function renderZone(zoneId: string, pathname: string) {
    mockUsePathname.mockReturnValue(pathname);
    const zone = NAV_GROUPS.find((g) => g.id === zoneId)!;
    renderWithProviders(<NavZone zone={{ ...zone, defaultExpanded: true }} slug="test-org" />, {
      capabilities: ALL,
      enabledModules: ["bulk_billing", "conflict_check", "disbursements", "lssa_tariff"],
    });
  }

  it("highlights only Fee notes on a fee-note detail page", () => {
    renderZone("fees", "/org/test-org/invoices/abc");
    expect(activeLinks()).toEqual(["Fee notes"]);
  });

  it("highlights only Fee runs on a fee-run detail page", () => {
    renderZone("fees", "/org/test-org/invoices/billing-runs/x");
    expect(activeLinks()).toEqual(["Fee runs"]);
  });

  it("highlights only Debtors on the collections page", () => {
    renderZone("fees", "/org/test-org/invoices/collections");
    expect(activeLinks()).toEqual(["Debtors"]);
  });

  it("highlights only New matter on /matters/new", () => {
    renderZone("matters", "/org/test-org/matters/new");
    expect(activeLinks()).toEqual(["New matter"]);
  });

  it("highlights only Matters on a matter detail page", () => {
    renderZone("matters", "/org/test-org/matters/123");
    expect(activeLinks()).toEqual(["Matters"]);
  });

  it("does not prefix-match a sibling route that merely shares the href's leading characters", () => {
    renderZone("matters", "/org/test-org/matters-archive");
    expect(activeLinks()).toEqual([]);
  });
});
