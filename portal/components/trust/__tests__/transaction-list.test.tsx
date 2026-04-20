import {
  describe,
  it,
  expect,
  vi,
  beforeEach,
  afterEach,
} from "vitest";
import {
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockGetMatterTransactions = vi.fn();
vi.mock("@/lib/api/trust", async () => {
  const actual =
    await vi.importActual<typeof import("@/lib/api/trust")>(
      "@/lib/api/trust",
    );
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

  it("synthesises a fallback description when the backend sends an empty one", async () => {
    mockGetMatterTransactions.mockResolvedValueOnce(
      buildPage([
        {
          id: "txn-empty",
          transactionType: "INTEREST_POSTED",
          amount: 12.5,
          runningBalance: 1262.5,
          occurredAt: "2026-04-15T08:30:00Z",
          description: "",
          reference: "INT-001",
        },
      ]),
    );

    render(<TransactionList matterId="matter-1" />);

    await waitFor(() =>
      expect(
        screen.getAllByText(/Interest Posted — INT-001/).length,
      ).toBeGreaterThan(0),
    );
  });
});
