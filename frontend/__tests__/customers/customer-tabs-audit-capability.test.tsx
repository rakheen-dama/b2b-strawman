import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation BEFORE importing the component.
const mockReplace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: mockReplace }),
  useSearchParams: () => new URLSearchParams(),
}));

// audit-timeline-tab transitively pulls a `server-only` module via the audit
// data layer; stub it so this jsdom test can import the audit hook.
vi.mock("server-only", () => ({}));
vi.mock("@/lib/actions/audit-events", () => ({
  fetchEntityAuditPage: vi.fn(),
}));

// Mock useOrgProfile / useTerminology — the audit-tab gate is the focus here.
vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({ isModuleEnabled: () => false }),
}));
vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({ t: (s: string) => s }),
}));
vi.mock("@/lib/terminology-map", () => ({
  auditTabLabel: () => "Audit Trail",
}));

// GroupedTabBar uses motion/react for the underline indicator
vi.mock("motion/react", () => ({
  motion: {
    span: "span",
  },
}));

import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";
import { CapabilityProvider } from "@/lib/capabilities";

afterEach(() => {
  cleanup();
});

function withCaps(ui: React.ReactNode, { caps = ["TEAM_OVERSIGHT"], isAdmin = false } = {}) {
  return (
    <CapabilityProvider
      capabilities={caps}
      role={isAdmin ? "Admin" : "Member"}
      isAdmin={isAdmin}
      isOwner={false}
    >
      {ui}
    </CapabilityProvider>
  );
}

const placeholder = <div>placeholder</div>;

const baseProps = {
  detailsPanel: placeholder,
  fieldsPanel: placeholder,
  tagsPanel: placeholder,
  overviewPanel: placeholder,
  projectsPanel: placeholder,
  documentsPanel: placeholder,
  auditPanel: <div data-testid="audit-panel">audit</div>,
};

describe("CustomerGroupedTabs — Audit tab capability gating (PR #1281 follow-up)", () => {
  it("renders the Activity group (containing audit) when the viewer has TEAM_OVERSIGHT", () => {
    render(withCaps(<CustomerGroupedTabs {...baseProps} />, { caps: ["TEAM_OVERSIGHT"] }));
    // The Activity group trigger should be visible
    expect(screen.getByTestId("tab-group-activity")).toBeInTheDocument();
  });

  it("does NOT render the Activity group when the viewer lacks TEAM_OVERSIGHT", () => {
    render(withCaps(<CustomerGroupedTabs {...baseProps} />, { caps: [] }));
    expect(screen.queryByTestId("tab-group-activity")).not.toBeInTheDocument();
  });

  it("renders the Activity group for admins regardless of explicit capability list", () => {
    render(withCaps(<CustomerGroupedTabs {...baseProps} />, { caps: [], isAdmin: true }));
    expect(screen.getByTestId("tab-group-activity")).toBeInTheDocument();
  });

  it("does NOT render the Activity group when no auditPanel was supplied (panel-absent path)", () => {
    const { auditPanel: _unused, ...noAuditProps } = baseProps;
    render(withCaps(<CustomerGroupedTabs {...noAuditProps} />, { caps: ["TEAM_OVERSIGHT"] }));
    expect(screen.queryByTestId("tab-group-activity")).not.toBeInTheDocument();
  });
});
