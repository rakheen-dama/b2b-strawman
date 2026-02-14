import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ProjectFinancialsTab } from "@/components/profitability/project-financials-tab";
import type { ProjectProfitabilityResponse } from "@/lib/types";

function makeProfitability(
  overrides: Partial<ProjectProfitabilityResponse> = {},
): ProjectProfitabilityResponse {
  return {
    projectId: "p1",
    projectName: "Alpha Project",
    currencies: [
      {
        currency: "USD",
        totalBillableHours: 120,
        totalNonBillableHours: 30,
        totalHours: 150,
        billableValue: 18000,
        costValue: 10800,
        margin: 7200,
        marginPercent: 40,
      },
    ],
    ...overrides,
  };
}

describe("ProjectFinancialsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders profitability data with currency breakdowns", () => {
    const profitability = makeProfitability();
    render(<ProjectFinancialsTab profitability={profitability} />);

    expect(screen.getByText("Project Profitability")).toBeInTheDocument();
    expect(screen.getByText("120.0h")).toBeInTheDocument();
    expect(screen.getByText("$18,000.00")).toBeInTheDocument();
    expect(screen.getByText("$10,800.00")).toBeInTheDocument();
    expect(screen.getByText("$7,200.00")).toBeInTheDocument();
    expect(screen.getByText("40.0%")).toBeInTheDocument();
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
    render(<ProjectFinancialsTab profitability={profitability} />);

    // Cost shows N/A from formatCurrencySafe, margin and margin% show N/A
    const naElements = screen.getAllByText("N/A");
    expect(naElements.length).toBeGreaterThanOrEqual(3);
  });

  it("shows empty state when profitability is null", () => {
    render(<ProjectFinancialsTab profitability={null} />);

    expect(screen.getByText("No financial data yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Track billable time and set up billing rates to see project profitability here.",
      ),
    ).toBeInTheDocument();
  });
});
