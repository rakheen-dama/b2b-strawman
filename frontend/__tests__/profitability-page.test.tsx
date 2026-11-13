import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import type { UtilizationResponse, OrgProfitabilityResponse } from "@/lib/types";
import { BillingTrendChart } from "@/components/profitability/billing-trend-chart";
import { UTILIZATION_NOTE } from "@/components/profitability/utilization-chart";
import type { BillingTrendResponse } from "@/lib/types";

// happy-dom has no layout: stub recharts' SVG internals so the real presets → ChartCard (figure
// name, sr summary, "View as table") render. next/dynamic is mocked globally (vitest.setup.ts:
// React.lazy) — a chart resolves asynchronously the first time, hence findBy*.
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
    AreaChart: ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
      <div data-testid="area-chart" data-rows={JSON.stringify(data)}>
        {children}
      </div>
    ),
    Area: ({ dataKey, strokeDasharray }: { dataKey: string; strokeDasharray?: string }) => (
      <div data-testid={`area-${dataKey}`} data-dash={strokeDasharray} />
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

// DataTable (643C) calls useRouter() for its optional row click.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/org/acme/reports/profitability",
}));

// Mock server actions
const mockGetUtilization = vi.fn();
const mockGetOrgProfitability = vi.fn();
const mockExportProfitabilityCsvAction = vi.fn();

vi.mock("@/app/(app)/org/[slug]/profitability/actions", () => ({
  getUtilization: (...args: unknown[]) => mockGetUtilization(...args),
  getOrgProfitability: (...args: unknown[]) => mockGetOrgProfitability(...args),
  exportProfitabilityCsvAction: (...args: unknown[]) => mockExportProfitabilityCsvAction(...args),
}));

function makeUtilization(overrides: Partial<UtilizationResponse> = {}): UtilizationResponse {
  return {
    from: "2026-02-01",
    to: "2026-02-14",
    members: [
      {
        memberId: "m1",
        memberName: "Alice Smith",
        totalHours: 40,
        billableHours: 32,
        nonBillableHours: 8,
        utilizationPercent: 80,
        currencies: [{ currency: "USD", billableValue: 4800, costValue: 3200 }],
      },
      {
        memberId: "m2",
        memberName: "Bob Jones",
        totalHours: 35,
        billableHours: 14,
        nonBillableHours: 21,
        utilizationPercent: 40,
        currencies: [],
      },
    ],
    ...overrides,
  };
}

function makeProfitability(
  overrides: Partial<OrgProfitabilityResponse> = {}
): OrgProfitabilityResponse {
  return {
    matters: [
      {
        matterId: "p1",
        matterName: "Alpha Project",
        clientName: "Acme Corp",
        currency: "USD",
        billableHours: 100,
        billableValue: 15000,
        costValue: 9000,
        margin: 6000,
        marginPercent: 40,
      },
      {
        matterId: "p2",
        matterName: "Beta Project",
        clientName: null,
        currency: "EUR",
        billableHours: 50,
        billableValue: 7500,
        costValue: null,
        margin: null,
        marginPercent: null,
      },
    ],
    ...overrides,
  };
}

