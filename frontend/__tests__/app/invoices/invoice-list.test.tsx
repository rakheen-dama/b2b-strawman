import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StatusBadge } from "@/components/invoices/status-badge";
import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import type { InvoiceLineResponse, InvoiceStatus } from "@/lib/types";

describe("StatusBadge", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders all status variants with correct labels", () => {
    const statuses: InvoiceStatus[] = ["DRAFT", "APPROVED", "SENT", "PAID", "VOID"];
    const labels = ["Draft", "Approved", "Sent", "Paid", "Void"];

    const { unmount } = render(
      <div>
        {statuses.map((status) => (
          <StatusBadge key={status} status={status} />
        ))}
      </div>,
    );

    for (const label of labels) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }

    unmount();
  });

  it("renders DRAFT badge with neutral variant", () => {
    render(<StatusBadge status="DRAFT" />);
    const badge = screen.getByText("Draft");
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders PAID badge with success variant", () => {
    render(<StatusBadge status="PAID" />);
    const badge = screen.getByText("Paid");
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders VOID badge with destructive variant", () => {
    render(<StatusBadge status="VOID" />);
    const badge = screen.getByText("Void");
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });
});

describe("InvoiceLineTable", () => {
  const sampleLines: InvoiceLineResponse[] = [
    {
      id: "line-1",
      projectId: "p1",
      projectName: "Project Alpha",
      timeEntryId: "te-1",
      expenseId: null,
      lineType: "TIME",
      description: "Development work",
      quantity: 8,
      unitPrice: 150,
      amount: 1200,
      sortOrder: 0,
      taxRateId: null,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
    {
      id: "line-2",
      projectId: null,
      projectName: null,
      timeEntryId: null,
      expenseId: null,
      lineType: "MANUAL",
      description: "Flat fee item",
      quantity: 2,
      unitPrice: 250,
      amount: 500,
      sortOrder: 1,
      taxRateId: null,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table columns with line items", () => {
    render(
      <InvoiceLineTable
        lines={sampleLines}
        currency="USD"
        editable={false}
      />,
    );

    expect(screen.getByText("Line Items")).toBeInTheDocument();
    expect(screen.getByText("Development work")).toBeInTheDocument();
    expect(screen.getByText("Flat fee item")).toBeInTheDocument();
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("$1,200.00")).toBeInTheDocument();
    expect(screen.getByText("$250.00")).toBeInTheDocument(); // unit price of flat fee item
  });

  it("shows empty message when no lines", () => {
    render(
      <InvoiceLineTable
        lines={[]}
        currency="USD"
        editable={true}
        onAddLine={vi.fn()}
      />,
    );

    expect(
      screen.getByText("No line items yet. Add a line item to get started."),
    ).toBeInTheDocument();
  });

  it("shows Add Line button in editable mode", () => {
    const onAddLine = vi.fn();
    render(
      <InvoiceLineTable
        lines={sampleLines}
        currency="USD"
        editable={true}
        onAddLine={onAddLine}
      />,
    );

    const addBtn = screen.getByRole("button", { name: /add line/i });
    expect(addBtn).toBeInTheDocument();
  });

  it("shows edit/delete icons in editable mode", () => {
    render(
      <InvoiceLineTable
        lines={sampleLines}
        currency="USD"
        editable={true}
        onEditLine={vi.fn()}
        onDeleteLine={vi.fn()}
      />,
    );

    expect(
      screen.getByRole("button", { name: /edit development work/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /delete development work/i }),
    ).toBeInTheDocument();
  });

  it("hides edit/delete icons in read-only mode", () => {
    render(
      <InvoiceLineTable
        lines={sampleLines}
        currency="USD"
        editable={false}
      />,
    );

    expect(
      screen.queryByRole("button", { name: /edit/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete/i }),
    ).not.toBeInTheDocument();
  });

  it("calls onDeleteLine when delete button is clicked", async () => {
    const user = userEvent.setup();
    const onDeleteLine = vi.fn();

    render(
      <InvoiceLineTable
        lines={sampleLines}
        currency="USD"
        editable={true}
        onEditLine={vi.fn()}
        onDeleteLine={onDeleteLine}
      />,
    );

    const deleteBtn = screen.getByRole("button", {
      name: /delete development work/i,
    });
    await user.click(deleteBtn);

    expect(onDeleteLine).toHaveBeenCalledWith("line-1");
  });
});
