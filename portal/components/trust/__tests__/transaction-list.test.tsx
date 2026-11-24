import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockGetMatterTransactions = vi.fn();
vi.mock("@/lib/api/trust", async () => {
  const actual =
    await vi.importActual<typeof import("@/lib/api/trust")>("@/lib/api/trust");
  return {
    ...actual,
    getMatterTransactions: (...args: unknown[]) =>
      mockGetMatterTransactions(...args),
  };
});

import { TransactionList } from "@/components/trust/transaction-list";
import type { PortalTrustTransactionResponse } from "@/lib/api/trust";

function buildPage(
  content: PortalTrustTransactionResponse[],
  pageNumber = 0,
  totalPages = 1,
) {
  return {
    content,
    page: {
      totalElements: content.length + (totalPages - 1) * content.length,
      totalPages,
      size: content.length,
      number: pageNumber,
    },
  };
}

/** A general-ledger (CLIENT) deposit row; each test overrides what it is about. */
function txn(
  overrides: Partial<PortalTrustTransactionResponse> &
    Pick<PortalTrustTransactionResponse, "id">,
): PortalTrustTransactionResponse {
  return {
    transactionType: "DEPOSIT",
    typeLabel: "Deposit",
    ledger: "CLIENT",
    amount: 100,
    runningBalance: 100,
    transactionDate: "2026-04-15",
    occurredAt: "2026-04-15T08:30:00Z",
    description: "Deposit",
    reference: "DEP-1",
    ...overrides,
  };
}

