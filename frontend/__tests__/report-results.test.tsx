import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportResults } from "@/components/reports/report-results";
import type { ReportExecutionResponse } from "@/lib/api/reports";
import { formatCurrency, formatLocalDate } from "@/lib/format";

vi.mock("server-only", () => ({}));

/** en-ZA currency text as Testing Library sees it (its normaliser folds NBSP grouping to spaces). */
function money(amount: number): string {
  return formatCurrency(amount).replace(/\s/g, " ");
}

function makeResponse(overrides: Partial<ReportExecutionResponse> = {}): ReportExecutionResponse {
  return {
    reportName: "Test Report",
    parameters: { dateFrom: "2026-01-01" },
    generatedAt: "2026-01-15T10:00:00Z",
    columns: [
      { key: "memberName", label: "Member", type: "string" },
      { key: "totalHours", label: "Total Hours", type: "decimal" },
      { key: "billableAmount", label: "Billable Amount", type: "currency" },
    ],
    rows: [
      { memberName: "Alice", totalHours: 40.5, billableAmount: 4050.0 },
      { memberName: "Bob", totalHours: 35.0, billableAmount: null },
      { memberName: "Charlie", totalHours: 22.75, billableAmount: 2275.5 },
    ],
    summary: {
      totalHours: 98.25,
      averageRate: 55.0,
    },
    pagination: {
      page: 0,
      size: 25,
      totalElements: 3,
      totalPages: 2,
    },
    ...overrides,
  };
}

