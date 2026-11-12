import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { DebtorsWidget } from "@/components/dashboard/debtors-widget";
import { formatCurrency } from "@/lib/format";
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
    expect(within(list).getAllByRole("img")).toHaveLength(2);
    expect(
      within(list).getByRole("img", {
        name: "Aging of Naidoo Attorneys Trust's outstanding fees",
      })
    ).toBeInTheDocument();
  });

  it("aging is never colour-only: each meter's description states every bucket, plus a text legend", () => {
    render(<DebtorsWidget slug="acme" data={DATA} />);

    const firstMeter = screen.getByRole("img", {
      name: "Aging of Naidoo Attorneys Trust's outstanding fees",
    });
    const breakdown = ["Current", "30 days", "60 days", "90+ days"]
      .map((label, i) => `${label} ${formatCurrency([2000, 3000, 5000, 0][i], "ZAR")}`)
      .join(", ");
    expect(firstMeter).toHaveAccessibleDescription(breakdown);
    // The row says its breakdown once (the meter's summary), not twice. A function matcher, because
    // a string matcher is not whitespace-normalised and en-ZA currency uses non-breaking spaces.
    const row = firstMeter.closest("li")!;
    expect(within(row).getAllByText((_, el) => el?.textContent === breakdown)).toHaveLength(1);
    const legend = screen.getByRole("list", { name: "Aging legend" });
    expect(legend.textContent).toBe("Current30 days60 days90+ days");
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
