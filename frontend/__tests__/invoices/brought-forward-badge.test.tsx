import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { BroughtForwardBadge } from "@/components/invoices/brought-forward-badge";
import { InvoiceHeaderActions } from "@/components/invoices/invoice-header-actions";
import type { InvoiceResponse } from "@/lib/types";

/** The server page's business date (ADR-396: the client never computes "today"). */
const TODAY = "2026-11-27";

vi.mock("server-only", () => ({}));

vi.mock("@/lib/capabilities", () => ({
  RequiresCapability: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function makeInvoice(overrides: Partial<InvoiceResponse> = {}): InvoiceResponse {
  return {
    id: "inv-bf-1",
    clientId: "cust-1",
    invoiceNumber: "INV-2291",
    status: "SENT",
    currency: "ZAR",
    issueDate: "2026-09-15",
    dueDate: "2026-10-15",
    subtotal: 12345.67,
    taxAmount: 0,
    total: 12345.67,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    amountPaid: 0,
    balanceDue: 12345.67,
    payments: [],
    paymentReference: null,
    paidAt: null,
    clientName: "Thandi Nkosi",
    clientEmail: "thandi@example.co.za",
    clientAddress: null,
    orgName: "Mathebula & Partners",
    createdBy: "user-1",
    createdByName: "Alice",
    approvedBy: "user-1",
    approvedByName: "Alice",
    createdAt: "2026-11-24T10:00:00Z",
    updatedAt: "2026-11-24T10:00:00Z",
    lines: [],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: "OPERATING",
    taxBreakdown: [],
    taxInclusive: false,
    taxRegistrationNumber: null,
    taxRegistrationLabel: null,
    taxLabel: null,
    hasPerLineTax: false,
    takeOnBatchId: "batch-658",
    ...overrides,
  };
}

const noop = () => {};

function renderHeader(invoice: InvoiceResponse) {
  const status = invoice.status;
  return render(
    <InvoiceHeaderActions
      todayIso={TODAY}
      invoice={invoice}
      isAdmin
      isPending={false}
      isDraft={status === "DRAFT"}
      isApproved={status === "APPROVED"}
      isSent={status === "SENT"}
      canRecordPayment={status === "SENT" || status === "PARTIALLY_PAID"}
      onPreview={noop}
      onApprove={noop}
      onDelete={noop}
      onSend={noop}
      onVoid={noop}
      onShowPaymentForm={noop}
    />
  );
}

afterEach(() => {
  cleanup();
});

describe("Brought forward — take-on badge (658A)", () => {
  it("marks a brought-forward fee note in the fee-note list row", () => {
    render(<BroughtForwardBadge takeOnBatchId="batch-658" />);
    expect(screen.getByTestId("fee-note-takeon-badge")).toHaveTextContent(
      "Brought forward — take-on"
    );
  });

  it("shows nothing for a fee note Kazi generated", () => {
    const { container } = render(
      <>
        <BroughtForwardBadge takeOnBatchId={null} />
        <BroughtForwardBadge />
      </>
    );
    expect(screen.queryByTestId("fee-note-takeon-badge")).not.toBeInTheDocument();
    expect(container).toBeEmptyDOMElement();
  });

  it("marks the fee-note detail header; a SENT note offers Record Payment and Void only", () => {
    renderHeader(makeInvoice());
    expect(screen.getByTestId("fee-note-takeon-badge")).toHaveTextContent(
      "Brought forward — take-on"
    );
    expect(screen.getByRole("button", { name: "Record Payment" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Void" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send fee note" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete Draft" })).not.toBeInTheDocument();
    // no raw enum or batch id on screen
    expect(document.body.textContent).not.toMatch(/batch-658|DEBTOR_BALANCES|TAKE_ON/);
  });

  it("shows no badge on an ordinary fee note's header", () => {
    renderHeader(makeInvoice({ takeOnBatchId: null, invoiceNumber: "INV-0007" }));
    expect(screen.queryByTestId("fee-note-takeon-badge")).not.toBeInTheDocument();
  });
});
