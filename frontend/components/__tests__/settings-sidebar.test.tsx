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
    expect(screen.getAllByText("Email").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Automations").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Batch Billing").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Roles & Permissions").length).toBeGreaterThanOrEqual(1);
  });

  it("renders Coming Soon items as non-clickable (not anchor elements)", () => {
    mockUsePathname.mockReturnValue("/org/test-org/settings/billing");

    render(<SettingsSidebar slug="test-org" isAdmin={true} />);

    // "Organization" and "Security" are comingSoon — should not be <a> tags
    // getAllByText because mobile + desktop both render them
    const orgElements = screen.getAllByText("Organization");
    for (const el of orgElements) {
      expect(el.tagName).not.toBe("A");
    }

    const secElements = screen.getAllByText("Security");
    for (const el of secElements) {
      expect(el.tagName).not.toBe("A");
    }
  });
});
