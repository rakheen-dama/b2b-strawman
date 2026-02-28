import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import { InvoiceLineTable } from "@/components/invoices/invoice-line-table";
import type {
  InvoiceResponse,
  InvoiceLineResponse,
  TaxRateResponse,
} from "@/lib/types";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

// Mock server actions
vi.mock("@/app/(app)/org/[slug]/invoices/actions", () => ({
  approveInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  deleteInvoice: vi.fn().mockResolvedValue({ success: true }),
  sendInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  recordPayment: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  voidInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  updateInvoice: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  addLineItem: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  updateLineItem: vi.fn().mockResolvedValue({ success: true, invoice: null }),
  deleteLineItem: vi.fn().mockResolvedValue({ success: true }),
  refreshPaymentLink: vi
    .fn()
    .mockResolvedValue({ success: true, invoice: null }),
}));

function makeLine(
  overrides?: Partial<InvoiceLineResponse>,
): InvoiceLineResponse {
  return {
    id: "line-1",
    projectId: "p1",
    projectName: "Project Alpha",
    timeEntryId: null,
    expenseId: null,
    lineType: "TIME",
    description: "Consulting work",
    quantity: 10,
    unitPrice: 100,
    amount: 1000,
    sortOrder: 0,
    taxRateId: null,
    taxRateName: null,
    taxRatePercent: null,
    taxAmount: null,
    taxExempt: false,
    ...overrides,
  };
}

function makeInvoice(
  overrides?: Partial<InvoiceResponse>,
): InvoiceResponse {
  return {
    id: "inv-1",
    customerId: "c1",
    invoiceNumber: null,
    status: "DRAFT",
    currency: "USD",
    issueDate: null,
    dueDate: null,
    subtotal: 1000,
    taxAmount: 150,
    total: 1150,
    notes: null,
    paymentTerms: null,
    paymentReference: null,
    paidAt: null,
    customerName: "Acme Corp",
    customerEmail: "billing@acme.com",
    customerAddress: null,
    orgName: "My Org",
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
    hasPerLineTax: false,
    ...overrides,
  };
}

