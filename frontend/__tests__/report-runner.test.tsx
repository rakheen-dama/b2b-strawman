import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReportRunner } from "@/components/reports/report-runner";
import type {
  ReportDefinitionDetail,
  ReportExecutionResponse,
} from "@/lib/api/reports";

vi.mock("server-only", () => ({}));

const mockExecuteReportAction = vi.fn();
const mockExportCsvAction = vi.fn();
const mockExportPdfAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/reports/[reportSlug]/actions", () => ({
  executeReportAction: (...args: unknown[]) =>
    mockExecuteReportAction(...args),
  exportReportCsvAction: (...args: unknown[]) =>
    mockExportCsvAction(...args),
  exportReportPdfAction: (...args: unknown[]) =>
    mockExportPdfAction(...args),
}));

function makeDefinition(): ReportDefinitionDetail {
  return {
    slug: "timesheet",
    name: "Timesheet Report",
    description: "Detailed timesheet data",
    category: "OPERATIONAL",
    parameterSchema: {
      parameters: [
        {
          name: "dateFrom",
          type: "date",
          label: "Date From",
          required: true,
        },
      ],
    },
    columnDefinitions: {
      columns: [
        { key: "memberName", label: "Member", type: "string" },
        { key: "hours", label: "Hours", type: "decimal" },
      ],
    },
    isSystem: true,
  };
}

function makeExecutionResponse(): ReportExecutionResponse {
  return {
    reportName: "Timesheet Report",
    parameters: { dateFrom: "2026-01-01" },
    generatedAt: "2026-01-15T10:00:00Z",
    columns: [
      { key: "memberName", label: "Member", type: "string" },
      { key: "hours", label: "Hours", type: "decimal" },
    ],
    rows: [{ memberName: "Alice", hours: 40 }],
    summary: { totalHours: 40 },
    pagination: { page: 0, size: 25, totalElements: 1, totalPages: 1 },
  };
}

describe("ReportRunner", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("renders export buttons disabled initially", () => {
    render(<ReportRunner definition={makeDefinition()} orgSlug="acme" />);

    const csvBtn = screen.getByRole("button", { name: /export csv/i });
    const pdfBtn = screen.getByRole("button", { name: /export pdf/i });

    expect(csvBtn).toBeDisabled();
    expect(pdfBtn).toBeDisabled();
  });

  it("enables export buttons after running a report", async () => {
    const user = userEvent.setup();
    mockExecuteReportAction.mockResolvedValue({
      data: makeExecutionResponse(),
    });

    render(<ReportRunner definition={makeDefinition()} orgSlug="acme" />);

    // Fill required date field
    const dateInput = screen.getByLabelText(/date from/i);
    await user.type(dateInput, "2026-01-01");

    // Click run
    await user.click(screen.getByRole("button", { name: /run report/i }));

    await waitFor(() => {
      const csvBtn = screen.getByRole("button", { name: /export csv/i });
      expect(csvBtn).not.toBeDisabled();
    });

    const pdfBtn = screen.getByRole("button", { name: /export pdf/i });
    expect(pdfBtn).not.toBeDisabled();
  });

  it("renders results data after execution", async () => {
    const user = userEvent.setup();
    mockExecuteReportAction.mockResolvedValue({
      data: makeExecutionResponse(),
    });

    render(<ReportRunner definition={makeDefinition()} orgSlug="acme" />);

    const dateInput = screen.getByLabelText(/date from/i);
    await user.type(dateInput, "2026-01-01");
    await user.click(screen.getByRole("button", { name: /run report/i }));

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    expect(screen.getByText("Total Hours")).toBeInTheDocument();
  });

  it("calls executeReportAction with correct arguments", async () => {
    const user = userEvent.setup();
    mockExecuteReportAction.mockResolvedValue({
      data: makeExecutionResponse(),
    });

    render(<ReportRunner definition={makeDefinition()} orgSlug="acme" />);

    const dateInput = screen.getByLabelText(/date from/i);
    await user.type(dateInput, "2026-02-15");
    await user.click(screen.getByRole("button", { name: /run report/i }));

    await waitFor(() => {
      expect(mockExecuteReportAction).toHaveBeenCalledWith(
        "timesheet",
        expect.objectContaining({ dateFrom: "2026-02-15" }),
        0,
        25,
      );
    });
  });

  it("shows error message when execution fails", async () => {
    const user = userEvent.setup();
    mockExecuteReportAction.mockResolvedValue({
      data: null,
      error: "Report execution failed",
    });

    render(<ReportRunner definition={makeDefinition()} orgSlug="acme" />);

    const dateInput = screen.getByLabelText(/date from/i);
    await user.type(dateInput, "2026-01-01");
    await user.click(screen.getByRole("button", { name: /run report/i }));

    await waitFor(() => {
      expect(
        screen.getByText("Report execution failed"),
      ).toBeInTheDocument();
    });
  });
});
