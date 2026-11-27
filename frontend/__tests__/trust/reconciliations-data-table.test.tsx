import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import {
  OpenStatementsDataTable,
  ReconciliationsDataTable,
  type OpenStatementRow,
} from "@/components/trust/reconciliations-data-table";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import type { BankStatementResponse, TrustReconciliationResponse } from "@/lib/types";

// Phase 91 667B: Trust › Reconciliation history (server-paged DataTable) and the statements not yet
// reconciled (client-mode DataTable with DiscardStatementDialog).

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions", () => ({
  discardBankStatement: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting/reconciliation",
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
    [key: string]: unknown;
  }) => {
    const anchorProps = { ...props };
    delete anchorProps.scroll;
    delete anchorProps.prefetch;
    return (
      <a href={href} {...anchorProps}>
        {children}
      </a>
    );
  },
}));

const BASE = "/org/acme/trust-accounting/reconciliation";

function recon(overrides: Partial<TrustReconciliationResponse> = {}): TrustReconciliationResponse {
  return {
    id: "recon-1",
    trustAccountId: "acc-1",
    periodEnd: "2026-10-31",
    bankStatementId: "stmt-1",
    bankBalance: 103265,
    outstandingDeposits: 0,
    outstandingPayments: 0,
    adjustedBankBalance: 103265,
    cashbookBalance: 101630,
    clientLedgerTotal: 100380,
    creditorBreakdown: {} as TrustReconciliationResponse["creditorBreakdown"],
    bankToCashbookDifference: 0,
    cashbookToCreditorsDifference: 0,
    difference: 0,
    excludedLinesTotal: 0,
    unresolvedLineCount: 0,
    isBalanced: true,
    integrityFlags: [],
    status: "COMPLETED",
    completedBy: "member-1",
    completedAt: "2026-11-02T08:15:00Z",
    monthlyListId: "list-1",
    notes: null,
    createdAt: "2026-11-01T00:00:00Z",
    updatedAt: "2026-11-02T08:15:00Z",
    ...overrides,
  };
}

function renderHistory(rows: TrustReconciliationResponse[], { pageIndex = 0, pageCount = 1 } = {}) {
  return render(
    <ReconciliationsDataTable
      rows={rows}
      currency="ZAR"
      pageIndex={pageIndex}
      pageCount={pageCount}
      basePath={BASE}
      empty={<div data-testid="recon-empty">No reconciliations yet.</div>}
    />
  );
}

function statement(overrides: Partial<BankStatementResponse> = {}): BankStatementResponse {
  return {
    id: "stmt-open",
    trustAccountId: "acc-1",
    periodStart: "2026-11-02",
    periodEnd: "2026-11-03",
    openingBalance: 0,
    closingBalance: 135,
    fileName: "open-statement-2026-11.csv",
    format: "CSV",
    lineCount: 2,
    matchedCount: 0,
    status: "IMPORTED",
    importedBy: "member-1",
    createdAt: "2026-11-04T00:00:00Z",
    updatedAt: "2026-11-04T00:00:00Z",
    retiredAt: null,
    retiredBy: null,
    retiredReason: null,
    replacedByStatementId: null,
    lines: [],
    ...overrides,
  } as BankStatementResponse;
}

afterEach(() => cleanup());

