import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UnbilledWipWidget } from "@/components/dashboard/unbilled-wip-widget";
import { formatChartValue } from "@/lib/chart-format";
import type { UnbilledWipResponse } from "@/lib/dashboard-types";

// happy-dom has no layout: stub recharts' SVG internals so the real BarChartPreset → ChartCard
// (figure name, sr summary, "View as table") renders. next/dynamic is mocked globally
// (vitest.setup.ts: React.lazy) — the chart resolves asynchronously, hence findBy*.
vi.mock("recharts", async (importOriginal) => {
  const actual = await importOriginal<typeof import("recharts")>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
    BarChart: ({
      children,
      data,
      layout,
    }: {
      children: React.ReactNode;
      data: unknown[];
      layout?: string;
    }) => (
      <div data-testid="bar-chart" data-rows={JSON.stringify(data)} data-layout={layout}>
        {children}
      </div>
    ),
    Bar: ({ dataKey, fill }: { dataKey: string; fill?: string }) => (
      <div data-testid={`bar-${dataKey}`} data-fill={fill} />
    ),
    CartesianGrid: () => null,
    XAxis: () => null,
    YAxis: () => null,
    LabelList: () => null,
    ReferenceLine: () => null,
    Tooltip: () => null,
    Legend: () => null,
  };
});

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...props
  }: {
    href: string;
    children: React.ReactNode;
    [key: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const FULL: UnbilledWipResponse = {
  asOf: "2026-10-21",
  currency: "ZAR",
  totalMinutes: 900,
  totalValue: 27500,
  unbilledDisbursementTotal: 1250.5,
  byFeeEarner: [
    {
      memberId: "m1",
      memberName: "Thandi Nkosi",
      minutes: 600,
      value: 20000,
      oldestEntryDate: "2026-09-02",
    },
    {
      memberId: "m2",
      memberName: "Pieter van Wyk",
      minutes: 300,
      value: 7500,
      oldestEntryDate: "2026-10-01",
    },
  ],
  redacted: false,
};

/** The fee earner's redacted view: own row, minutes only, every value null. */
const REDACTED: UnbilledWipResponse = {
  asOf: "2026-10-21",
  currency: "ZAR",
  totalMinutes: 300,
  totalValue: null,
  unbilledDisbursementTotal: null,
  byFeeEarner: [
    {
      memberId: "m2",
      memberName: "Pieter van Wyk",
      minutes: 300,
      value: null,
      oldestEntryDate: "2026-10-01",
    },
  ],
  redacted: true,
};

describe("UnbilledWipWidget", () => {
  afterEach(() => cleanup());

  it("full view: value headline, disbursement line, unbilled link — one 'Unbilled WIP' title", () => {
    render(<UnbilledWipWidget slug="acme" data={FULL} personal={false} showLink />);

    const card = screen.getByTestId("unbilled-wip-widget");
    expect(card).toHaveAttribute("data-variant", "firm");
    expect(within(card).getByText("Unbilled WIP")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-wip-headline").textContent).toMatch(/27\s?500/);
    expect(within(card).getByText("15h of unbilled time")).toBeInTheDocument();
    expect(screen.getByTestId("unbilled-disbursements").textContent).toContain(
      "Disbursements not yet billed"
    );
    expect(screen.getByRole("link", { name: "Open unbilled work" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/unbilled"
    );
  });

  it("≥ 2 fee earners: a ranked bar figure named after the card title, with its sr summary", async () => {
    render(<UnbilledWipWidget slug="acme" data={FULL} personal={false} showLink />);

    const card = screen.getByTestId("unbilled-wip-widget");
    const figure = await within(card).findByRole("figure", { name: "Unbilled WIP" });
    expect(figure.querySelector('[data-slot="chart-summary"]')?.textContent).toBe(
      `Unbilled WIP: 2 bars; highest Thandi Nkosi ${formatChartValue(20000, "currency", "ZAR")}, ` +
        `lowest Pieter van Wyk ${formatChartValue(7500, "currency", "ZAR")}.`
    );
    // Horizontal bars (recharts' "vertical" layout), value desc as the endpoint sent them.
    const chart = within(figure).getByTestId("bar-chart");
    expect(chart).toHaveAttribute("data-layout", "vertical");
    expect(JSON.parse(chart.getAttribute("data-rows") ?? "[]")).toEqual([
      { feeEarner: "Thandi Nkosi", value: 20000 },
      { feeEarner: "Pieter van Wyk", value: 7500 },
    ]);
    // The chart replaces the per-earner list; the title stays a single text node (no inner
    // header) and the figure draws no frame of its own.
    expect(within(card).queryByRole("list", { name: "Unbilled WIP by fee earner" })).toBeNull();
    expect(within(card).getAllByText("Unbilled WIP")).toHaveLength(1);
    expect(figure.querySelector('[data-slot="card-header"]')).toBeNull();

    // "View as table" mounts exactly one table, on demand.
    expect(within(card).queryByRole("table")).toBeNull();
    await userEvent.click(within(figure).getByRole("button", { name: "View as table" }));
    const table = within(card).getByRole("table");
    expect(within(card).getAllByRole("table")).toHaveLength(1);
    expect(within(table).getByRole("columnheader", { name: "Fee earner" })).toBeInTheDocument();
    expect(within(table).getByRole("rowheader", { name: "Thandi Nkosi" })).toBeInTheDocument();
  });

  it("one fee earner: a text row, not a one-bar chart", async () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{ ...FULL, totalValue: 20000, byFeeEarner: [FULL.byFeeEarner[0]] }}
        personal={false}
        showLink
      />
    );

    const list = screen.getByRole("list", { name: "Unbilled WIP by fee earner" });
    expect(within(list).getByText("Thandi Nkosi")).toBeInTheDocument();
    expect(within(list).getByText(/20\s?000/)).toBeInTheDocument();
    expect(screen.queryByRole("figure")).toBeNull();
  });

  it("firm view with nothing unbilled: compact empty state, copy unchanged", () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{
          ...FULL,
          totalMinutes: 0,
          totalValue: 0,
          unbilledDisbursementTotal: null,
          byFeeEarner: [],
        }}
        personal={false}
        showLink
      />
    );

    const title = screen.getByText("Nothing unbilled.");
    expect(title.closest('[data-slot="empty-state"]')).toHaveAttribute("data-variant", "compact");
    expect(
      screen.getByText("Billable time appears here until it is on a fee note.")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("unbilled-wip-headline")).toBeNull();
  });

  it("hides the disbursement line when unbilledDisbursementTotal is null (module off)", () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{ ...FULL, unbilledDisbursementTotal: null }}
        personal={false}
        showLink
      />
    );
    expect(screen.queryByTestId("unbilled-disbursements")).not.toBeInTheDocument();
    expect(screen.queryByText("Disbursements not yet billed")).not.toBeInTheDocument();
  });

  it("no unbilled link without INVOICING", () => {
    render(<UnbilledWipWidget slug="acme" data={FULL} personal={false} showLink={false} />);
    expect(screen.queryByRole("link", { name: "Open unbilled work" })).not.toBeInTheDocument();
  });

  it("redacted: 'My unbilled time' shows own hours only — no values, no firm total", () => {
    render(<UnbilledWipWidget slug="acme" data={REDACTED} personal showLink />);

    const card = screen.getByTestId("unbilled-wip-widget");
    expect(card).toHaveAttribute("data-variant", "personal");
    // One bar is not a chart: the caller's own hours are a StatCard, never a figure.
    const tile = within(card).getByRole("group", { name: "My unbilled time" });
    expect(within(tile).getByTestId("unbilled-wip-headline")).toHaveTextContent("5h");
    expect(within(tile).getByText(/Billable time not yet on a fee note/)).toBeInTheDocument();
    expect(within(card).queryByRole("figure")).toBeNull();
    expect(card.textContent).not.toMatch(/R\s?\d/);
    expect(within(card).queryByText("Unbilled WIP")).not.toBeInTheDocument();
    expect(screen.queryByTestId("unbilled-disbursements")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open unbilled work" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open timesheet" })).toHaveAttribute(
      "href",
      "/org/acme/my-work/timesheet"
    );
  });

  it("redacted with no unbilled time: says what to do next", () => {
    render(
      <UnbilledWipWidget
        slug="acme"
        data={{ ...REDACTED, totalMinutes: 0, byFeeEarner: [] }}
        personal
        showLink={false}
      />
    );
    const tile = screen.getByRole("group", { name: "My unbilled time" });
    expect(within(tile).getByText(/No unbilled time/)).toBeInTheDocument();
    expect(tile.querySelector('[data-slot="stat-card-value"]')).toHaveTextContent("0m");
  });

  it("error: quiet error line, titled for the caller's audience", () => {
    render(<UnbilledWipWidget slug="acme" data={null} personal showLink={false} />);
    expect(screen.getByText("My unbilled time")).toBeInTheDocument();
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
    expect(screen.queryByRole("figure")).toBeNull();
  });

  it("firm error: quiet error line under the firm title", () => {
    render(<UnbilledWipWidget slug="acme" data={null} personal={false} showLink />);
    expect(screen.getByTestId("unbilled-wip-widget")).toHaveAttribute("data-variant", "firm");
    expect(
      screen.getByText("Unbilled WIP could not be loaded. Refresh to try again.")
    ).toBeInTheDocument();
  });
});
