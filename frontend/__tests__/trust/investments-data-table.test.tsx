import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { InvestmentsDataTable } from "@/components/trust/investments-data-table";
import { formatCurrency } from "@/lib/format";
import type { TrustInvestment } from "@/lib/types/trust";

// Phase 91 667B: Trust › Investments' register on DataTable (client mode), K-91 sticky Actions and
// the 86(3) note out of the mono money column, K-36 accent token, DA-6/DA-7 maturity row.

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/investments/actions", () => ({
  fetchInvestments: vi.fn(),
  placeInvestment: vi.fn(),
  recordInvestmentInterest: vi.fn(),
  fetchUnmatchedCreditLines: vi.fn(),
  withdrawInvestment: vi.fn(),
  fetchMaturing: vi.fn(),
  fetchInvestmentInterestEntries: vi.fn(),
  reverseInvestmentInterestEntry: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting/investments",
  useSearchParams: () => new URLSearchParams(),
}));

const TODAY = "2026-11-27";

function investment(overrides: Partial<TrustInvestment> = {}): TrustInvestment {
  return {
    id: "inv-1",
    trustAccountId: "acc-1",
    investmentAccountId: "inv-acc-4",
    investmentAccountName: "Khumalo s86(4) Fixed Deposit",
    clientId: "client-1",
    clientName: "Khumalo Holdings",
    matterId: "matter-1",
    institution: "Standard Bank",
    accountNumber: "INV-4001",
    principal: 20000,
    interestRate: 0.075,
    depositDate: "2026-10-01",
    maturityDate: null,
    grossInterest: 100,
    fundShare: 5,
    netInterest: 95,
    balance: 20095,
    status: "ACTIVE",
    awaitingApproval: false,
    withdrawalDate: null,
    withdrawalAmount: null,
    depositTransactionId: "txn-1",
    withdrawalTransactionId: null,
    notes: null,
    investmentBasis: "CLIENT_INSTRUCTION",
    createdAt: "2026-10-01T09:00:00Z",
    updatedAt: "2026-10-01T09:00:00Z",
    ...overrides,
  };
}

function renderRegister(rows: TrustInvestment[], { canManageTrust = true } = {}) {
  return render(
    <InvestmentsDataTable
      rows={rows}
      currency="ZAR"
      accountId="acc-1"
      canManageTrust={canManageTrust}
      todayIso={TODAY}
      toolbar={{ filters: <div data-testid="basis-filter-slot">filter</div> }}
      empty={<div data-testid="empty-investments">No investments yet.</div>}
    />
  );
}

afterEach(() => cleanup());

