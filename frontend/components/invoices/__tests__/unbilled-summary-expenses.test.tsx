import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { UnbilledSummary } from "@/components/invoices/unbilled-summary";
import type { UnbilledTimeResponse, ExpenseCategory } from "@/lib/types";

const sampleDataWithExpenses: UnbilledTimeResponse = {
  customerId: "c1",
  customerName: "Acme Corp",
  projects: [
    {
      projectId: "p1",
      projectName: "Case 12345",
      entries: [
        {
          id: "te1",
          taskTitle: "Research",
          memberName: "Alice",
          date: "2026-02-10",
          durationMinutes: 120,
          billingRateSnapshot: 500,
          billingRateCurrency: "ZAR",
          billableValue: 1000,
          description: null,
        },
      ],
      totals: {
        ZAR: { hours: 2, amount: 1000 },
      },
    },
  ],
  grandTotals: {
    ZAR: { hours: 2, amount: 1000 },
  },
  unbilledExpenses: [
    {
      id: "exp1",
      projectId: "p1",
      projectName: "Case 12345",
      date: "2026-02-15",
      description: "Court filing fee",
      amount: 250,
      currency: "ZAR",
      category: "FILING_FEE" as ExpenseCategory,
      markupPercent: 20,
      billableAmount: 300,
      notes: null,
    },
    {
      id: "exp2",
      projectId: "p1",
      projectName: "Case 12345",
      date: "2026-02-16",
      description: "Courier delivery",
      amount: 150,
      currency: "ZAR",
      category: "COURIER" as ExpenseCategory,
      markupPercent: null,
      billableAmount: 150,
      notes: null,
    },
  ],
  unbilledExpenseTotals: {
    ZAR: 450,
  },
};

const emptyData: UnbilledTimeResponse = {
  customerId: "c1",
  customerName: "Acme Corp",
  projects: [],
  grandTotals: {},
  unbilledExpenses: [],
  unbilledExpenseTotals: {},
};

describe("UnbilledSummary — expense section", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders expense entries with category badges and amounts", () => {
    render(<UnbilledSummary data={sampleDataWithExpenses} />);

    expect(screen.getByText("Expenses")).toBeInTheDocument();
    expect(screen.getByText("Court filing fee")).toBeInTheDocument();
    expect(screen.getByText("Courier delivery")).toBeInTheDocument();
    expect(screen.getByText("Filing Fee")).toBeInTheDocument();
    expect(screen.getByText("Courier")).toBeInTheDocument();
    // Markup percent column
    expect(screen.getByText("20%")).toBeInTheDocument();
  });

  it("shows expense subtotal per currency", () => {
    render(<UnbilledSummary data={sampleDataWithExpenses} />);

    expect(screen.getByText("Expense Subtotal")).toBeInTheDocument();
    // The expense subtotal section should contain ZAR 450
    // locale-agnostic: normalize separators before asserting
    const subtotalSection = screen.getByText("Expense Subtotal").closest("div");
    const subtotalText = subtotalSection?.textContent?.replace(/[\s\u00a0]/g, " ") ?? "";
    expect(subtotalText.replace(/,/g, ".")).toContain("450.00");
  });

  it("shows grand total combining time and expenses", () => {
    render(<UnbilledSummary data={sampleDataWithExpenses} />);

    expect(screen.getByText("Grand Total")).toBeInTheDocument();
    // ZAR 1000 (time) + ZAR 450 (expenses) = ZAR 1450
    // locale-agnostic: normalize separators before asserting
    const grandTotalSection = screen.getByText("Grand Total").closest("div");
    const grandTotalText = grandTotalSection?.textContent?.replace(/[\s\u00a0]/g, "") ?? "";
    // After removing spaces: en-ZA "1450,00" or en-US "1,450.00" → replace commas → "1.450.00" or "1.450.00"
    // Simpler: strip all separators and check for "145000" (1450.00 without separators)
    expect(grandTotalText.replace(/[,.\s\u00a0]/g, "")).toContain("145000");
  });

  it("renders empty state when no unbilled items", () => {
    render(<UnbilledSummary data={emptyData} />);

    expect(screen.getByText("No unbilled items found.")).toBeInTheDocument();
  });
});
