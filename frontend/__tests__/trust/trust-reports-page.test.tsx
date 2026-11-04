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

const TRUST_SLUGS = [
  "trust-receipts-payments",
  "client-trust-balances",
  "client-ledger-statement",
  "trust-reconciliation",
  "investment-register",
  "interest-allocation",
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
  });

  afterEach(() => cleanup());

  it("VIEW_TRUST + FINANCIAL_VISIBILITY lists the seven trust report cards", async () => {
    mockFetchMyCapabilities.mockResolvedValue(caps(["VIEW_TRUST", "FINANCIAL_VISIBILITY"]));

    await renderPage();

    expect(mockGetReportDefinitions).toHaveBeenCalledTimes(1);
    for (const slug of TRUST_SLUGS) {
      expect(screen.getByTestId(`report-card-${slug}`)).toBeInTheDocument();
    }
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
