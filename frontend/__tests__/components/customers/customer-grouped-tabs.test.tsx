import { describe, it, expect, vi, afterEach } from "vitest";
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
  const enabled: Record<string, boolean> = {
    trust_accounting: true,
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

import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Minimal required props — all required panels provided */
const baseProps = {
  detailsPanel: <div>Details</div>,
  fieldsPanel: <div>Fields</div>,
  tagsPanel: <div>Tags</div>,
  overviewPanel: <div>Overview</div>,
  projectsPanel: <div>Projects</div>,
  documentsPanel: <div>Documents</div>,
};

/** All optional panels provided */
const allPanels = {
  ...baseProps,
  generatedPanel: <div>Generated</div>,
  invoicesPanel: <div>Invoices</div>,
  ratesPanel: <div>Rates</div>,
  retainerPanel: <div>Retainer</div>,
  financialsPanel: <div>Financials</div>,
  trustPanel: <div>Trust</div>,
  onboardingPanel: <div>Onboarding</div>,
  requestsPanel: <div>Requests</div>,
  auditPanel: <div>Audit</div>,
};

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  mockIsModuleEnabled.mockImplementation((moduleId: string) => {
    const enabled: Record<string, boolean> = {
      trust_accounting: true,
    };
    return enabled[moduleId] ?? false;
  });
  mockUseAuditTabVisible.mockImplementation(() => true);
  mockSearchParams = new URLSearchParams();
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("CustomerGroupedTabs", () => {
  it("renders GroupedTabBar with 6 groups when all panels provided", () => {
    render(<CustomerGroupedTabs {...allPanels} />);

    expect(screen.getByTestId("grouped-tab-bar")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-details")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-overview")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-work")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-compliance")).toBeInTheDocument();
    expect(screen.getByTestId("tab-group-activity")).toBeInTheDocument();
  });

  it("hides Finance group when all finance panels are null", () => {
    render(<CustomerGroupedTabs {...baseProps} />);

    // Finance group should not be present — no finance panels provided
    expect(screen.queryByTestId("tab-group-finance")).not.toBeInTheDocument();
  });

  it("hides Activity group when auditVisible is false", () => {
    mockUseAuditTabVisible.mockImplementation(() => false);

    render(<CustomerGroupedTabs {...baseProps} auditPanel={<div>Audit</div>} />);

    // Activity group hidden — audit is the only tab and it is gated off
    expect(screen.queryByTestId("tab-group-activity")).not.toBeInTheDocument();
  });

  it("defaults to overview tab when no ?tab= param", () => {
    render(<CustomerGroupedTabs {...allPanels} />);

    // Overview group should be active (aria-selected)
    const overviewGroup = screen.getByTestId("tab-group-overview");
    expect(overviewGroup.getAttribute("aria-selected")).toBe("true");
  });

  it("resolves ?tab=invoices to Finance group", () => {
    mockSearchParams = new URLSearchParams("tab=invoices");

    render(<CustomerGroupedTabs {...allPanels} />);

    // Finance group should be active
    const financeGroup = screen.getByTestId("tab-group-finance");
    expect(financeGroup.getAttribute("aria-selected")).toBe("true");
  });

  it("resolves group-level alias ?tab=work to projects", () => {
    mockSearchParams = new URLSearchParams("tab=work");

    render(<CustomerGroupedTabs {...allPanels} />);

    // Work group should be active
    const workGroup = screen.getByTestId("tab-group-work");
    expect(workGroup.getAttribute("aria-selected")).toBe("true");
  });

  it("hides trust tab when trust_accounting disabled", () => {
    mockIsModuleEnabled.mockImplementation(() => false);
    // Set tab=invoices so the Finance group is already active; clicking it
    // opens the dropdown instead of navigating to the first sub-tab.
    mockSearchParams = new URLSearchParams("tab=invoices");

    render(
      <CustomerGroupedTabs
        {...baseProps}
        invoicesPanel={<div>Invoices</div>}
        ratesPanel={<div>Rates</div>}
        trustPanel={<div>Trust</div>}
      />
    );

    // Finance group should still be visible (invoices + rates are present)
    const financeGroup = screen.getByTestId("tab-group-finance");
    expect(financeGroup).toBeInTheDocument();

    // Open the finance dropdown to see sub-tabs
    fireEvent.click(financeGroup);

    // Trust tab should NOT be in the dropdown items
    expect(screen.queryByTestId("tab-item-trust")).not.toBeInTheDocument();
  });

  it("renders Compliance as standalone tab when only requests visible", () => {
    render(<CustomerGroupedTabs {...baseProps} requestsPanel={<div>Requests</div>} />);

    // Compliance group should be present
    const complianceGroup = screen.getByTestId("tab-group-compliance");
    expect(complianceGroup).toBeInTheDocument();

    // Should render as standalone (no dropdown chevron) — only 'requests' tab visible
    expect(complianceGroup.tagName).toBe("BUTTON");
    expect(complianceGroup.querySelector("svg")).toBeNull();
  });
});
