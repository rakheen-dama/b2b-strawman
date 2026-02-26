import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";

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
const mockGetPaymentStatus = vi.fn();
vi.mock("@/lib/api-client", () => ({
  portalGet: (...args: unknown[]) => mockPortalGet(...args),
  getPaymentStatus: (...args: unknown[]) => mockGetPaymentStatus(...args),
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
import PaymentSuccessPage from "@/app/(authenticated)/invoices/[id]/payment-success/page";
import PaymentCancelledPage from "@/app/(authenticated)/invoices/[id]/payment-cancelled/page";

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
  paymentUrl: "https://pay.example.com/session/abc123",
  lines: [
    {
      id: "line-1",
      description: "Design consultation",
      quantity: 5,
      unitPrice: 200.0,
      amount: 1000.0,
      sortOrder: 1,
    },
  ],
};

describe("InvoiceDetailPage — Payment Section", () => {
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

  it("renders Pay Now button when paymentUrl present and status SENT", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(screen.getByText("Pay Now")).toBeInTheDocument();
    });

    const payLink = screen.getByText("Pay Now").closest("a");
    expect(payLink).toHaveAttribute(
      "href",
      "https://pay.example.com/session/abc123",
    );
    expect(payLink).toHaveAttribute("target", "_blank");
    expect(payLink).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("hides Pay Now when status is PAID and shows paid confirmation", async () => {
    mockPortalGet.mockResolvedValue({
      ...mockInvoiceDetail,
      status: "PAID",
      paymentUrl: null,
    });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(
        screen.getByText("This invoice has been paid"),
      ).toBeInTheDocument();
    });

    expect(screen.queryByText("Pay Now")).not.toBeInTheDocument();
  });

  it("shows contact message when no paymentUrl and status SENT", async () => {
    mockPortalGet.mockResolvedValue({
      ...mockInvoiceDetail,
      paymentUrl: null,
    });

    render(<InvoiceDetailPage />);

    await waitFor(() => {
      expect(
        screen.getByText("Contact Test Org to arrange payment"),
      ).toBeInTheDocument();
    });

    expect(screen.queryByText("Pay Now")).not.toBeInTheDocument();
  });
});

describe("PaymentSuccessPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("shows processing message while polling", async () => {
    // Make getPaymentStatus return SENT (not PAID) so polling continues
    mockGetPaymentStatus.mockResolvedValue({
      status: "SENT",
      paidAt: null,
    });

    render(<PaymentSuccessPage />);

    // The initial poll resolves with SENT, so isPolling remains true
    await waitFor(() => {
      expect(
        screen.getByText("Payment is being processed..."),
      ).toBeInTheDocument();
    });
  });

  it("shows payment confirmed when status is PAID", async () => {
    mockGetPaymentStatus.mockResolvedValue({
      status: "PAID",
      paidAt: "2026-02-26T12:00:00Z",
    });

    render(<PaymentSuccessPage />);

    await waitFor(() => {
      expect(screen.getByText("Payment confirmed")).toBeInTheDocument();
    });

    expect(screen.getByText("Payment received — thank you!")).toBeInTheDocument();
    expect(
      screen.queryByText("Payment is being processed..."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("Payment is still being processed"),
    ).not.toBeInTheDocument();
  });

  it("shows timeout message when polling exceeds duration", async () => {
    // Return SENT forever — never resolve to PAID
    mockGetPaymentStatus.mockImplementation(
      () => new Promise((resolve) => {
        setTimeout(() => resolve({ status: "SENT", paidAt: null }), 10);
      }),
    );

    vi.useFakeTimers({ shouldAdvanceTime: true });

    try {
      render(<PaymentSuccessPage />);

      // Let initial poll resolve
      await vi.advanceTimersByTimeAsync(50);

      // Advance past MAX_DURATION (30s) in chunks to let intervals fire
      for (let i = 0; i < 11; i++) {
        await vi.advanceTimersByTimeAsync(3100);
      }

      expect(
        screen.getByText("Payment is still being processed"),
      ).toBeInTheDocument();

      expect(
        screen.queryByText("Payment is being processed..."),
      ).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("PaymentCancelledPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    cleanup();
  });

  it("shows retry button with paymentUrl", async () => {
    mockPortalGet.mockResolvedValue(mockInvoiceDetail);

    render(<PaymentCancelledPage />);

    await waitFor(() => {
      expect(screen.getByText("Payment was cancelled")).toBeInTheDocument();
    });

    const payLink = screen.getByText("Pay Now").closest("a");
    expect(payLink).toHaveAttribute(
      "href",
      "https://pay.example.com/session/abc123",
    );
    expect(screen.getByText("View Invoice")).toBeInTheDocument();
  });
});
