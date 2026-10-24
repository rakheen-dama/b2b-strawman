import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatCurrency } from "@b2mash/shared/format";

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
let mockWindowOpen: ReturnType<typeof vi.fn>;

import InvoicesPage from "@/app/(authenticated)/invoices/page";

const mockInvoices = [
  {
    id: "inv-1",
    invoiceNumber: "INV-001",
    status: "SENT",
    issueDate: "2026-02-01",
    dueDate: "2026-03-01",
    total: 1250.0,
    amountPaid: 0,
    balanceDue: 1250.0,
    currency: "ZAR",
  },
  {
    id: "inv-2",
    invoiceNumber: "INV-002",
    status: "PAID",
    issueDate: "2026-01-15",
    dueDate: "2026-02-15",
    total: 3500.0,
    amountPaid: 3500.0,
    balanceDue: 0,
    currency: "ZAR",
  },
];

// Phase 88 624H (portal half of G-C11): a part-paid fee note.
const mockPartiallyPaidInvoice = {
  id: "inv-3",
  invoiceNumber: "INV-003",
  status: "PARTIALLY_PAID",
  issueDate: "2026-03-01",
  dueDate: "2026-04-01",
  total: 42814.5,
  amountPaid: 5000.0,
  balanceDue: 37814.5,
  currency: "ZAR",
};

/** Whitespace-insensitive (Node "R 37,814.50" vs Chrome "R 37 814,50" — R-14). */
function norm(text: string | null | undefined): string {
  return (text ?? "").replace(/\s+/g, " ").trim();
}

describe("InvoicesPage", () => {
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

  it("renders invoice table after loading", async () => {
    mockPortalGet.mockResolvedValue(mockInvoices);

    render(<InvoicesPage />);

    expect(screen.getByText("Fee notes")).toBeInTheDocument();

    // Mobile cards and desktop table both render (CSS toggles visibility).
    await waitFor(() => {
      expect(screen.getAllByText("INV-001").length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText("INV-002").length).toBeGreaterThanOrEqual(1);
    });

    // Status badges render in both layouts
    expect(screen.getAllByText("Sent").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Paid").length).toBeGreaterThanOrEqual(1);
  });

  it("shows Partially paid and a Balance sub-line on a part-paid row only", async () => {
    mockPortalGet.mockResolvedValue([
      ...mockInvoices,
      mockPartiallyPaidInvoice,
    ]);

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getAllByText("INV-003").length).toBeGreaterThanOrEqual(1);
    });

    // Badge reads sentence case in both layouts (mobile card + desktop row).
    expect(screen.getAllByText("Partially paid")).toHaveLength(2);

    // Balance line renders once per layout, only for the part-paid row.
    const hints = screen.getAllByTestId("row-balance-due");
    expect(hints).toHaveLength(2);
    const expected = norm(`Balance ${formatCurrency(37814.5, "ZAR")}`);
    for (const hint of hints) {
      expect(norm(hint.textContent)).toBe(expected);
    }
  });

  it("shows no Balance sub-line for SENT/PAID rows or a part-paid row with amountPaid 0", async () => {
    mockPortalGet.mockResolvedValue([
      ...mockInvoices,
      // Pre-V2 row (624A follow-up): PARTIALLY_PAID but amountPaid not yet synced.
      { ...mockPartiallyPaidInvoice, amountPaid: 0, balanceDue: 42814.5 },
    ]);

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getAllByText("INV-003").length).toBeGreaterThanOrEqual(1);
    });

    expect(screen.getAllByText("Partially paid").length).toBeGreaterThanOrEqual(
      1,
    );
    expect(screen.queryByTestId("row-balance-due")).not.toBeInTheDocument();
  });

  it("shows empty state when no invoices exist", async () => {
    mockPortalGet.mockResolvedValue([]);

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getByText("No fee notes yet.")).toBeInTheDocument();
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
    mockPortalGet.mockResolvedValueOnce(mockInvoices).mockResolvedValueOnce({
      downloadUrl: "https://s3.example.com/invoice.pdf?signed=abc",
    });

    render(<InvoicesPage />);

    await waitFor(() => {
      expect(screen.getAllByText("INV-001").length).toBeGreaterThanOrEqual(1);
    });

    const user = userEvent.setup();
    // Download button renders in both mobile + desktop layouts.
    const downloadBtn = screen.getAllByLabelText("Download INV-001")[0];
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
