import { createElement, Fragment } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { FEE_NOTE_HERO_TILE_LABELS, feeNoteHeroTiles } from "@/lib/fee-note-hero";
import { formatCurrency } from "@/lib/format";
import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";

// Phase 91 666B (ADR-396, FD-5): the fee-note hero tiles, derived on the client from the live fee
// note with a server-supplied business date.

const TODAY = "2026-03-11";

afterEach(() => {
  cleanup();
});

function makeInvoice(overrides: Partial<InvoiceResponse> = {}): InvoiceResponse {
  return {
    id: "inv-1",
    clientId: "c1",
    invoiceNumber: "INV-0001",
    status: "SENT",
    currency: "ZAR",
    issueDate: "2026-02-01",
    dueDate: "2026-03-01",
    subtotal: 1000,
    taxAmount: 150,
    total: 1150,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    amountPaid: 0,
    balanceDue: 1150,
    payments: [],
    paymentReference: null,
    paidAt: null,
    clientName: "Acme Corp",
    clientEmail: null,
    clientAddress: null,
    orgName: "My Org",
    createdBy: "user-1",
    createdByName: null,
    approvedBy: null,
    approvedByName: null,
    createdAt: "2026-02-01T00:00:00Z",
    updatedAt: "2026-02-01T00:00:00Z",
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

const ids = (invoice: InvoiceResponse) => feeNoteHeroTiles(invoice, TODAY).map((t) => t.id);
const tile = (invoice: InvoiceResponse, id: string) =>
  feeNoteHeroTiles(invoice, TODAY).find((t) => t.id === id);

function renderHint(invoice: InvoiceResponse) {
  return render(createElement(Fragment, null, tile(invoice, "due")?.hint));
}

describe("feeNoteHeroTiles", () => {
  it("DRAFT: only the Total tile, even with a due date", () => {
    const draft = makeInvoice({ status: "DRAFT", invoiceNumber: null, issueDate: null });
    expect(ids(draft)).toEqual(["total"]);
    expect(tile(draft, "total")).toMatchObject({
      label: "Total",
      value: formatCurrency(1150, "ZAR"),
      state: "ready",
    });
  });

  it("APPROVED: Total + Due, no days hint (not sent, nothing owed yet)", () => {
    const approved = makeInvoice({ status: "APPROVED" });
    expect(ids(approved)).toEqual(["total", "due"]);
    expect(tile(approved, "due")).toMatchObject({ label: "Due", value: "1 Mar 2026" });
    expect(tile(approved, "due")?.hint).toBeUndefined();
  });

  it("formats a date-only dueDate in the SA calendar, whatever the runner's zone", () => {
    // "2026-03-01" parses as UTC midnight; a zone-less formatter would print "28 Feb 2026" west
    // of UTC. formatDateInSA pins Africa/Johannesburg, so the tile reads the stored calendar day.
    expect(tile(makeInvoice({ status: "APPROVED", dueDate: "2026-03-01" }), "due")?.value).toBe(
      "1 Mar 2026"
    );
    expect(tile(makeInvoice({ status: "VOID", dueDate: "2026-12-31" }), "due")?.value).toBe(
      "31 Dec 2026"
    );
  });

  it("SENT overdue: 4 tiles in order, paid 0, balance = balanceDue, '10d overdue' with the danger icon", () => {
    const sent = makeInvoice({ status: "SENT", dueDate: "2026-03-01" });
    expect(ids(sent)).toEqual(["total", "paid", "balance", "due"]);
    expect(tile(sent, "paid")?.value).toBe(formatCurrency(0, "ZAR"));
    expect(tile(sent, "balance")?.value).toBe(formatCurrency(1150, "ZAR"));

    const { container } = renderHint(sent);
    expect(screen.getByText("10d overdue")).toBeInTheDocument();
    const icon = container.querySelector('svg[aria-hidden="true"]');
    expect(icon).not.toBeNull();
    expect(icon).toHaveClass("text-danger");
  });

  it("SENT due today: 'Due today', no icon", () => {
    const { container } = renderHint(makeInvoice({ dueDate: TODAY }));
    expect(screen.getByText("Due today")).toBeInTheDocument();
    expect(container.querySelector("svg")).toBeNull();
  });

  it("SENT not yet due: '{n}d until due', no icon", () => {
    const { container } = renderHint(makeInvoice({ dueDate: "2026-03-25" }));
    expect(screen.getByText("14d until due")).toBeInTheDocument();
    expect(container.querySelector("svg")).toBeNull();
  });

  it("reads an instant-shaped due date by its calendar day", () => {
    renderHint(makeInvoice({ dueDate: "2026-03-01T00:00:00Z" }));
    expect(screen.getByText("10d overdue")).toBeInTheDocument();
  });

  it("PARTIALLY_PAID: paid and balance from the fee note, hint while the balance is owed", () => {
    const part = makeInvoice({ status: "PARTIALLY_PAID", amountPaid: 400, balanceDue: 750 });
    expect(ids(part)).toEqual(["total", "paid", "balance", "due"]);
    expect(tile(part, "paid")?.value).toBe(formatCurrency(400, "ZAR"));
    expect(tile(part, "balance")?.value).toBe(formatCurrency(750, "ZAR"));
    renderHint(part);
    expect(screen.getByText("10d overdue")).toBeInTheDocument();
  });

  it("PAID: 4 tiles, the Due tile has no hint", () => {
    const paid = makeInvoice({ status: "PAID", amountPaid: 1150, balanceDue: 0 });
    expect(ids(paid)).toEqual(["total", "paid", "balance", "due"]);
    expect(tile(paid, "balance")?.value).toBe(formatCurrency(0, "ZAR"));
    expect(tile(paid, "due")?.hint).toBeUndefined();
  });

  it("VOID: Total + Due, no hint", () => {
    const voided = makeInvoice({ status: "VOID" });
    expect(ids(voided)).toEqual(["total", "due"]);
    expect(tile(voided, "due")?.hint).toBeUndefined();
  });

  it("a SENT fee note with a zero balance shows no hint", () => {
    expect(tile(makeInvoice({ balanceDue: 0, amountPaid: 1150 }), "due")?.hint).toBeUndefined();
  });

  it("amountPaid null → the paid tile shows a formatted 0", () => {
    const sent = makeInvoice({ amountPaid: null as unknown as number });
    expect(tile(sent, "paid")?.value).toBe(formatCurrency(0, "ZAR"));
  });

  it("balanceDue null → the balance tile falls back to the total", () => {
    const sent = makeInvoice({ balanceDue: null as unknown as number });
    expect(tile(sent, "balance")?.value).toBe(formatCurrency(1150, "ZAR"));
  });

  it.each<InvoiceStatus>(["APPROVED", "SENT", "PARTIALLY_PAID", "PAID", "VOID"])(
    "%s without a due date has no Due tile",
    (status) => {
      expect(ids(makeInvoice({ status, dueDate: null }))).not.toContain("due");
    }
  );

  it("uses the fee note's currency", () => {
    const usd = makeInvoice({ currency: "USD" });
    expect(tile(usd, "total")?.value).toBe(formatCurrency(1150, "USD"));
  });

  it("labels are the FD-5 set — never 'Subtotal'", () => {
    const all = (["DRAFT", "APPROVED", "SENT", "PARTIALLY_PAID", "PAID", "VOID"] as const).flatMap(
      (status) => feeNoteHeroTiles(makeInvoice({ status }), TODAY).map((t) => t.label)
    );
    expect(new Set(all)).toEqual(new Set(Object.values(FEE_NOTE_HERO_TILE_LABELS)));
    expect(all).not.toContain("Subtotal");
  });
});