describe("Profitability Page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  describe("UtilizationTable", () => {
    it("renders utilization table with member data", () => {
      const data = makeUtilization();
      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      expect(screen.getByText("Team Utilization")).toBeInTheDocument();
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
      expect(screen.getByText("32.0h")).toBeInTheDocument();
      expect(screen.getByText("80.0%")).toBeInTheDocument();
      expect(screen.getByText("40.0%")).toBeInTheDocument();
    });

    it("renders empty state when no utilization data", () => {
      const data = makeUtilization({ members: [] });
      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      expect(screen.getByText("No utilization data for this period")).toBeInTheDocument();
    });

    it("sorts by utilization when clicking column header", async () => {
      const user = userEvent.setup();
      const data = makeUtilization();
      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      // Default sort is utilization desc, so Alice (80%) comes first
      const rows = screen.getAllByRole("row");
      // Row 0 is header, row 1 is first data row
      expect(within(rows[1]).getByText("Alice Smith")).toBeInTheDocument();

      // Click utilization header to toggle to asc
      const utilizationHeader = screen.getByRole("button", {
        name: /Utilization %/i,
      });
      await user.click(utilizationHeader);

      // Now Bob (40%) should come first
      const updatedRows = screen.getAllByRole("row");
      expect(within(updatedRows[1]).getByText("Bob Jones")).toBeInTheDocument();
    });

    it("refetches data when date range changes", async () => {
      const user = userEvent.setup();
      const data = makeUtilization();
      const newData = makeUtilization({
        members: [
          {
            memberId: "m3",
            memberName: "Carol White",
            totalHours: 20,
            billableHours: 15,
            nonBillableHours: 5,
            utilizationPercent: 75,
            currencies: [],
          },
        ],
      });

      mockGetUtilization.mockResolvedValue({ data: newData });

      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      // Change the "from" date
      const fromInput = screen.getByLabelText("From");
      await user.clear(fromInput);
      await user.type(fromInput, "2026-01-01");

      expect(mockGetUtilization).toHaveBeenCalledWith("2026-01-01", "2026-02-14");
    });

    it("renders the kit column headers by name, with aria-sort on the default sort column", () => {
      render(
        <UtilizationTable
          initialData={makeUtilization()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />
      );

      for (const name of ["Name", "Total Hours", "Billable", "Non-Billable", "Utilization %"]) {
        expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
      }
      // Default sort: utilization desc.
      expect(screen.getByRole("columnheader", { name: "Utilization %" })).toHaveAttribute(
        "aria-sort",
        "descending"
      );
      expect(screen.getByRole("columnheader", { name: "Name" })).toHaveAttribute(
        "aria-sort",
        "none"
      );
      // Headers sort in local state: buttons, never links.
      expect(screen.queryAllByRole("link")).toHaveLength(0);
      expect(screen.getAllByRole("table")).toHaveLength(1);
      // The name cell holds only the member name (e2e filters rows by an exact cell name).
      expect(screen.getByRole("cell", { name: "Alice Smith" })).toBeInTheDocument();
    });

    it("clicking another header sorts by it descending; clicking again flips to ascending", async () => {
      const user = userEvent.setup();
      render(
        <UtilizationTable
          initialData={makeUtilization()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />
      );
      const firstRowName = () => within(screen.getAllByRole("row")[1]).getAllByRole("cell")[1];

      await user.click(screen.getByRole("button", { name: "Non-Billable" }));
      expect(screen.getByRole("columnheader", { name: "Non-Billable" })).toHaveAttribute(
        "aria-sort",
        "descending"
      );
      expect(screen.getByRole("columnheader", { name: "Utilization %" })).toHaveAttribute(
        "aria-sort",
        "none"
      );
      expect(firstRowName()).toHaveTextContent("Bob Jones"); // 21h non-billable

      await user.click(screen.getByRole("button", { name: "Non-Billable" }));
      expect(screen.getByRole("columnheader", { name: "Non-Billable" })).toHaveAttribute(
        "aria-sort",
        "ascending"
      );
      expect(firstRowName()).toHaveTextContent("Alice Smith");
    });

    it("gives the expand toggle the kit's focus-visible ring, not the browser outline", () => {
      render(
        <UtilizationTable
          initialData={makeUtilization()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />
      );
      expect(screen.getByRole("button", { name: "Expand currency breakdown" })).toHaveClass(
        "outline-none",
        "focus-visible:ring-[3px]",
        "focus-visible:ring-ring/50"
      );
    });

    it("expands and collapses a member's currency breakdown, which survives a sort", async () => {
      const user = userEvent.setup();
      render(
        <UtilizationTable
          initialData={makeUtilization()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
        />
      );

      // Only Alice has currencies: one toggle, no breakdown row by default.
      expect(screen.getAllByRole("button", { name: "Expand currency breakdown" })).toHaveLength(1);
      expect(screen.getAllByRole("row")).toHaveLength(3);
      expect(screen.queryByText("USD:")).not.toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "Expand currency breakdown" }));
      const collapse = screen.getByRole("button", { name: "Collapse currency breakdown" });
      expect(collapse).toHaveFocus();
      expect(screen.getAllByRole("row")).toHaveLength(4);
      const breakdown = screen.getByText("USD:").closest("tr")!;
      expect(breakdown).toHaveTextContent(/Billable US\$4\s800,00/);
      expect(breakdown).toHaveTextContent(/Cost US\$3\s200,00/);
      // Directly under Alice's row.
      const rows = screen.getAllByRole("row");
      expect(
        within(rows[rows.indexOf(breakdown) - 1]).getByText("Alice Smith")
      ).toBeInTheDocument();

      // Sort so Alice moves to the bottom: the breakdown stays open and follows her row.
      await user.click(screen.getByRole("button", { name: /Utilization %/ }));
      expect(
        screen.getByRole("button", { name: "Collapse currency breakdown" })
      ).toBeInTheDocument();
      const afterSort = screen.getAllByRole("row");
      expect(afterSort).toHaveLength(4);
      expect(within(afterSort[1]).getByText("Bob Jones")).toBeInTheDocument();
      expect(within(afterSort[2]).getByText("Alice Smith")).toBeInTheDocument();
      expect(afterSort[3]).toHaveTextContent("USD:");

      await user.click(screen.getByRole("button", { name: "Collapse currency breakdown" }));
      expect(screen.queryByText("USD:")).not.toBeInTheDocument();
      expect(screen.getAllByRole("row")).toHaveLength(3);
    });
  });

  describe("ProjectProfitabilityTable", () => {
    it("renders project profitability table with project data", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      expect(screen.getByText("Matter Profitability")).toBeInTheDocument();
      expect(screen.getByText("Alpha Project")).toBeInTheDocument();
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
      expect(screen.getByText(/US\$15\s000,00/)).toBeInTheDocument();
      expect(screen.getByText(/US\$6\s000,00/)).toBeInTheDocument();
    });

    it("renders Revenue, Cost and Margin in the inherited table font, not the kit's mono default (643C OBS D1, re-OBS D2b)", () => {
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      const revenue = screen.getByText(/US\$15\s000,00/);
      const cost = screen.getByText(/US\$9\s000,00/);
      const margin = screen.getByText(/US\$6\s000,00/);
      for (const money of [revenue, cost, margin]) {
        expect(money).toHaveClass("font-[family-name:inherit]", "tabular-nums");
        expect(money).not.toHaveClass("font-mono");
        expect(money).not.toHaveClass("font-sans");
      }
      expect(margin).toHaveClass("text-success-foreground");
      // A missing cost stays the plain "N/A" text.
      const betaRow = screen.getByText("Beta Project").closest("tr")!;
      expect(within(betaRow).getAllByRole("cell")[5]).toHaveTextContent(/^N\/A$/);
    });

    it("shows N/A badge when margin data is missing", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      // Beta Project has null margin/marginPercent
      const badges = screen.getAllByText("N/A");
      expect(badges.length).toBeGreaterThanOrEqual(2); // margin + margin %
    });

    it("renders empty state when no project data", () => {
      const data = makeProfitability({ matters: [] });
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      expect(screen.getByText("No matter profitability data for this period")).toBeInTheDocument();
    });

    it("shows dash for projects without a customer", () => {
      const data = makeProfitability();
      render(
        <ProjectProfitabilityTable
          initialData={data}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      // Beta Project has no customer
      expect(screen.getByText("\u2014")).toBeInTheDocument();
    });

    it("renders the kit column headers by name, with aria-sort on Margin by default", () => {
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      for (const name of [
        "Matter",
        "Client",
        "Currency",
        "Billable Hours",
        "Revenue",
        "Cost",
        "Margin",
        "Margin %",
      ]) {
        expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
      }
      expect(screen.getByRole("columnheader", { name: "Margin" })).toHaveAttribute(
        "aria-sort",
        "descending"
      );
      // Currency is not sortable (as before).
      expect(screen.getByRole("columnheader", { name: "Currency" })).not.toHaveAttribute(
        "aria-sort"
      );
      expect(
        within(screen.getByRole("columnheader", { name: "Currency" })).queryByRole("button")
      ).toBeNull();
      expect(screen.getAllByRole("table")).toHaveLength(1);
      // Hours keep the dot decimal + "h" (e2e /\d+\.\dh/).
      expect(screen.getByText("100.0h")).toBeInTheDocument();
      // A missing cost reads "N/A" text.
      const beta = screen.getByText("Beta Project").closest("tr")!;
      expect(within(beta).getAllByRole("cell")[5]).toHaveTextContent("N/A");
    });

    it("sorts by a header button: Revenue descending, then ascending", async () => {
      const user = userEvent.setup();
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability({
            matters: [
              { ...makeProfitability().matters[0], billableValue: 5000 },
              makeProfitability().matters[1],
            ],
          })}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );
      const firstMatter = () => within(screen.getAllByRole("row")[1]).getAllByRole("cell")[0];
      // Default margin desc: Alpha (6000) above Beta (null → 0).
      expect(firstMatter()).toHaveTextContent("Alpha Project");

      await user.click(screen.getByRole("button", { name: "Revenue" }));
      expect(screen.getByRole("columnheader", { name: "Revenue" })).toHaveAttribute(
        "aria-sort",
        "descending"
      );
      expect(screen.getByRole("columnheader", { name: "Margin" })).toHaveAttribute(
        "aria-sort",
        "none"
      );
      expect(firstMatter()).toHaveTextContent("Beta Project"); // 7500 > 5000

      await user.click(screen.getByRole("button", { name: "Revenue" }));
      expect(screen.getByRole("columnheader", { name: "Revenue" })).toHaveAttribute(
        "aria-sort",
        "ascending"
      );
      expect(firstMatter()).toHaveTextContent("Alpha Project");
    });

    it("keeps profitability-export-csv as the card's only Export CSV button", () => {
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      const exports = screen.getAllByRole("button", { name: /Export CSV/ });
      expect(exports).toHaveLength(1);
      expect(exports[0]).toHaveAttribute("data-testid", "profitability-export-csv");
      // No kit "Columns" menu either (columnVisibility off).
      expect(screen.queryByRole("button", { name: "Columns" })).not.toBeInTheDocument();
    });

    describe("Export CSV", () => {
      let originalCreateObjectURL: typeof URL.createObjectURL | undefined;
      let originalRevokeObjectURL: typeof URL.revokeObjectURL | undefined;
      let originalAnchorClick: HTMLAnchorElement["click"] | undefined;
      let clickedDownloads: string[];

      beforeEach(() => {
        originalCreateObjectURL = global.URL.createObjectURL;
        originalRevokeObjectURL = global.URL.revokeObjectURL;
        originalAnchorClick = HTMLAnchorElement.prototype.click;
        clickedDownloads = [];
        // happy-dom doesn't ship URL.createObjectURL — stub.
        global.URL.createObjectURL = vi.fn(() => "blob:mock");
        global.URL.revokeObjectURL = vi.fn();
        // Stub anchor.click() so the test doesn't navigate; record the download name.
        const anchorProto = HTMLAnchorElement.prototype as unknown as {
          click: (this: HTMLAnchorElement) => void;
        };
        anchorProto.click = vi.fn(function (this: HTMLAnchorElement) {
          clickedDownloads.push(this.download);
        });
      });

      afterEach(() => {
        if (originalCreateObjectURL) global.URL.createObjectURL = originalCreateObjectURL;
        if (originalRevokeObjectURL) global.URL.revokeObjectURL = originalRevokeObjectURL;
        if (originalAnchorClick) HTMLAnchorElement.prototype.click = originalAnchorClick;
      });

      it("exports the Matter card's current range and downloads profitability-<from>-to-<to>.csv", async () => {
        const user = userEvent.setup();
        mockExportProfitabilityCsvAction.mockResolvedValue({
          data: "Matter,Client\r\nAlpha Project,Acme Corp\r\n",
        });
        render(
          <ProjectProfitabilityTable
            initialData={makeProfitability()}
            initialFrom="2026-02-01"
            initialTo="2026-02-14"
            defaultCurrency={null}
          />
        );

        const button = screen.getByTestId("profitability-export-csv");
        expect(button).toHaveTextContent("Export CSV");
        await user.click(button);

        expect(mockExportProfitabilityCsvAction).toHaveBeenCalledWith("2026-02-01", "2026-02-14");
        expect(global.URL.createObjectURL).toHaveBeenCalledTimes(1);
        expect(clickedDownloads).toEqual(["profitability-2026-02-01-to-2026-02-14.csv"]);
        expect(global.URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock");
      });

      it("follows the card's From date after it changes", async () => {
        const user = userEvent.setup();
        mockGetOrgProfitability.mockResolvedValue({ data: makeProfitability() });
        mockExportProfitabilityCsvAction.mockResolvedValue({ data: "Matter\r\n" });
        render(
          <ProjectProfitabilityTable
            initialData={makeProfitability()}
            initialFrom="2026-02-01"
            initialTo="2026-02-14"
            defaultCurrency={null}
          />
        );

        const fromInput = screen.getByLabelText("From");
        await user.clear(fromInput);
        await user.type(fromInput, "2026-01-01");
        await user.click(screen.getByTestId("profitability-export-csv"));

        expect(mockExportProfitabilityCsvAction).toHaveBeenLastCalledWith(
          "2026-01-01",
          "2026-02-14"
        );
        expect(clickedDownloads.at(-1)).toBe("profitability-2026-01-01-to-2026-02-14.csv");
      });

      it("falls back to profitability.csv when a date is empty", async () => {
        const user = userEvent.setup();
        mockGetOrgProfitability.mockResolvedValue({ data: makeProfitability() });
        mockExportProfitabilityCsvAction.mockResolvedValue({ data: "Matter\r\n" });
        render(
          <ProjectProfitabilityTable
            initialData={makeProfitability()}
            initialFrom="2026-02-01"
            initialTo="2026-02-14"
            defaultCurrency={null}
          />
        );

        await user.clear(screen.getByLabelText("To"));
        await user.click(screen.getByTestId("profitability-export-csv"));

        expect(mockExportProfitabilityCsvAction).toHaveBeenLastCalledWith("2026-02-01", "");
        expect(clickedDownloads.at(-1)).toBe("profitability.csv");
      });

      it("shows the export error inline and downloads nothing", async () => {
        const user = userEvent.setup();
        mockExportProfitabilityCsvAction.mockResolvedValue({
          data: null,
          error: "Insufficient permissions",
        });
        render(
          <ProjectProfitabilityTable
            initialData={makeProfitability()}
            initialFrom="2026-02-01"
            initialTo="2026-02-14"
            defaultCurrency={null}
          />
        );

        await user.click(screen.getByTestId("profitability-export-csv"));

        expect(await screen.findByText("Insufficient permissions")).toBeInTheDocument();
        expect(global.URL.createObjectURL).not.toHaveBeenCalled();
        expect(clickedDownloads).toEqual([]);
        expect(screen.getByTestId("profitability-export-csv")).not.toBeDisabled();
      });
    });
  });
});

