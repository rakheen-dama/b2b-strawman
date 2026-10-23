import fs from "node:fs";
import path from "node:path";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

import {
  CapabilityProvider,
  useCapabilities,
  RequiresCapability,
  CAPABILITIES,
  OWNER_ONLY_CAPABILITIES,
} from "@/lib/capabilities";

afterEach(() => {
  cleanup();
});

// Literal names (not the mirror itself) so the behaviour tests are not tautological.
const OWNER_ONLY_NAMES = [
  "MANAGE_COMPLIANCE_DESTRUCTIVE",
  "OVERRIDE_MATTER_CLOSURE",
  "FINANCIAL_RECONCILE",
] as const;

function CapProbe({ caps }: { caps: readonly string[] }) {
  const { hasCapability } = useCapabilities();
  return (
    <>
      {caps.map((c) => (
        <span key={c} data-testid={`cap-${c}`}>
          {String(hasCapability(c))}
        </span>
      ))}
    </>
  );
}

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
        <span data-testid="has-automations">{String(hasCapability(CAPABILITIES.AUTOMATIONS))}</span>
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

    expect(screen.getByTestId("has-automations")).toHaveTextContent("false");
  });

  it("isAdmin bypasses non-owner-only capabilities — hasCapability true without the capability", () => {
    function AdminBypassChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="admin-has-automations">
          {String(hasCapability(CAPABILITIES.AUTOMATIONS))}
        </span>
      );
    }

    render(
      <CapabilityProvider capabilities={[]} role="Admin" isAdmin={true} isOwner={false}>
        <AdminBypassChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("admin-has-automations")).toHaveTextContent("true");
  });

  it("isOwner bypasses capability check — hasCapability always true", () => {
    function OwnerBypassChecker() {
      const { hasCapability } = useCapabilities();
      return (
        <span data-testid="owner-has-automations">
          {String(hasCapability(CAPABILITIES.AUTOMATIONS))}
        </span>
      );
    }

    render(
      <CapabilityProvider capabilities={[]} role="Owner" isAdmin={false} isOwner={true}>
        <OwnerBypassChecker />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("owner-has-automations")).toHaveTextContent("true");
  });

  it("admin is denied owner-only capabilities (ADR-355, G-D7)", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Admin" isAdmin={true} isOwner={false}>
        <CapProbe caps={OWNER_ONLY_NAMES} />
      </CapabilityProvider>
    );

    for (const cap of OWNER_ONLY_NAMES) {
      expect(screen.getByTestId(`cap-${cap}`)).toHaveTextContent("false");
    }
  });

  it("owner holds owner-only capabilities", () => {
    render(
      <CapabilityProvider capabilities={[]} role="Owner" isAdmin={false} isOwner={true}>
        <CapProbe caps={OWNER_ONLY_NAMES} />
      </CapabilityProvider>
    );

    for (const cap of OWNER_ONLY_NAMES) {
      expect(screen.getByTestId(`cap-${cap}`)).toHaveTextContent("true");
    }
  });

  it("an explicit grant still wins for owner-only capabilities", () => {
    // A custom role or a +OVERRIDE_MATTER_CLOSURE member override grants it on the backend.
    render(
      <CapabilityProvider
        capabilities={["OVERRIDE_MATTER_CLOSURE"]}
        role="Senior Associate"
        isAdmin={false}
        isOwner={false}
      >
        <CapProbe caps={["OVERRIDE_MATTER_CLOSURE"]} />
      </CapabilityProvider>
    );

    expect(screen.getByTestId("cap-OVERRIDE_MATTER_CLOSURE")).toHaveTextContent("true");
  });
});

// Drift pin (ADR-355, OQ #18): the client mirror must equal backend Capability.OWNER_ONLY.
// Repo-relative read of the Java source, same technique as __tests__/lib/constants/work-types.test.ts.
const CAPABILITY_JAVA = path.resolve(
  __dirname,
  "../../backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java"
);

function backendOwnerOnly(): string[] {
  const source = fs.readFileSync(CAPABILITY_JAVA, "utf8");
  const block = source.match(/\bOWNER_ONLY\s*=\s*Set\.of\(([\s\S]*?)\);/);
  if (!block) {
    throw new Error(`Capability.OWNER_ONLY = Set.of(...) not found in ${CAPABILITY_JAVA}`);
  }
  return [...block[1].matchAll(/\b([A-Z][A-Z0-9_]*)\.name\(\)/g)].map((m) => m[1]).sort();
}

describe("OWNER_ONLY_CAPABILITIES mirror", () => {
  it("equals backend Capability.OWNER_ONLY (reads Capability.java)", () => {
    const backend = backendOwnerOnly();
    expect(backend.length).toBeGreaterThan(0); // parser self-check
    expect([...OWNER_ONLY_CAPABILITIES].sort()).toEqual(backend);
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
