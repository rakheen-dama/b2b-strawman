import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/acme/dashboard"),
}));

import { usePathname } from "next/navigation";
import { Breadcrumbs } from "@/components/breadcrumbs";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

describe("Breadcrumbs", () => {
  it("renders Settings as a link segment when on settings subpage", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");
    render(<Breadcrumbs slug="acme" />);
    const settingsLink = screen.getByRole("link", { name: "Settings" });
    expect(settingsLink).toBeInTheDocument();
    expect(settingsLink).toHaveAttribute("href", "/org/acme/settings");
  });

  it('renders correct label for rates segment ("Rates & Currency")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/rates");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByText("Rates & Currency")).toBeInTheDocument();
  });

  it('renders correct label for custom-fields segment ("Custom Fields")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/custom-fields");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByText("Custom Fields")).toBeInTheDocument();
  });

  it('renders correct label for project-naming segment ("Matter Naming")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/project-naming");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByText("Matter Naming")).toBeInTheDocument();
  });

  it("renders Settings > Billing two-level path correctly", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");
    render(<Breadcrumbs slug="acme" />);
    // "Settings" should be a link (intermediate)
    const settingsLink = screen.getByRole("link", { name: "Settings" });
    expect(settingsLink).toBeInTheDocument();
    // "Billing" is the last segment — renders as a <span> with font-medium
    const billingSpan = screen.getByText("Billing");
    expect(billingSpan.tagName).toBe("SPAN");
    expect(billingSpan).toHaveClass("font-medium");
  });

  describe("intermediate segments without a page", () => {
    const TEMPLATE_ID = "3f1c2a4b-5d6e-4f70-8a9b-0c1d2e3f4a5b";
    const REQUEST_ID = "9a8b7c6d-5e4f-4a3b-9c2d-1e0f9a8b7c6d";

    function renderAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("renders legal as plain text, not a link, on /legal/disbursements", () => {
      renderAt("/org/acme/legal/disbursements");
      const crumb = screen.getByText("legal");
      expect(crumb.tagName).toBe("SPAN");
      expect(crumb.closest("a")).toBeNull();
      expect(screen.queryByRole("link", { name: "legal" })).not.toBeInTheDocument();
    });

    it("renders ai as plain text, not a link, on /ai/reviews", () => {
      renderAt("/org/acme/ai/reviews");
      expect(screen.getByText("ai").closest("a")).toBeNull();
      expect(screen.queryByRole("link", { name: "ai" })).not.toBeInTheDocument();
    });

    it("renders information-requests as plain text on /information-requests/<id>", () => {
      renderAt(`/org/acme/information-requests/${REQUEST_ID}`);
      expect(screen.getByText("information-requests").closest("a")).toBeNull();
    });

    it("renders the template id as plain text on /settings/templates/<id>/edit", () => {
      renderAt(`/org/acme/settings/templates/${TEMPLATE_ID}/edit`);
      expect(screen.getByText(TEMPLATE_ID).closest("a")).toBeNull();
      // Routable ancestors stay linked
      expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
        "href",
        "/org/acme/settings"
      );
      expect(screen.getByRole("link", { name: "Templates" })).toHaveAttribute(
        "href",
        "/org/acme/settings/templates"
      );
    });

    it("still links intermediate segments that have a page", () => {
      renderAt("/org/acme/settings/rates");
      expect(screen.getByRole("link", { name: "Settings" })).toHaveAttribute(
        "href",
        "/org/acme/settings"
      );
    });
  });
  describe("matter and client routes (Phase 87 610E)", () => {
    const MATTER_ID = "5b1d7c2e-3f4a-4b5c-8d6e-7f8091a2b3c4";
    const CLIENT_ID = "6c2e8d3f-4a5b-4c6d-9e7f-8091a2b3c4d5";

    function renderCrumbsAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("labels /matters/<id> as Matters > Matter and links the list", () => {
      renderCrumbsAt(`/org/acme/matters/${MATTER_ID}`);
      expect(screen.getByRole("link", { name: "Matters" })).toHaveAttribute(
        "href",
        "/org/acme/matters"
      );
      expect(screen.getByText("Matter").tagName).toBe("SPAN");
      expect(screen.queryByText(MATTER_ID)).not.toBeInTheDocument();
    });

    it("labels /clients/<id> as Clients > Client and links the list", () => {
      renderCrumbsAt(`/org/acme/clients/${CLIENT_ID}`);
      expect(screen.getByRole("link", { name: "Clients" })).toHaveAttribute(
        "href",
        "/org/acme/clients"
      );
      expect(screen.getByText("Client").tagName).toBe("SPAN");
    });

    it("labels the /matters list page Matters (not the raw segment)", () => {
      renderCrumbsAt("/org/acme/matters");
      expect(screen.getByText("Matters").tagName).toBe("SPAN");
      expect(screen.queryByText("matters")).not.toBeInTheDocument();
    });

    it("labels /trust-accounting/client-ledgers/<id> as Client Ledger", () => {
      renderCrumbsAt(`/org/acme/trust-accounting/client-ledgers/${CLIENT_ID}`);
      expect(screen.getByText("Client Ledger").tagName).toBe("SPAN");
    });
  });

  describe("legal labels (Phase 87 611B)", () => {
    const INVOICE_ID = "7d3f9e4a-5b6c-4d7e-8f90-a1b2c3d4e5f6";
    const MATTER_ID = "8e4a0f5b-6c7d-4e8f-9a01-b2c3d4e5f6a7";

    function renderCrumbsAt(pathname: string) {
      mockUsePathname.mockReturnValue(pathname);
      render(<Breadcrumbs slug="acme" />);
    }

    it("labels /settings/project-templates as Matter Templates", () => {
      renderCrumbsAt("/org/acme/settings/project-templates");
      expect(screen.getByText("Matter Templates")).toBeInTheDocument();
      expect(screen.queryByText("Project Templates")).not.toBeInTheDocument();
    });

    it("labels the /invoices list page Fee notes", () => {
      renderCrumbsAt("/org/acme/invoices");
      expect(screen.getByText("Fee notes").tagName).toBe("SPAN");
      expect(screen.queryByText("Invoices")).not.toBeInTheDocument();
    });

    it("labels /invoices/<id> as Fee notes > Fee note and links the list", () => {
      renderCrumbsAt(`/org/acme/invoices/${INVOICE_ID}`);
      expect(screen.getByRole("link", { name: "Fee notes" })).toHaveAttribute(
        "href",
        "/org/acme/invoices"
      );
      expect(screen.getByText("Fee note").tagName).toBe("SPAN");
    });

    it("labels /matters/<id>/expenses as Disbursements (not the raw segment)", () => {
      renderCrumbsAt(`/org/acme/matters/${MATTER_ID}/expenses`);
      expect(screen.getByText("Disbursements").tagName).toBe("SPAN");
      expect(screen.queryByText("expenses")).not.toBeInTheDocument();
    });
  });
});