describe("InvestmentsDataTable", () => {
  it("keeps the eight columns in order with Actions for MANAGE_TRUST", () => {
    renderRegister([investment()]);

    const table = screen.getByTestId("investments-table");
    expect(within(table).getByText("Investment Register", { selector: "caption" })).toHaveClass(
      "sr-only"
    );
    expect(Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent)).toEqual([
      "Client",
      "Investment account",
      "Rate",
      "Term",
      "Principal",
      "Balance",
      "Status",
      "Actions",
    ]);
    const row = screen.getByTestId("investment-row-inv-1");
    const cells = row.querySelectorAll("td");
    expect(cells).toHaveLength(8);
    // e2e reads td nth(5) as Balance, exactly the amount
    expect(cells[5].textContent).toBe(formatCurrency(20095, "ZAR"));
    expect(within(row).getByRole("button", { name: "Record interest" })).toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "Withdraw" })).toBeInTheDocument();
  });

  it("drops the Actions column without MANAGE_TRUST", () => {
    renderRegister([investment()], { canManageTrust: false });

    const table = screen.getByTestId("investments-table");
    expect(table.querySelectorAll("thead th")).toHaveLength(7);
    expect(screen.getByTestId("investment-row-inv-1").querySelectorAll("td")).toHaveLength(7);
    expect(screen.queryByRole("button", { name: "Withdraw" })).not.toBeInTheDocument();
  });

  it("offers no actions on an investment awaiting approval or no longer held", () => {
    renderRegister([
      investment({ id: "inv-pending", awaitingApproval: true }),
      investment({ id: "inv-gone", status: "WITHDRAWN", balance: 0 }),
    ]);

    for (const id of ["inv-pending", "inv-gone"]) {
      const row = screen.getByTestId(`investment-row-${id}`);
      expect(within(row).queryByRole("button")).not.toBeInTheDocument();
    }
  });

  it("K-91: the Actions column sticks right on an opaque card background", () => {
    renderRegister([investment()]);

    const table = screen.getByTestId("investments-table");
    const actionsHead = table.querySelectorAll("thead th")[7];
    const actionsCell = screen.getByTestId("investment-row-inv-1").querySelectorAll("td")[7];
    for (const el of [actionsHead, actionsCell]) {
      expect(el.className).toContain("sticky");
      expect(el.className).toContain("right-0");
      expect(el.className).toContain("bg-card");
      expect(el.className).toContain("pr-0");
    }
    // No other column is sticky
    for (const th of Array.from(table.querySelectorAll("thead th")).slice(0, 7)) {
      expect(th.className).not.toContain("sticky");
    }
  });

  it("K-91: a section 86(3) note is a sans sub-line; the principal alone is mono", () => {
    renderRegister([
      investment({
        id: "inv-s863",
        clientId: null,
        clientName: null,
        matterId: null,
        investmentBasis: "FIRM_DISCRETION",
      }),
    ]);

    const row = screen.getByTestId("investment-row-inv-s863");
    expect(within(row).getByText("Firm (section 86(3))")).toBeInTheDocument();
    const note = within(row).getByTestId("investment-interest-fund-note");
    expect(note).toHaveTextContent(
      "Interest is the Fund's — record it from the investment account's statement"
    );
    expect(note.className).toContain("font-sans");
    const principalCell = row.querySelectorAll("td")[4];
    expect(principalCell.className).not.toContain("font-mono");
    expect(principalCell.className).toContain("text-right");
    const principal = principalCell.querySelector("span");
    expect(principal?.textContent).toBe(formatCurrency(20000, "ZAR"));
    expect(principal?.className).toContain("font-mono");
    expect(row.textContent).not.toMatch(/Gross interest/);
  });

  it("K-36: the statutory LPFF share reads in the accent token", () => {
    renderRegister([investment()]);

    const statutory = within(screen.getByTestId("investment-row-inv-1")).getByText(
      "5% (statutory)"
    );
    expect(statutory.className).toContain("text-(--button-accent)");
    expect(statutory.className).toContain("dark:text-teal-400");
    expect(statutory.className).not.toContain("text-teal-600");
  });

  it("every header and cell may wrap (no kit whitespace-nowrap)", () => {
    renderRegister([investment()]);

    const table = screen.getByTestId("investments-table");
    for (const cell of Array.from(table.querySelectorAll("th, td"))) {
      expect(cell.className).not.toContain("whitespace-nowrap");
    }
  });

  it("flags an ACTIVE investment maturing within 30 days of the server's today as maturity-alert", () => {
    renderRegister([
      investment({ id: "inv-soon", clientName: "Soon Client", maturityDate: "2026-12-12" }),
      investment({ id: "inv-today", clientName: "Today Client", maturityDate: TODAY }),
      investment({ id: "inv-later", clientName: "Later Client", maturityDate: "2027-02-25" }),
      investment({
        id: "inv-matured",
        clientName: "Matured Client",
        maturityDate: "2026-12-01",
        status: "MATURED",
      }),
      investment({ id: "inv-past", clientName: "Past Client", maturityDate: "2026-11-26" }),
    ]);

    const alerts = screen.getAllByTestId("maturity-alert");
    expect(alerts).toHaveLength(2);
    expect(alerts.map((row) => row.querySelector("td")?.textContent)).toEqual([
      expect.stringContaining("Soon Client"),
      expect.stringContaining("Today Client"),
    ]);
    for (const id of ["inv-later", "inv-matured", "inv-past"]) {
      expect(screen.getByTestId(`investment-row-${id}`)).toBeInTheDocument();
    }
    // The register wrapper tints the row, and the sticky cell of that row, amber
    const register = alerts[0].closest("[data-slot=investments-register]");
    expect(register?.className).toContain("[&_tr[data-testid=maturity-alert]]:bg-amber-50");
    expect(register?.className).toContain(
      "[&_tr[data-testid=maturity-alert]>td:last-child]:bg-amber-50"
    );
  });

  it("renders the toolbar above the table and above the empty state", () => {
    const { unmount } = renderRegister([investment()]);
    expect(screen.getByTestId("basis-filter-slot")).toBeInTheDocument();
    unmount();

    renderRegister([]);
    expect(screen.getByTestId("basis-filter-slot")).toBeInTheDocument();
    expect(screen.getByTestId("empty-investments")).toBeInTheDocument();
    expect(screen.queryByTestId("investments-table")).not.toBeInTheDocument();
  });
});