const sampleTaxRates: TaxRateResponse[] = [
  {
    id: "tr-1",
    name: "VAT",
    rate: 15,
    isDefault: true,
    isExempt: false,
    active: true,
    sortOrder: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "tr-2",
    name: "Zero Rated",
    rate: 0,
    isDefault: false,
    isExempt: true,
    active: true,
    sortOrder: 1,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("Invoice Tax UI", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("Tax rate dropdown", () => {
    it("renders tax rate select in add line form when tax rates are provided", async () => {
      const user = userEvent.setup();
      const invoice = makeInvoice();
      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
          taxRates={sampleTaxRates}
        />,
      );

      // Click "Add Line" button to open the form
      const addLineBtn = screen.getByRole("button", { name: /add line/i });
      await user.click(addLineBtn);

      // Tax Rate label should appear in the add line form
      expect(screen.getByText("Tax Rate")).toBeInTheDocument();
    });

    it("does not render tax rate select when no tax rates provided", async () => {
      const user = userEvent.setup();
      const invoice = makeInvoice();
      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
          taxRates={[]}
        />,
      );

      // Click "Add Line" button
      const addLineBtn = screen.getByRole("button", { name: /add line/i });
      await user.click(addLineBtn);

      // No Tax Rate label in the form
      expect(screen.queryByText("Tax Rate")).not.toBeInTheDocument();
    });
  });

  describe("Per-line tax display in line table", () => {
    it("shows Tax column header when hasPerLineTax is true", () => {
      const lines = [
        makeLine({
          taxRateId: "tr-1",
          taxRateName: "VAT",
          taxRatePercent: 15,
          taxAmount: 150,
          taxExempt: false,
        }),
      ];

      render(
        <InvoiceLineTable
          lines={lines}
          currency="USD"
          editable={false}
          hasPerLineTax={true}
        />,
      );

      // Tax column header should be present
      expect(screen.getByText("Tax")).toBeInTheDocument();

      // Tax rate info should appear
      expect(screen.getByText(/VAT \(15%\)/)).toBeInTheDocument();
      expect(screen.getByText(/\$150\.00/)).toBeInTheDocument();
    });

    it("does not show Tax column when hasPerLineTax is false", () => {
      const lines = [makeLine()];

      render(
        <InvoiceLineTable
          lines={lines}
          currency="USD"
          editable={false}
          hasPerLineTax={false}
        />,
      );

      // No Tax column header
      const headers = screen.getAllByRole("columnheader");
      const headerTexts = headers.map((h) => h.textContent);
      expect(headerTexts).not.toContain("Tax");
    });

    it("shows Exempt for tax-exempt lines", () => {
      const lines = [
        makeLine({
          taxExempt: true,
          taxRateId: "tr-2",
          taxRateName: "Zero Rated",
          taxRatePercent: 0,
          taxAmount: 0,
        }),
      ];

      render(
        <InvoiceLineTable
          lines={lines}
          currency="USD"
          editable={false}
          hasPerLineTax={true}
        />,
      );

      expect(screen.getByText("Exempt")).toBeInTheDocument();
    });
  });

  describe("Tax breakdown in totals section", () => {
    it("shows tax breakdown entries when hasPerLineTax is true", () => {
      const invoice = makeInvoice({
        hasPerLineTax: true,
        taxBreakdown: [
          {
            taxRateName: "VAT",
            taxRatePercent: 15,
            taxableAmount: 1000,
            taxAmount: 150,
          },
        ],
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      // Tax breakdown should show rate name and percentage in totals
      expect(screen.getByText("VAT (15%)")).toBeInTheDocument();
    });

    it("shows simple Tax row when hasPerLineTax is false", () => {
      const invoice = makeInvoice({ hasPerLineTax: false });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      // Simple Tax row should appear in totals
      // Tax appears both as column header "TAX" (but only when hasPerLineTax) and as "Tax" label
      // With hasPerLineTax false, only the totals "Tax" label should be present
      const taxElements = screen.getAllByText("Tax");
      expect(taxElements.length).toBeGreaterThanOrEqual(1);
    });
  });

  describe("Manual tax input visibility", () => {
    it("shows manual tax input when hasPerLineTax is false and status is DRAFT", () => {
      const invoice = makeInvoice({
        status: "DRAFT",
        hasPerLineTax: false,
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      expect(screen.getByLabelText("Tax Amount")).toBeInTheDocument();
    });

    it("hides manual tax input when hasPerLineTax is true", () => {
      const invoice = makeInvoice({
        status: "DRAFT",
        hasPerLineTax: true,
        taxBreakdown: [
          {
            taxRateName: "VAT",
            taxRatePercent: 15,
            taxableAmount: 1000,
            taxAmount: 150,
          },
        ],
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      expect(screen.queryByLabelText("Tax Amount")).not.toBeInTheDocument();
    });
  });

  describe("Tax-inclusive indicator", () => {
    it("shows tax-inclusive note when taxInclusive is true and taxLabel is set", () => {
      const invoice = makeInvoice({
        taxInclusive: true,
        taxLabel: "VAT",
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      expect(screen.getByText("Prices include VAT")).toBeInTheDocument();
    });

    it("does not show tax-inclusive note when taxInclusive is false", () => {
      const invoice = makeInvoice({
        taxInclusive: false,
        taxLabel: "VAT",
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      expect(
        screen.queryByText("Prices include VAT"),
      ).not.toBeInTheDocument();
    });

    it("does not show tax-inclusive note when taxLabel is null", () => {
      const invoice = makeInvoice({
        taxInclusive: true,
        taxLabel: null,
      });

      render(
        <InvoiceDetailClient
          invoice={invoice}
          slug="acme"
          isAdmin={true}
        />,
      );

      expect(screen.queryByText(/Prices include/)).not.toBeInTheDocument();
    });
  });
});
