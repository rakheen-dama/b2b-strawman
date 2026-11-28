import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, cleanup, renderHook } from "@testing-library/react";
import { useInvoiceDetail } from "@/components/invoices/use-invoice-detail";
import { addLineItem, updateLineItem } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";
import type { InvoiceResponse, InvoiceLineResponse, TaxRateResponse } from "@/lib/types";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  updateInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  deleteInvoice: vi.fn().mockResolvedValue({ success: true }),
  addLineItem: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  updateLineItem: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  deleteLineItem: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock("@/app/(app)/org/[slug]/invoices/invoice-payment-actions", () => ({
  approveInvoice: vi.fn(),
  sendInvoice: vi.fn(),
  recordPayment: vi.fn(),
  voidInvoice: vi.fn(),
  refreshPaymentLink: vi.fn(),
}));

const VAT: TaxRateResponse = {
  id: "tr-vat",
  name: "VAT",
  rate: 15,
  isDefault: true,
  isExempt: false,
  active: true,
  sortOrder: 0,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const ZERO_RATED: TaxRateResponse = {
  ...VAT,
  id: "tr-zero",
  name: "Zero-rated",
  rate: 0,
  isDefault: false,
  isExempt: false,
  sortOrder: 1,
};

function makeLine(overrides?: Partial<InvoiceLineResponse>): InvoiceLineResponse {
  return {
    id: "line-1",
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
    taxRateId: VAT.id,
    taxRateName: "VAT",
    taxRatePercent: 15,
    taxAmount: 15,
    taxExempt: false,
    tariffItemId: null,
    lineSource: null,
    ...overrides,
  };
}

function makeInvoice(): InvoiceResponse {
  return {
    id: "inv-1",
    clientId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "ZAR",
    issueDate: null,
    dueDate: null,
    subtotal: 100,
    taxAmount: 15,
    total: 115,
    notes: null,
    paymentTerms: null,
    poNumber: null,
    taxType: null,
    billingPeriodStart: null,
    billingPeriodEnd: null,
    amountPaid: 0,
    balanceDue: 115,
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
    lines: [makeLine()],
    paymentSessionId: null,
    paymentUrl: null,
    paymentDestination: null,
    taxBreakdown: [],
    taxInclusive: false,
    taxRegistrationNumber: null,
    taxRegistrationLabel: null,
    taxLabel: null,
    hasPerLineTax: true,
  };
}

function renderDetail(taxRates: TaxRateResponse[]) {
  return renderHook(() =>
    useInvoiceDetail({ initialInvoice: makeInvoice(), slug: "acme", taxRates })
  );
}

async function addLine(
  hook: ReturnType<typeof renderDetail>["result"],
  taxRateChoice: string | null
) {
  act(() => hook.current.handleAddLine());
  act(() => {
    hook.current.setNewLineDesc("Drafting");
    hook.current.setNewLineRate("50");
    if (taxRateChoice !== null) hook.current.setNewLineTaxRateId(taxRateChoice);
  });
  await act(async () => hook.current.submitAddLine());
  return vi.mocked(addLineItem).mock.calls[0][3];
}

async function editLine(hook: ReturnType<typeof renderDetail>["result"], taxRateChoice: string) {
  act(() => hook.current.handleEditLine(makeLine()));
  act(() => hook.current.setEditLineTaxRateId(taxRateChoice));
  await act(async () => hook.current.submitEditLine());
  return vi.mocked(updateLineItem).mock.calls[0][4];
}

describe("useInvoiceDetail — the 'None' tax choice on a fee-note line", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("adding a line", () => {
    it("sends the explicit no-tax signal when 'None' is chosen while a default VAT rate exists", async () => {
      const { result } = renderDetail([VAT, ZERO_RATED]);
      const request = await addLine(result, "none");
      expect(request).toMatchObject({ taxRateId: null, noTaxRate: true });
    });

    it("sends the pre-selected default rate, with no no-tax signal, when the choice is left alone", async () => {
      const { result } = renderDetail([VAT, ZERO_RATED]);
      const request = await addLine(result, null);
      expect(request.taxRateId).toBe(VAT.id);
      expect(request.noTaxRate).toBeUndefined();
    });

    it("sends a zero-rated rate as a rate, not as 'None'", async () => {
      const { result } = renderDetail([VAT, ZERO_RATED]);
      const request = await addLine(result, ZERO_RATED.id);
      expect(request.taxRateId).toBe(ZERO_RATED.id);
      expect(request.noTaxRate).toBeUndefined();
    });

    it("sends no no-tax signal when no tax-rate choice was shown (rates did not load)", async () => {
      const { result } = renderDetail([]);
      const request = await addLine(result, null);
      expect(request.taxRateId).toBeNull();
      expect(request.noTaxRate).toBeUndefined();
    });
  });

  describe("editing a line", () => {
    it("sends clearTaxRate when a taxed line is changed to 'None'", async () => {
      const { result } = renderDetail([VAT, ZERO_RATED]);
      const request = await editLine(result, "none");
      expect(request).toMatchObject({ taxRateId: null, clearTaxRate: true });
    });

    it("sends the kept rate, with no clear signal, when the line keeps its rate", async () => {
      const { result } = renderDetail([VAT, ZERO_RATED]);
      const request = await editLine(result, VAT.id);
      expect(request.taxRateId).toBe(VAT.id);
      expect(request.clearTaxRate).toBeUndefined();
    });
  });
});
