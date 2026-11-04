import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import type { Mock } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatCurrency } from "@b2mash/shared/format";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/invoices/inv-1",
  useParams: () => ({ id: "inv-1" }),
}));

// Mock next/link
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock api-client
const mockPortalGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
}));

// Mock useAuth
vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    jwt: "test-jwt",
    customer: {
      id: "cust-1",
      name: "Test Corp",
      email: "alice@test.com",
      orgId: "org_abc",
    },
    logout: vi.fn(),
  }),
}));

// Mock useBranding
vi.mock("@/hooks/use-branding", () => ({
  useBranding: () => ({
    orgName: "Test Org",
    logoUrl: null,
    brandColor: "#3B82F6",
    footerText: null,
    isLoading: false,
  }),
}));

// Mock window.open
let mockWindowOpen: Mock<typeof window.open>;

import InvoiceDetailPage from "@/app/(authenticated)/invoices/[id]/page";

const mockInvoiceDetail = {
  id: "inv-1",
  invoiceNumber: "INV-001",
  status: "SENT",
  issueDate: "2026-02-01",
  dueDate: "2026-03-01",
  subtotal: 1000.0,
  taxAmount: 150.0,
  total: 1150.0,
  amountPaid: 0,
  balanceDue: 1150.0,
  currency: "ZAR",
  notes: "Payment due within 30 days",
  paymentUrl: null,
  lines: [
    {
      id: "line-1",
      description: "Design consultation",
      quantity: 5,
      unitPrice: 200.0,
      amount: 1000.0,
      sortOrder: 1,
      taxRateName: null,
      taxRatePercent: null,
      taxAmount: null,
      taxExempt: false,
    },
  ],
  taxBreakdown: null,
  taxRegistrationNumber: null,
  taxRegistrationLabel: null,
  taxLabel: null,
  taxInclusive: false,
  hasPerLineTax: false,
};

const mockInvoiceWithTax = {
  ...mockInvoiceDetail,
  id: "inv-tax",
  invoiceNumber: "INV-TAX-001",
  subtotal: 10000.0,
  taxAmount: 1500.0,
  total: 11500.0,
  hasPerLineTax: true,
  taxLabel: "VAT",
  taxInclusive: false,
  taxRegistrationNumber: "4200000000",
  taxRegistrationLabel: "VAT Number",
  taxBreakdown: [
    {
      rateName: "VAT",
      ratePercent: 15.0,
      taxableAmount: 10000.0,
      taxAmount: 1500.0,
    },
  ],
  lines: [
    {
      id: "line-tax-1",
      description: "Development work",
      quantity: 10,
      unitPrice: 1000.0,
      amount: 10000.0,
      sortOrder: 0,
      taxRateName: "VAT",
      taxRatePercent: 15.0,
      taxAmount: 1500.0,
      taxExempt: false,
    },
  ],
};

const mockInvoiceWithTaxInclusive = {
  ...mockInvoiceWithTax,
  id: "inv-tax-inc",
  invoiceNumber: "INV-TAX-INC",
  taxInclusive: true,
};

/**
 * Whitespace-free comparison (Node "R 37,814.50" vs Chrome "R 37 814,50" — R-14;
 * the summary is a <dl>, so label and amount are adjacent text nodes).
 */
function norm(text: string | null | undefined): string {
  return (text ?? "").replace(/\s+/g, "");
}

const mockPartiallyPaid = {
  ...mockInvoiceDetail,
  id: "inv-pp",
  invoiceNumber: "INV-PP-001",
  status: "PARTIALLY_PAID",
  subtotal: 37230.0,
  taxAmount: 5584.5,
  total: 42814.5,
  amountPaid: 5000.0,
  balanceDue: 37814.5,
};

