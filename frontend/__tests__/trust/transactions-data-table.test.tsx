import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { TransactionsDataTable } from "@/components/trust/transactions-data-table";
import { formatCurrency } from "@/lib/format";
import type { TrustTransaction, TrustTransactionTypeInfo } from "@/lib/types";

// Phase 91 667A: Trust › Transactions on DataTable (server-paged), the Actions column and the pager.

vi.mock("server-only", () => ({}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  approveTransaction: vi.fn(),
  rejectTransaction: vi.fn(),
  reverseTransaction: vi.fn(),
}));

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting/transactions",
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

function typeInfo(code: string, label: string, reversal: string): TrustTransactionTypeInfo {
  return {
    code,
    label,
    direction: "OUT",
    ledger: "CLIENT",
    origin: "FIRM",
    requiresApproval: false,
    reversal,
    clientVisible: true,
    takeOnOnly: false,
    clearedAtCutover: false,
  } as TrustTransactionTypeInfo;
}

const TYPES = [
  typeInfo("DEPOSIT", "Deposit", "NEEDS_APPROVAL"),
  typeInfo("PAYMENT", "Payment", "IMMEDIATE"),
];

function tx(overrides: Partial<TrustTransaction> = {}): TrustTransaction {
  return {
    id: "tx-1",
    trustAccountId: "acc-1",
    transactionType: "PAYMENT",
    typeLabel: "Payment",
    direction: "OUT",
    ledger: "CLIENT",
    amount: 3500,
    clientId: "cust-1",
    matterId: null,
    counterpartyClientId: null,
    invoiceId: null,
    reference: "PAY-001",
    description: null,
    transactionDate: "2026-04-01",
    status: "APPROVED",
    approvedBy: null,
    approvedAt: null,
    secondApprovedBy: null,
    secondApprovedAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    reversalOf: null,
    reversedById: null,
    reversalPending: false,
    bankStatementLineId: null,
    recordedBy: "member-2",
    createdAt: "2026-04-01T09:00:00Z",
    creditorAccountId: null,
    investmentId: null,
    postedAt: "2026-04-01T10:00:00Z",
    runningBalance: null,
    ...overrides,
  } as TrustTransaction;
}

const BASE = "/org/acme/trust-accounting/transactions";

interface RenderOptions {
  rows?: TrustTransaction[];
  pageIndex?: number;
  pageCount?: number;
  query?: Record<string, string>;
  canApproveTrust?: boolean;
  canManageTrust?: boolean;
  currentMemberId?: string;
}

function renderTable({
  rows = [tx()],
  pageIndex = 0,
  pageCount = 1,
  query = {},
  canApproveTrust = true,
  canManageTrust = true,
  currentMemberId = "member-9",
}: RenderOptions = {}) {
  return render(
    <TransactionsDataTable
      rows={rows}
      currency="ZAR"
      pageIndex={pageIndex}
      pageCount={pageCount}
      basePath={BASE}
      query={query}
      types={TYPES}
      canApproveTrust={canApproveTrust}
      canManageTrust={canManageTrust}
      currentMemberId={currentMemberId}
      toolbar={{ filters: <div data-testid="status-filters">filters</div> }}
      empty={<p>No transactions match the current filters</p>}
    />
  );
}

const HEADERS = ["Date", "Reference", "Type", "Amount", "Status", "Actions"];

