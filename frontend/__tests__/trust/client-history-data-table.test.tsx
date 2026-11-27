import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { ClientHistoryDataTable } from "@/components/trust/client-history-data-table";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { TrustTransaction } from "@/lib/types";

// Phase 91 667A: a client's ledger history on DataTable (server-paged), server running balance.

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting/client-ledgers/cust-1",
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

function tx(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "DEPOSIT",
    typeLabel: "Deposit",
    direction: "IN",
    ledger: "CLIENT",
    amount: 10000,
    clientId: "cust-1",
    matterId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "DEP-001",
    description: null,
    transactionDate: "2026-10-01",
    status: "RECORDED",
    approvedBy: null,
    approvedAt: null,
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    reversalPending: false,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-10-01T09:00:00Z",
    creditorAccountId: null,
    investmentId: null,
    postedAt: "2026-10-01T09:00:00Z",
    runningBalance: 10000,
    ...overrides,
  } as TrustTransaction;
}

const BASE = "/org/acme/trust-accounting/client-ledgers/cust-1";

function renderTable(
  rows: TrustTransaction[],
  { pageIndex = 0, pageCount = 1, query = {} as Record<string, string> } = {}
) {
  return render(
    <ClientHistoryDataTable
      rows={rows}
      currency="ZAR"
      pageIndex={pageIndex}
      pageCount={pageCount}
      basePath={BASE}
      query={query}
      toolbar={{ filters: <div data-testid="date-filters">filters</div> }}
      empty={<p>No transactions match the current filters</p>}
    />
  );
}

describe("ClientHistoryDataTable (667A)", () => {
  afterEach(() => {
    cleanup();
  });

  it("keeps the column order, the table testid and a per-row testid", () => {
    renderTable([tx()]);

    const table = screen.getByTestId("history-table");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((th) => th.textContent)
    ).toEqual(["Date", "Reference", "Type", "Amount", "Status", "Running Balance"]);
    const cells = within(screen.getByTestId("history-row-tx-1")).getAllByRole("cell");
    expect(cells.map((c) => c.textContent)).toEqual([
      formatLocalDate("2026-10-01"),
      "DEP-001",
      "Deposit",
      formatCurrency(10000, "ZAR"),
      "RECORDED",
      formatCurrency(10000, "ZAR"),
    ]);
    expect(screen.getByTestId("date-filters")).toBeInTheDocument();
  });

  it("renders the server running balance as-is and '—' for a null (unposted) row (ADR-361)", () => {
    renderTable([
      tx({ id: "tx-rej", status: "REJECTED", runningBalance: null }),
      tx({ id: "tx-dep", runningBalance: 7850 }),
    ]);

    const last = (id: string) => {
      const cells = within(screen.getByTestId(`history-row-${id}`)).getAllByRole("cell");
      return cells[cells.length - 1].textContent;
    };
    expect(last("tx-rej")).toBe("—");
    expect(last("tx-dep")).toBe(formatCurrency(7850, "ZAR"));
  });

  it("wraps a long reference over a floor and the type badge (K-10, K-90)", () => {
    const reference = `667A-K10 spaced ${"word ".repeat(27)}`.trim();
    renderTable([tx({ id: "tx-long", reference, typeLabel: "Unidentified deposit allocated" })]);

    const cells = within(screen.getByTestId("history-row-tx-long")).getAllByRole("cell");
    expect(cells[1].textContent).toBe(reference);
    expect(cells[1]).toHaveClass("whitespace-normal", "[overflow-wrap:anywhere]");
    expect(cells[1].style.minWidth).toBe("6rem");
    expect(cells[2]).toHaveClass("whitespace-normal");
    expect(cells[2].querySelector("[data-slot='badge']")).toHaveClass("rounded-md");
  });

  it("styles inflows with the success token (K-40)", () => {
    renderTable([
      tx({ id: "tx-in" }),
      tx({ id: "tx-out", direction: "OUT", transactionType: "PAYMENT", typeLabel: "Payment" }),
    ]);

    const amount = (id: string) =>
      within(screen.getByTestId(`history-row-${id}`))
        .getAllByRole("cell")[3]
        .querySelector("span") as HTMLElement;
    expect(amount("tx-in")).toHaveClass("text-success-foreground");
    expect(amount("tx-in")).toHaveAttribute("data-direction", "in");
    expect(amount("tx-out")).not.toHaveClass("text-success-foreground");
    expect(amount("tx-out")).toHaveAttribute("data-direction", "out");
  });

  it("pages by links that keep every param and set page", () => {
    renderTable([tx()], {
      pageIndex: 1,
      pageCount: 2,
      query: { status: "RECORDED", dateFrom: "2026-01-01", page: "1" },
    });

    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${BASE}?status=RECORDED&dateFrom=2026-01-01&page=0`
    );
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("renders the empty slot instead of the table", () => {
    renderTable([]);

    expect(screen.queryByTestId("history-table")).not.toBeInTheDocument();
    expect(screen.getByText("No transactions match the current filters")).toBeInTheDocument();
  });
});