describe("InvoiceDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockWindowOpen = vi.fn<typeof window.open>();
    vi.spyOn(window, "open").mockImplementation(mockWindowOpen);
    window.alert = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("renders invoice detail after loading", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    // Status badge (sentence case — Phase 88 624H)
    expect(screen.getByText("Sent")).toBeInTheDocument();
    // Notes
    expect(screen.getByText("Payment due within 30 days")).toBeInTheDocument();
    // Back link
    expect(screen.getByText("Back to fee notes")).toBeInTheDocument();
    // Download button
    expect(screen.getByText("Download PDF")).toBeInTheDocument();
  });

  it("renders line items table", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    // Line items render in both mobile cards and desktop table.
    await waitFor(() => {
      expect(
        screen.getAllByText("Design consultation").length,
      ).toBeGreaterThanOrEqual(1);
    });

    expect(screen.getByText("Line Items")).toBeInTheDocument();
    expect(screen.getAllByText("Subtotal").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Tax").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Total").length).toBeGreaterThanOrEqual(1);
  });

  it("downloads PDF when button is clicked", async () => {
    mockPortalGet
      .mockResolvedValueOnce(mockInvoiceDetail)
      .mockResolvedValueOnce({
        downloadUrl: "https://s3.example.com/invoice.pdf?signed=abc",
      });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByText("Download PDF"));

    expect(mockPortalGet).toHaveBeenCalledWith(
      "/portal/invoices/inv-1/download",
    );
    expect(mockWindowOpen).toHaveBeenCalledWith(
      "https://s3.example.com/invoice.pdf?signed=abc",
      "_blank",
    );
  });

  it("shows error state on fetch failure", async () => {
    mockPortalGet.mockRejectedValue(new Error("Network error"));

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("hides notes section when notes is null", async () => {
    mockPortalGet.mockResolvedValue({
      ...mockInvoiceDetail,
      notes: null,
    });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    expect(screen.queryByText("Notes")).not.toBeInTheDocument();
  });

  // ── Tax breakdown display tests ──────────────────────────────────────

  it("renders tax breakdown when hasPerLineTax is true", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceWithTax);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-TAX-001")).toBeInTheDocument();
    });

    // Tax column header (desktop) + mobile per-line label — >= 1 match
    expect(screen.getAllByText("VAT").length).toBeGreaterThanOrEqual(1);
    // Tax breakdown row in footer (mobile + desktop)
    expect(screen.getAllByText("VAT (15%)").length).toBeGreaterThanOrEqual(1);
    // Tax registration number
    expect(screen.getByText("4200000000")).toBeInTheDocument();
    expect(screen.getByText(/VAT Number/)).toBeInTheDocument();
    // Per-line tax display (mobile + desktop)
    expect(screen.getAllByText("VAT 15%").length).toBeGreaterThanOrEqual(1);
  });

  it("shows legacy flat Tax row for invoices without per-line tax", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    // Should show flat "Tax" row, not breakdown (renders in both mobile + desktop)
    expect(screen.getAllByText("Tax").length).toBeGreaterThanOrEqual(1);
    // Tax column header should NOT be present (no extra column)
    const headers = screen.getAllByRole("columnheader");
    expect(headers).toHaveLength(4); // Description, Quantity, Rate, Amount
  });

  it("shows tax-inclusive note when taxInclusive and hasPerLineTax", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceWithTaxInclusive);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-TAX-INC")).toBeInTheDocument();
    });

    // Renders in both mobile + desktop footers.
    expect(
      screen.getAllByText(/All amounts include VAT/).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("shows 5 column headers when hasPerLineTax is true", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceWithTax);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-TAX-001")).toBeInTheDocument();
    });

    const headers = screen.getAllByRole("columnheader");
    expect(headers).toHaveLength(5); // Description, Quantity, Rate, Tax, Amount
  });

  // ── Phase 88 624H: part-paid balance (portal half of G-C11 / OQ #13) ──

  it("shows Total, Amount paid and Balance due for a part-paid fee note", async () => {
    mockPortalGet.mockResolvedValue(mockPartiallyPaid);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-PP-001")).toBeInTheDocument();
    });

    expect(screen.getByText("Partially paid")).toBeInTheDocument();
    const summary = screen.getByTestId("invoice-balance-summary");
    const text = norm(summary.textContent);
    expect(text).toContain(norm(`Total${formatCurrency(42814.5, "ZAR")}`));
    expect(text).toContain(norm(`Amount paid${formatCurrency(5000, "ZAR")}`));
    expect(text).toContain(
      norm(`Balance due${formatCurrency(37814.5, "ZAR")}`),
    );
  });

  it("falls back to contact copy (no amounts) when a part-paid note has amountPaid 0", async () => {
    // Pre-V2 rows (624A follow-up) read amountPaid 0 until their next sync.
    mockPortalGet.mockResolvedValue({
      ...mockPartiallyPaid,
      amountPaid: 0,
      balanceDue: 42814.5,
    });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-PP-001")).toBeInTheDocument();
    });

    expect(
      screen.queryByTestId("invoice-balance-summary"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Amount paid/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Balance due/)).not.toBeInTheDocument();
    expect(
      screen.getByText(
        "A part payment has been received. Contact Test Org for the outstanding balance.",
      ),
    ).toBeInTheDocument();
  });

  it("shows no balance summary for a SENT fee note", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    expect(
      screen.queryByTestId("invoice-balance-summary"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("invoice-balance-contact"),
    ).not.toBeInTheDocument();
  });
});

// ── Phase 89 632C: payments with a receipt download per row (G88-F32) ──