describe("TransactionsDataTable (667A)", () => {
  afterEach(() => {
    cleanup();
  });

  it("keeps the column order, the table testid and a per-row testid", () => {
    renderTable({ rows: [tx({ id: "tx-a" }), tx({ id: "tx-b", reference: "PAY-002" })] });

    const table = screen.getByTestId("transactions-table");
    expect(table.tagName).toBe("TABLE");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((th) => th.textContent)
    ).toEqual(HEADERS);
    expect(screen.getByTestId("transaction-row-tx-a")).toBeInTheDocument();
    expect(screen.getByTestId("transaction-row-tx-b")).toBeInTheDocument();
    expect(screen.getByTestId("status-filters")).toBeInTheDocument();
  });

  it("keeps the Reference (2nd) and Amount (4th) cells' text exactly the value (e2e reads them by position)", () => {
    const reference = `667A-K10-${"X".repeat(141)}`;
    renderTable({ rows: [tx({ id: "tx-long", reference })] });

    const cells = within(screen.getByTestId("transaction-row-tx-long")).getAllByRole("cell");
    expect(cells[1].textContent).toBe(reference);
    expect(cells[3].textContent).toBe(formatCurrency(3500, "ZAR"));
    // K-10: the reference wraps over a floor instead of widening the table
    expect(cells[1]).toHaveClass("whitespace-normal", "[overflow-wrap:anywhere]");
    // F-667A-A4: 6rem (not 9rem) so the sticky Actions column leaves Amount in view at 1024;
    // the Type floor stays 10rem (K-90)
    expect(cells[1].style.minWidth).toBe("6rem");
    expect(cells[2].style.minWidth).toBe("10rem");
  });

  it("sticks the Actions column to the right edge (K-90)", () => {
    renderTable();

    const header = screen.getByRole("columnheader", { name: "Actions" });
    expect(header).toHaveClass("sticky", "right-0", "bg-card");
    const cells = within(screen.getByTestId("transaction-row-tx-1")).getAllByRole("cell");
    expect(cells[5]).toHaveClass("sticky", "right-0", "bg-card", "whitespace-normal");
  });

  it("wraps the type badge and shows the progress of a first dual approval (register)", () => {
    renderTable({
      rows: [
        tx({
          id: "tx-place",
          transactionType: "INV_PLACE",
          typeLabel: "Placed in section 86(4) investment",
          description: "Placed in section 86(4) investment at Investec",
          status: "AWAITING_APPROVAL",
          approvedBy: "member-1",
        }),
      ],
    });

    const badge = screen.getByTestId("transaction-type-tx-place");
    expect(badge).toHaveClass("shrink", "whitespace-normal", "rounded-md");
    expect(badge).not.toHaveClass("rounded-full");
    expect(screen.getByTestId("transaction-description-tx-place")).toHaveTextContent("at Investec");
    expect(screen.getByTestId("approval-progress-tx-place")).toHaveTextContent("1 of 2 approvals");
  });

  it("offers Approve / Reject only on an AWAITING_APPROVAL row and only to an approver", () => {
    const rows = [
      tx({ id: "tx-await", status: "AWAITING_APPROVAL" }),
      tx({ id: "tx-done", status: "APPROVED" }),
    ];
    renderTable({ rows });

    const awaiting = within(screen.getByTestId("transaction-row-tx-await"));
    expect(awaiting.getByTestId("approval-actions-tx-await")).toBeInTheDocument();
    // F-667A-A4: ApprovalBadge's button row may wrap, so the sticky column narrows
    expect(awaiting.getByTestId("approval-actions-tx-await")).toHaveClass("[&>div]:flex-wrap");
    expect(awaiting.getByRole("button", { name: "Approve" })).toBeEnabled();
    expect(awaiting.getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(screen.queryByTestId("approval-actions-tx-done")).not.toBeInTheDocument();

    cleanup();
    renderTable({ rows, canApproveTrust: false, canManageTrust: false });
    expect(document.querySelectorAll('[data-testid^="approval-actions-"]')).toHaveLength(0);
    expect(document.querySelectorAll('[data-testid^="reversal-action-"]')).toHaveLength(0);
    // The column stays (today's always-rendered "Actions" header), its cells empty.
    expect(screen.getByRole("columnheader", { name: "Actions" })).toBeInTheDocument();
  });

  it("disables Approve for the member who gave the first dual approval (LZKC-016)", () => {
    renderTable({
      rows: [tx({ id: "tx-dual", status: "AWAITING_APPROVAL", approvedBy: "member-1" })],
      currentMemberId: "member-1",
    });

    const approve = within(screen.getByTestId("approval-actions-tx-dual")).getByTestId(
      "approve-button"
    );
    expect(approve).toBeDisabled();
    expect(approve).toHaveTextContent("Approved by you");

    cleanup();
    renderTable({
      rows: [tx({ id: "tx-dual", status: "AWAITING_APPROVAL", approvedBy: "member-1" })],
      currentMemberId: "member-2",
    });
    expect(screen.getByTestId("approve-button")).toBeEnabled();
  });

  it("offers Reverse to a trust manager per the row type's reversal rule", () => {
    renderTable({
      rows: [
        tx({ id: "tx-pay", transactionType: "PAYMENT", status: "APPROVED" }),
        tx({ id: "tx-dep", transactionType: "DEPOSIT", status: "RECORDED" }),
        tx({ id: "tx-reversed", status: "REVERSED", reversedById: "tx-x" }),
      ],
    });

    expect(
      within(screen.getByTestId("reversal-action-tx-pay")).getByTestId("reverse-button")
    ).toBeInTheDocument();
    expect(screen.getByTestId("reversal-action-tx-dep")).toBeInTheDocument();
    expect(screen.queryByTestId("reversal-action-tx-reversed")).not.toBeInTheDocument();

    cleanup();
    renderTable({ canManageTrust: false });
    expect(screen.queryByTestId("reversal-action-tx-1")).not.toBeInTheDocument();
  });

  it("pages by links that keep every param and set page (zero-based, pageUrl parity)", () => {
    renderTable({
      pageIndex: 1,
      pageCount: 3,
      query: { status: "APPROVED", type: "DEPOSIT", dateFrom: "2026-01-01", page: "1" },
    });

    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Previous" })).toHaveAttribute(
      "href",
      `${BASE}?status=APPROVED&type=DEPOSIT&dateFrom=2026-01-01&page=0`
    );
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute(
      "href",
      `${BASE}?status=APPROVED&type=DEPOSIT&dateFrom=2026-01-01&page=2`
    );
  });

  it("renders no pager for a single page and disables Previous on the first", () => {
    renderTable({ pageCount: 1 });
    expect(screen.queryByText(/Page \d+ of \d+/)).not.toBeInTheDocument();

    cleanup();
    renderTable({ pageIndex: 0, pageCount: 2 });
    expect(screen.getByText("Page 1 of 2")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute("href", `${BASE}?page=1`);
  });

  it("renders the empty slot instead of the table, keeping the toolbar", () => {
    renderTable({ rows: [] });

    expect(screen.queryByTestId("transactions-table")).not.toBeInTheDocument();
    expect(screen.getByText("No transactions match the current filters")).toBeInTheDocument();
    expect(screen.getByTestId("status-filters")).toBeInTheDocument();
  });
});
