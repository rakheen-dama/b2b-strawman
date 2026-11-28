import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, renderHook } from "@testing-library/react";
import { useInvoiceDetail } from "@/components/invoices/use-invoice-detail";
import {
  deleteLineItem,
  fetchInvoice,
  updateInvoice,
} from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import type { InvoiceResponse, InvoiceLineResponse } from "@/lib/types";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchInvoice: vi.fn(),
  updateInvoice: vi.fn(),
  deleteInvoice: vi.fn(),
  addLineItem: vi.fn(),
  updateLineItem: vi.fn(),
  deleteLineItem: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-payment-actions", () => ({
  approveInvoice: vi.fn(),
  sendInvoice: vi.fn(),
  recordPayment: vi.fn(),
  voidInvoice: vi.fn(),
  refreshPaymentLink: vi.fn(),
}));

const NO_TAX: Partial<InvoiceLineResponse> = {
  taxRateId: null,
  taxRateName: null,
  taxRatePercent: null,
  taxAmount: null,
};

function makeLine(overrides?: Partial<InvoiceLineResponse>): InvoiceLineResponse {
  return {
    id: "line-vat",
    matterId: "p1",
    matterName: "Matter",
    timeEntryId: null,
    expenseId: null,
    lineType: "MANUAL",
    description: "Consultation",
    quantity: 1,
    unitPrice: 100,
    amount: 100,
    sortOrder: 0,
    taxRateId: "tr-vat",
    taxRateName: "VAT",
    taxRatePercent: 15,
    taxAmount: 15,
    taxExempt: false,
    tariffItemId: null,
    lineSource: null,
    ...overrides,
  };
}

const VAT_LINE = makeLine();
const NONE_LINE = makeLine({
  id: "line-none",
  description: "Drafting",
  unitPrice: 50,
  amount: 50,
  ...NO_TAX,
});
const TARIFF_LINE = makeLine({
  id: "line-tariff",
  description: "Tariff item",
  unitPrice: 200,
  amount: 200,
  lineType: "TARIFF",
  ...NO_TAX,
});

function makeInvoice(overrides?: Partial<InvoiceResponse>): InvoiceResponse {
  return {
    id: "inv-1",
    clientId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "ZAR",
    issueDate: null,
    dueDate: null,
    subtotal: 150,
    taxAmount: 15,
    total: 165,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    amountPaid: 0,
    balanceDue: 165,
    payments: [],
    paymentReference: null,
    paidAt: null,
    clientName: "Client",
    clientEmail: "client@example.com",
    clientAddress: null,
    orgName: "Firm",
    createdBy: "user-1",
    createdByName: null,
    approvedBy: null,
    approvedByName: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lines: [VAT_LINE, NONE_LINE],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
    taxBreakdown: [{ rateName: "VAT", ratePercent: 15, taxableAmount: 100, taxAmount: 15 }],
    taxInclusive: false,
    taxRegistrationNumber: null,
    taxRegistrationLabel: null,
    taxLabel: null,
    hasPerLineTax: true,
    ...overrides,
  };
}

/** One 15% line (R15 VAT) and one "None" line: the note is in per-line tax mode. */
const MIXED = makeInvoice();
/** What the server holds once the taxed line is deleted: header-tax mode, no VAT. */
const AFTER_DELETE = makeInvoice({
  lines: [NONE_LINE],
  subtotal: 50,
  taxAmount: 0,
  total: 50,
  balanceDue: 50,
  taxBreakdown: [],
  hasPerLineTax: false,
});
/** ...and once an untaxed tariff line is added through the tariff dialog. */
const AFTER_TARIFF = makeInvoice({
  lines: [NONE_LINE, TARIFF_LINE],
  subtotal: 250,
  taxAmount: 0,
  total: 250,
  balanceDue: 250,
  taxBreakdown: [],
  hasPerLineTax: false,
});

/** A fake server: line deletes and dialog adds change what fetchInvoice returns. */
let server: InvoiceResponse;

