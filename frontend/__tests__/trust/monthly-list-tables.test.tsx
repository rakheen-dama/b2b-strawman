import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import {
  MonthlyListAfterLockTable,
  MonthlyListCreditorsTable,
} from "@/components/trust/monthly-list-tables";
import { formatCurrency, formatDateTime, formatLocalDate } from "@/lib/format";
import type { CreditorLine, MonthlyListAdjustmentEntry } from "@/lib/types";

// Phase 91 667B: the Rule 54.15 monthly list's two tables on the kit (creditors on `ui/table` with a
// tfoot total — DA-11; the after-lock entries on client-mode DataTable).

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

function line(overrides: Partial<CreditorLine> = {}): CreditorLine {
  return {
    kind: "CLIENT",
    clientId: "client-1",
    name: "Khumalo Holdings",
    matters: [],
    balance: 31000,
    unidentifiedDepositId: null,
    receivedDate: null,
    bankDescription: null,
    totalDeposits: null,
    totalPayments: null,
    totalFeeTransfers: null,
    lastTransactionDate: null,
    broughtForward: null,
    ...overrides,
  };
}

function entry(overrides: Partial<MonthlyListAdjustmentEntry> = {}): MonthlyListAdjustmentEntry {
  return {
    transactionId: "tx-late",
    transactionDate: "2026-10-28",
    postedAt: "2026-11-27T05:08:00Z",
    transactionType: "DEPOSIT",
    typeLabel: "Deposit",
    ledger: "CLIENT" as MonthlyListAdjustmentEntry["ledger"],
    clientId: "client-1",
    clientName: "Khumalo Holdings",
    reference: "AFTER-LOCK",
    description: null,
    cashbookEffect: 500,
    ledgerEffect: 500,
    ...overrides,
  };
}

afterEach(() => cleanup());

describe("MonthlyListCreditorsTable", () => {
  const lines: CreditorLine[] = [
    line({
      broughtForward: 2500,
      matters: [
        { matterId: "m-1", name: "Estate late M Khumalo", balance: 30000 },
        { matterId: null, name: null, balance: 1000 },
      ],
    }),
    line({ kind: "FUND", clientId: null, name: null, balance: 0 }),
    line({
      kind: "UNIDENTIFIED",
      clientId: null,
      name: null,
      balance: 1250,
      bankDescription: "ACB CREDIT UNKNOWN",
      receivedDate: "2026-10-22",
    }),
  ];

  function renderCreditors() {
    return render(
      <MonthlyListCreditorsTable
        lines={lines}
        creditorTotal={32250}
        currency="ZAR"
        caption={`Trust creditors as at ${formatLocalDate("2026-10-31")}`}
      />
    );
  }

  it("keeps the e2e structure: tbody rows of name · kind · balance and a tfoot total", () => {
    renderCreditors();

    const table = screen.getByTestId("monthly-list-creditors");
    expect(table.tagName).toBe("TABLE");
    expect(Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent)).toEqual([
      "Trust creditor",
      "Kind",
      "Balance",
    ]);
    const first = table.querySelector("tbody tr") as HTMLElement;
    expect(first.querySelector("td:nth-child(1)")?.textContent).toBe("Khumalo Holdings");
    expect(first.querySelector("td:nth-child(2)")?.textContent).toBe("Client");
    expect(first.querySelectorAll("td")[2].textContent).toBe(formatCurrency(31000, "ZAR"));

    // trust-balanced-month.spec reads `tfoot td` nth(1) as the creditor total
    const footCells = table.querySelectorAll("tfoot td");
    expect(footCells).toHaveLength(2);
    expect(footCells[0]).toHaveAttribute("colspan", "2");
    expect(footCells[0].textContent).toBe("Total trust creditors");
    expect(footCells[1].textContent).toBe(formatCurrency(32250, "ZAR"));
    expect(table.querySelector("tfoot")?.getAttribute("data-slot")).toBe("table-footer");
    expect(table.querySelector("caption")).toHaveClass("sr-only");
  });

  it("indents the brought-forward and per-matter sub-rows inside tbody", () => {
    renderCreditors();

    const table = screen.getByTestId("monthly-list-creditors");
    const bf = within(table).getByTestId("monthly-list-brought-forward");
    expect(bf.parentElement?.tagName).toBe("TBODY");
    expect(bf.querySelector("td")?.textContent).toBe("B/f at take-on");
    expect(bf.querySelector("td")?.className).toContain("pl-6");
    expect(bf.querySelectorAll("td")[1].textContent).toBe("Take-on");

    const matters = within(table).getAllByTestId("monthly-list-matter");
    expect(matters).toHaveLength(2);
    expect(matters[0].querySelector("td")?.textContent).toBe("Estate late M Khumalo");
    expect(matters[1].querySelector("td")?.textContent).toBe("Not linked to a matter");
    expect(matters[0].querySelectorAll("td")[1].textContent).toBe("Matter");
    expect(matters[0].querySelector("td")?.className).toContain("pl-6");
  });

  it("labels every kind (no raw enum) and names an unidentified deposit by when it was received", () => {
    renderCreditors();

    const table = screen.getByTestId("monthly-list-creditors");
    // The Fund line has no name: the label names it, and is its kind
    expect(
      within(table).getAllByText("Legal Practitioners' Fidelity Fund", { selector: "td" })
    ).toHaveLength(2);
    expect(
      within(table).getByText(`ACB CREDIT UNKNOWN · received ${formatLocalDate("2026-10-22")}`)
    ).toBeInTheDocument();
    expect(table).not.toHaveTextContent("UNIDENTIFIED");
  });
});