describe("InvoiceDetailPage — payments and receipts", () => {
  // useParams() is mocked to { id: "inv-1" }, so every path uses inv-1.
  const PAYMENTS_PATH = "/portal/invoices/inv-1/payments";

  const payments = [
    {
      paymentEventId: "pe-1",
      date: "2026-11-04",
      methodLabel: "EFT",
      amount: 5000,
      reference: "EFT-1",
      receiptAvailable: true,
    },
    {
      paymentEventId: "pe-2",
      date: "2026-11-05",
      methodLabel: "Payment",
      amount: 250,
      reference: null,
      receiptAvailable: false,
    },
  ];

  function routeGet(routes: Record<string, unknown>) {
    mockPortalGet.mockImplementation((path: string) =>
      path in routes
        ? Promise.resolve(routes[path])
        : Promise.reject(new Error(`unexpected GET ${path}`)),
    );
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockWindowOpen = vi.fn<typeof window.open>();
    vi.spyOn(window, "open").mockImplementation(mockWindowOpen);
    window.alert = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("lists a part-paid fee note's payments, each with a Download receipt button", async () => {
    routeGet({
      "/portal/invoices/inv-1": mockPartiallyPaid,
      [PAYMENTS_PATH]: payments,
    });

    render(<InvoiceDetailPage />);

    const list = await screen.findByRole("list", { name: "Payments" });
    expect(
      screen.getByRole("heading", { level: 2, name: "Payments" }),
    ).toBeInTheDocument();
    const rows = list.querySelectorAll("li");
    expect(rows).toHaveLength(2);
    expect(norm(rows[0].textContent)).toContain(
      norm(formatCurrency(5000, "ZAR")),
    );
    expect(rows[0].textContent).toContain("EFT");
    expect(rows[0].textContent).toContain("EFT-1");
    // methodLabel is rendered as sent — "Payment" when no method was recorded.
    expect(rows[1].textContent).toContain("Payment");
    // receiptAvailable false still offers the download (generated lazily server-side).
    expect(
      screen.getAllByRole("button", { name: "Download receipt" }),
    ).toHaveLength(2);
  });

  it("opens the presigned receipt URL for the clicked payment", async () => {
    const user = userEvent.setup();
    routeGet({
      "/portal/invoices/inv-1": mockPartiallyPaid,
      [PAYMENTS_PATH]: payments,
      [`${PAYMENTS_PATH}/pe-1/receipt`]: {
        downloadUrl: "https://s3.example.com/receipt.pdf?signed=abc",
      },
    });

    render(<InvoiceDetailPage />);

    const buttons = await screen.findAllByRole("button", {
      name: "Download receipt",
    });
    await user.click(buttons[0]);

    await waitFor(() => {
      expect(mockWindowOpen).toHaveBeenCalledWith(
        "https://s3.example.com/receipt.pdf?signed=abc",
        "_blank",
      );
    });
    expect(mockPortalGet).toHaveBeenCalledWith(`${PAYMENTS_PATH}/pe-1/receipt`);
    expect(window.alert).not.toHaveBeenCalled();
  });

  it("alerts when the receipt download fails", async () => {
    const user = userEvent.setup();
    routeGet({
      "/portal/invoices/inv-1": mockPartiallyPaid,
      [PAYMENTS_PATH]: payments,
    });

    render(<InvoiceDetailPage />);

    const buttons = await screen.findAllByRole("button", {
      name: "Download receipt",
    });
    await user.click(buttons[1]);

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        "Receipt download failed. Please try again.",
      );
    });
    expect(mockWindowOpen).not.toHaveBeenCalled();
  });

  it("lists payments on a PAID fee note", async () => {
    routeGet({
      "/portal/invoices/inv-1": {
        ...mockInvoiceDetail,
        status: "PAID",
        amountPaid: 1150,
        balanceDue: 0,
      },
      [PAYMENTS_PATH]: [payments[0]],
    });

    render(<InvoiceDetailPage />);

    const list = await screen.findByRole("list", { name: "Payments" });
    expect(screen.getByText("This fee note has been paid")).toBeInTheDocument();
    expect(list.querySelectorAll("li")).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: "Download receipt" }),
    ).toBeInTheDocument();
  });

  it("shows no payments block and never fetches payments for a SENT fee note", async () => {
    routeGet({ "/portal/invoices/inv-1": mockInvoiceDetail });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("invoice-payments")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Payments" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Download receipt" }),
    ).not.toBeInTheDocument();
    expect(
      mockPortalGet.mock.calls.some(([path]) =>
        String(path).endsWith("/payments"),
      ),
    ).toBe(false);
  });

  it("still renders the fee note when the payments call fails", async () => {
    routeGet({ "/portal/invoices/inv-1": mockPartiallyPaid });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-PP-001")).toBeInTheDocument();
    });
    expect(mockPortalGet).toHaveBeenCalledWith(PAYMENTS_PATH);
    expect(screen.queryByTestId("invoice-payments")).not.toBeInTheDocument();
  });
});
