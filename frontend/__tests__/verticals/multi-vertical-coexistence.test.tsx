import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// --- Mocks (before component imports) ---

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

// --- Imports after mocks ---

import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { NAV_GROUPS } from "@/lib/nav-items";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Profile wrappers ---

function withLegalProfile(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile="legal-za"
      enabledModules={["court_calendar", "conflict_check", "lssa_tariff"]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

function withAccountingProfile(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile="accounting-za"
      enabledModules={["regulatory_deadlines"]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

// --- Legal nav items (expected visible for legal profile) ---
const LEGAL_NAV_MODULES = ["court_calendar", "conflict_check", "lssa_tariff"];

// --- Accounting nav items (expected visible for accounting profile) ---
const ACCOUNTING_NAV_MODULES = ["regulatory_deadlines"];

// ===== Tests =====

describe("Multi-vertical coexistence — nav visibility", () => {
  it("legal profile renders court calendar, conflict check, tariffs in sidebar nav", () => {
    // Collect all nav items that require one of the legal modules
    const legalNavItems = NAV_GROUPS.flatMap((g) => g.items).filter(
      (item) => item.requiredModule && LEGAL_NAV_MODULES.includes(item.requiredModule)
    );

    // Should find at least: Court Calendar, Conflict Check, Adverse Parties, Tariffs
    expect(legalNavItems.length).toBeGreaterThanOrEqual(4);

    // Render each with ModuleGate in legal profile to verify visibility
    const { unmount } = render(
      withLegalProfile(
        <>
          {legalNavItems.map((item) => (
            <ModuleGate key={item.label} module={item.requiredModule!}>
              <span data-testid={`nav-${item.label}`}>{item.label}</span>
            </ModuleGate>
          ))}
        </>
      )
    );

    for (const item of legalNavItems) {
      expect(screen.getByTestId(`nav-${item.label}`)).toBeInTheDocument();
    }

    unmount();
  });

  it("accounting profile does NOT render legal nav items", () => {
    const legalNavItems = NAV_GROUPS.flatMap((g) => g.items).filter(
      (item) => item.requiredModule && LEGAL_NAV_MODULES.includes(item.requiredModule)
    );

    render(
      withAccountingProfile(
        <>
          {legalNavItems.map((item) => (
            <ModuleGate key={item.label} module={item.requiredModule!}>
              <span data-testid={`nav-${item.label}`}>{item.label}</span>
            </ModuleGate>
          ))}
        </>
      )
    );

    for (const item of legalNavItems) {
      expect(screen.queryByTestId(`nav-${item.label}`)).not.toBeInTheDocument();
    }
  });

  it("legal profile does NOT render accounting-specific nav items", () => {
    const accountingNavItems = NAV_GROUPS.flatMap((g) => g.items).filter(
      (item) => item.requiredModule && ACCOUNTING_NAV_MODULES.includes(item.requiredModule)
    );

    expect(accountingNavItems.length).toBeGreaterThanOrEqual(1);

    render(
      withLegalProfile(
        <>
          {accountingNavItems.map((item) => (
            <ModuleGate key={item.label} module={item.requiredModule!}>
              <span data-testid={`nav-${item.label}`}>{item.label}</span>
            </ModuleGate>
          ))}
        </>
      )
    );

    for (const item of accountingNavItems) {
      expect(screen.queryByTestId(`nav-${item.label}`)).not.toBeInTheDocument();
    }
  });

  it("profile switch from accounting to legal updates visible modules", () => {
    // Start with accounting profile
    const { rerender } = render(
      withAccountingProfile(
        <>
          <ModuleGate module="court_calendar">
            <span data-testid="court-calendar">Court Calendar</span>
          </ModuleGate>
          <ModuleGate module="regulatory_deadlines">
            <span data-testid="deadlines">Deadlines</span>
          </ModuleGate>
          <ModuleGate module="lssa_tariff">
            <span data-testid="tariffs">Tariffs</span>
          </ModuleGate>
        </>
      )
    );

    // Accounting: deadlines visible, court calendar and tariffs hidden
    expect(screen.getByTestId("deadlines")).toBeInTheDocument();
    expect(screen.queryByTestId("court-calendar")).not.toBeInTheDocument();
    expect(screen.queryByTestId("tariffs")).not.toBeInTheDocument();

    // Switch to legal profile
    rerender(
      withLegalProfile(
        <>
          <ModuleGate module="court_calendar">
            <span data-testid="court-calendar">Court Calendar</span>
          </ModuleGate>
          <ModuleGate module="regulatory_deadlines">
            <span data-testid="deadlines">Deadlines</span>
          </ModuleGate>
          <ModuleGate module="lssa_tariff">
            <span data-testid="tariffs">Tariffs</span>
          </ModuleGate>
        </>
      )
    );

    // Legal: court calendar and tariffs visible, deadlines hidden
    expect(screen.getByTestId("court-calendar")).toBeInTheDocument();
    expect(screen.getByTestId("tariffs")).toBeInTheDocument();
    expect(screen.queryByTestId("deadlines")).not.toBeInTheDocument();
  });
});
