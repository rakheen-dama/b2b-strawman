import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import type { InvoiceResponse } from "@/lib/types";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

// Mock server actions
const mockApproveInvoice = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockDeleteInvoice = vi.fn().mockResolvedValue({ success: true });
const mockSendInvoice = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockRecordPayment = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockVoidInvoice = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockUpdateInvoice = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockAddLineItem = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockUpdateLineItem = vi.fn().mockResolvedValue({ success: true, invoice: null });
const mockDeleteLineItem = vi.fn().mockResolvedValue({ success: true });

const mockRefreshPaymentLink = vi.fn().mockResolvedValue({ success: true, invoice: null });

vi.mock("@/app/(app)/org/[slug]/invoices/actions", () => ({
  approveInvoice: (...args: unknown[]) => mockApproveInvoice(...args),
  deleteInvoice: (...args: unknown[]) => mockDeleteInvoice(...args),
  sendInvoice: (...args: unknown[]) => mockSendInvoice(...args),
  recordPayment: (...args: unknown[]) => mockRecordPayment(...args),
  voidInvoice: (...args: unknown[]) => mockVoidInvoice(...args),
  updateInvoice: (...args: unknown[]) => mockUpdateInvoice(...args),
  addLineItem: (...args: unknown[]) => mockAddLineItem(...args),
  updateLineItem: (...args: unknown[]) => mockUpdateLineItem(...args),
  deleteLineItem: (...args: unknown[]) => mockDeleteLineItem(...args),
  refreshPaymentLink: (...args: unknown[]) => mockRefreshPaymentLink(...args),
}));

function makeDraftInvoice(overrides?: Partial<InvoiceResponse>): InvoiceResponse {
  return {
    id: "inv-1",
    customerId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "USD",
    issueDate: null,
    dueDate: null,
    subtotal: 1000,
    taxAmount: 100,
    total: 1100,
    notes: null,
    paymentTerms: null,
    paymentReference: null,
    paidAt: null,
    customerName: "Acme Corp",
    customerEmail: "billing@acme.com",
    customerAddress: null,
    orgName: "My Org",
    createdBy: "user-1",
    approvedBy: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lines: [
      {
        id: "line-1",
        projectId: "p1",
        projectName: "Project Alpha",
        timeEntryId: "te-1",
        description: "Consulting work",
        quantity: 10,
        unitPrice: 100,
        amount: 1000,
        sortOrder: 0,
      },
    ],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
    ...overrides,
  };
}

