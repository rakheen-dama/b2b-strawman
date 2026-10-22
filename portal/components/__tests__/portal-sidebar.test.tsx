import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/navigation", () => ({
  usePathname: () => "/matters",
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockLogout = vi.fn();
vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    jwt: "test-jwt",
    customer: {
      id: "c",
      name: "Acme",
      email: "a@a.com",
      orgId: "org_1",
    },
    logout: mockLogout,
  }),
}));

vi.mock("@/hooks/use-portal-context", () => ({
  usePortalContext: () => ({
    tenantProfile: "legal-za",
    enabledModules: [
      "trust_accounting",
      "deadlines",
      "information_requests",
      "document_acceptance",
    ],
    terminologyKey: "legal-za",
    brandColor: "#3B82F6",
    orgName: "Acme",
    logoUrl: null,
  }),
  useBranding: () => ({
    orgName: "Acme",
    logoUrl: null,
    brandColor: "#3B82F6",
    footerText: null,
    isLoading: false,
  }),
}));

import {
  PortalSidebar,
  PortalSidebarMobile,
} from "@/components/portal-sidebar";

describe("PortalSidebar (desktop)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  // Phase 87 S10 (617A, arch §12.8): legal-native labels; Acceptance dropped (no page).
  it("renders the 7 nav items for legal-za with all modules enabled", () => {
    render(<PortalSidebar />);
    for (const label of [
      "Home",
      "Matters",
      "Trust statement",
      "Diary dates",
      "Fee notes",
      "Documents & requests",
      "Activity",
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getAllByRole("link")).toHaveLength(7 + 1); // + footer Profile link
    for (const gone of [
      "Acceptance",
      "Invoices",
      "Projects",
      "Fee Notes",
      "Trust",
      "Deadlines",
      "Requests",
    ]) {
      expect(screen.queryByText(gone)).not.toBeInTheDocument();
    }
  });

  it("links Matters to /matters", () => {
    render(<PortalSidebar />);
    expect(screen.getByText("Matters").closest("a")).toHaveAttribute(
      "href",
      "/matters",
    );
    expect(
      document.querySelector('a[href="/acceptance"]'),
    ).not.toBeInTheDocument();
  });

  it("does not render a Documents link (GAP-P-07: route not implemented)", () => {
    render(<PortalSidebar />);
    expect(screen.queryByText("Documents")).not.toBeInTheDocument();
  });

  it("renders the active-route indicator on the current route only", () => {
    render(<PortalSidebar />);
    // pathname mock = /matters → the "Matters" link is the only active item.
    const indicators = screen.getAllByTestId("nav-active-indicator");
    expect(indicators).toHaveLength(1);
  });

  it("exposes the Profile link and Logout button in the footer", () => {
    render(<PortalSidebar />);
    expect(screen.getByText("Profile")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /logout/i })).toBeInTheDocument();
  });
});

describe("PortalSidebarMobile", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders nav items when opened and hides them when closed", () => {
    const { rerender } = render(
      <PortalSidebarMobile open={false} onOpenChange={() => {}} />,
    );
    // Radix Dialog mounts content only when open; closed state → no nav items
    expect(screen.queryByText("Matters")).not.toBeInTheDocument();

    rerender(<PortalSidebarMobile open={true} onOpenChange={() => {}} />);
    expect(screen.getByText("Matters")).toBeInTheDocument();
  });
});