describe("ReconciliationsDataTable", () => {
  it("renders today's seven columns, row testids and a caption on the kit table", () => {
    renderHistory([recon()]);

    const table = screen.getByTestId("reconciliations-table");
    expect(table.tagName).toBe("TABLE");
    expect(within(table).getByText("Reconciliation History", { selector: "caption" })).toHaveClass(
      "sr-only"
    );
    expect(Array.from(table.querySelectorAll("thead th")).map((th) => th.textContent)).toEqual([
      "Period",
      "Bank Balance",
      "Cashbook Balance",
      "Client Ledger Total",
      "Status",
      "Completed",
      "Monthly list",
    ]);
    const row = screen.getByTestId("reconciliation-row-recon-1");
    const cells = row.querySelectorAll("td");
    expect(cells).toHaveLength(7);
    expect(cells[0]).toHaveTextContent(formatLocalDate("2026-10-31"));
    expect(cells[1].textContent).toBe(formatCurrency(103265, "ZAR"));
    expect(cells[2].textContent).toBe(formatCurrency(101630, "ZAR"));
    expect(cells[3].textContent).toBe(formatCurrency(100380, "ZAR"));
    expect(cells[5]).toHaveTextContent(formatLocalDate("2026-11-02"));
  });

  it("fits 1024: Period wraps, money headers wrap, Completed hides below a 56rem frame (F-667B-1, F-667B-A3)", () => {
    renderHistory([recon()]);

    const table = screen.getByTestId("reconciliations-table");
    const ths = table.querySelectorAll("thead th");
    for (const index of [1, 2, 3, 6]) {
      expect(ths[index].className).toContain("whitespace-normal");
    }
    expect(ths[5].className).toContain("hidden");
    expect(ths[5].className).toContain("@min-[56rem]:table-cell");
    const cells = screen.getByTestId("reconciliation-row-recon-1").querySelectorAll("td");
    expect(cells[0].className).toContain("whitespace-normal");
    expect(cells[0].className).not.toContain("whitespace-nowrap");
    expect(cells[5].className).toContain("@min-[56rem]:table-cell");
    // The Monthly list link is never hidden
    expect(ths[6].className).not.toContain("hidden");
  });

  it("keeps the status chips and the neutral link to a locked monthly list", () => {
    renderHistory([
      recon({ id: "recon-done" }),
      recon({
        id: "recon-draft",
        isBalanced: false,
        status: "DRAFT",
        completedAt: null,
        monthlyListId: null,
      }),
    ]);

    const done = screen.getByTestId("reconciliation-row-recon-done");
    expect(within(done).getByText("Balanced").closest("[data-slot=badge]")).toBeInTheDocument();
    const link = within(done).getByTestId("list-locked-recon-done");
    expect(link).toHaveAttribute("href", `${BASE}/lists/list-1`);
    expect(link).toHaveTextContent("List locked");

    const draft = screen.getByTestId("reconciliation-row-recon-draft");
    expect(within(draft).getByText("Unbalanced")).toBeInTheDocument();
    expect(within(draft).queryByTestId("list-locked-recon-draft")).not.toBeInTheDocument();
    const cells = draft.querySelectorAll("td");
    // Today's "---" placeholders (not the kit's "—")
    expect(cells[5]).toHaveTextContent(/^---$/);
    expect(cells[6]).toHaveTextContent(/^---$/);
  });

  it("pages with links that write only ?page=N (today's pageUrl)", () => {
    renderHistory([recon()], { pageIndex: 1, pageCount: 3 });

    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${BASE}?page=0`
    );
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute("href", `${BASE}?page=2`);
  });

  it("disables Previous on the first page and Next on the last", () => {
    const { unmount } = renderHistory([recon()], { pageIndex: 0, pageCount: 2 });
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute("href", `${BASE}?page=1`);
    unmount();

    renderHistory([recon()], { pageIndex: 1, pageCount: 2 });
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${BASE}?page=0`
    );
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("shows no pager for a single page", () => {
    renderHistory([recon()], { pageIndex: 0, pageCount: 1 });
    expect(screen.queryByText(/^Page \d+ of \d+$/)).not.toBeInTheDocument();
  });

  it("renders the pre-rendered empty state instead of the table", () => {
    renderHistory([], { pageIndex: 0, pageCount: 0 });
    expect(screen.getByTestId("recon-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("reconciliations-table")).not.toBeInTheDocument();
  });
});

describe("OpenStatementsDataTable", () => {
  const rows: OpenStatementRow[] = [
    { statement: statement(), accountName: "Mathebula & Partners Trust Account" },
  ];

  it("renders today's columns, the row testid and Discard for MANAGE_TRUST", () => {
    render(<OpenStatementsDataTable rows={rows} canManageTrust />);

    const table = screen.getByTestId("open-statements-table");
    expect(
      within(table).getByText("Statements Not Yet Reconciled", { selector: "caption" })
    ).toBeInTheDocument();
    const headers = Array.from(table.querySelectorAll("thead th"));
    expect(headers.map((th) => th.textContent)).toEqual([
      "Trust Account",
      "File",
      "Period",
      "Lines",
      "Matched",
      "Actions",
    ]);
    expect(headers[5].querySelector(".sr-only")).toHaveTextContent("Actions");

    const row = screen.getByTestId("open-statement-row-stmt-open");
    const cells = row.querySelectorAll("td");
    expect(cells[0]).toHaveTextContent("Mathebula & Partners Trust Account");
    expect(cells[0].className).toContain("whitespace-normal");
    expect(cells[1]).toHaveTextContent("open-statement-2026-11.csv");
    expect(cells[1].className).toContain("[overflow-wrap:anywhere]");
    expect(cells[2]).toHaveTextContent(
      `${formatLocalDate("2026-11-02")} – ${formatLocalDate("2026-11-03")}`
    );
    // F-667B-A1: the range wraps between its two dates, never inside one
    expect(cells[2].className).toContain("whitespace-normal");
    expect(cells[2].className).not.toContain("whitespace-nowrap");
    const dates = Array.from(cells[2].querySelectorAll("span"));
    expect(dates.map((span) => span.textContent)).toEqual([
      `${formatLocalDate("2026-11-02")} –`,
      formatLocalDate("2026-11-03"),
    ]);
    for (const span of dates) expect(span.className).toContain("whitespace-nowrap");
    expect(cells[3]).toHaveTextContent("2");
    expect(cells[4]).toHaveTextContent("0");
    expect(within(row).getByTestId("discard-statement-stmt-open")).toBeInTheDocument();
  });

  it("offers no Discard without MANAGE_TRUST", () => {
    render(<OpenStatementsDataTable rows={rows} canManageTrust={false} />);

    const row = screen.getByTestId("open-statement-row-stmt-open");
    expect(within(row).queryByTestId("discard-statement-stmt-open")).not.toBeInTheDocument();
    expect(within(row).queryByRole("button")).not.toBeInTheDocument();
  });

  it("has no pager (every open statement is listed)", () => {
    const many: OpenStatementRow[] = Array.from({ length: 30 }, (_, i) => ({
      statement: statement({ id: `stmt-${i}`, fileName: `file-${i}.csv` }),
      accountName: "Main Trust",
    }));
    render(<OpenStatementsDataTable rows={many} canManageTrust={false} />);

    expect(screen.getAllByTestId(/^open-statement-row-/)).toHaveLength(30);
    expect(screen.queryByText(/^Page \d+ of \d+$/)).not.toBeInTheDocument();
  });
});
