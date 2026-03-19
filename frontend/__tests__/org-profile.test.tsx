import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { OrgProfileProvider, useOrgProfile } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";

afterEach(() => {
  cleanup();
});

describe("OrgProfileProvider and ModuleGate", () => {
  // 370.7 Test 1: useOrgProfile provides correct verticalProfile
  it("useOrgProfile provides correct verticalProfile", () => {
    function TestConsumer() {
      const { verticalProfile } = useOrgProfile();
      return <span data-testid="profile">{verticalProfile ?? "none"}</span>;
    }

    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["trust_accounting"]}
        terminologyNamespace="en-ZA-legal"
      >
        <TestConsumer />
      </OrgProfileProvider>,
    );

    expect(screen.getByTestId("profile").textContent).toBe("legal-za");
  });

  // 370.7 Test 2: isModuleEnabled returns true for enabled module
  it("isModuleEnabled returns true for a module in the enabled list", () => {
    function TestConsumer() {
      const { isModuleEnabled } = useOrgProfile();
      return (
        <span data-testid="result">
          {isModuleEnabled("trust_accounting") ? "true" : "false"}
        </span>
      );
    }

    render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["trust_accounting", "court_calendar"]}
        terminologyNamespace={null}
      >
        <TestConsumer />
      </OrgProfileProvider>,
    );

    expect(screen.getByTestId("result").textContent).toBe("true");
  });

  // 370.7 Test 3: isModuleEnabled returns false for disabled module
  it("isModuleEnabled returns false for a module NOT in the enabled list", () => {
    function TestConsumer() {
      const { isModuleEnabled } = useOrgProfile();
      return (
        <span data-testid="result">
          {isModuleEnabled("trust_accounting") ? "true" : "false"}
        </span>
      );
    }

    render(
      <OrgProfileProvider
        verticalProfile="consulting-generic"
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <TestConsumer />
      </OrgProfileProvider>,
    );

    expect(screen.getByTestId("result").textContent).toBe("false");
  });

  // 370.7 Test 4: ModuleGate renders children when enabled, fallback when disabled
  it("ModuleGate renders children when module is enabled and fallback when disabled", () => {
    const { rerender } = render(
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["trust_accounting"]}
        terminologyNamespace={null}
      >
        <ModuleGate module="trust_accounting" fallback={<span>No Access</span>}>
          <span>Trust Accounting Content</span>
        </ModuleGate>
      </OrgProfileProvider>,
    );

    // Module enabled — children visible
    expect(screen.getByText("Trust Accounting Content")).toBeInTheDocument();
    expect(screen.queryByText("No Access")).not.toBeInTheDocument();

    // Re-render with module disabled
    rerender(
      <OrgProfileProvider
        verticalProfile="consulting-generic"
        enabledModules={[]}
        terminologyNamespace={null}
      >
        <ModuleGate module="trust_accounting" fallback={<span>No Access</span>}>
          <span>Trust Accounting Content</span>
        </ModuleGate>
      </OrgProfileProvider>,
    );

    // Module disabled — fallback visible
    expect(screen.queryByText("Trust Accounting Content")).not.toBeInTheDocument();
    expect(screen.getByText("No Access")).toBeInTheDocument();
  });
});
