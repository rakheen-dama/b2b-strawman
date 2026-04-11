import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation — SettingsSidebar uses usePathname
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/org/test-org/settings/billing"),
}));

import { usePathname } from "next/navigation";
import { SettingsSidebar } from "@/components/settings/settings-sidebar";
import { OrgProfileProvider } from "@/lib/org-profile";

const mockUsePathname = vi.mocked(usePathname);

function renderSidebar(
  props: { slug: string; isAdmin: boolean },
  enabledModules: string[] = [
    "automation_builder",
    "bulk_billing",
    "resource_planning",
  ],
) {
  return render(
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={enabledModules}
      terminologyNamespace={null}
    >
      <SettingsSidebar {...props} />
    </OrgProfileProvider>,
  );
}

afterEach(() => {
  cleanup();
});

describe("SettingsSidebar", () => {
  it("renders all 7 group headers", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    renderSidebar({ slug: "test-org", isAdmin: true });

    // "General" appears as both a group header and a nav link, so use getAllByText
    expect(screen.getAllByText("General").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Work")).toBeInTheDocument();
    expect(screen.getByText("Documents")).toBeInTheDocument();
    expect(screen.getByText("Finance")).toBeInTheDocument();
    expect(screen.getByText("Clients")).toBeInTheDocument();
    // "Features" appears as both a group header and a nav link
    expect(screen.getAllByText("Features").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Access & Integrations")).toBeInTheDocument();
  });

  it("hides adminOnly items when isAdmin=false", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    renderSidebar({ slug: "test-org", isAdmin: false });

    // adminOnly items should not be visible
    expect(screen.queryByText("Email")).not.toBeInTheDocument();
    expect(screen.queryByText("Automations")).not.toBeInTheDocument();
    expect(screen.queryByText("Batch Billing")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles & Permissions")).not.toBeInTheDocument();
  });

  it("shows adminOnly items when isAdmin=true", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    renderSidebar({ slug: "test-org", isAdmin: true });

    // getAllByText because mobile + desktop both render them
    expect(screen.getAllByText("Email").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Automations").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Batch Billing").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Roles & Permissions").length).toBeGreaterThanOrEqual(2);
  });

  it("renders Coming Soon items as non-clickable (not anchor elements)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    renderSidebar({ slug: "test-org", isAdmin: true });

    // "Security" is comingSoon — should not be <a> tags
    // They only appear in desktop nav (filtered out of mobile)
    const secElements = screen.getAllByText("Security");
    expect(secElements.length).toBe(1); // desktop only
    for (const el of secElements) {
      expect(el.tagName).not.toBe("A");
    }
  });

  it("applies active border and bg classes to the current settings link", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    renderSidebar({ slug: "acme", isAdmin: true });

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

    renderSidebar({ slug: "acme", isAdmin: true });

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

    renderSidebar({ slug: "acme", isAdmin: true });

    // "Security" is comingSoon — should show a badge
    const badges = screen.getAllByText("Coming soon");
    // 1 comingSoon item in SETTINGS_NAV_GROUPS: Security
    expect(badges.length).toBe(1);
  });

  it("renders Coming Soon items as <span> not <a>", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    renderSidebar({ slug: "acme", isAdmin: true });

    const secElements = screen.getAllByText("Security");
    // comingSoon items only appear in desktop nav (filtered out of mobile tab row)
    expect(secElements.length).toBe(1);
    expect(secElements[0].closest("a")).toBeNull();
  });

  it("renders mobile tab row with non-comingSoon items only", () => {
    mockUsePathname.mockReturnValue("/org/acme/settings/billing");

    renderSidebar({ slug: "acme", isAdmin: true });

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
