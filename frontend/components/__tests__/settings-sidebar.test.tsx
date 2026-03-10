import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation — SettingsSidebar uses usePathname
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/settings/billing"),
}));

import { usePathname } from "next/navigation";
import { SettingsSidebar } from "@/components/settings/settings-sidebar";

const mockUsePathname = vi.mocked(usePathname);

afterEach(() => {
  cleanup();
});

describe("SettingsSidebar", () => {
  it("renders all 6 group headers", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    render(<SettingsSidebar slug="test-org" isAdmin={true} />);

    expect(screen.getByText("General")).toBeInTheDocument();
    expect(screen.getByText("Work")).toBeInTheDocument();
    expect(screen.getByText("Documents")).toBeInTheDocument();
    expect(screen.getByText("Finance")).toBeInTheDocument();
    expect(screen.getByText("Clients")).toBeInTheDocument();
    expect(screen.getByText("Access & Integrations")).toBeInTheDocument();
  });

  it("hides adminOnly items when isAdmin=false", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    render(<SettingsSidebar slug="test-org" isAdmin={false} />);

    // adminOnly items should not be visible
    expect(screen.queryByText("Email")).not.toBeInTheDocument();
    expect(screen.queryByText("Automations")).not.toBeInTheDocument();
    expect(screen.queryByText("Batch Billing")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles & Permissions")).not.toBeInTheDocument();
  });

  it("shows adminOnly items when isAdmin=true", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    render(<SettingsSidebar slug="test-org" isAdmin={true} />);

    // getAllByText because mobile + desktop both render them
    expect(screen.getAllByText("Email").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Automations").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Batch Billing").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Roles & Permissions").length).toBeGreaterThanOrEqual(2);
  });

  it("renders Coming Soon items as non-clickable (not anchor elements)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    render(<SettingsSidebar slug="test-org" isAdmin={true} />);

    // "Organization" and "Security" are comingSoon — should not be <a> tags
    // They only appear in desktop nav (filtered out of mobile)
    const orgElements = screen.getAllByText("Organization");
    expect(orgElements.length).toBe(1); // desktop only
    for (const el of orgElements) {
      expect(el.tagName).not.toBe("A");
    }

    const secElements = screen.getAllByText("Security");
    expect(secElements.length).toBe(1); // desktop only
    for (const el of secElements) {
      expect(el.tagName).not.toBe("A");
    }
  });

  it("applies active border and bg classes to the current settings link", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    render(<SettingsSidebar slug="acme" isAdmin={true} />);

    // Billing link should have active styling
    // Mobile renders Billing too, so use getAllByText
    const billingLinks = screen.getAllByText("Billing");
    // Find the one that is an <a> tag in desktop nav
    const desktopBillingLink = billingLinks.find(
      (el) => el.tagName === "A" && el.closest("nav") !== null,
    );
    expect(desktopBillingLink).toBeDefined();
    expect(desktopBillingLink?.className).toContain("border-teal-600");
    expect(desktopBillingLink?.className).toContain("bg-teal-50");
  });

  it("does not apply active classes to non-current links", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    render(<SettingsSidebar slug="acme" isAdmin={true} />);

    // Notifications is not the active link
    const notifLinks = screen.getAllByText("Notifications");
    // At least one should be an <a> in desktop nav
    const desktopNotifLink = notifLinks.find(
      (el) => el.tagName === "A" && el.closest("nav") !== null,
    );
    expect(desktopNotifLink).toBeDefined();
    expect(desktopNotifLink?.className).not.toContain("border-teal-600");
    expect(desktopNotifLink?.className).not.toContain("bg-teal-50");
  });

  it("renders Coming Soon badge for comingSoon items", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    render(<SettingsSidebar slug="acme" isAdmin={true} />);

    // "Organization" and "Security" are comingSoon — each should show a badge
    const badges = screen.getAllByText("Coming soon");
    // 2 comingSoon items in SETTINGS_NAV_GROUPS: Organization and Security
    expect(badges.length).toBeGreaterThanOrEqual(2);
  });

  it("renders Coming Soon items as <span> not <a>", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    render(<SettingsSidebar slug="acme" isAdmin={true} />);

    const orgElements = screen.getAllByText("Organization");
    // comingSoon items only appear in desktop nav (filtered out of mobile tab row)
    expect(orgElements.length).toBe(1);
    expect(orgElements[0].closest("a")).toBeNull(); // Not wrapped in <a>

    const secElements = screen.getAllByText("Security");
    expect(secElements.length).toBe(1);
    expect(secElements[0].closest("a")).toBeNull();
  });

  it("renders mobile tab row with non-comingSoon items only", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    render(<SettingsSidebar slug="acme" isAdmin={true} />);

    // Mobile tab row renders pill links. "Billing" appears in both mobile and desktop.
    // "Organization" (comingSoon) should NOT appear in mobile (filtered out)
    const billingElements = screen.getAllByText("Billing");
    // Should appear at least twice: mobile pill + desktop link
    expect(billingElements.length).toBeGreaterThanOrEqual(2);

    // The mobile pill should have rounded-full class (desktop uses rounded-r-md)
    const mobilePill = billingElements.find(
      (el) => el.tagName === "A" && el.className.includes("rounded-full"),
    );
    expect(mobilePill).toBeDefined();
  });
});
