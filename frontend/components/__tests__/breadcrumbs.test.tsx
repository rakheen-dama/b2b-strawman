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

  it('renders correct label for project-naming segment ("Project Naming")', () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/project-naming");
    render(<Breadcrumbs slug="acme" />);
    expect(screen.getByText("Project Naming")).toBeInTheDocument();
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
});