describe("ReportResults", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  // 657D (§17.3.15): Receipts & Payments over a cutover opens with the take-on brought-forward line
  it("marks the take-on brought-forward row and formats summary.broughtForward as money", () => {
    render(
      <ReportResults
        response={makeResponse({
          columns: [
            { key: "date", label: "Date", type: "string" },
            { key: "type", label: "Type", type: "string" },
            { key: "credit", label: "Receipts", type: "currency" },
          ],
          rows: [
            {
              date: "2026-10-31",
              type: "Balance brought forward — take-on",
              credit: 403500,
              broughtForward: true,
            },
            { date: "2026-11-10", type: "Deposit", credit: 60000 },
          ],
          summary: { totalReceipts: 60000, broughtForward: 403500 },
        })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );
    const rows = screen.getAllByTestId("report-row-brought-forward");
    expect(rows).toHaveLength(1);
    expect(rows[0]).toHaveTextContent("Balance brought forward — take-on");
    expect(rows[0].className).toContain("font-medium");
    expect(screen.getByText("Brought Forward")).toBeInTheDocument();
    expect(screen.getAllByText(money(403500)).length).toBeGreaterThanOrEqual(2);
  });

  it("prints summary.broughtForwardNote as a footnote under the table, not a stat card", () => {
    const note =
      "The opening position taken on at 31 October 2026 is shown as one brought-forward line, not as receipts.";
    render(
      <ReportResults
        response={makeResponse({ summary: { totalHours: 98.25, broughtForwardNote: note } })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );
    expect(screen.getByTestId("report-footnote-brought-forward")).toHaveTextContent(note);
    expect(screen.getAllByText(note)).toHaveLength(1);
    expect(screen.queryByText("Brought Forward Note")).not.toBeInTheDocument();
  });

  it("renders nothing when response is null", () => {
    const { container } = render(
      <ReportResults response={null} isLoading={false} onPageChange={vi.fn()} />
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders summary stat cards for each summary key", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    // "Total Hours" appears as both summary label and column header
    const totalHoursElements = screen.getAllByText("Total Hours");
    expect(totalHoursElements.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("98.25")).toBeInTheDocument();
    expect(screen.getByText("Average Rate")).toBeInTheDocument();
    expect(screen.getByText("55")).toBeInTheDocument();
  });

  it("renders column headers matching response.columns", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    expect(screen.getByText("Member")).toBeInTheDocument();
    // "Total Hours" appears in both summary and headers
    const totalHoursElements = screen.getAllByText("Total Hours");
    expect(totalHoursElements.length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Billable Amount")).toBeInTheDocument();
  });

  it("renders correct number of data rows", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("Charlie")).toBeInTheDocument();
  });

  it("formats decimal values to 2 decimal places and currency values as currency", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    expect(screen.getByText("40.50")).toBeInTheDocument();
    // G89-F18: currency columns render through formatCurrency (en-ZA), not toFixed(2).
    expect(screen.getByText(money(4050))).toBeInTheDocument();
    expect(screen.queryByText("4050.00")).not.toBeInTheDocument();
  });

  it("renders null values as em-dash", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    // Bob's billableAmount is null
    const emDashes = screen.getAllByText("\u2014");
    expect(emDashes.length).toBeGreaterThan(0);
  });

  // G89-F18: the Section 35 data pack summary holds `sections` (a list of maps) and a raw
  // BigDecimal year-end total; its rows carry money in a currency `amount` column.
  it("skips object and array summary values instead of printing [object Object]", () => {
    const { container } = render(
      <ReportResults
        response={makeResponse({
          summary: {
            sectionCount: 6,
            sections: [{ sectionName: "Client Trust Balances", rowCount: 1 }],
            reconciliation: { status: "BALANCED" },
          },
        })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );

    expect(container.textContent).not.toContain("[object Object]");
    expect(screen.queryByText("Sections")).not.toBeInTheDocument();
    expect(screen.queryByText("Reconciliation")).not.toBeInTheDocument();
    expect(screen.getByText("Section Count")).toBeInTheDocument();
    expect(screen.getByText("6")).toBeInTheDocument();
  });

  it("formats money summary values and currency cells as currency", () => {
    render(
      <ReportResults
        response={makeResponse({
          columns: [
            { key: "_section", label: "Section", type: "string" },
            { key: "label", label: "Item", type: "string" },
            { key: "amount", label: "Amount", type: "currency" },
          ],
          rows: [{ _section: "Client Trust Balances", label: "Dlamini", amount: 38019.15 }],
          summary: { totalTrustBalanceAtYearEnd: 38019.15, totalHours: 12.5 },
        })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );

    const yearEnd = money(38019.15);
    // once on the summary card, once in the amount cell
    expect(screen.getAllByText(yearEnd)).toHaveLength(2);
    expect(screen.queryByText("38019.15")).not.toBeInTheDocument();
    // hours are not money
    expect(screen.getByText("12.5")).toBeInTheDocument();
    // G89-F47: text cells wrap within a capped width; the amount cell stays right-aligned
    const item = screen.getByText("Dlamini").closest("td")!;
    expect(item.className).toContain("whitespace-normal");
    expect(item.className).toContain("wrap-anywhere");
    expect(screen.getAllByText(yearEnd)[1].closest("td")!.className).not.toContain("wrap-anywhere");
  });

  // 650C (A-M2): the trust position and reconciliation summaries name their money by ledger, and
  // carry booleans — money prints as currency, booleans as words, counts stay counts.
  it("formats trust position money keys as currency and booleans as Yes / No", () => {
    render(
      <ReportResults
        response={makeResponse({
          columns: [{ key: "accountName", label: "Trust Account", type: "string" }],
          rows: [{ accountName: "Main trust" }],
          summary: {
            cashbook: 1111.11,
            clientLedgers: 1222.22,
            investmentCreditors: 1333.33,
            fundLedger: -1444.44,
            unidentifiedDeposits: 1555.55,
            interAccount: 1666.66,
            otherNonClient: 1777.77,
            difference: 1888.88,
            investmentAccountTotal: 1999.99,
            balanced: true,
            isBalanced: false,
            accountCount: 3,
            openItems: 4,
          },
        })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );

    for (const amount of [
      1111.11, 1222.22, 1333.33, -1444.44, 1555.55, 1666.66, 1777.77, 1888.88, 1999.99,
    ]) {
      expect(screen.getByText(money(amount))).toBeInTheDocument();
    }
    expect(screen.queryByText("1111.11")).not.toBeInTheDocument();
    expect(screen.getByText("Yes")).toBeInTheDocument();
    expect(screen.getByText("No")).toBeInTheDocument();
    expect(screen.queryByText("true")).not.toBeInTheDocument();
    expect(screen.queryByText("false")).not.toBeInTheDocument();
    // counts are not money
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
  });

  // F-650C-OBS-4 / OBS-5: summary dates read as dates, the after-lock net as money
  it("formats ISO date summary values as dates and the after-lock net as currency", () => {
    render(
      <ReportResults
        response={makeResponse({
          summary: {
            periodFrom: "2026-08-01",
            asOfDate: "2026-09-30",
            afterLockNet: 0,
            source: "Locked 2026-11-19 by Alice",
          },
        })}
        isLoading={false}
        onPageChange={vi.fn()}
      />
    );

    expect(screen.getByText(formatLocalDate("2026-08-01"))).toBeInTheDocument();
    expect(screen.getByText(formatLocalDate("2026-09-30"))).toBeInTheDocument();
    expect(screen.queryByText("2026-08-01")).not.toBeInTheDocument();
    expect(screen.queryByText("2026-09-30")).not.toBeInTheDocument();
    expect(screen.getByText(money(0))).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
    // only a whole value that is an ISO date is reformatted
    expect(screen.getByText("Locked 2026-11-19 by Alice")).toBeInTheDocument();
  });

  it("shows pagination as 'Page 1 of 2' for totalPages=2", () => {
    render(<ReportResults response={makeResponse()} isLoading={false} onPageChange={vi.fn()} />);

    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
  });

  it("disables Previous on page 1; enables Next when totalPages > 1", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();

    render(
      <ReportResults response={makeResponse()} isLoading={false} onPageChange={onPageChange} />
    );

    const prevBtn = screen.getByRole("button", { name: /previous/i });
    const nextBtn = screen.getByRole("button", { name: /next/i });

    expect(prevBtn).toBeDisabled();
    expect(nextBtn).not.toBeDisabled();

    await user.click(nextBtn);
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});
