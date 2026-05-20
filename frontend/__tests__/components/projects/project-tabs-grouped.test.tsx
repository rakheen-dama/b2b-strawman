import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// ---------------------------------------------------------------------------
// Mocks — hoisted above component import
// ---------------------------------------------------------------------------

const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ replace: mockReplace }),
}));

// Controllable module-gating mock
const mockIsModuleEnabled = vi.fn((moduleId: string) => {
  // Default: all modules enabled except trust_accounting
  const enabled: Record<string, boolean> = {
    court_calendar: true,
    conflict_check: true,
    trust_accounting: false,
    disbursements: true,
  };
  return enabled[moduleId] ?? false;
});

vi.mock("@/lib/org-profile", () => ({
  useOrgProfile: () => ({
    isModuleEnabled: (moduleId: string) => mockIsModuleEnabled(moduleId),
  }),
}));

// Controllable audit visibility mock
const mockUseAuditTabVisible = vi.fn(() => true);

vi.mock("@/components/audit/audit-timeline-tab", () => ({
  useAuditTabVisible: () => mockUseAuditTabVisible(),
}));

vi.mock("motion/react", () => ({
  motion: {
    span: "span",
  },
}));

vi.mock("@/lib/terminology", () => ({
  useTerminology: () => ({
    t: (term: string) => term,
    verticalProfile: null,
  }),
}));

vi.mock("@/lib/terminology-map", () => ({
  auditTabLabel: (t: (s: string) => string) => {
    const v = t("audit.tab");
    return v === "audit.tab" ? "Audit" : v;
  },
}));

// ---------------------------------------------------------------------------
// Import component AFTER mocks
// ---------------------------------------------------------------------------

import { ProjectTabs } from "@/components/projects/project-tabs";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Minimal required props for ProjectTabs (all required panels provided) */
const baseProps = {
  overviewPanel: <div>Overview</div>,
  documentsPanel: <div>Documents</div>,
  membersPanel: <div>Members</div>,
  customersPanel: <div>Customers</div>,
  tasksPanel: <div>Tasks</div>,
  timePanel: <div>Time</div>,
  activityPanel: <div>Activity</div>,
};

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  // Restore default mock implementations
  mockIsModuleEnabled.mockImplementation((moduleId: string) => {
    const enabled: Record<string, boolean> = {
      court_calendar: true,
      conflict_check: true,
      trust_accounting: false,
      disbursements: true,
    };
    return enabled[moduleId] ?? false;
  });
  mockUseAuditTabVisible.mockImplementation(() => true);
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ProjectTabs with GroupedTabBar integration", () => {
  it("renders GroupedTabBar (not flat TabsPrimitive.List)", () => {
    render(<ProjectTabs {...baseProps} />);

    // GroupedTabBar should be present
    expect(screen.getByTestId("grouped-tab-bar")).toBeInTheDocument();

    // Should render group triggers, not individual tab triggers
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
  });

  it("hides trust tab in finance group when trust_accounting module is off", () => {
    render(
      <ProjectTabs
        {...baseProps}
        trustPanel={<div>Trust</div>}
        expensesPanel={<div>Expenses</div>}
        ratesPanel={<div>Rates</div>}
      />
    );

    // Finance group should still be visible (has other tabs like time, expenses)
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();

    // Trust tab should NOT be in the dropdown items (trust_accounting is off)
    expect(screen.queryByTestId("tab-item-trust")).not.toBeInTheDocument();
  });

  it("hides schedule group when court_calendar module is off", () => {
    // Override: disable court_calendar
    mockIsModuleEnabled.mockImplementation((moduleId: string) => {
      const enabled: Record<string, boolean> = {
        court_calendar: false,
        conflict_check: true,
        trust_accounting: false,
        disbursements: false,
      };
      return enabled[moduleId] ?? false;
    });

    render(<ProjectTabs {...baseProps} courtDatesPanel={<div>Court Dates</div>} />);

    // Schedule group should be completely hidden (court-dates is its only tab and it is gated off)
    expect(screen.queryByTestId("tab-group-schedule")).not.toBeInTheDocument();
  });

  it("renders activity group as standalone tab when audit is hidden", () => {
    // Override: audit not visible
    mockUseAuditTabVisible.mockImplementation(() => false);

    render(<ProjectTabs {...baseProps} auditPanel={<div>Audit</div>} />);

    // Activity group should be present
    const activityGroup = screen.getByTestId("tab-group-activity");
    expect(activityGroup).toBeInTheDocument();

    // Should render as standalone (no dropdown chevron) — only 'activity' tab visible
    expect(activityGroup.tagName).toBe("BUTTON");
    expect(activityGroup.querySelector("svg")).toBeNull();
  });
});
