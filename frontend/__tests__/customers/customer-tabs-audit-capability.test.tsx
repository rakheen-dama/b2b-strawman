import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Mock next/navigation BEFORE importing the component.
vi.mock("next/navigation", () => ({
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

import { CustomerTabs } from "@/components/customers/customer-tabs";
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

const baseProps = {
  projectsPanel: <div>projects</div>,
  documentsPanel: <div>documents</div>,
  auditPanel: <div data-testid="audit-panel">audit</div>,
};

describe("CustomerTabs — Audit tab capability gating (PR #1281 follow-up)", () => {
  it("renders the Audit tab when the viewer has TEAM_OVERSIGHT", () => {
    render(withCaps(<CustomerTabs {...baseProps} />, { caps: ["TEAM_OVERSIGHT"] }));
    expect(screen.getByRole("tab", { name: "Audit Trail" })).toBeInTheDocument();
  });

  it("does NOT render the Audit tab when the viewer lacks TEAM_OVERSIGHT", () => {
    render(withCaps(<CustomerTabs {...baseProps} />, { caps: [] }));
    expect(screen.queryByRole("tab", { name: "Audit Trail" })).not.toBeInTheDocument();
  });

  it("renders the Audit tab for admins regardless of explicit capability list", () => {
    render(withCaps(<CustomerTabs {...baseProps} />, { caps: [], isAdmin: true }));
    expect(screen.getByRole("tab", { name: "Audit Trail" })).toBeInTheDocument();
  });

  it("does NOT render the Audit tab when no auditPanel was supplied (panel-absent path)", () => {
    const { auditPanel: _unused, ...noAuditProps } = baseProps;
    render(withCaps(<CustomerTabs {...noAuditProps} />, { caps: ["TEAM_OVERSIGHT"] }));
    expect(screen.queryByRole("tab", { name: "Audit Trail" })).not.toBeInTheDocument();
  });
});
