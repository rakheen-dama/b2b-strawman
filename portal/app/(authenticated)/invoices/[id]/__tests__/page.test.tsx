import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

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
let mockWindowOpen: ReturnType<typeof vi.fn>;

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

describe("InvoiceDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockWindowOpen = vi.fn();
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

    // Status badge
    expect(screen.getByText("SENT")).toBeInTheDocument();
    // Notes
    expect(
      screen.getByText("Payment due within 30 days"),
    ).toBeInTheDocument();
    // Back link
    expect(screen.getByText("Back to invoices")).toBeInTheDocument();
    // Download button
    expect(screen.getByText("Download PDF")).toBeInTheDocument();
  });

  it("renders line items table", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Design consultation")).toBeInTheDocument();
    });

    expect(screen.getByText("Line Items")).toBeInTheDocument();
    expect(screen.getByText("Subtotal")).toBeInTheDocument();
    expect(screen.getByText("Tax")).toBeInTheDocument();
    expect(screen.getByText("Total")).toBeInTheDocument();
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

    // Tax column header should be visible
    expect(screen.getByText("VAT")).toBeInTheDocument();
    // Tax breakdown row in footer
    expect(screen.getByText("VAT (15%)")).toBeInTheDocument();
    // Tax registration number
    expect(screen.getByText("4200000000")).toBeInTheDocument();
    expect(screen.getByText(/VAT Number/)).toBeInTheDocument();
    // Per-line tax display
    expect(screen.getByText("VAT 15%")).toBeInTheDocument();
  });

  it("shows legacy flat Tax row for invoices without per-line tax", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    // Should show flat "Tax" row, not breakdown
    expect(screen.getByText("Tax")).toBeInTheDocument();
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

    expect(
      screen.getByText(/All amounts include VAT/),
    ).toBeInTheDocument();
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
});