describe("TransactionList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("renders 10 rows on the first page and advances to page 2 on Next click", async () => {
    const firstPage = buildPage(
      Array.from({ length: 10 }).map((_, i) =>
        txn({
          id: `txn-${i}`,
          amount: 100 + i,
          runningBalance: 1000 + i,
          description: `Deposit ${i}`,
          reference: `DEP-${i}`,
        }),
      ),
      0,
      2,
    );
    const secondPage = buildPage(
      [
        txn({
          id: "txn-10",
          transactionType: "PAYMENT",
          typeLabel: "Payment",
          amount: 50,
          runningBalance: 950,
          transactionDate: "2026-04-16",
          occurredAt: "2026-04-16T08:30:00Z",
          description: "Payment page 2",
          reference: "PAY-1",
        }),
      ],
      1,
      2,
    );

    mockGetMatterTransactions
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(secondPage);

    render(<TransactionList matterId="matter-1" pageSize={10} />);

    // Wait for first page to render.
    await waitFor(() =>
      expect(screen.getAllByText("Deposit 0").length).toBeGreaterThan(0),
    );

    // 10 description cells expected (mobile + desktop both render in happy-dom,
    // so use getAllByText and assert the count is at least 10 unique entries).
    for (let i = 0; i < 10; i += 1) {
      expect(screen.getAllByText(`Deposit ${i}`).length).toBeGreaterThan(0);
    }

    // Click Next → page 2 fetched.
    const nextBtn = screen.getByRole("button", { name: /next page/i });
    await userEvent.click(nextBtn);

    await waitFor(() =>
      expect(mockGetMatterTransactions).toHaveBeenCalledWith(
        "matter-1",
        expect.objectContaining({ page: 1, size: 10 }),
      ),
    );

    await waitFor(() =>
      expect(screen.getAllByText("Payment page 2").length).toBeGreaterThan(0),
    );
  });

  it("labels the transaction type in sentence case, never the enum (G88-F31)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "t-fee",
          transactionType: "FEE_TRANSFER",
          typeLabel: "Fee transfer",
          amount: 250,
          runningBalance: 750,
          description: "Transfer to business account",
          reference: "FT-1",
        }),
        txn({
          id: "t-ref",
          transactionType: "REFUND",
          typeLabel: "Refund",
          amount: 100,
          runningBalance: 650,
          transactionDate: "2026-04-16",
          occurredAt: "2026-04-16T08:30:00Z",
          description: "Refund to client",
          reference: "RF-1",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(screen.getAllByText("Fee transfer").length).toBeGreaterThan(0),
    );
    expect(screen.getAllByText("Refund").length).toBeGreaterThan(0);
    expect(screen.queryByText("FEE_TRANSFER")).toBeNull();
    expect(screen.queryByText(/REFUND/)).toBeNull();
  });

  it("labels an allocated unidentified deposit in words, never the code (647D)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "t-alloc",
          transactionType: "UNIDENT_ALLOC_IN",
          typeLabel: "Deposit identified and allocated",
          amount: 300,
          runningBalance: 300,
          transactionDate: "2026-11-10",
          occurredAt: "2026-11-10T08:30:00Z",
          description:
            "Deposit received 3 November 2026 — CREDIT TRANSFER J SMITH",
          reference: "UID-1A2B3C4D",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText("Deposit identified and allocated").length,
      ).toBeGreaterThan(0),
    );
    expect(
      screen.getAllByText(
        "Deposit received 3 November 2026 — CREDIT TRANSFER J SMITH",
      ).length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText(/UNIDENT_ALLOC_IN/)).toBeNull();
    expect(screen.queryByText(/Unident alloc in/i)).toBeNull();
  });

  it("labels a balance brought forward at take-on in words, never the code (657C)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "t-bf",
          transactionType: "TAKEON_CLIENT",
          typeLabel: "Balance brought forward — take-on",
          amount: 300000,
          runningBalance: 300000,
          transactionDate: "2026-10-31",
          occurredAt: "2026-11-24T10:00:00Z",
          // The commit writes the firm label as the description (TrustOpeningPositionService).
          description: "Balance brought forward — take-on",
          reference: "TAKEON-CL-2",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText("Balance brought forward — take-on").length,
      ).toBeGreaterThan(0),
    );
    const table = screen.getByRole("table", { name: "Trust transactions" });
    expect(table.textContent).toContain("31 Oct 2026");
    expect(screen.queryByText(/TAKEON_CLIENT/)).toBeNull();
    expect(screen.queryByText(/Takeon client/i)).toBeNull();
  });

  it("renders the backend's label for section 86(4) rows, never the code (648D, 648A A-1)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "t-interest",
          transactionType: "INV_INTEREST",
          typeLabel: "Section 86(4) investment interest",
          ledger: "CLIENT_INVESTMENT",
          amount: 95,
          runningBalance: 7595,
          transactionDate: "2026-11-08",
          description: "Interest credited by the bank",
          reference: "INT-1",
        }),
        txn({
          id: "t-place",
          transactionType: "INV_PLACE",
          typeLabel: "Placed in section 86(4) investment",
          amount: 7500,
          runningBalance: 2500,
          transactionDate: "2026-11-01",
          description: "Placed with Investec",
          reference: "INV-1",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText("Section 86(4) investment interest").length,
      ).toBeGreaterThan(0),
    );
    expect(
      screen.getAllByText("Placed in section 86(4) investment").length,
    ).toBeGreaterThan(0);
    expect(
      screen.queryByText(/INV_INTEREST|INV_PLACE|CLIENT_INVESTMENT/),
    ).toBeNull();
    expect(screen.queryByText(/Inv interest|Inv place/i)).toBeNull();
  });

  it("qualifies an investment row's running balance as the invested balance (648D OQ-1)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "t-interest",
          transactionType: "INV_INTEREST",
          typeLabel: "Section 86(4) investment interest",
          ledger: "CLIENT_INVESTMENT",
          amount: 95,
          runningBalance: 7595,
          transactionDate: "2026-11-08",
          description: "Interest credited by the bank",
          reference: "INT-1",
        }),
        txn({
          id: "t-deposit",
          amount: 10000,
          runningBalance: 10000,
          transactionDate: "2026-10-01",
          description: "Initial deposit",
          reference: "DEP-1",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(screen.getAllByText("Initial deposit").length).toBeGreaterThan(0),
    );

    // Desktop: the invested row's balance cell names the balance it moved.
    const table = screen.getByRole("table", { name: "Trust transactions" });
    const tableRows = within(table).getAllByRole("row");
    const interestRow = tableRows.find((r) =>
      r.textContent?.includes("Interest credited by the bank"),
    )!;
    const depositRow = tableRows.find((r) =>
      r.textContent?.includes("Initial deposit"),
    )!;
    const interestCells = within(interestRow).getAllByRole("cell");
    const interestBalance = interestCells[interestCells.length - 1];
    expect(interestBalance.textContent).toMatch(/7[ ,\u00A0\u202F]?595[.,]00/);
    expect(interestBalance.textContent).toContain("Invested");
    expect(depositRow.textContent).not.toContain("Invested");

    // Mobile: "Invested bal" for the investment row, "Bal" for the general one.
    const list = screen.getByRole("list", { name: "Trust transactions" });
    const items = within(list).getAllByRole("listitem");
    const interestItem = items.find((i) =>
      i.textContent?.includes("Interest credited by the bank"),
    )!;
    const depositItem = items.find((i) =>
      i.textContent?.includes("Initial deposit"),
    )!;
    expect(interestItem.textContent).toMatch(
      /Invested bal R\s?7[ ,\u00A0\u202F]?595[.,]00/,
    );
    expect(depositItem.textContent).toMatch(/Bal R/);
    expect(depositItem.textContent).not.toContain("Invested");
  });

  it("dates each row by its transaction date, not the day it was recorded (G89-F39)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "txn-backdated",
          // Back-dated deposit: dated 1 Oct, recorded on 7 Nov.
          transactionDate: "2026-10-01",
          occurredAt: "2026-11-07T08:00:00Z",
          description: "Back-dated deposit",
          reference: "DEP-OCT01",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(screen.getAllByText("Back-dated deposit").length).toBeGreaterThan(
        0,
      ),
    );
    const table = screen.getByRole("table", { name: "Trust transactions" });
    expect(table.textContent).toContain("01 Oct 2026");
    expect(table.textContent).not.toContain("Nov 2026");
  });

  it("synthesises a fallback description from the backend's label when the description is empty", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        txn({
          id: "txn-empty",
          transactionType: "INV_INTEREST",
          typeLabel: "Section 86(4) investment interest",
          ledger: "CLIENT_INVESTMENT",
          amount: 12.5,
          runningBalance: 1262.5,
          description: "",
          reference: "INT-001",
        }),
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText(/Section 86\(4\) investment interest — INT-001/)
          .length,
      ).toBeGreaterThan(0),
    );
  });
});
