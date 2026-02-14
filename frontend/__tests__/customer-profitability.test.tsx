import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomerProfitabilitySection } from "@/components/profitability/customer-profitability-section";
import type { OrgProfitabilityResponse } from "@/lib/types";

// Mock server action
const mockGetOrgProfitability = vi.fn();

vi.mock("@/app/(app)/org/[slug]/profitability/actions", () => ({
  getOrgProfitability: (...args: unknown[]) =>
    mockGetOrgProfitability(...args),
}));

function makeData(
  overrides: Partial<OrgProfitabilityResponse> = {},
): OrgProfitabilityResponse {
  return {
    projects: [
      {
        projectId: "p1",
        projectName: "Alpha Project",
        customerName: "Acme Corp",
        currency: "USD",
        billableHours: 100,
        billableValue: 15000,
        costValue: 9000,
        margin: 6000,
        marginPercent: 40,
      },
      {
        projectId: "p2",
        projectName: "Beta Project",
        customerName: "Acme Corp",
        currency: "USD",
        billableHours: 50,
        billableValue: 7500,
        costValue: 4500,
        margin: 3000,
        marginPercent: 40,
      },
    ],
    ...overrides,
  };
}

const defaultProps = {
  initialFrom: "2026-02-01",
  initialTo: "2026-02-14",
};

describe("CustomerProfitabilitySection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders customer rows with aggregated revenue, cost, and margin", () => {
    const data = makeData();
    render(
      <CustomerProfitabilitySection initialData={data} {...defaultProps} />,
    );

    expect(screen.getByText("Customer Profitability")).toBeInTheDocument();
    // Acme Corp aggregate: revenue = 15000 + 7500 = 22500
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    expect(screen.getByText("$22,500.00")).toBeInTheDocument();
    // cost = 9000 + 4500 = 13500
    expect(screen.getByText("$13,500.00")).toBeInTheDocument();
    // margin = 22500 - 13500 = 9000
    expect(screen.getByText("$9,000.00")).toBeInTheDocument();
    // margin % = (9000 / 22500) * 100 = 40.0%
    expect(screen.getByText("40.0%")).toBeInTheDocument();
  });

  it("shows em-dash when cost data is missing", () => {
    const data = makeData({
      projects: [
        {
          projectId: "p1",
          projectName: "No Cost Project",
          customerName: "Widget Inc",
          currency: "EUR",
          billableHours: 80,
          billableValue: 12000,
          costValue: null,
          margin: null,
          marginPercent: null,
        },
      ],
    });
    render(
      <CustomerProfitabilitySection initialData={data} {...defaultProps} />,
    );

    expect(screen.getByText("Widget Inc")).toBeInTheDocument();
    // Cost column shows N/A from formatCurrencySafe
    expect(screen.getByText("N/A")).toBeInTheDocument();
    // Margin and Margin % columns show em-dash
    const dashes = screen.getAllByText("\u2014");
    expect(dashes.length).toBe(2); // margin + margin %
  });

  it("renders empty state when no project data", () => {
    const data = makeData({ projects: [] });
    render(
      <CustomerProfitabilitySection initialData={data} {...defaultProps} />,
    );

    expect(
      screen.getByText("No customer profitability data for this period"),
    ).toBeInTheDocument();
  });

  it("expand/collapse shows project breakdown rows", async () => {
    const user = userEvent.setup();
    const data = makeData();
    render(
      <CustomerProfitabilitySection initialData={data} {...defaultProps} />,
    );

    // Initially, project names should not be visible (only customer aggregate row)
    expect(screen.queryByText("Alpha Project")).not.toBeInTheDocument();
    expect(screen.queryByText("Beta Project")).not.toBeInTheDocument();

    // Click the customer row to expand
    const customerRow = screen.getByText("Acme Corp").closest("tr")!;
    await user.click(customerRow);

    // Now project breakdown rows should be visible
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    expect(screen.getByText("Beta Project")).toBeInTheDocument();

    // Verify project-level data is shown
    expect(screen.getByText("$15,000.00")).toBeInTheDocument();
    expect(screen.getByText("$7,500.00")).toBeInTheDocument();

    // Click again to collapse
    await user.click(customerRow);

    expect(screen.queryByText("Alpha Project")).not.toBeInTheDocument();
    expect(screen.queryByText("Beta Project")).not.toBeInTheDocument();
  });

  it("groups projects without a customer under Unassigned", () => {
    const data = makeData({
      projects: [
        {
          projectId: "p1",
          projectName: "Orphan Project",
          customerName: null,
          currency: "USD",
          billableHours: 20,
          billableValue: 3000,
          costValue: 1500,
          margin: 1500,
          marginPercent: 50,
        },
      ],
    });
    render(
      <CustomerProfitabilitySection initialData={data} {...defaultProps} />,
    );

    expect(screen.getByText("Unassigned")).toBeInTheDocument();
    expect(screen.getByText("$3,000.00")).toBeInTheDocument();
  });
});
