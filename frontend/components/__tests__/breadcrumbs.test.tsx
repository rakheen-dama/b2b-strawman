import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/acme/dashboard"),
}));

import { usePathname } from "next/navigation";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { TerminologyProvider } from "@/lib/terminology";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

describe("Breadcrumbs", () => {
  it("renders Settings as a link segment when on settings subpage", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");
    render(
      <TerminologyProvider verticalProfile={null}>
        <Breadcrumbs slug="acme" />
      </TerminologyProvider>
    );
    const settingsLink = screen.getByRole("link", { name: "Settings" });
    expect(settingsLink).toBeInTheDocument();
    expect(settingsLink).toHaveAttribute("href", "/org/acme/settings");
  });

  it('renders correct label for rates segment ("Rates & Currency")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/rates");
    render(
      <TerminologyProvider verticalProfile={null}>
        <Breadcrumbs slug="acme" />
      </TerminologyProvider>
    );
    expect(screen.getByText("Rates & Currency")).toBeInTheDocument();
  });

  it('renders correct label for custom-fields segment ("Custom Fields")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/custom-fields");
    render(
      <TerminologyProvider verticalProfile={null}>
        <Breadcrumbs slug="acme" />
      </TerminologyProvider>
    );
    expect(screen.getByText("Custom Fields")).toBeInTheDocument();
  });

  it('renders correct label for project-naming segment ("Project Naming")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/project-naming");
    render(
      <TerminologyProvider verticalProfile={null}>
        <Breadcrumbs slug="acme" />
      </TerminologyProvider>
    );
    expect(screen.getByText("Project Naming")).toBeInTheDocument();
  });

  it("renders Settings > Billing two-level path correctly", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");
    render(
      <TerminologyProvider verticalProfile={null}>
        <Breadcrumbs slug="acme" />
      </TerminologyProvider>
    );
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
      render(
        <TerminologyProvider verticalProfile={null}>
          <Breadcrumbs slug="acme" />
        </TerminologyProvider>
      );
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
});
