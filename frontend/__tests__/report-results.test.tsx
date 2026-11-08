import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportResults } from "@/components/reports/report-results";
import type { ReportExecutionResponse } from "@/lib/api/reports";
import { formatCurrency } from "@/lib/format";

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
