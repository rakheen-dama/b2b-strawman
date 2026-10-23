import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Phase 88 620C (U3, ADR-347): the Fee notes debtor-book tiles read
// GET /api/invoices/outstanding-summary, never a client-side sum of the list. The summary needs
// FINANCIAL_VISIBILITY (owner/admin inherit it); the page itself only needs INVOICING, so the
// tiles drop out (present or absent, never greyed) for a role without it.

vi.mock("server-only", () => ({}));

const {
  mockCaps,
  mockApiGet,
  mockListClients,
  mockGetOrgSettings,
  mockFetchSummary,
  MockApiError,
} = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    mockCaps: vi.fn(),
    mockApiGet: vi.fn(),
    mockListClients: vi.fn(),
    mockGetOrgSettings: vi.fn(),
    mockFetchSummary: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: mockCaps }));
vi.mock("@/lib/api", () => ({ api: { get: mockApiGet }, ApiError: MockApiError }));
vi.mock("@/lib/api/clients", () => ({ listClients: mockListClients }));
vi.mock("@/lib/api/settings", () => ({ getOrgSettings: mockGetOrgSettings }));
// The page imports "./invoice-crud-actions"; vitest mocks by resolved id.
vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchOutstandingSummaryAction: mockFetchSummary,
}));
vi.mock("@/components/invoices/create-invoice-button", () => ({
  CreateInvoiceButton: () => null,
}));
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import InvoicesPage from "@/app/(app)/org/[slug]/invoices/page";
import { formatCurrency } from "@/lib/format";

const OWNER = { capabilities: [], role: "Owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "Admin", isAdmin: true, isOwner: false };
const INVOICING_ONLY = {
  capabilities: ["INVOICING"],
  role: "Biller",
  isAdmin: false,
  isOwner: false,
};
const FINANCE = {
  capabilities: ["INVOICING", "FINANCIAL_VISIBILITY"],
  role: "Bookkeeper",
  isAdmin: false,
  isOwner: false,
};

const SUMMARY = {
  currency: "ZAR",
  outstandingTotal: 3450,
  outstandingCount: 1,
  overdueTotal: 1200,
  overdueCount: 1,
  approvedUnsentTotal: 1725,
  approvedUnsentCount: 1,
};

function invoice(overrides: Record<string, unknown> = {}) {
  return {
    id: "inv-1",
    invoiceNumber: "INV-0001",
    status: "SENT",
    currency: "ZAR",
    issueDate: "2026-10-01",
    dueDate: "2026-10-05",
    total: 9999,
    paidAt: null,
    clientName: "Sipho Ndlovu",
    paymentUrl: null,
    ...overrides,
  };
}

async function renderPage() {
  render(
    await InvoicesPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    })
  );
}

const TILE_IDS = [
  "fee-notes-total-outstanding",
  "fee-notes-overdue",
  "fee-notes-approved-unsent",
] as const;

function expectSummaryTiles() {
  expect(screen.getByText("Total outstanding")).toBeInTheDocument();
  expect(screen.getByText("Overdue")).toBeInTheDocument();
  expect(screen.getByText("Approved, not sent")).toBeInTheDocument();
  expect(screen.getByTestId("fee-notes-total-outstanding").textContent).toBe(
    formatCurrency(3450, "ZAR")
  );
  expect(screen.getByTestId("fee-notes-overdue").textContent).toBe(formatCurrency(1200, "ZAR"));
  expect(screen.getByTestId("fee-notes-approved-unsent").textContent).toBe(
    formatCurrency(1725, "ZAR")
  );
}

function expectNoSummaryTiles() {
  for (const id of TILE_IDS) expect(screen.queryByTestId(id)).not.toBeInTheDocument();
  expect(screen.queryByText("Total outstanding")).not.toBeInTheDocument();
  expect(screen.queryByText("Approved, not sent")).not.toBeInTheDocument();
  // The list and the one list-derived tile still render.
  expect(screen.getByText("INV-0001")).toBeInTheDocument();
  expect(screen.getByTestId("fee-notes-paid-this-month")).toBeInTheDocument();
}

describe("Fee notes summary tiles (Phase 88 620C)", () => {
  beforeEach(() => {
    mockCaps.mockResolvedValue(OWNER);
    // A list whose client-side APPROVED+SENT sum (9999 + 5000, both overdue) differs from the summary.
    mockApiGet.mockResolvedValue([
      invoice(),
      invoice({ id: "inv-2", invoiceNumber: "INV-0002", status: "APPROVED", total: 5000 }),
    ]);
    mockListClients.mockResolvedValue([]);
    mockGetOrgSettings.mockResolvedValue({ defaultCurrency: "ZAR" });
    mockFetchSummary.mockResolvedValue({ state: "ok", summary: SUMMARY });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("reads Total outstanding / Overdue / Approved, not sent from the summary, not the list", async () => {
    await renderPage();

    expect(mockFetchSummary).toHaveBeenCalledTimes(1);
    expectSummaryTiles();
    // The client-side sum of the list (R 14 999,00) appears nowhere.
    expect(screen.queryByText(formatCurrency(14999, "ZAR"))).not.toBeInTheDocument();
    // Old copy gone.
    expect(screen.queryByText("Total Outstanding")).not.toBeInTheDocument();
    expect(screen.queryByText("Total Overdue")).not.toBeInTheDocument();
    expect(screen.getByTestId("fee-notes-paid-this-month")).toBeInTheDocument();
  });

  it("admin (inherits FINANCIAL_VISIBILITY) fetches the summary", async () => {
    mockCaps.mockResolvedValue(ADMIN);
    await renderPage();

    expect(mockFetchSummary).toHaveBeenCalledTimes(1);
    expectSummaryTiles();
  });

  it("a FINANCIAL_VISIBILITY role fetches the summary", async () => {
    mockCaps.mockResolvedValue(FINANCE);
    await renderPage();

    expect(mockFetchSummary).toHaveBeenCalledTimes(1);
    expectSummaryTiles();
  });

  it("INVOICING-only role: no summary call, no debtor-book tiles, the page still renders", async () => {
    mockCaps.mockResolvedValue(INVOICING_ONLY);
    await renderPage();

    expect(mockFetchSummary).not.toHaveBeenCalled();
    expectNoSummaryTiles();
  });

  it("forbidden or absent summary (403/404) → tiles dropped, list + Paid This Month still render", async () => {
    mockFetchSummary.mockResolvedValue({ state: "absent" });
    await renderPage();

    expect(mockFetchSummary).toHaveBeenCalledTimes(1);
    expectNoSummaryTiles();
  });

  it("summary error → the three tiles render an em dash, the page does not crash", async () => {
    mockFetchSummary.mockResolvedValue({ state: "error" });
    await renderPage();

    for (const id of TILE_IDS) expect(screen.getByTestId(id).textContent).toBe("—");
    expect(screen.getByText("INV-0001")).toBeInTheDocument();
    expect(screen.getByTestId("fee-notes-paid-this-month")).toBeInTheDocument();
  });
});
