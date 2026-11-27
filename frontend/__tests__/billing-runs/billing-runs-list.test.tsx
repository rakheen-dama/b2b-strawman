import { describe, it, expect, afterEach, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { BillingRunItemStatusBadge } from "@/components/billing-runs/billing-run-item-status-badge";
import { BillingRunsTable } from "@/components/billing-runs/billing-runs-table";
import { formatCurrency } from "@/lib/format";
import type { BillingRun } from "@/lib/api/billing-runs";

// BillingRunsTable is a DataTable, which calls useRouter().
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/invoices/billing-runs",
  useSearchParams: () => new URLSearchParams(),
}));

afterEach(() => {
  cleanup();
});

describe("BillingRunStatusBadge", () => {
  it("renders PREVIEW with neutral variant", () => {
    render(<BillingRunStatusBadge status="PREVIEW" />);
    const badge = screen.getByText("Preview", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders IN_PROGRESS with warning variant", () => {
    render(<BillingRunStatusBadge status="IN_PROGRESS" />);
    const badge = screen.getByText("In Progress", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("renders COMPLETED with success variant", () => {
    render(<BillingRunStatusBadge status="COMPLETED" />);
    const badge = screen.getByText("Completed", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders CANCELLED with destructive variant", () => {
    render(<BillingRunStatusBadge status="CANCELLED" />);
    const badge = screen.getByText("Cancelled", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });
});

describe("BillingRunItemStatusBadge", () => {
  it("renders PENDING with neutral variant", () => {
    render(<BillingRunItemStatusBadge status="PENDING" />);
    const badge = screen.getByText("Pending", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });

  it("renders GENERATING with warning variant", () => {
    render(<BillingRunItemStatusBadge status="GENERATING" />);
    const badge = screen.getByText("Generating", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "warning");
  });

  it("renders GENERATED with success variant", () => {
    render(<BillingRunItemStatusBadge status="GENERATED" />);
    const badge = screen.getByText("Generated", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "success");
  });

  it("renders FAILED with destructive variant", () => {
    render(<BillingRunItemStatusBadge status="FAILED" />);
    const badge = screen.getByText("Failed", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "destructive");
  });

  it("renders EXCLUDED with secondary variant", () => {
    render(<BillingRunItemStatusBadge status="EXCLUDED" />);
    const badge = screen.getByText("Excluded", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "secondary");
  });

  it("renders CANCELLED with neutral variant", () => {
    render(<BillingRunItemStatusBadge status="CANCELLED" />);
    const badge = screen.getByText("Cancelled", {
      selector: "[data-slot='badge']",
    });
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveAttribute("data-variant", "neutral");
  });
});

// Phase 91 666A: the Fee runs list on DataTable (client mode, no row click, no CSV).
describe("BillingRunsTable", () => {
  const run = (overrides: Partial<BillingRun> = {}): BillingRun => ({
    id: "run-1",
    name: "March run",
    status: "COMPLETED",
    periodFrom: "2026-03-01",
    periodTo: "2026-03-31",
    currency: "ZAR",
    includeExpenses: true,
    totalClients: 2,
    totalInvoices: 2,
    totalAmount: 6468.75,
    totalSent: 0,
    totalFailed: 0,
    createdBy: "m-1",
    // 23:30 UTC on 31 March is 1 April on the South African calendar.
    createdAt: "2026-03-31T23:30:00Z",
    updatedAt: "2026-03-31T23:40:00Z",
    completedAt: null,
    ...overrides,
  });

  it("renders the old headers and each run's cells", () => {
    render(<BillingRunsTable slug="acme" runs={[run()]} empty={<p>none</p>} />);
    expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual([
      "Name",
      "Status",
      "Period",
      "Clients",
      "Fee notes",
      "Total",
      "Created",
    ]);
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(screen.getByRole("link", { name: "March run" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/billing-runs/run-1"
    );
    expect(cells[2].textContent).toBe("01 Mar 2026 – 31 Mar 2026");
    expect(cells[5].textContent).toBe(formatCurrency(6468.75, "ZAR"));
    expect(cells[6].textContent).toBe("1 Apr 2026");
    expect(screen.queryByRole("button", { name: "Export CSV" })).not.toBeInTheDocument();
  });

  it("titles an unnamed run by its period (K-65) and dashes missing totals", () => {
    render(
      <BillingRunsTable
        slug="acme"
        runs={[run({ name: null as unknown as string, totalClients: null, totalAmount: null })]}
        empty={<p>none</p>}
      />
    );
    expect(
      screen.getByRole("link", { name: "Fee run · 01 Mar 2026 – 31 Mar 2026" })
    ).toBeInTheDocument();
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(cells[3].textContent).toBe("—");
    expect(cells[5].textContent).toBe("—");
  });

  // F-666A-A1: fits a 710px frame at 1024 with the Name on two lines.
  it("floors the Name column at 11rem and hides only Period below a 67rem frame", () => {
    render(<BillingRunsTable slug="acme" runs={[run()]} empty={<p>none</p>} />);
    const headers = screen.getAllByRole("columnheader");
    const cells = within(screen.getAllByRole("row")[1]).getAllByRole("cell");
    expect(headers[0].style.minWidth).toBe("11rem");
    expect(cells[0].style.minWidth).toBe("11rem");
    expect(headers[2]).toHaveClass("hidden", "@min-[67rem]:table-cell");
    expect(cells[2]).toHaveClass("hidden", "@min-[67rem]:table-cell");
    for (const index of [0, 1, 3, 4, 5, 6]) {
      expect(cells[index]).not.toHaveClass("hidden");
    }
  });

  it("renders the empty slot instead of the table", () => {
    render(<BillingRunsTable slug="acme" runs={[]} empty={<p>No fixture runs.</p>} />);
    expect(screen.getByText("No fixture runs.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
});
