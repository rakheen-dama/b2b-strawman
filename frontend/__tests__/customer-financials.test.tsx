import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CustomerFinancialsTab } from "@/components/profitability/customer-financials-tab";
import type {
  CustomerProfitabilityResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";

function makeProfitability(
  overrides: Partial<CustomerProfitabilityResponse> = {},
): CustomerProfitabilityResponse {
  return {
    customerId: "c1",
    customerName: "Acme Corp",
    currencies: [
      {
        currency: "USD",
        totalBillableHours: 200,
        totalNonBillableHours: 40,
        totalHours: 240,
        billableValue: 30000,
        costValue: 18000,
        margin: 12000,
        marginPercent: 40,
      },
    ],
    ...overrides,
  };
}

function makeProjectBreakdown(
  overrides: Partial<OrgProfitabilityResponse> = {},
): OrgProfitabilityResponse {
  return {
    projects: [
      {
        projectId: "p1",
        projectName: "Alpha Project",
        customerName: "Acme Corp",
        currency: "USD",
        billableHours: 120,
        billableValue: 18000,
        costValue: 10800,
        margin: 7200,
        marginPercent: 40,
      },
      {
        projectId: "p2",
        projectName: "Beta Project",
        customerName: "Acme Corp",
        currency: "USD",
        billableHours: 80,
        billableValue: 12000,
        costValue: 7200,
        margin: 4800,
        marginPercent: 40,
      },
    ],
    ...overrides,
  };
}

describe("CustomerFinancialsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders customer profitability data with stat cards", () => {
    const profitability = makeProfitability();
    render(
      <CustomerFinancialsTab
        profitability={profitability}
        projectBreakdown={null}
      />,
    );

    expect(screen.getByText("Customer Profitability")).toBeInTheDocument();
    expect(screen.getByText("200.0h")).toBeInTheDocument();
    expect(screen.getByText("$30,000.00")).toBeInTheDocument();
    expect(screen.getByText("$18,000.00")).toBeInTheDocument();
    expect(screen.getByText("$12,000.00")).toBeInTheDocument();
    expect(screen.getByText("40.0%")).toBeInTheDocument();
  });

  it("renders per-project breakdown table", () => {
    const profitability = makeProfitability();
    const breakdown = makeProjectBreakdown();
    render(
      <CustomerFinancialsTab
        profitability={profitability}
        projectBreakdown={breakdown}
      />,
    );

    expect(screen.getByText("Per-Project Breakdown")).toBeInTheDocument();
    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    expect(screen.getByText("Beta Project")).toBeInTheDocument();
    expect(screen.getByText("120.0h")).toBeInTheDocument();
    expect(screen.getByText("80.0h")).toBeInTheDocument();
  });

  it("shows N/A for margin when cost rates are not available", () => {
    const profitability = makeProfitability({
      currencies: [
        {
          currency: "EUR",
          totalBillableHours: 80,
          totalNonBillableHours: 10,
          totalHours: 90,
          billableValue: 12000,
          costValue: null,
          margin: null,
          marginPercent: null,
        },
      ],
    });
    render(
      <CustomerFinancialsTab
        profitability={profitability}
        projectBreakdown={null}
      />,
    );

    const naElements = screen.getAllByText("N/A");
    expect(naElements.length).toBeGreaterThanOrEqual(3);
  });

  it("shows empty state when profitability is null", () => {
    render(
      <CustomerFinancialsTab
        profitability={null}
        projectBreakdown={null}
      />,
    );

    expect(screen.getByText("No financial data yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Track billable time and set up billing rates to see customer profitability here.",
      ),
    ).toBeInTheDocument();
  });
});
