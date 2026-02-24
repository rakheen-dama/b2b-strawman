import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/invoices",
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

import InvoicesPage from "@/app/(authenticated)/invoices/page";

const mockInvoices = [
  {
    id: "inv-1",
    invoiceNumber: "INV-001",
    status: "SENT",
    issueDate: "2026-02-01",
    dueDate: "2026-03-01",
    total: 1250.0,
    currency: "ZAR",
  },
  {
    id: "inv-2",
    invoiceNumber: "INV-002",
    status: "PAID",
    issueDate: "2026-01-15",
    dueDate: "2026-02-15",
    total: 3500.0,
    currency: "ZAR",
  },
];

describe("InvoicesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("renders invoice table after loading", async () => {
    mockPortalGet.mockResolvedValue(mockInvoices);

    render(<InvoicesPage />);

    expect(screen.getByText("Invoices")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
      expect(screen.getByText("INV-002")).toBeInTheDocument();
    });

    // Status badges
    expect(screen.getByText("SENT")).toBeInTheDocument();
    expect(screen.getByText("PAID")).toBeInTheDocument();
  });

  it("shows empty state when no invoices exist", async () => {
    mockPortalGet.mockResolvedValue([]);

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getByText("No invoices yet.")).toBeInTheDocument();
    });
  });

  it("shows error state on fetch failure", async () => {
    mockPortalGet.mockRejectedValue(new Error("Network error"));

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("downloads PDF when download button is clicked", async () => {
    mockPortalGet
      .mockResolvedValueOnce(mockInvoices)
      .mockResolvedValueOnce({
        downloadUrl: "https://s3.example.com/invoice.pdf?signed=abc",
      });

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getByText("INV-001")).toBeInTheDocument();
    });

    const user = userEvent.setup();
    const downloadBtn = screen.getByLabelText("Download INV-001");
    await user.click(downloadBtn);

    expect(mockPortalGet).toHaveBeenCalledWith(
      "/portal/invoices/inv-1/download",
    );
    expect(mockWindowOpen).toHaveBeenCalledWith(
      "https://s3.example.com/invoice.pdf?signed=abc",
      "_blank",
    );
  });
});
