import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LedgerTotalsBreakdown } from "@/components/trust/ledger-totals-breakdown";
import { formatCurrency } from "@/lib/format";

// PR #1746 review: the "−" sign before a subtracted total is aria-hidden, so a screen reader heard
// "Payments R 1 250,00" as if it added to the balance. Subtracted rows carry an sr-only "less".

afterEach(() => cleanup());

const LEDGER = {
  balance: 3750,
  totalDeposits: 5000,
  totalTransfersIn: 0,
  totalPayments: 1250,
  totalFeeTransfers: 0,
  totalRefunds: 0,
  totalTransfersOut: 0,
  totalInvestmentPlacements: 0,
  totalInvestmentReturns: 0,
  investedBalance: 0,
};

const term = (key: string) => screen.getByTestId(`ledger-total-${key}`).querySelector("dt")!;

describe("LedgerTotalsBreakdown — screen-reader signs", () => {
  it("reads 'less' before each subtracted row", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} />);

    for (const key of ["payments", "fee-transfers", "refunds"]) {
      const srOnly = term(key).querySelector(".sr-only");
      expect(srOnly?.textContent, key).toBe("less ");
    }
  });

  it("adds no 'less' to the deposits or balance rows", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} />);

    for (const key of ["deposits", "balance"]) {
      expect(term(key).textContent, key).not.toContain("less");
    }
    // Phase 92 (646A): interest credits are no longer posted, so there is no interest row
    expect(screen.queryByTestId("ledger-total-interest")).toBeNull();
  });
});

// 648E (ADR-379 L7): a section 86(4) placement moves money from the general balance to the
// client's investment; the client's total held in trust does not move.
describe("LedgerTotalsBreakdown — section 86(4) invested balance", () => {
  const amount = (key: string) =>
    screen.getByTestId(`ledger-total-${key}`).querySelector("dd")!.textContent;

  it("shows Invested (section 86(4)) and the total held beside the general balance", () => {
    render(
      <LedgerTotalsBreakdown
        ledger={{
          ...LEDGER,
          balance: 20000,
          totalDeposits: 50000,
          totalPayments: 0,
          totalInvestmentPlacements: 30000,
          totalInvestmentReturns: 0,
          investedBalance: 30000,
        }}
      />
    );

    expect(amount("investment-placements")).toBe(formatCurrency(30000, "ZAR"));
    expect(term("investment-placements").querySelector(".sr-only")?.textContent).toBe("less ");
    expect(term("investment-placements").textContent).toContain(
      "Placed in section 86(4) investments"
    );
    expect(term("balance").textContent).toContain("General balance");
    expect(amount("balance")).toBe(formatCurrency(20000, "ZAR"));
    expect(term("invested").textContent).toContain("Invested (section 86(4))");
    expect(amount("invested")).toBe(formatCurrency(30000, "ZAR"));
    expect(term("held").textContent).toContain("Total held in trust");
    expect(amount("held")).toBe(formatCurrency(50000, "ZAR"));
    expect(screen.queryByTestId("ledger-total-investment-returns")).toBeNull();
    expect(screen.queryByTestId("ledger-total-transfers-out")).toBeNull();
  });

  it("hides every investment row while nothing was ever invested", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} />);

    for (const key of ["investment-placements", "investment-returns", "invested", "held"]) {
      expect(screen.queryByTestId(`ledger-total-${key}`), key).toBeNull();
    }
    expect(term("balance").textContent).toContain("Balance");
    expect(term("balance").textContent).not.toContain("General");
  });

  it("after the withdrawal shows placed and returned, and no invested figure", () => {
    render(
      <LedgerTotalsBreakdown
        ledger={{
          ...LEDGER,
          balance: 50000,
          totalDeposits: 50000,
          totalPayments: 0,
          totalInvestmentPlacements: 30000,
          totalInvestmentReturns: 30000,
          investedBalance: 0,
        }}
      />
    );

    expect(amount("investment-placements")).toBe(formatCurrency(30000, "ZAR"));
    expect(amount("investment-returns")).toBe(formatCurrency(30000, "ZAR"));
    expect(term("investment-returns").textContent).toContain(
      "Returned from section 86(4) investments"
    );
    expect(term("investment-returns").textContent).not.toContain("less");
    expect(screen.queryByTestId("ledger-total-invested")).toBeNull();
    expect(screen.queryByTestId("ledger-total-held")).toBeNull();
    expect(amount("balance")).toBe(formatCurrency(50000, "ZAR"));
  });
});
