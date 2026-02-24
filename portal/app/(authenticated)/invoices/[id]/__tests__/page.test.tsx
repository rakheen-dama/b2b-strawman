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
const mockWindowOpen = vi.fn();
Object.defineProperty(window, "open", {
  value: mockWindowOpen,
  writable: true,
});

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

describe("InvoiceDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
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
});
