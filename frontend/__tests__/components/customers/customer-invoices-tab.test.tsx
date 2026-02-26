import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CustomerInvoicesTab } from "@/components/customers/customer-invoices-tab";
import type { InvoiceResponse } from "@/lib/types";

// Mock the invoice actions used by InvoiceGenerationDialog
vi.mock("@/app/(app)/org/[slug]/customers/[id]/invoice-actions", () => ({
  fetchUnbilledTime: vi.fn(),
  createInvoiceDraft: vi.fn(),
}));

const sampleInvoices: InvoiceResponse[] = [
  {
    id: "inv-1",
    customerId: "c1",
    invoiceNumber: "INV-2026-001",
    status: "PAID",
    currency: "USD",
    issueDate: "2026-01-10",
    dueDate: "2026-02-10",
    subtotal: 500,
    taxAmount: 0,
    total: 500,
    notes: null,
    paymentTerms: null,
    paymentReference: null,
    paidAt: "2026-01-20T00:00:00Z",
    customerName: "Acme Corp",
    customerEmail: "billing@acme.com",
    customerAddress: null,
    orgName: "My Org",
    createdBy: "user-1",
    approvedBy: "user-2",
    createdAt: "2026-01-05T00:00:00Z",
    updatedAt: "2026-01-20T00:00:00Z",
    lines: [],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
  },
  {
    id: "inv-2",
    customerId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "USD",
    issueDate: null,
    dueDate: null,
    subtotal: 300,
    taxAmount: 0,
    total: 300,
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
    createdAt: "2026-01-15T00:00:00Z",
    updatedAt: "2026-01-15T00:00:00Z",
    lines: [],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
  },
];

describe("CustomerInvoicesTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders table with invoices", () => {
    render(
      <CustomerInvoicesTab
        invoices={sampleInvoices}
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        canManage={true}
        defaultCurrency="USD"
      />,
    );

    expect(screen.getByText("Invoices")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument(); // count badge
    expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
    // "Draft" appears both as invoice number placeholder and status badge
    const draftElements = screen.getAllByText("Draft");
    expect(draftElements.length).toBe(2); // link text + status badge
    expect(screen.getByText("Paid")).toBeInTheDocument(); // status badge
    expect(screen.getByText("$500.00")).toBeInTheDocument();
    expect(screen.getByText("$300.00")).toBeInTheDocument();
  });

  it("shows New Invoice button when canManage is true and opens dialog", async () => {
    const user = userEvent.setup();

    render(
      <CustomerInvoicesTab
        invoices={sampleInvoices}
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        canManage={true}
        defaultCurrency="USD"
      />,
    );

    const newInvoiceButton = screen.getByRole("button", { name: /new invoice/i });
    expect(newInvoiceButton).toBeInTheDocument();

    await user.click(newInvoiceButton);

    expect(screen.getByText("Generate Invoice")).toBeInTheDocument();
  });

  it("hides New Invoice button when canManage is false", () => {
    render(
      <CustomerInvoicesTab
        invoices={sampleInvoices}
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        canManage={false}
        defaultCurrency="USD"
      />,
    );

    expect(screen.queryByRole("button", { name: /new invoice/i })).toBeNull();
    // Table should still render
    expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
  });

  it("shows empty state when no invoices", () => {
    render(
      <CustomerInvoicesTab
        invoices={[]}
        customerId="c1"
        customerName="Acme Corp"
        slug="acme"
        canManage={true}
        defaultCurrency="USD"
      />,
    );

    expect(screen.getByText("No invoices yet")).toBeInTheDocument();
    expect(
      screen.getByText("Generate an invoice from unbilled time entries"),
    ).toBeInTheDocument();
  });
});