describe("InvoiceDetailClient", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders draft invoice with editable fields", () => {
    const invoice = makeDraftInvoice();
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(screen.getByText("Draft Invoice")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument(); // StatusBadge
    expect(screen.getByText("Customer: Acme Corp")).toBeInTheDocument();

    // Editable form fields
    expect(screen.getByLabelText("Due Date")).toBeInTheDocument();
    expect(screen.getByLabelText("Tax Amount")).toBeInTheDocument();
    expect(screen.getByLabelText("Payment Terms")).toBeInTheDocument();
    expect(screen.getByLabelText("Notes")).toBeInTheDocument();

    // Action buttons
    expect(screen.getByRole("button", { name: /approve/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /delete draft/i })).toBeInTheDocument();

    // Line items
    expect(screen.getByText("Consulting work")).toBeInTheDocument();

    // $1,000.00 appears as line amount and as subtotal
    const thousandElements = screen.getAllByText("$1,000.00");
    expect(thousandElements.length).toBeGreaterThanOrEqual(2);

    // Total
    expect(screen.getByText("$1,100.00")).toBeInTheDocument();
  });

  it("renders APPROVED invoice with lifecycle buttons", () => {
    const invoice = makeDraftInvoice({
      status: "APPROVED",
      invoiceNumber: "INV-2026-001",
      issueDate: "2026-01-15",
      dueDate: "2026-02-15",
    });
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
    expect(screen.getByText("Approved")).toBeInTheDocument(); // StatusBadge

    // Lifecycle buttons
    expect(screen.getByRole("button", { name: /mark as sent/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /void/i })).toBeInTheDocument();

    // No draft edit form
    expect(screen.queryByLabelText("Due Date")).not.toBeInTheDocument();
  });

  it("renders PAID invoice with payment details", () => {
    const invoice = makeDraftInvoice({
      status: "PAID",
      invoiceNumber: "INV-2026-002",
      issueDate: "2026-01-15",
      dueDate: "2026-02-15",
      paidAt: "2026-02-01T00:00:00Z",
      paymentReference: "CHK-12345",
    });
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(screen.getByText("Paid")).toBeInTheDocument(); // StatusBadge
    expect(screen.getByText("Payment Received")).toBeInTheDocument();
    expect(screen.getByText(/Reference: CHK-12345/)).toBeInTheDocument();

    // No action buttons for paid
    expect(screen.queryByRole("button", { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /void/i })).not.toBeInTheDocument();
  });

  it("does not render admin buttons for non-admin", () => {
    const invoice = makeDraftInvoice();
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={false} />,
    );

    expect(screen.getByText("Draft Invoice")).toBeInTheDocument();

    // No action buttons
    expect(screen.queryByRole("button", { name: /approve/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /delete draft/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /preview/i })).not.toBeInTheDocument();

    // No editable form
    expect(screen.queryByLabelText("Due Date")).not.toBeInTheDocument();
  });

  it("renders SENT invoice with Record Payment and Void buttons", () => {
    const invoice = makeDraftInvoice({
      status: "SENT",
      invoiceNumber: "INV-2026-003",
      issueDate: "2026-01-15",
      dueDate: "2026-02-15",
    });
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(screen.getByText("Sent")).toBeInTheDocument(); // StatusBadge
    expect(
      screen.getByRole("button", { name: /record payment/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /void/i }),
    ).toBeInTheDocument();

    // No Approve button for SENT
    expect(
      screen.queryByRole("button", { name: /approve/i }),
    ).not.toBeInTheDocument();
  });

  it("renders VOID invoice with no action buttons", () => {
    const invoice = makeDraftInvoice({
      status: "VOID",
      invoiceNumber: "INV-2026-004",
      issueDate: "2026-01-15",
      dueDate: "2026-02-15",
    });
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(screen.getByText("Void")).toBeInTheDocument(); // StatusBadge
    expect(
      screen.getByText("This invoice has been voided."),
    ).toBeInTheDocument();

    // No action buttons for VOID
    expect(
      screen.queryByRole("button", { name: /approve/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /mark as sent/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /record payment/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /delete draft/i }),
    ).not.toBeInTheDocument();
  });

  it("calls deleteInvoice on delete confirmation", async () => {
    const user = userEvent.setup();
    const invoice = makeDraftInvoice();

    // Mock confirm for happy-dom
    window.confirm = vi.fn().mockReturnValue(true);

    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    const deleteBtn = screen.getByRole("button", { name: /delete draft/i });
    await user.click(deleteBtn);

    expect(mockDeleteInvoice).toHaveBeenCalledWith("acme", "inv-1", "c1");
  });

  it("renders preview button for DRAFT invoice", () => {
    const invoice = makeDraftInvoice();
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(
      screen.getByRole("button", { name: /preview/i }),
    ).toBeInTheDocument();
  });

  it("renders preview button for APPROVED invoice", () => {
    const invoice = makeDraftInvoice({
      status: "APPROVED",
      invoiceNumber: "INV-2026-010",
      issueDate: "2026-01-15",
      dueDate: "2026-02-15",
    });
    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    expect(
      screen.getByRole("button", { name: /preview/i }),
    ).toBeInTheDocument();
  });

  it("opens preview in new tab on click", async () => {
    const user = userEvent.setup();
    const invoice = makeDraftInvoice();

    const mockOpen = vi.spyOn(window, "open").mockImplementation(() => null);

    render(
      <InvoiceDetailClient invoice={invoice} slug="acme" isAdmin={true} />,
    );

    const previewBtn = screen.getByRole("button", { name: /preview/i });
    await user.click(previewBtn);

    expect(mockOpen).toHaveBeenCalledWith(
      "/api/invoices/inv-1/preview",
      "_blank",
    );
  });
});
