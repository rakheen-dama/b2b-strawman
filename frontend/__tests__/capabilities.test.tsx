import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

import {
  CapabilityProvider,
  useCapabilities,
  RequiresCapability,
  CAPABILITIES,
} from "@/lib/capabilities";

afterEach(() => {
  cleanup();
});

function TestConsumer() {
  const { capabilities, role, isAdmin, isOwner } = useCapabilities();
  return (
    <div>
      <span data-testid="role">{role}</span>
      <span data-testid="isAdmin">{String(isAdmin)}</span>
      <span data-testid="isOwner">{String(isOwner)}</span>
      <span data-testid="count">{capabilities.size}</span>
    </div>
  );
}

describe("CapabilityProvider", () => {
  it("provides capabilities, role, and flags to consumers", () => {
    render(
      <CapabilityProvider
        capabilities={[CAPABILITIES.INVOICING, CAPABILITIES.PROJECT_MANAGEMENT]}
        role="Accountant"
        isAdmin={false}
        isOwner={false}
      >
        <TestConsumer />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("role")).toHaveTextContent("Accountant");
    expect(screen.getByTestId("isAdmin")).toHaveTextContent("false");
    expect(screen.getByTestId("isOwner")).toHaveTextContent("false");
    expect(screen.getByTestId("count")).toHaveTextContent("2");
  });

  it("hasCapability returns true when capability is present", () => {
    function HasCapChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="has-invoicing">{String(hasCapability(CAPABILITIES.INVOICING))}</span>
      );
    }

    render(
      <CapabilityProvider
        capabilities={[CAPABILITIES.INVOICING]}
        role="Accountant"
        isAdmin={false}
        isOwner={false}
      >
        <HasCapChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("has-invoicing")).toHaveTextContent("true");
  });

  it("hasCapability returns false when capability is missing", () => {
    function MissingCapChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="has-resource">
          {String(hasCapability(CAPABILITIES.RESOURCE_PLANNING))}
        </span>
      );
    }

    render(
      <CapabilityProvider
        capabilities={[CAPABILITIES.INVOICING]}
        role="Accountant"
        isAdmin={false}
        isOwner={false}
      >
        <MissingCapChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("has-resource")).toHaveTextContent("false");
  });

  it("isAdmin bypasses capability check — hasCapability always true", () => {
    function AdminBypassChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="admin-has-resource">
          {String(hasCapability(CAPABILITIES.RESOURCE_PLANNING))}
        </span>
      );
    }

    render(
      <CapabilityProvider capabilities={[]} role="Admin" isAdmin={true} isOwner={false}>
        <AdminBypassChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("admin-has-resource")).toHaveTextContent("true");
  });

  it("isOwner bypasses capability check — hasCapability always true", () => {
    function OwnerBypassChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="owner-has-resource">
          {String(hasCapability(CAPABILITIES.RESOURCE_PLANNING))}
        </span>
      );
    }

    render(
      <CapabilityProvider capabilities={[]} role="Owner" isAdmin={false} isOwner={true}>
        <OwnerBypassChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("owner-has-resource")).toHaveTextContent("true");
  });
});

describe("RequiresCapability", () => {
  it("renders children when user has the required capability", () => {
    render(
      <CapabilityProvider
        capabilities={[CAPABILITIES.INVOICING]}
        role="Accountant"
        isAdmin={false}
        isOwner={false}
      >
        <RequiresCapability cap={CAPABILITIES.INVOICING}>
          <span>Invoice Section</span>
        </RequiresCapability>
      </CapabilityProvider>
    );

    expect(screen.getByText("Invoice Section")).toBeInTheDocument();
  });

  it("renders nothing when user lacks the required capability", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Member" isAdmin={false} isOwner={false}>
        <RequiresCapability cap={CAPABILITIES.INVOICING}>
          <span>Invoice Section Hidden</span>
        </RequiresCapability>
      </CapabilityProvider>
    );

    expect(screen.queryByText("Invoice Section Hidden")).not.toBeInTheDocument();
  });
});

describe("useCapabilities outside provider", () => {
  it("throws when used without CapabilityProvider", () => {
    function Orphan() {
      useCapabilities();
      return <span>should not render</span>;
    }

    // Suppress React error boundary console noise
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Orphan />)).toThrow(
      "useCapabilities must be used within a CapabilityProvider"
    );
    spy.mockRestore();
  });
});
