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

describe("Nav items - resource planning gating", () => {
  it("Resources nav item declares requiredModule: resource_planning", () => {
    const teamGroup = NAV_GROUPS.find((g) => g.id === "team");
    const resourcesItem = teamGroup?.items.find(
      (i) => i.label === "Resources",
    );
    expect(resourcesItem).toBeDefined();
    expect(resourcesItem?.requiredModule).toBe("resource_planning");
  });

  it("Utilization nav item exists with requiredModule: resource_planning", () => {
    const teamGroup = NAV_GROUPS.find((g) => g.id === "team");
    const utilizationItem = teamGroup?.items.find(
      (i) => i.label === "Utilization",
    );
    expect(utilizationItem).toBeDefined();
    expect(utilizationItem?.requiredModule).toBe("resource_planning");
  });
});

describe("Nav items - bulk billing gating", () => {
  it("Billing Runs nav item exists in finance group with requiredModule: bulk_billing", () => {
    const financeGroup = NAV_GROUPS.find((g) => g.id === "finance");
    const billingRunsItem = financeGroup?.items.find(
      (i) => i.label === "Billing Runs",
    );
    expect(billingRunsItem).toBeDefined();
    expect(billingRunsItem?.requiredModule).toBe("bulk_billing");
  });

  it("Batch Billing settings entry has requiredModule: bulk_billing", () => {
    const batchBilling = SETTINGS_ITEMS.find((i) => i.title === "Batch Billing");
    expect(batchBilling?.requiredModule).toBe("bulk_billing");
  });
});

describe("Settings nav groups - automation gating", () => {
  it("Automations item has requiredModule: automation_builder in work group", () => {
    const workGroup = SETTINGS_NAV_GROUPS.find((g) => g.id === "work");
    const automations = workGroup?.items.find((i) => i.label === "Automations");
    expect(automations?.requiredModule).toBe("automation_builder");
  });

  it("Automations entry in SETTINGS_ITEMS has requiredModule: automation_builder", () => {
    const automations = SETTINGS_ITEMS.find((i) => i.title === "Automations");
    expect(automations?.requiredModule).toBe("automation_builder");
  });
});

describe("Settings nav groups - features group", () => {
  it("Features group exists with a single Features item", () => {
    const featuresGroup = SETTINGS_NAV_GROUPS.find((g) => g.id === "features");
    expect(featuresGroup).toBeDefined();
    expect(featuresGroup?.items).toHaveLength(1);
    expect(featuresGroup?.items[0]?.href).toBe("features");
  });

  it("Features entry exists in SETTINGS_ITEMS", () => {
    const features = SETTINGS_ITEMS.find((i) => i.title === "Features");
    expect(features).toBeDefined();
    expect(features?.href("acme")).toBe("/org/acme/settings/features");
  });
});

describe("ModuleGate page-level gating", () => {
  it("hides resource planning content when module disabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <ModuleGate
          module="resource_planning"
          fallback={
            <ModuleDisabledFallback moduleName="Resource Planning" slug="acme" />
          }
        >
          <div>Resource planning content</div>
        </ModuleGate>
      </OrgProfileProvider>,
    );

    expect(screen.queryByText("Resource planning content")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /resource planning is not enabled/i }),
    ).toBeInTheDocument();
  });

  it("shows resource planning content when module enabled", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={["resource_planning"]}
        terminologyNamespace={null}
      >
        <ModuleGate
          module="resource_planning"
          fallback={
            <ModuleDisabledFallback moduleName="Resource Planning" slug="acme" />
          }
        >
          <div>Resource planning content</div>
        </ModuleGate>
      </OrgProfileProvider>,
    );

    expect(screen.getByText("Resource planning content")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: /resource planning is not enabled/i }),
    ).not.toBeInTheDocument();
  });

  it("hides automation widget when module disabled (no fallback)", () => {
    render(
      <OrgProfileProvider
        verticalProfile={null}
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <ModuleGate module="automation_builder">
          <div>Automation runs widget</div>
        </ModuleGate>
      </OrgProfileProvider>,
    );

    expect(screen.queryByText("Automation runs widget")).not.toBeInTheDocument();
  });
});
