import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { ClientLedgersDataTable } from "@/components/trust/client-ledgers-data-table";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { ClientLedgerCard } from "@/lib/types";

// Phase 91 667A: Trust › Client Ledgers on DataTable (server-paged), K-89 s86(4) columns, K-35 link.

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting/client-ledgers",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => {
    const anchorProps = { ...props };
    delete anchorProps.scroll;
    delete anchorProps.prefetch;
    return (
      <a href={href} {...anchorProps}>
        {children}
      </a>
    );
  },
}));

function ledger(overrides: Partial<ClientLedgerCard> = {}): ClientLedgerCard {
  return {
    id: "ledger-1",
    trustAccountId: "acc-1",
    clientId: "cust-1",
    clientName: "Acme Corp",
    balance: 50000,
    totalDeposits: 100000,
    totalPayments: 40000,
    totalFeeTransfers: 10000,
    totalRefunds: 0,
    totalTransfersIn: 0,
    totalTransfersOut: 0,
    totalInvestmentPlacements: 0,
    totalInvestmentReturns: 0,
    broughtForward: 0,
    investedBalance: 0,
    lastTransactionDate: "2026-04-01",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2026-04-01T00:00:00Z",
    ...overrides,
  };
}

const BASE = "/org/acme/trust-accounting/client-ledgers";

function renderTable(
  rows: ClientLedgerCard[],
  { pageIndex = 0, pageCount = 1, query = {} as Record<string, string> } = {}
) {
  return render(
    <ClientLedgersDataTable
      slug="acme"
      rows={rows}
      currency="ZAR"
      pageIndex={pageIndex}
      pageCount={pageCount}
      basePath={BASE}
      query={query}
      toolbar={{ filters: <div data-testid="ledger-filters">filters</div> }}
      empty={<div data-testid="empty-state">No client ledgers match the current filters</div>}
    />
  );
}

const headers = () =>
  within(screen.getByTestId("client-ledgers-table"))
    .getAllByRole("columnheader")
    .map((th) => th.textContent);

const BASE_HEADERS = [
  "Client Name",
  "Trust Balance",
  "Total Deposits",
  "Total Payments",
  "Total Fee Transfers",
  "Total Refunds",
  "Last Transaction",
];

describe("ClientLedgersDataTable (667A)", () => {
  afterEach(() => {
    cleanup();
  });

  it("keeps today's seven columns when no client has an investment", () => {
    renderTable([ledger()]);

    expect(headers()).toEqual(BASE_HEADERS);
    const row = screen.getByTestId("ledger-row-cust-1");
    const cells = within(row).getAllByRole("cell");
    expect(cells.map((c) => c.textContent)).toEqual([
      "Acme Corp",
      formatCurrency(50000, "ZAR"),
      formatCurrency(100000, "ZAR"),
      formatCurrency(40000, "ZAR"),
      formatCurrency(10000, "ZAR"),
      formatCurrency(0, "ZAR"),
      formatLocalDate("2026-04-01"),
    ]);
    expect(screen.getByTestId("ledger-filters")).toBeInTheDocument();
  });

  it("links the client name to its ledger with the accent token (K-35)", () => {
    renderTable([ledger()]);

    const link = within(screen.getByTestId("ledger-row-cust-1")).getByRole("link", {
      name: "Acme Corp",
    });
    expect(link).toHaveAttribute("href", `${BASE}/cust-1`);
    expect(link).toHaveClass("text-(--button-accent)");
    expect(link).not.toHaveClass("text-teal-600");
  });

  it("says 'No transactions' for a ledger without a last transaction", () => {
    renderTable([ledger({ lastTransactionDate: null })]);

    const cells = within(screen.getByTestId("ledger-row-cust-1")).getAllByRole("cell");
    expect(cells[cells.length - 1].textContent).toBe("No transactions");
  });

  it("adds the s86(4) columns and reads 'General balance' while a client has an investment (K-89)", () => {
    renderTable([
      ledger({
        clientId: "cust-qc",
        clientName: "QuickCollect",
        balance: 15000,
        totalDeposits: 45000,
        totalPayments: 0,
        totalFeeTransfers: 0,
        totalInvestmentPlacements: 30000,
        investedBalance: 30095,
      }),
      ledger(),
    ]);

    expect(headers()).toEqual([
      "Client Name",
      "General balance",
      "Total Deposits",
      "Total Payments",
      "Total Fee Transfers",
      "Total Refunds",
      "Placed in s86(4)",
      "Invested (s86(4))",
      "Last Transaction",
    ]);
    const cells = within(screen.getByTestId("ledger-row-cust-qc")).getAllByRole("cell");
    expect(cells[6].textContent).toBe(formatCurrency(30000, "ZAR"));
    expect(cells[7].textContent).toBe(formatCurrency(30095, "ZAR"));
    const other = within(screen.getByTestId("ledger-row-cust-1")).getAllByRole("cell");
    expect(other[6].textContent).toBe(formatCurrency(0, "ZAR"));
  });

  it("shows only the placed column after the money came back (placed ≠ 0, invested = 0)", () => {
    renderTable([ledger({ totalInvestmentPlacements: 30000, totalInvestmentReturns: 30000 })]);

    expect(headers()).toEqual([
      "Client Name",
      "Trust Balance",
      "Total Deposits",
      "Total Payments",
      "Total Fee Transfers",
      "Total Refunds",
      "Placed in s86(4)",
      "Last Transaction",
    ]);
  });

  it("pages by links that keep every param and set page", () => {
    renderTable([ledger()], {
      pageIndex: 1,
      pageCount: 3,
      query: { nonZeroOnly: "true", search: "acme", page: "1" },
    });

    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${BASE}?nonZeroOnly=true&search=acme&page=0`
    );
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute(
      "href",
      `${BASE}?nonZeroOnly=true&search=acme&page=2`
    );
  });

  it("renders the empty slot instead of the table", () => {
    renderTable([]);

    expect(screen.queryByTestId("client-ledgers-table")).not.toBeInTheDocument();
    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
  });
});
