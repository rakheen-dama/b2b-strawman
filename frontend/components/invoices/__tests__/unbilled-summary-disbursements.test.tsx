import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { UnbilledSummary } from "@/components/invoices/unbilled-summary";
import type { UnbilledTimeResponse } from "@/lib/types";

const baseData: UnbilledTimeResponse = {
  customerId: "c1",
  customerName: "Acme Attorneys",
  projects: [
    {
      projectId: "p1",
      projectName: "Matter 2026/001",
      entries: [
        {
          id: "te1",
          taskTitle: "Drafting",
          memberName: "Alice",
          date: "2026-02-10",
          durationMinutes: 60,
          billingRateSnapshot: 1000,
          billingRateCurrency: "ZAR",
          billableValue: 1000,
          description: null,
          rateSource: "SNAPSHOT",
        },
      ],
      totals: {
        ZAR: { hours: 1, amount: 1000 },
      },
    },
  ],
  grandTotals: {
    ZAR: { hours: 1, amount: 1000 },
  },
  unbilledExpenses: [],
  unbilledExpenseTotals: {},
  disbursements: [
    {
      id: "d1",
      incurredDate: "2026-02-12",
      category: "SHERIFF_FEES",
      description: "Sheriff service of summons",
      amount: 500,
      vatTreatment: "ZERO_RATED_PASS_THROUGH",
      vatAmount: 0,
      supplierName: "Sheriff Sandton",
    },
    {
      id: "d2",
      incurredDate: "2026-02-14",
      category: "COUNSEL_FEES",
      description: "Counsel consultation",
      amount: 2000,
      vatTreatment: "STANDARD_15",
      vatAmount: 300,
      supplierName: "Adv. Jane Doe",
    },
  ],
};

describe("UnbilledSummary — disbursements section", () => {
  afterEach(() => {
    cleanup();
  });

  it("does not render disbursements section when module is disabled", () => {
    render(<UnbilledSummary data={baseData} isDisbursementsModuleEnabled={false} />);

    expect(screen.queryByText("Disbursements")).not.toBeInTheDocument();
    expect(screen.queryByTestId("unbilled-disbursements-section")).not.toBeInTheDocument();
    // Supplier name from disbursement data should not leak through
    expect(screen.queryByText("Sheriff Sandton")).not.toBeInTheDocument();
  });

  it("does not render disbursements section when prop is omitted (default false)", () => {
    render(<UnbilledSummary data={baseData} />);

    expect(screen.queryByText("Disbursements")).not.toBeInTheDocument();
  });

  it("renders disbursements section when module is enabled", () => {
    render(<UnbilledSummary data={baseData} isDisbursementsModuleEnabled={true} />);

    expect(screen.getByText("Disbursements")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-disbursements-section")).toBeInTheDocument();
    expect(screen.getByText("Sheriff service of summons")).toBeInTheDocument();
    expect(screen.getByText("Counsel consultation")).toBeInTheDocument();
    expect(screen.getByText("Sheriff Sandton")).toBeInTheDocument();
    expect(screen.getByText("Adv. Jane Doe")).toBeInTheDocument();
  });

  it("rolls disbursement totals (incl VAT) into the grand total", () => {
    render(<UnbilledSummary data={baseData} isDisbursementsModuleEnabled={true} />);

    // Time 1000 + disbursements (500 + 0) + (2000 + 300) = 3800
    const grandTotalSection = screen.getByText("Grand Total").closest("div");
    const normalized = grandTotalSection?.textContent?.replace(/[,.\s\u00a0]/g, "") ?? "";
    expect(normalized).toContain("380000");
  });

  it("renders disbursement subtotal per currency", () => {
    render(<UnbilledSummary data={baseData} isDisbursementsModuleEnabled={true} />);

    expect(screen.getByText("Disbursement Subtotal")).toBeInTheDocument();
    const subtotalSection = screen.getByText("Disbursement Subtotal").closest("div");
    const normalized = subtotalSection?.textContent?.replace(/[,.\s\u00a0]/g, "") ?? "";
    // 500 + 0 + 2000 + 300 = 2800 → "280000"
    expect(normalized).toContain("280000");
  });

  it("treats missing disbursements field as empty (byte-compatible)", () => {
    const dataNoDisb: UnbilledTimeResponse = {
      ...baseData,
      disbursements: undefined,
    };
    render(<UnbilledSummary data={dataNoDisb} isDisbursementsModuleEnabled={true} />);

    // No disbursements header because list is empty
    expect(screen.queryByText("Disbursements")).not.toBeInTheDocument();
  });
});
