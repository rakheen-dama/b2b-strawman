import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

// ---------------------------------------------------------------------------
// Mocks — hoisted above component import
// ---------------------------------------------------------------------------

const mockReplace = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock("next/navigation", () => ({
  useSearchParams: () => mockSearchParams,
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

// ---------------------------------------------------------------------------
// Import component AFTER mocks
// ---------------------------------------------------------------------------

import { MatterTabs } from "@/components/matters/matter-tabs";
import { MATTER_TAB_GROUPS } from "@/lib/constants/matter-tab-groups";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Minimal required props for MatterTabs (all required panels provided) */
const baseProps = {
  detailsPanel: <div>Details panel</div>,
  fieldsPanel: <div>Fields panel</div>,
  overviewPanel: <div>Overview</div>,
  documentsPanel: <div>Documents</div>,
  clientsPanel: <div>Clients panel</div>,
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
  mockSearchParams = new URLSearchParams();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("MatterTabs with GroupedTabBar integration", () => {
  it("renders GroupedTabBar (not flat TabsPrimitive.List)", () => {
    render(<MatterTabs {...baseProps} />);

    // GroupedTabBar should be present
    expect(screen.getByTestId("grouped-tab-bar")).toBeInTheDocument();

    // Should render group triggers, not individual tab triggers
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
  });

  it("hides trust tab in finance group when trust_accounting module is off", () => {
    render(
      <MatterTabs {...baseProps} trustPanel={<div>Trust</div>} ratesPanel={<div>Rates</div>} />
    );

    // Finance group should still be visible (has other tabs like time, rates)
    const financeGroup = screen.getByTestId("tab-group-finance");
    expect(financeGroup).toBeInTheDocument();

    // Open the finance dropdown so Radix mounts the menu items
    fireEvent.click(financeGroup);

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

    render(<MatterTabs {...baseProps} courtDatesPanel={<div>Court Dates</div>} />);

    // Schedule group should be completely hidden (court-dates is its only tab and it is gated off)
    expect(screen.queryByTestId("tab-group-schedule")).not.toBeInTheDocument();
  });

  it("resolves the legacy ?tab=members link to Details › Team (ADR-346) and renders no staffing tab", () => {
    mockSearchParams = new URLSearchParams("tab=members");

    render(<MatterTabs {...baseProps} teamPanel={<div>Team panel</div>} />);

    // members is an alias of team (legacy-tab-ids.ts) — the old setup-step link and bookmarks
    // land on the Team tab without a router redirect. The Epic 599 staffing tab stays gone.
    expect(mockReplace).not.toHaveBeenCalled();
    expect(screen.getByTestId("tab-group-details")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Team panel")).toBeInTheDocument();
    expect(screen.queryByTestId("tab-item-staffing")).not.toBeInTheDocument();
  });

  it("falls back to Overview for ?tab=team when no team panel is provided", () => {
    mockSearchParams = new URLSearchParams("tab=team");

    render(<MatterTabs {...baseProps} />);

    expect(screen.getByTestId("tab-group-overview")).toHaveAttribute("aria-selected", "true");
  });

  it("renders activity group as standalone tab when audit is hidden", () => {
    // Override: audit not visible
    mockUseAuditTabVisible.mockImplementation(() => false);

    render(<MatterTabs {...baseProps} auditPanel={<div>Audit</div>} />);

    // Activity group should be present
    const activityGroup = screen.getByTestId("tab-group-activity");
    expect(activityGroup).toBeInTheDocument();

    // Should render as standalone (no dropdown chevron) — only 'activity' tab visible
    expect(activityGroup.tagName).toBe("BUTTON");
    expect(activityGroup.querySelector("svg")).toBeNull();
  });
  // -------------------------------------------------------------------------
  // Phase 87 §12.6.5 / ADR-340 (615E): relabelled groups, legacy ?tab= aliases, no Expenses tab
  // -------------------------------------------------------------------------

  it("labels the groups with the legal words (Fees, Diary)", () => {
    render(<MatterTabs {...baseProps} courtDatesPanel={<div>Court dates panel</div>} />);

    expect(screen.getByTestId("tab-group-finance").textContent).toContain("Fees");
    expect(screen.getByTestId("tab-group-schedule").textContent).toContain("Diary");
  });

  it("resolves the legacy ?tab=customers deep link to the Clients tab", () => {
    mockSearchParams = new URLSearchParams("tab=customers");

    render(<MatterTabs {...baseProps} />);

    expect(screen.getByTestId("tab-group-client")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Clients panel")).toBeInTheDocument();
  });

  it("resolves the legacy ?tab=expenses deep link to the Disbursements tab", () => {
    mockSearchParams = new URLSearchParams("tab=expenses");

    render(<MatterTabs {...baseProps} disbursementsPanel={<div>Disbursements panel</div>} />);

    expect(screen.getByTestId("tab-group-finance")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Disbursements panel")).toBeInTheDocument();
  });

  it("falls back to Overview for ?tab=expenses when the disbursements module is off", () => {
    mockIsModuleEnabled.mockImplementation(() => false);
    mockSearchParams = new URLSearchParams("tab=expenses");

    render(<MatterTabs {...baseProps} disbursementsPanel={<div>Disbursements panel</div>} />);

    expect(screen.getByTestId("tab-group-overview")).toHaveAttribute("aria-selected", "true");
    expect(screen.queryByText("Disbursements panel")).not.toBeInTheDocument();
  });

  it("offers Disbursements but no Expenses item in the Fees dropdown", () => {
    mockSearchParams = new URLSearchParams("tab=time");

    render(<MatterTabs {...baseProps} disbursementsPanel={<div>Disbursements panel</div>} />);

    // Fees is the active group, so clicking its trigger opens the dropdown.
    const feesGroup = screen.getByTestId("tab-group-finance");
    fireEvent.pointerDown(feesGroup, { button: 0, pointerType: "mouse" });
    fireEvent.click(feesGroup);

    expect(screen.getByTestId("tab-item-disbursements")).toBeInTheDocument();
    expect(screen.queryByTestId("tab-item-expenses")).not.toBeInTheDocument();
  });

  it("resolves every MATTER_TAB_GROUPS tab id from ?tab= (validTabIds == constants)", () => {
    const allPanels = {
      ...baseProps,
      detailsPanel: <div>Details</div>,
      fieldsPanel: <div>Fields</div>,
      teamPanel: <div>Team</div>,
      correspondencePanel: <div>Correspondence</div>,
      ratesPanel: <div>Rates</div>,
      budgetPanel: <div>Budget</div>,
      financialsPanel: <div>Financials</div>,
      generatedPanel: <div>Generated</div>,
      requestsPanel: <div>Requests</div>,
      customerCommentsPanel: <div>Comments</div>,
      courtDatesPanel: <div>Court dates</div>,
      adversePartiesPanel: <div>Adverse parties</div>,
      trustPanel: <div>Trust</div>,
      disbursementsPanel: <div>Disbursements</div>,
      statementsPanel: <div>Statements</div>,
      auditPanel: <div>Audit</div>,
    };
    mockIsModuleEnabled.mockImplementation(() => true);

    const tabs = MATTER_TAB_GROUPS.flatMap((g) =>
      g.tabs.map((t) => ({ groupId: g.id, tabId: t.id }))
    );
    expect(tabs).toHaveLength(22);

    for (const { groupId, tabId } of tabs) {
      mockSearchParams = new URLSearchParams(`tab=${tabId}`);
      const { unmount } = render(<MatterTabs {...allPanels} />);
      expect(screen.getByTestId(`tab-group-${groupId}`), tabId).toHaveAttribute(
        "aria-selected",
        "true"
      );
      unmount();
    }
  });

  // -------------------------------------------------------------------------
  // Phase 88 G-B14 / G88-M12: the URL is the single source of truth — a click writes ?tab=
  // through window.history.replaceState (no RSC round-trip), and no local tab state exists.
  // -------------------------------------------------------------------------

  describe("URL-as-source tabs (G-B14)", () => {
    let replaceStateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      replaceStateSpy = vi.spyOn(window.history, "replaceState").mockImplementation(() => {});
    });

    afterEach(() => {
      replaceStateSpy.mockRestore();
    });

    it("writes ?tab=time when Fees is clicked on a ?tab=tasks landing", () => {
      mockSearchParams = new URLSearchParams("tab=tasks");
      render(<MatterTabs {...baseProps} />);
      expect(screen.getByTestId("tab-group-work")).toHaveAttribute("aria-selected", "true");

      // Fees is inactive, so its trigger navigates to its first sub-tab (time recording).
      fireEvent.click(screen.getByTestId("tab-group-finance"));

      expect(replaceStateSpy).toHaveBeenCalledTimes(1);
      expect(replaceStateSpy).toHaveBeenCalledWith(null, "", "?tab=time");
      // No soft navigation: the page segment never reads ?tab= server-side.
      expect(mockReplace).not.toHaveBeenCalled();
    });

    it("writes ?tab= on a click when the page was opened without one", () => {
      render(<MatterTabs {...baseProps} />);

      fireEvent.click(screen.getByTestId("tab-group-finance"));

      expect(replaceStateSpy).toHaveBeenCalledWith(null, "", "?tab=time");
    });

    it("keeps other query params (taskId) when switching tabs", () => {
      mockSearchParams = new URLSearchParams("tab=tasks&taskId=t-1");
      render(<MatterTabs {...baseProps} />);

      fireEvent.click(screen.getByTestId("tab-group-overview"));

      expect(replaceStateSpy).toHaveBeenCalledTimes(1);
      const url = String(replaceStateSpy.mock.calls[0][2]);
      const params = new URLSearchParams(url.replace(/^\?/, ""));
      expect(params.get("tab")).toBe("overview");
      expect(params.get("taskId")).toBe("t-1");
    });

    it("renders whatever tab the URL says, even after a click (no local tab state)", () => {
      const { rerender } = render(<MatterTabs {...baseProps} />);

      // A click alone does not switch the panel — the URL update does (replaceState is stubbed
      // here, so the URL stays empty and Overview stays selected).
      fireEvent.click(screen.getByTestId("tab-group-finance"));
      rerender(<MatterTabs {...baseProps} />);
      expect(screen.getByTestId("tab-group-overview")).toHaveAttribute("aria-selected", "true");

      mockSearchParams = new URLSearchParams("tab=time");
      rerender(<MatterTabs {...baseProps} />);
      expect(screen.getByTestId("tab-group-finance")).toHaveAttribute("aria-selected", "true");
      expect(screen.getByText("Time")).toBeInTheDocument();
    });
  });
});