describe("MonthlyListAfterLockTable", () => {
  it("renders the after-lock entries on DataTable with row testids and the footer sentence", () => {
    render(
      <MonthlyListAfterLockTable
        entries={[entry(), entry({ transactionId: "tx-anon", clientName: null, reference: null })]}
        currency="ZAR"
        empty={<p data-testid="after-lock-empty">Nothing</p>}
        footer={<p data-testid="after-lock-footer">Trust creditors are now …</p>}
      />
    );

    const table = screen.getByRole("table", { name: "Entered after this list was locked" });
    expect(Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent)).toEqual([
      "Date",
      "Entered",
      "Type",
      "Client",
      "Reference",
      "Cashbook",
    ]);
    const row = screen.getByTestId("after-lock-entry-tx-late");
    const cells = row.querySelectorAll("td");
    expect(cells[0].textContent).toBe(formatLocalDate("2026-10-28"));
    expect(cells[1].textContent).toBe(formatDateTime("2026-11-27T05:08:00Z"));
    expect(cells[2].textContent).toBe("Deposit");
    expect(cells[3].textContent).toBe("Khumalo Holdings");
    expect(cells[4].textContent).toBe("AFTER-LOCK");
    expect(cells[4].className).toContain("[overflow-wrap:anywhere]");
    expect(cells[5].textContent).toBe(formatCurrency(500, "ZAR"));
    expect(cells[5].className).toContain("text-right");

    // A missing client / reference is the kit's "—"
    const anon = screen.getByTestId("after-lock-entry-tx-anon").querySelectorAll("td");
    expect(anon[3].textContent).toBe("—");
    expect(anon[4].textContent).toBe("—");

    expect(screen.getByTestId("after-lock-footer")).toBeInTheDocument();
    expect(screen.queryByTestId("after-lock-empty")).not.toBeInTheDocument();
  });

  it("shows the empty state and no footer when nothing was entered after the lock", () => {
    render(
      <MonthlyListAfterLockTable
        entries={[]}
        currency="ZAR"
        empty={<p data-testid="after-lock-empty">Nothing</p>}
        footer={<p data-testid="after-lock-footer">footer</p>}
      />
    );

    expect(screen.getByTestId("after-lock-empty")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByTestId("after-lock-footer")).not.toBeInTheDocument();
  });
});
