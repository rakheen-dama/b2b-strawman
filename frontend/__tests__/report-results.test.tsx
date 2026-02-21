import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportResults } from "@/components/reports/report-results";
import type { ReportExecutionResponse } from "@/lib/api/reports";

vi.mock("server-only", () => ({}));

function makeResponse(
  overrides: Partial<ReportExecutionResponse> = {},
): ReportExecutionResponse {
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
      <ReportResults response={null} isLoading={false} onPageChange={vi.fn()} />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders summary stat cards for each summary key", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    // "Total Hours" appears as both summary label and column header
    const totalHoursElements = screen.getAllByText("Total Hours");
    expect(totalHoursElements.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("98.25")).toBeInTheDocument();
    expect(screen.getByText("Average Rate")).toBeInTheDocument();
    expect(screen.getByText("55")).toBeInTheDocument();
  });

  it("renders column headers matching response.columns", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Member")).toBeInTheDocument();
    // "Total Hours" appears in both summary and headers
    const totalHoursElements = screen.getAllByText("Total Hours");
    expect(totalHoursElements.length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Billable Amount")).toBeInTheDocument();
  });

  it("renders correct number of data rows", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("Charlie")).toBeInTheDocument();
  });

  it("formats decimal values to 2 decimal places", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    expect(screen.getByText("40.50")).toBeInTheDocument();
    expect(screen.getByText("4050.00")).toBeInTheDocument();
  });

  it("renders null values as em-dash", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    // Bob's billableAmount is null
    const emDashes = screen.getAllByText("\u2014");
    expect(emDashes.length).toBeGreaterThan(0);
  });

  it("shows pagination as 'Page 1 of 2' for totalPages=2", () => {
    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
  });

  it("disables Previous on page 1; enables Next when totalPages > 1", async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();

    render(
      <ReportResults
        response={makeResponse()}
        isLoading={false}
        onPageChange={onPageChange}
      />,
    );

    const prevBtn = screen.getByRole("button", { name: /previous/i });
    const nextBtn = screen.getByRole("button", { name: /next/i });

    expect(prevBtn).toBeDisabled();
    expect(nextBtn).not.toBeDisabled();

    await user.click(nextBtn);
    expect(onPageChange).toHaveBeenCalledWith(1);
  });
});
