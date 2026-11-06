import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
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

function buildPage(
  content: Array<{
    id: string;
    transactionType: string;
    amount: number;
    runningBalance: number;
    transactionDate: string;
    occurredAt: string;
    description: string;
    reference: string;
  }>,
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
      Array.from({ length: 10 }).map((_, i) => ({
        id: `txn-${i}`,
        transactionType: "DEPOSIT",
        amount: 100 + i,
        runningBalance: 1000 + i,
        transactionDate: "2026-04-15",
        occurredAt: "2026-04-15T08:30:00Z",
        description: `Deposit ${i}`,
        reference: `DEP-${i}`,
      })),
      0,
      2,
    );
    const secondPage = buildPage(
      [
        {
          id: "txn-10",
          transactionType: "WITHDRAWAL",
          amount: -50,
          runningBalance: 950,
          transactionDate: "2026-04-16",
          occurredAt: "2026-04-16T08:30:00Z",
          description: "Withdrawal page 2",
          reference: "WDR-1",
        },
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
      expect(screen.getAllByText("Withdrawal page 2").length).toBeGreaterThan(
        0,
      ),
    );
  });

  it("labels the transaction type in sentence case, never the enum (G88-F31)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        {
          id: "t-fee",
          transactionType: "FEE_TRANSFER",
          amount: -250,
          runningBalance: 750,
          transactionDate: "2026-04-15",
          occurredAt: "2026-04-15T08:30:00Z",
          description: "Transfer to business account",
          reference: "FT-1",
        },
        {
          id: "t-ref",
          transactionType: "REFUND",
          amount: -100,
          runningBalance: 650,
          transactionDate: "2026-04-16",
          occurredAt: "2026-04-16T08:30:00Z",
          description: "Refund to client",
          reference: "RF-1",
        },
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

  it("dates each row by its transaction date, not the day it was recorded (G89-F39)", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        {
          id: "txn-backdated",
          transactionType: "DEPOSIT",
          amount: 100,
          runningBalance: 100,
          // Back-dated deposit: dated 1 Oct, recorded on 7 Nov.
          transactionDate: "2026-10-01",
          occurredAt: "2026-11-07T08:00:00Z",
          description: "Back-dated deposit",
          reference: "DEP-OCT01",
        },
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

  it("synthesises a fallback description when the backend sends an empty one", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        {
          id: "txn-empty",
          transactionType: "INTEREST_CREDIT",
          amount: 12.5,
          runningBalance: 1262.5,
          transactionDate: "2026-04-15",
          occurredAt: "2026-04-15T08:30:00Z",
          description: "",
          reference: "INT-001",
        },
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText(/Interest credited — INT-001/).length,
      ).toBeGreaterThan(0),
    );
  });
});