function renderDetail(initialInvoice: InvoiceResponse) {
  server = initialInvoice;
  return renderHook(() => useInvoiceDetail({ initialInvoice, slug: "acme", taxRates: [] }));
}

describe("useInvoiceDetail — delete and refresh take the fee note from the server", () => {
  beforeEach(() => {
    // reset, not clear: a one-off mock an earlier test left unconsumed must not leak forward
    vi.resetAllMocks();
    vi.mocked(fetchInvoice).mockImplementation(async () => ({ success: true, invoice: server }));
    vi.mocked(deleteLineItem).mockImplementation(async () => {
      server = AFTER_DELETE;
      return { success: true };
    });
    vi.mocked(updateInvoice).mockImplementation(async () => ({ success: true, invoice: server }));
  });

  afterEach(() => {
    cleanup();
  });

  describe("deleting a line", () => {
    it("shows the server's totals and VAT once the taxed line is deleted, without a reload", async () => {
      const { result } = renderDetail(MIXED);

      await act(async () => result.current.handleDeleteLine(VAT_LINE.id));

      expect(fetchInvoice).toHaveBeenCalledWith("inv-1");
      expect(result.current.invoice.lines.map((l) => l.id)).toEqual([NONE_LINE.id]);
      expect(result.current.invoice.hasPerLineTax).toBe(false);
      expect(result.current.invoice.subtotal).toBe(50);
      expect(result.current.invoice.taxAmount).toBe(0);
      expect(result.current.invoice.total).toBe(50);
      expect(result.current.taxAmount).toBe("0");
    });

    it("shows the reload error when the line is deleted but the fee note cannot be re-read", async () => {
      vi.mocked(fetchInvoice).mockResolvedValueOnce({ success: false, error: "Reload failed" });
      const { result } = renderDetail(MIXED);

      await act(async () => result.current.handleDeleteLine(VAT_LINE.id));

      expect(result.current.error).toBe("Reload failed");
    });
  });

  describe("delete, then a tariff or disbursement refresh", () => {
    it("does not bring the deleted line's VAT back into the header Tax Amount, nor save it", async () => {
      const { result } = renderDetail(MIXED);
      expect(result.current.taxAmount).toBe("15");

      await act(async () => result.current.handleDeleteLine(VAT_LINE.id));
      server = AFTER_TARIFF; // the tariff dialog added an untaxed line, then asks for a refresh
      await act(async () => result.current.handleRefresh());

      expect(result.current.invoice.hasPerLineTax).toBe(false);
      expect(result.current.taxAmount).toBe("0");

      await act(async () => result.current.handleSaveDraft());
      expect(vi.mocked(updateInvoice).mock.calls[0][3].taxAmount).toBe(0);
    });
  });

  describe("refreshing", () => {
    it("resets the header Tax Amount to the server's tax when the note leaves per-line mode", async () => {
      const { result } = renderDetail(MIXED);
      server = AFTER_TARIFF;

      await act(async () => result.current.handleRefresh());

      expect(result.current.invoice.hasPerLineTax).toBe(false);
      expect(result.current.taxAmount).toBe("0");
    });

    it("keeps a header tax the user typed on a header-tax note", async () => {
      const headerTaxNote = makeInvoice({ ...AFTER_DELETE, taxAmount: 10, total: 60 });
      const { result } = renderDetail(headerTaxNote);
      act(() => result.current.setTaxAmount("12"));
      server = { ...headerTaxNote, lines: [NONE_LINE, TARIFF_LINE], subtotal: 250, total: 260 };

      await act(async () => result.current.handleRefresh());

      expect(result.current.invoice.lines).toHaveLength(2);
      expect(result.current.taxAmount).toBe("12");
    });

    it("counts as pending work while the fee note is being re-read", async () => {
      let release!: () => void;
      vi.mocked(fetchInvoice).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            release = () => resolve({ success: true, invoice: server });
          })
      );
      const { result } = renderDetail(MIXED);

      act(() => {
        void result.current.handleRefresh();
      });
      expect(result.current.isPending).toBe(true);

      await act(async () => release());
      expect(result.current.isPending).toBe(false);
    });
  });
});
