import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TrustBalanceTrendChart } from "@/components/trust/trust-balance-trend-chart";
import type { TrustBalanceTrendResponse } from "@/lib/dashboard-types";

// happy-dom has no layout: stub recharts' SVG internals so the real TrendAreaChart → ChartCard
// (figure name, sr summary, "View as table") renders. next/dynamic is mocked globally
// (vitest.setup.ts: React.lazy) — the chart resolves asynchronously, hence findBy*.
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
    AreaChart: ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
      <div data-testid="area-chart" data-rows={JSON.stringify(data)}>
        {children}
      </div>
    ),
    Area: ({
      dataKey,
      stroke,
      strokeDasharray,
    }: {
      dataKey: string;
      stroke?: string;
      strokeDasharray?: string;
    }) => <div data-testid={`area-${dataKey}`} data-stroke={stroke} data-dash={strokeDasharray} />,
    CartesianGrid: () => null,
    XAxis: () => null,
    YAxis: () => null,
    LabelList: () => null,
    Tooltip: () => null,
    Legend: () => null,
  };
});

const MONTH_KEYS = [
  "2025-12",
  "2026-01",
  "2026-02",
  "2026-03",
  "2026-04",
  "2026-05",
  "2026-06",
  "2026-07",
  "2026-08",
  "2026-09",
  "2026-10",
  "2026-11",
];

/** 12 months, Dec 2025 → Nov 2026 (to date); cashbook rises 10 000 a month, ledgers match. */
function twelveMonths(): TrustBalanceTrendResponse {
  return {
    trustAccountId: "acc-1",
    accountName: "Main Trust Account",
    currency: "ZAR",
    asOf: "2026-11-13",
    openingCashbookBalance: 50000,
    openingClientLedgerTotal: 50000,
    months: MONTH_KEYS.map((month, i) => ({
      month,
      periodEnd: i === 11 ? "2026-11-13" : `${month}-28`,
      cashbookBalance: 60000 + i * 10000,
      clientLedgerTotal: i === 11 ? 170001.34 : 60000 + i * 10000,
      partial: i === 11,
    })),
    accounts: [{ id: "acc-1", accountName: "Main Trust Account", isPrimary: true }],
  };
}

function zeroMonths(): TrustBalanceTrendResponse {
  const trend = twelveMonths();
  return {
    ...trend,
    openingCashbookBalance: 0,
    openingClientLedgerTotal: 0,
    months: trend.months.map((p) => ({ ...p, cashbookBalance: 0, clientLedgerTotal: 0 })),
  };
}

describe("TrustBalanceTrendChart", () => {
  afterEach(() => cleanup());

  it("renders the 12-month series with a to-date last point and an sr summary", async () => {
    render(<TrustBalanceTrendChart trend={twelveMonths()} />);

    const figure = await screen.findByRole("figure", { name: "Trust balance trend" });
    expect(figure).toHaveTextContent("Month-end balances as currently recorded (ZAR)");

    const rows = JSON.parse(screen.getByTestId("area-chart").getAttribute("data-rows") ?? "[]");
    expect(rows).toHaveLength(12);
    expect(rows[0].month).toBe("Dec 2025");
    expect(rows[0].cashbook).toBe(60000);
    // The partial current month is drawn by the dashed companion series.
    expect(rows[11].cashbook).toBeNull();
    expect(rows[11].cashbook__partial).toBe(170000);
    expect(rows[11].ledger__partial).toBe(170001.34);
    expect(screen.getByTestId("area-cashbook__partial")).toHaveAttribute("data-dash", "4 4");
    expect(screen.getByTestId("area-ledger")).not.toHaveAttribute("data-dash");

    const summary = document.querySelector('[data-slot="chart-summary"]')?.textContent ?? "";
    expect(summary).toContain("Trust balance trend:");
    expect(summary).toContain("Cashbook from");
    expect(summary).toContain("Client ledgers");
    expect(summary).toContain("in Dec 2025");
    expect(summary).toContain("in Nov 2026 (to date)");
  });

  it("View as table lists all 12 months with the partial one labelled (to date)", async () => {
    const user = userEvent.setup();
    render(<TrustBalanceTrendChart trend={twelveMonths()} />);

    await user.click(await screen.findByRole("button", { name: "View as table" }));
    const table = screen.getByRole("table");
    expect(within(table).getByRole("columnheader", { name: "Month" })).toBeInTheDocument();
    expect(within(table).getAllByRole("rowheader")).toHaveLength(12);
    expect(
      within(table).getByRole("rowheader", { name: "Nov 2026 (to date)" })
    ).toBeInTheDocument();
    expect(within(table).getByRole("rowheader", { name: "Dec 2025" })).toBeInTheDocument();
  });

  // Zero balances do not mean "no postings" (a month can net to zero): the flat line is drawn.
  it("an all-zero trend renders the chart, not an empty state", async () => {
    render(<TrustBalanceTrendChart trend={zeroMonths()} />);

    const figure = await screen.findByRole("figure", { name: "Trust balance trend" });
    const rows = JSON.parse(
      (await screen.findByTestId("area-chart")).getAttribute("data-rows") ?? "[]"
    );
    expect(rows).toHaveLength(12);
    expect(rows[0].cashbook).toBe(0);
    expect(rows[0].ledger).toBe(0);
    expect(figure.querySelector('[data-slot="empty-state"]')).not.toBeInTheDocument();
    expect(within(figure).queryByText("Balance trend not available.")).not.toBeInTheDocument();
  });

  it("an absent read (403/404) renders the compact empty state", () => {
    render(<TrustBalanceTrendChart trend={null} />);

    const figure = screen.getByRole("figure", { name: "Trust balance trend" });
    expect(within(figure).getByText("Balance trend not available.")).toBeInTheDocument();
    expect(figure.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
      "data-variant",
      "compact"
    );
    expect(screen.queryByTestId("area-chart")).not.toBeInTheDocument();
  });

  it("a failed read renders the error line", () => {
    render(<TrustBalanceTrendChart trend={null} failed />);

    expect(screen.getByRole("status")).toHaveTextContent("Couldn't load the balance trend.");
    expect(screen.queryByTestId("area-chart")).not.toBeInTheDocument();
  });
});
