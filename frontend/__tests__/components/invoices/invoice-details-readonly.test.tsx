import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { InvoiceDetailsReadonly } from "@/components/invoices/invoice-details-readonly";
import type { InvoiceResponse } from "@/lib/types";

function makeInvoice(overrides: Partial<InvoiceResponse> = {}): InvoiceResponse {
  return {
    id: "inv-1",
    customerId: "cust-1",
    invoiceNumber: "INV-001",
    status: "SENT",
    currency: "USD",
    issueDate: "2026-01-01",
    dueDate: "2026-02-01",
    subtotal: 100,
    taxAmount: 15,
    total: 115,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    paymentReference: null,
    paidAt: null,
    customerName: "Acme",
    customerEmail: null,
    customerAddress: null,
    orgName: "Org",
    createdBy: "u1",
    createdByName: null,
    approvedBy: null,
    approvedByName: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lines: [],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
    taxBreakdown: [],
    taxInclusive: false,
    taxRegistrationNumber: null,
    taxRegistrationLabel: null,
    taxLabel: null,
    hasPerLineTax: false,
    ...overrides,
  };
}

describe("InvoiceDetailsReadonly — promoted fields (Epic 464)", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders PO Number when present", () => {
    render(<InvoiceDetailsReadonly invoice={makeInvoice({ poNumber: "PO-2026-XYZ" })} />);
    expect(screen.getByText("PO Number")).toBeInTheDocument();
    expect(screen.getByText("PO-2026-XYZ")).toBeInTheDocument();
  });

  it("renders Tax Type label for VAT", () => {
    render(<InvoiceDetailsReadonly invoice={makeInvoice({ taxType: "VAT" })} />);
    expect(screen.getByText("Tax Type")).toBeInTheDocument();
    expect(screen.getByText("VAT")).toBeInTheDocument();
  });

  it("renders Sales Tax human label for SALES_TAX enum value", () => {
    render(<InvoiceDetailsReadonly invoice={makeInvoice({ taxType: "SALES_TAX" })} />);
    expect(screen.getByText("Sales Tax")).toBeInTheDocument();
  });

  it("renders billing period range when both dates present", () => {
    render(
      <InvoiceDetailsReadonly
        invoice={makeInvoice({
          billingPeriodStart: "2026-01-01",
          billingPeriodEnd: "2026-01-31",
        })}
      />,
    );
    expect(screen.getByText("Billing Period")).toBeInTheDocument();
  });

  it("does not render billing period block when both dates are null", () => {
    render(<InvoiceDetailsReadonly invoice={makeInvoice()} />);
    expect(screen.queryByText("Billing Period")).not.toBeInTheDocument();
    expect(screen.queryByText("PO Number")).not.toBeInTheDocument();
    expect(screen.queryByText("Tax Type")).not.toBeInTheDocument();
  });
});
