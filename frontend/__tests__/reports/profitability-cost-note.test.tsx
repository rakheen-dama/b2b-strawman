import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// Must mock server-only before importing modules that use it
vi.mock("server-only", () => ({}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({
    capabilities: ["FINANCIAL_VISIBILITY"],
    role: "Admin",
    isAdmin: true,
    isOwner: false,
  }),
}));

const mockApiGet = vi.fn();
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => mockApiGet(...args) },
}));

// 643B: the page also reads the billing trend through settle() — stub both server-only modules.
vi.mock("@/lib/api/reports", () => ({ getBillingTrend: vi.fn() }));
vi.mock("@/lib/settle", () => ({ settle: vi.fn().mockResolvedValue({ state: "absent" }) }));

const mockContentProps = vi.fn();
vi.mock("@/components/profitability/profitability-content", () => ({
  ProfitabilityContent: (props: Record<string, unknown>) => {
    mockContentProps(props);
    return <div data-testid="profitability-content-stub" />;
  },
}));

// happy-dom has no layout: render recharts' container as a plain div so the ready billing-trend
// card (next/dynamic → React.lazy, vitest.setup.ts) mounts; the SVG internals are not asserted.
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
    AreaChart: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="area-chart">{children}</div>
    ),
    Area: () => null,
    CartesianGrid: () => null,
    XAxis: () => null,
    YAxis: () => null,
    Tooltip: () => null,
    Legend: () => null,
  };
});

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// Must import page AFTER vi.mock declarations
import ReportsProfitabilityPage from "@/app/(app)/org/[slug]/reports/profitability/page";
import { settle } from "@/lib/settle";
import type { BillingTrendResponse } from "@/lib/types";

const NOTE = "Costs reflect logged expenses only; disbursements are shown as fee-note lines";

async function renderPage() {
  const result = await ReportsProfitabilityPage({ params: Promise.resolve({ slug: "acme" }) });
  render(result);
}

afterEach(() => {
  cleanup();
  mockApiGet.mockReset();
  mockContentProps.mockReset();
});

// Phase 87 ADR-340 / Open Question #1 (615E): the cost leg still reads Expense until the
// follow-up lands, so the page says so in one line.
describe("Profitability page cost note (615E)", () => {
  it("shows the note above the data state", async () => {
    mockApiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith("/api/reports/utilization")
          ? { from: "2026-10-01", to: "2026-10-22", members: [{ memberId: "m1" }] }
          : { matters: [] }
      )
    );

    await renderPage();

    expect(screen.getByTestId("profitability-content-stub")).toBeInTheDocument();
    expect(screen.getByTestId("profitability-cost-note")).toHaveTextContent(NOTE);
  });

  it("shows the note in the empty state too", async () => {
    mockApiGet.mockRejectedValue(new Error("backend down"));

    await renderPage();

    expect(screen.queryByTestId("profitability-content-stub")).not.toBeInTheDocument();
    expect(screen.getByTestId("profitability-cost-note")).toHaveTextContent(NOTE);
  });
});

/** 12 months of history, Dec 2025 → Nov 2026 (to date). */
const TREND: BillingTrendResponse = {
  currency: "ZAR",
  from: "2025-12",
  to: "2026-11",
  asOf: "2026-11-13",
  months: Array.from({ length: 12 }, (_, i) => ({
    month: `${i === 0 ? 2025 : 2026}-${String(i === 0 ? 12 : i).padStart(2, "0")}`,
    billed: 10000 + i * 1000,
    billedCount: 3,
    collected: 8000 + i * 500,
    collectedCount: 2,
    partial: i === 11,
  })),
};

// 643B (CodeRabbit): the billing trend covers the last 12 SA months, independent of the page's
// current-month range, so a firm with no activity this month still sees its history.
describe("Profitability page billing trend (643B)", () => {
  it("renders the billing-trend card alongside the current-month empty state", async () => {
    vi.mocked(settle).mockResolvedValueOnce({ state: "ok", data: TREND });
    mockApiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith("/api/reports/utilization")
          ? { from: "2026-11-01", to: "2026-11-13", members: [] }
          : { matters: [] }
      )
    );

    await renderPage();

    expect(screen.getByText("No profitability data yet")).toBeInTheDocument();
    expect(screen.queryByTestId("profitability-content-stub")).not.toBeInTheDocument();
    const trend = await screen.findByTestId("billing-trend");
    expect(trend).toHaveAccessibleName("Fees Billed vs Collected");
    expect(trend).toHaveTextContent("ZAR · VAT inclusive · last 12 months");
    // The trend stays the top card, above the empty state.
    expect(
      trend.compareDocumentPosition(screen.getByText("No profitability data yet")) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
  });

  it("renders the billing-trend card above the data state and passes its currency down", async () => {
    vi.mocked(settle).mockResolvedValueOnce({ state: "ok", data: TREND });
    mockApiGet.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith("/api/reports/utilization")
          ? { from: "2026-11-01", to: "2026-11-13", members: [{ memberId: "m1" }] }
          : { matters: [] }
      )
    );

    await renderPage();

    const trend = await screen.findByTestId("billing-trend");
    const content = screen.getByTestId("profitability-content-stub");
    expect(trend.compareDocumentPosition(content) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(mockContentProps).toHaveBeenCalledWith(expect.objectContaining({ billingTrend: TREND }));
    expect(mockContentProps.mock.calls[0][0]).not.toHaveProperty("billingTrendFailed");
  });

  it("a failed read shows the card's error line in the empty state", async () => {
    vi.mocked(settle).mockResolvedValueOnce({ state: "error" });
    mockApiGet.mockRejectedValue(new Error("backend down"));

    await renderPage();

    expect(screen.getByText("No profitability data yet")).toBeInTheDocument();
    expect(screen.getByTestId("billing-trend")).toHaveTextContent(
      "Couldn't load the billing trend."
    );
  });
});