const TREND_MONTHS = [
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

/** 12 months, Dec 2025 → Nov 2026 (to date). */
function makeBillingTrend(): BillingTrendResponse {
  return {
    currency: "ZAR",
    from: "2025-12",
    to: "2026-11",
    asOf: "2026-11-13",
    months: TREND_MONTHS.map((month, i) => ({
      month,
      billed: 10000 + i * 1000,
      billedCount: 3,
      collected: 8000 + i * 500,
      collectedCount: 2,
      partial: i === 11,
    })),
  };
}

describe("Profitability charts (643B)", () => {
  afterEach(() => {
    cleanup();
  });

  describe("Fees Billed vs Collected", () => {
    it("renders 12 months in the default currency with a to-date last point", async () => {
      render(<BillingTrendChart trend={makeBillingTrend()} failed={false} />);

      const figure = await screen.findByRole("figure", { name: "Fees Billed vs Collected" });
      expect(figure).toHaveTextContent("ZAR · VAT inclusive · last 12 months");
      expect(figure).toHaveTextContent("independent of the date ranges below");
      expect(figure).toHaveAttribute("data-testid", "billing-trend");

      const rows = JSON.parse(screen.getByTestId("area-chart").getAttribute("data-rows") ?? "[]");
      expect(rows).toHaveLength(12);
      expect(rows[0].month).toBe("Dec 2025");
      expect(rows[0].billed).toBe(10000);
      expect(rows[0].collected).toBe(8000);
      // The partial current month is drawn by the dashed companion series.
      expect(rows[11].billed__partial).toBe(21000);
      expect(rows[11].collected__partial).toBe(13500);
      expect(screen.getByTestId("area-billed__partial")).toHaveAttribute("data-dash", "4 4");
    });

    it("an absent read (403/404) renders the compact empty state, not an alert", () => {
      render(<BillingTrendChart trend={null} failed={false} />);

      const figure = screen.getByRole("figure", { name: "Fees Billed vs Collected" });
      expect(within(figure).getByText("Billing trend not available.")).toBeInTheDocument();
      expect(figure.querySelector('[data-slot="empty-state"]')).toHaveAttribute(
        "data-variant",
        "compact"
      );
      expect(screen.queryByTestId("area-chart")).not.toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("a failed read renders the error line", () => {
      render(<BillingTrendChart trend={null} failed />);

      expect(screen.getByRole("status")).toHaveTextContent("Couldn't load the billing trend.");
      expect(screen.queryByTestId("area-chart")).not.toBeInTheDocument();
    });
  });

  describe("Team Utilization chart", () => {
    it("draws one bar per fee earner, highest first, above the table", async () => {
      const data = makeUtilization({
        members: [{ ...makeUtilization().members[1] }, { ...makeUtilization().members[0] }],
      });
      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      const figure = await screen.findByRole("figure", { name: "Team Utilization" });
      const rows = JSON.parse(
        within(figure).getByTestId("bar-chart").getAttribute("data-rows") ?? "[]"
      );
      expect(rows.map((r: { feeEarner: string }) => r.feeEarner)).toEqual([
        "Alice Smith",
        "Bob Jones",
      ]);
      expect(rows[0].utilization).toBe(80);
      // The kit's table mounts only on "View as table": the card keeps exactly its own table.
      expect(screen.getAllByRole("table")).toHaveLength(1);
      // The chart sits above the table.
      expect(
        figure.compareDocumentPosition(screen.getByRole("table")) & Node.DOCUMENT_POSITION_FOLLOWING
      ).toBeTruthy();
      expect(screen.queryByText(UTILIZATION_NOTE)).not.toBeInTheDocument();
    });

    it("one fee earner → the note and the table, no chart", () => {
      const data = makeUtilization({ members: [makeUtilization().members[0]] });
      render(
        <UtilizationTable initialData={data} initialFrom="2026-02-01" initialTo="2026-02-14" />
      );

      expect(screen.getByText(UTILIZATION_NOTE)).toBeInTheDocument();
      expect(screen.queryByRole("figure")).not.toBeInTheDocument();
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
      expect(screen.getAllByRole("table")).toHaveLength(1);
    });
  });

  describe("Matter Profitability ranking", () => {
    it("ranks default-currency matters by margin and counts the others in a hint", async () => {
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency="USD"
        />
      );

      const figure = await screen.findByRole("figure", { name: "Matter Profitability" });
      const rows = JSON.parse(
        within(figure).getByTestId("bar-chart").getAttribute("data-rows") ?? "[]"
      );
      expect(rows).toHaveLength(1);
      expect(rows[0]).toMatchObject({ matter: "Alpha Project", margin: 6000 });
      expect(screen.getByText("Excludes 1 matter in other currencies")).toBeInTheDocument();
      expect(screen.getAllByRole("table")).toHaveLength(1);
    });

    it("ranks losses below gains and keeps at most 10 bars", async () => {
      const base = makeProfitability().matters[0];
      const matters = Array.from({ length: 12 }, (_, i) => ({
        ...base,
        matterId: `m${i}`,
        matterName: `Matter ${i}`,
        margin: (i - 3) * 1000,
      }));
      render(
        <ProjectProfitabilityTable
          initialData={{ matters }}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency="USD"
        />
      );

      const figure = await screen.findByRole("figure", { name: "Matter Profitability" });
      const rows = JSON.parse(
        within(figure).getByTestId("bar-chart").getAttribute("data-rows") ?? "[]"
      );
      expect(rows).toHaveLength(10);
      expect(rows[0]).toMatchObject({ matter: "Matter 11", margin: 8000 });
      expect(rows[9]).toMatchObject({ matter: "Matter 2", margin: -1000 });
      expect(screen.queryByText(/in other currencies/)).not.toBeInTheDocument();
    });

    it("without a default currency there is no ranking chart", () => {
      render(
        <ProjectProfitabilityTable
          initialData={makeProfitability()}
          initialFrom="2026-02-01"
          initialTo="2026-02-14"
          defaultCurrency={null}
        />
      );

      expect(screen.queryByRole("figure")).not.toBeInTheDocument();
      expect(screen.queryByText(/in other currencies/)).not.toBeInTheDocument();
      expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    });
  });
});
