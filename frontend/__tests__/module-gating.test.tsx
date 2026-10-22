import React from "react";
import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/dashboard",
}));

import { NAV_GROUPS } from "@/lib/nav-items";
import { SETTINGS_FAMILIES, getSettingsFamily, settingsHref } from "@/lib/settings-nav";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

afterEach(() => cleanup());

const settingsTab = (family: Parameters<typeof getSettingsFamily>[0], id: string) =>
  getSettingsFamily(family).tabs.find((t) => t.id === id);

describe("Nav items - resource planning removed (Epic 599)", () => {
  it("no nav item or settings entry references the resource_planning module", () => {
    const navItems = NAV_GROUPS.flatMap((g) => g.items);
    expect(navItems.find((i) => i.label === "Resources")).toBeUndefined();
    expect(navItems.find((i) => i.label === "Utilization")).toBeUndefined();
    expect(navItems.some((i) => i.requiredModule === "resource_planning")).toBe(false);
    expect(
      SETTINGS_FAMILIES.flatMap((f) => f.tabs).some(
        (t) => t.label === "Capacity" || t.requiredModule === "resource_planning"
      )
    ).toBe(false);
  });
});

describe("Nav items - bulk billing gating", () => {
  it("Fee runs nav item exists in the fees zone with requiredModule: bulk_billing", () => {
    const feesZone = NAV_GROUPS.find((g) => g.id === "fees");
    const billingRunsItem = feesZone?.items.find((i) => i.label === "Fee runs");
    expect(billingRunsItem).toBeDefined();
    expect(billingRunsItem?.requiredModule).toBe("bulk_billing");
  });

  it("Fees & Trust › Fee runs settings tab has requiredModule: bulk_billing", () => {
    expect(settingsTab("fees", "fee-runs")?.requiredModule).toBe("bulk_billing");
  });
});

// Phase 86 (ADR-334): the automation_builder module was removed — the automations admin page is
// always on (admin-only, D24), so the Connections › Automations tab carries no module gate.
describe("Settings nav source - automations always on", () => {
  it("Connections › Automations tab has no requiredModule and is admin-only", () => {
    const automations = settingsTab("connections", "automations");
    expect(automations).toBeDefined();
    expect(automations?.requiredModule).toBeUndefined();
    expect(automations?.adminOnly).toBe(true);
  });
});

describe("Settings nav source - features and packs", () => {
  it("Firm › Features and Connections › Packs are admin-only tabs", () => {
    expect(settingsTab("firm", "features")?.adminOnly).toBe(true);
    expect(settingsTab("connections", "packs")?.adminOnly).toBe(true);
  });

  it("Firm › Features links to its ?tab= URL", () => {
    expect(settingsHref("acme", "firm", "features")).toBe("/org/acme/settings/firm?tab=features");
  });
});

describe("ModuleGate page-level gating", () => {
  it("hides bulk billing content when module disabled", () => {
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <ModuleGate
          module="bulk_billing"
          fallback={<ModuleDisabledFallback moduleName="Bulk Billing Runs" slug="acme" />}
        >
          <div>Bulk billing content</div>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.queryByText("Bulk billing content")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /bulk billing runs is not enabled/i })
    ).toBeInTheDocument();
  });

  it("shows bulk billing content when module enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={["bulk_billing"]}
        terminologyNamespace={null}
      >
        <ModuleGate
          module="bulk_billing"
          fallback={<ModuleDisabledFallback moduleName="Bulk Billing Runs" slug="acme" />}
        >
          <div>Bulk billing content</div>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.getByText("Bulk billing content")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: /bulk billing runs is not enabled/i })
    ).not.toBeInTheDocument();
  });

  it("hides gated widget when module disabled (no fallback)", () => {
    render(
      <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
        <ModuleGate module="information_requests">
          <div>Information requests widget</div>
        </ModuleGate>
      </OrgProfileProvider>
    );

    expect(screen.queryByText("Information requests widget")).not.toBeInTheDocument();
  });
});
