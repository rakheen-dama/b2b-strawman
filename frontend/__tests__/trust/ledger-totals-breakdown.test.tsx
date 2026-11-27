import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { LedgerTotalsBreakdown } from "@/components/trust/ledger-totals-breakdown";
import { formatCurrency } from "@/lib/format";

// PR #1746 review: the "−" sign before a subtracted total is aria-hidden, so a screen reader heard
// "Payments R 1 250,00" as if it added to the balance. Subtracted rows carry an sr-only "less".

afterEach(() => cleanup());

const LEDGER = {
  balance: 3750,
  broughtForward: 0,
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

// 657A (§17.3.15): a client taken on from the previous system starts from its balance brought
// forward; the rows still sum to the balance.
describe("LedgerTotalsBreakdown — brought forward at take-on (657A)", () => {
  const amount = (key: string) =>
    screen.getByTestId(`ledger-total-${key}`).querySelector("dd")!.textContent;
  const sign = (key: string) => term(key).querySelector('[aria-hidden="true"]')!.textContent;

  it("opens with the balance brought forward, then deposits add to it", () => {
    render(
      <LedgerTotalsBreakdown
        ledger={{
          ...LEDGER,
          broughtForward: 300000,
          totalDeposits: 60000,
          totalPayments: 10000,
          balance: 350000,
        }}
      />
    );

    expect(term("brought-forward").textContent).toContain("Brought forward at take-on");
    expect(amount("brought-forward")).toBe(formatCurrency(300000, "ZAR"));
    expect(sign("brought-forward")).toBe("");
    expect(sign("deposits")).toBe("+");
    expect(amount("balance")).toBe(formatCurrency(350000, "ZAR"));
  });

  it("shows no brought-forward row for a client never taken on, and deposits carry no sign", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} />);

    expect(screen.queryByTestId("ledger-total-brought-forward")).toBeNull();
    expect(sign("deposits")).toBe("");
  });
});

// Phase 91 667A: the client ledger page shows the same figures as a row of compact tiles in the
// kit StatCard look. Each tile keeps its testid and is a valid `dl` group — a `div` of exactly one
// `dt` (label) and one `dd` (amount) (F-667A-A1; e2e reads `ledger-total-*` → `dd`).
describe("LedgerTotalsBreakdown — tiles layout (667A)", () => {
  const tile = (key: string) => screen.getByTestId(`ledger-total-${key}`);
  const amount = (key: string) => tile(key).querySelector("dd")!.textContent;
  const label = (key: string) => tile(key).querySelector("dt")!.textContent;

  /** Every child of the `dl` is a `div` holding exactly `dt` + `dd`, in that order. */
  function expectValidGroups(root: HTMLElement) {
    for (const group of Array.from(root.children)) {
      expect(group.tagName).toBe("DIV");
      expect(Array.from(group.children).map((c) => c.tagName)).toEqual(["DT", "DD"]);
    }
  }

  it("renders every visible figure as a compact tile with the list's labels and a dd", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} layout="tiles" />);

    const root = screen.getByTestId("ledger-totals-breakdown");
    expect(root.tagName).toBe("DL");
    expectValidGroups(root);
    const tiles = Array.from(root.children);
    expect(tiles.map((t) => t.getAttribute("data-testid"))).toEqual([
      "ledger-total-deposits",
      "ledger-total-payments",
      "ledger-total-fee-transfers",
      "ledger-total-refunds",
      "ledger-total-balance",
    ]);
    for (const t of tiles) {
      expect(t.querySelectorAll("dt")).toHaveLength(1);
      expect(t.querySelectorAll("dd")).toHaveLength(1);
      // The kit's compact StatCard look (stat-card.tsx compact on Card)
      expect(t).toHaveClass("bg-card", "rounded-lg", "border", "p-3", "shadow-sm");
    }
    expect(label("deposits")).toBe("Deposits");
    expect(label("payments")).toBe("Payments");
    expect(label("fee-transfers")).toBe("Fee transfers");
    expect(label("balance")).toBe("Balance");
    expect(amount("deposits")).toBe(formatCurrency(5000, "ZAR"));
    expect(amount("payments")).toBe(formatCurrency(1250, "ZAR"));
    expect(amount("balance")).toBe(formatCurrency(3750, "ZAR"));
    // The tiles drop the list's +/−/= signs: a dt holds only its label
    expect(tile("payments").querySelector("dt")!.children).toHaveLength(0);
    expect(screen.queryByTestId("ledger-total-invested")).toBeNull();
    expect(screen.queryByTestId("ledger-total-held")).toBeNull();
  });

  it("shows the general balance, the invested figure and the total held while invested", () => {
    render(
      <LedgerTotalsBreakdown
        ledger={{
          ...LEDGER,
          balance: 20000,
          totalDeposits: 50000,
          totalPayments: 0,
          totalInvestmentPlacements: 30000,
          investedBalance: 30000,
        }}
        layout="tiles"
      />
    );

    expect(label("investment-placements")).toBe("Placed in section 86(4) investments");
    expect(amount("investment-placements")).toBe(formatCurrency(30000, "ZAR"));
    expect(label("balance")).toBe("General balance");
    expect(amount("balance")).toBe(formatCurrency(20000, "ZAR"));
    expect(label("invested")).toBe("Invested (section 86(4))");
    expect(amount("invested")).toBe(formatCurrency(30000, "ZAR"));
    expect(label("held")).toBe("Total held in trust");
    expect(amount("held")).toBe(formatCurrency(50000, "ZAR"));
    expectValidGroups(screen.getByTestId("ledger-totals-breakdown"));
    // Each testid exactly once (Playwright strict mode)
    for (const key of ["balance", "invested", "held"]) {
      expect(screen.getAllByTestId(`ledger-total-${key}`)).toHaveLength(1);
    }
  });

  it("keeps the list layout by default (TrustBalanceCard)", () => {
    render(<LedgerTotalsBreakdown ledger={LEDGER} />);

    expect(term("balance")).not.toBeNull();
    expect(screen.getByTestId("ledger-total-balance")).not.toHaveClass("bg-card");
  });
});
