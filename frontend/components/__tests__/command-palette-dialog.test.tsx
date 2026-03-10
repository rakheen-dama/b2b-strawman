import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/dashboard"),
  useRouter: vi.fn(() => ({ push: vi.fn() })),
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

// Mock recent-items-provider — control recentItems in tests
const mockRecentItems: { href: string; label: string }[] = [];
vi.mock("@/components/recent-items-provider", () => ({
  useRecentItems: vi.fn(() => ({ items: mockRecentItems, addItem: vi.fn() })),
}));

import { CapabilityProvider } from "@/lib/capabilities";
import { CommandPaletteDialog } from "@/components/command-palette-dialog";

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
  "RESOURCE_PLANNING",
];

function renderDialog({
  capabilities = ALL_CAPABILITIES,
  isAdmin = false,
  isOwner = true,
  role = "Owner",
}: {
  capabilities?: string[];
  isAdmin?: boolean;
  isOwner?: boolean;
  role?: string;
} = {}) {
  return render(
    <CapabilityProvider
      capabilities={capabilities}
      role={role}
      isAdmin={isAdmin}
      isOwner={isOwner}
    >
      <CommandPaletteDialog slug="test-org" open={true} onOpenChange={vi.fn()} />
    </CapabilityProvider>,
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

  it("renders a capability-filtered nav item — Invoices hidden when user has no capabilities", () => {
    renderDialog({ capabilities: [], isAdmin: false, isOwner: false, role: "member" });
    expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
  });

  it("renders Pages items for user with capabilities — Dashboard is visible", () => {
    renderDialog({ capabilities: ALL_CAPABILITIES, isAdmin: false, isOwner: true });
    expect(screen.getByText("Dashboard")).toBeInTheDocument();
  });

  it("omits comingSoon settings items — Organization is not in DOM", () => {
    renderDialog();
    expect(screen.queryByText("Organization")).not.toBeInTheDocument();
  });

  it("omits adminOnly settings items for non-admin — Batch Billing not visible to member", () => {
    renderDialog({
      capabilities: ALL_CAPABILITIES,
      isAdmin: false,
      isOwner: false,
      role: "member",
    });
    expect(screen.queryByText("Batch Billing")).not.toBeInTheDocument();
  });

  it("shows Recent group heading when recentItems is non-empty", () => {
    mockRecentItems.push({ href: "/org/test-org/projects/abc", label: "Alpha Project" });
    renderDialog();
    const recentHeadings = screen.getAllByText("Recent");
    expect(recentHeadings.length).toBeGreaterThan(0);
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    // Clean up for other tests
    mockRecentItems.length = 0;
  });
});
