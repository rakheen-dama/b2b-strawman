import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LedgerTotalsBreakdown } from "@/components/trust/ledger-totals-breakdown";

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
