import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

vi.mock("server-only", () => ({}));

const mockGetReportDefinitions = vi.fn();
vi.mock("@/lib/api/reports", () => ({
  getReportDefinitions: (...args: unknown[]) => mockGetReportDefinitions(...args),
}));

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) => mockFetchMyCapabilities(...args),
}));

// 657D: the take-on reports list and the trust accounts (names), and the downloads' actions
const takeOnActions = vi.hoisted(() => ({
  listTakeOnReports: vi.fn(),
  getTakeOnReport: vi.fn(),
  downloadTakeOnReport: vi.fn(),
  retryReportRender: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/matters/take-on-actions", () => takeOnActions);
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
}));

vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: vi.fn().mockResolvedValue({
    enabledModules: ["trust_accounting"],
    defaultCurrency: "ZAR",
  }),
}));

vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
}));

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

import TrustReportsPage from "@/app/(app)/org/[slug]/trust-accounting/reports/page";

const EMPTY_STATE_COPY =
  "Trust reports need financial visibility. Ask an owner or admin to grant it.";

// Phase 92 650C: the trust-reports pack v4 ships nine reports; interest-allocation is retired
const TRUST_SLUGS = [
  "trust-receipts-payments",
  "client-trust-balances",
  "trust-position",
  "client-ledger-statement",
  "trust-reconciliation",
  "investment-register",
  "trust-interest-charges-schedule",
  "unidentified-deposits-age-analysis",
  "section-35-data-pack",
];

const TRUST_DEFINITIONS = {
  categories: [
    {
      category: "TRUST",
      label: "Trust",
      reports: TRUST_SLUGS.map((slug) => ({
        slug,
        name: slug,
        description: `Description of ${slug}`,
      })),
    },
  ],
};

function caps(capabilities: string[], role: { isAdmin?: boolean; isOwner?: boolean } = {}) {
  return {
    capabilities,
    role: role.isOwner ? "owner" : role.isAdmin ? "admin" : "member",
    isAdmin: role.isAdmin ?? false,
    isOwner: role.isOwner ?? false,
  };
}

async function renderPage() {
  render(await TrustReportsPage({ params: Promise.resolve({ slug: "acme" }) }));
}

describe("Trust reports page — capability gates (630A, §14.6.6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetReportDefinitions.mockResolvedValue(TRUST_DEFINITIONS);
    takeOnActions.listTakeOnReports.mockResolvedValue({ success: true, data: [] });
    mockFetchTrustAccounts.mockResolvedValue([]);
  });

  afterEach(() => cleanup());

  it("VIEW_TRUST + FINANCIAL_VISIBILITY lists the nine trust report cards", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST", "FINANCIAL_VISIBILITY"]));

    await renderPage();

    expect(mockGetReportDefinitions).toHaveBeenCalledTimes(1);
    for (const slug of TRUST_SLUGS) {
      expect(screen.getByTestId(`report-card-${slug}`)).toBeInTheDocument();
    }
    expect(screen.getAllByTestId(/^report-card-/)).toHaveLength(9);
    expect(screen.queryByTestId("report-card-interest-allocation")).not.toBeInTheDocument();
    expect(screen.getByTestId("report-card-section-35-data-pack").closest("a")).toHaveAttribute(
      "href",
      "/org/acme/reports/section-35-data-pack"
    );
    expect(
      screen.queryByTestId("trust-reports-financial-visibility-required")
    ).not.toBeInTheDocument();
  });

  it("VIEW_TRUST without FINANCIAL_VISIBILITY shows the specific empty state and makes no request", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST"]));

    await renderPage();

    expect(screen.getByTestId("trust-reports-page")).toBeInTheDocument();
    expect(screen.getByTestId("trust-reports-financial-visibility-required")).toHaveTextContent(
      EMPTY_STATE_COPY
    );
    expect(mockGetReportDefinitions).not.toHaveBeenCalled();
    expect(screen.queryByTestId("report-card-section-35-data-pack")).not.toBeInTheDocument();
    expect(screen.queryByText(/Unable to load report definitions/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No trust reports available/)).not.toBeInTheDocument();
  });

  // 657D: the take-on reports, VIEW_TRUST only (never FINANCIAL_VISIBILITY)
  it("lists the trust take-on reports with their downloads, even without FINANCIAL_VISIBILITY", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST"]));
    const ready = { status: "READY", sha256: "b".repeat(64), fileSize: 10, renderedAt: "" };
    takeOnActions.listTakeOnReports.mockResolvedValue({
      success: true,
      data: [
        {
          id: "report-1",
          batchId: "batch-1",
          trustAccountId: "acc-g",
          cutoverDate: "2026-10-31",
          generatedAt: "2026-11-24T10:00:00Z",
          generatedByName: "Alice Owner",
          files: { pdf: ready, csv: ready },
        },
      ],
    });
    mockFetchTrustAccounts.mockResolvedValue([{ id: "acc-g", accountName: "Trust Account G" }]);

    await renderPage();

    const card = screen.getByTestId("trust-take-on-reports");
    expect(card).toHaveTextContent("Take-on reports");
    const row = screen.getByTestId("trust-take-on-report-report-1");
    expect(row).toHaveTextContent("Trust Account G — taken on at 31 October 2026");
    expect(row).toHaveTextContent("by Alice Owner");
    expect(screen.getByTestId("take-on-report-download-pdf")).toBeInTheDocument();
    expect(screen.getByTestId("take-on-report-download-csv")).toBeInTheDocument();
    expect(screen.getByTestId("trust-reports-financial-visibility-required")).toBeInTheDocument();
  });

  it("hides the take-on reports card when there are none or the read fails", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST", "FINANCIAL_VISIBILITY"]));
    await renderPage();
    expect(screen.queryByTestId("trust-take-on-reports")).not.toBeInTheDocument();
    expect(mockFetchTrustAccounts).not.toHaveBeenCalled();
    cleanup();

    takeOnActions.listTakeOnReports.mockResolvedValue({ success: false, error: "Access denied" });
    await renderPage();
    expect(screen.queryByTestId("trust-take-on-reports")).not.toBeInTheDocument();
    expect(screen.getAllByTestId(/^report-card-/)).toHaveLength(9);
  });

  it("without VIEW_TRUST the page is not found and makes no request", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["FINANCIAL_VISIBILITY"]));

    await expect(TrustReportsPage({ params: Promise.resolve({ slug: "acme" }) })).rejects.toThrow(
      "NEXT_NOT_FOUND"
    );
    expect(mockGetReportDefinitions).not.toHaveBeenCalled();
  });

  it("an admin holds both capabilities implicitly and sees the cards", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps([], { isAdmin: true }));

    await renderPage();

    expect(mockGetReportDefinitions).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("report-card-section-35-data-pack")).toBeInTheDocument();
  });
});
