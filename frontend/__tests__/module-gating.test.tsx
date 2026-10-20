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

import { NAV_GROUPS, SETTINGS_ITEMS } from "@/lib/nav-items";
import { SETTINGS_NAV_GROUPS } from "@/components/settings/settings-nav-groups";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { ModuleDisabledFallback } from "@/components/module-disabled-fallback";

afterEach(() => cleanup());

describe("Nav items - resource planning removed (Epic 599)", () => {
  it("no nav item or settings entry references the resource_planning module", () => {
    const navItems = NAV_GROUPS.flatMap((g) => g.items);
    expect(navItems.find((i) => i.label === "Resources")).toBeUndefined();
    expect(navItems.find((i) => i.label === "Utilization")).toBeUndefined();
    expect(navItems.some((i) => i.requiredModule === "resource_planning")).toBe(false);
    expect(SETTINGS_ITEMS.some((i) => i.title === "Capacity")).toBe(false);
  });
});

describe("Nav items - bulk billing gating", () => {
  it("Fee runs nav item exists in the fees zone with requiredModule: bulk_billing", () => {
    const feesZone = NAV_GROUPS.find((g) => g.id === "fees");
    const billingRunsItem = feesZone?.items.find((i) => i.label === "Fee runs");
    expect(billingRunsItem).toBeDefined();
    expect(billingRunsItem?.requiredModule).toBe("bulk_billing");
  });

  it("Batch Billing settings entry has requiredModule: bulk_billing", () => {
    const batchBilling = SETTINGS_ITEMS.find((i) => i.title === "Batch Billing");
    expect(batchBilling?.requiredModule).toBe("bulk_billing");
  });
});

// Phase 86 (ADR-334): the automation_builder module was removed — the automations admin page is
// always on (admin-only), so neither nav entry carries a module gate.
describe("Settings nav groups - automations always on", () => {
  it("Automations item in work group has no requiredModule", () => {
    const workGroup = SETTINGS_NAV_GROUPS.find((g) => g.id === "work");
    const automations = workGroup?.items.find((i) => i.label === "Automations");
    expect(automations).toBeDefined();
    expect(automations?.requiredModule).toBeUndefined();
  });

  it("Automations entry in SETTINGS_ITEMS has no requiredModule", () => {
    const automations = SETTINGS_ITEMS.find((i) => i.title === "Automations");
    expect(automations).toBeDefined();
    expect(automations?.requiredModule).toBeUndefined();
  });
});

describe("Settings nav groups - features group", () => {
  it("Features group exists with Features and Packs items", () => {
    const featuresGroup = SETTINGS_NAV_GROUPS.find((g) => g.id === "features");
    expect(featuresGroup).toBeDefined();
    expect(featuresGroup?.items).toHaveLength(2);
    expect(featuresGroup?.items[0]?.href).toBe("features");
    expect(featuresGroup?.items[1]?.href).toBe("packs");
    expect(featuresGroup?.items[1]?.adminOnly).toBe(true);
  });

  it("Features entry exists in SETTINGS_ITEMS", () => {
    const features = SETTINGS_ITEMS.find((i) => i.title === "Features");
    expect(features).toBeDefined();
    expect(features?.href("acme")).toBe("/org/acme/settings/features");
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
