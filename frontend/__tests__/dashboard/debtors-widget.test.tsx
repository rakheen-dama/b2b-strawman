import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { DebtorsWidget } from "@/components/dashboard/debtors-widget";
import type { DebtorResponse, PaginatedDebtorsResponse } from "@/lib/api/collections";

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

function debtor(o: Partial<DebtorResponse> & { clientId: string }): DebtorResponse {
  return {
    clientName: `Client ${o.clientId}`,
    outstandingTotal: 10000,
    currency: "ZAR",
    invoiceCount: 2,
    oldestDaysOverdue: 45,
    buckets: { current: 2000, d30: 3000, d60: 5000, d90plus: 0 },
    signals: [],
    collectionsExempt: false,
    lastActivity: null,
    ...o,
  };
}

const DATA: PaginatedDebtorsResponse = {
  content: [
    debtor({ clientId: "c1", clientName: "Naidoo Attorneys Trust", outstandingTotal: 48250 }),
    debtor({ clientId: "c2", clientName: "Botha Farms", oldestDaysOverdue: 0 }),
  ],
  page: { totalElements: 12, totalPages: 3, size: 5, number: 0 },
};

describe("DebtorsWidget", () => {
  afterEach(() => cleanup());

  it("headline = number of debtors (no summed total), rows with amount, aging bar and overdue days", () => {
    render(<DebtorsWidget slug="acme" data={DATA} />);

    expect(screen.getByTestId("debtors-headline")).toHaveTextContent("12");
    expect(screen.getByText("clients owe fees")).toBeInTheDocument();
    const list = screen.getByRole("list", { name: "Largest debtors" });
    expect(within(list).getByText("Naidoo Attorneys Trust")).toBeInTheDocument();
    expect(within(list).getByText(/48\s?250/)).toBeInTheDocument();
    expect(within(list).getByText("45 days overdue")).toBeInTheDocument();
    expect(within(list).getByText("Not yet overdue")).toBeInTheDocument();
    expect(within(list).getAllByTestId("micro-stacked-bar")).toHaveLength(2);
  });

  it("aging is never colour-only: segment titles, a text legend, and a hidden per-row breakdown", () => {
    render(<DebtorsWidget slug="acme" data={DATA} />);

    const firstBar = screen.getAllByTestId("micro-stacked-bar")[0];
    const titles = within(firstBar)
      .getAllByTestId("segment")
      .map((s) => s.getAttribute("title"));
    expect(titles).toEqual(["Current: 2000", "30 days: 3000", "60 days: 5000", "90+ days: 0"]);
    const legend = screen.getByRole("list", { name: "Aging legend" });
    expect(legend.textContent).toBe("Current30 days60 days90+ days");
    expect(screen.getAllByText(/^Current R/)[0]).toHaveClass("sr-only");
  });

  it("links to collections", () => {
    render(<DebtorsWidget slug="acme" data={DATA} />);
    expect(screen.getByRole("link", { name: "Open collections" })).toHaveAttribute(
      "href",
      "/org/acme/invoices/collections"
    );
  });

  it("one debtor reads in the singular; none says so", () => {
    render(
      <DebtorsWidget
        slug="acme"
        data={{ ...DATA, content: [DATA.content[0]], page: { ...DATA.page, totalElements: 1 } }}
      />
    );
    expect(screen.getByText("client owes fees")).toBeInTheDocument();
    cleanup();

    render(
      <DebtorsWidget
        slug="acme"
        data={{ content: [], page: { totalElements: 0, totalPages: 0, size: 5, number: 0 } }}
      />
    );
    expect(screen.getByText("No client owes fees.")).toBeInTheDocument();
  });

  it("error: quiet error line", () => {
    render(<DebtorsWidget slug="acme" data={null} />);
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
  });
});
