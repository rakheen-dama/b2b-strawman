import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";

// Phase 92 650D (PR #1807 review): the card's posting dialogs receive the *resolved* account's
// lockedThrough — the primary account by default, or the account a caller passes in. A real
// (non-null) lockedThrough must flow from the accounts list through the card into the dialogs.

vi.mock("server-only", () => ({}));

vi.mock("@/lib/capabilities", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/capabilities")>()),
  useCapabilities: () => ({ hasCapability: () => true }),
}));

vi.mock("swr", () => ({
  default: vi.fn(),
  useSWRConfig: () => ({ mutate: vi.fn() }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/org/acme/clients/cust-1",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([]),
  fetchMatterTrustBalance: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions", () => ({
  fetchClientLedger: vi.fn().mockResolvedValue(null),
  fetchClientHistory: vi.fn().mockResolvedValue({ content: [] }),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  fetchTransactions: vi.fn().mockResolvedValue({ content: [] }),
  recordDeposit: vi.fn(),
  recordPayment: vi.fn(),
  recordFeeTransfer: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/clients/[id]/actions", () => ({
  fetchCustomerProjects: vi.fn().mockResolvedValue([]),
}));

// --- Imports after mocks ---

import useSWR from "swr";
import { OrgProfileProvider } from "@/lib/org-profile";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

const LOCKED =
  "This date falls in a month whose list is locked. The entry will be recorded and shown as a later adjustment.";

function withTrustEnabled(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile="legal-za"
      enabledModules={["trust_accounting"]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

const CLIENT_LEDGER = {
  id: "ledger-1",
  trustAccountId: "acc-1",
  clientId: "cust-1",
  clientName: "Moroka Holdings",
  balance: 3751.34,
  totalDeposits: 5001.34,
  totalTransfersIn: 0,
  totalPayments: 1250,
  totalFeeTransfers: 0,
  totalRefunds: 0,
  totalTransfersOut: 0,
  totalInvestmentPlacements: 0,
  totalInvestmentReturns: 0,
  investedBalance: 0,
  lastTransactionDate: "2026-11-02",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-11-02T00:00:00Z",
};

type Account = { id: string; isPrimary: boolean; status: string; lockedThrough: string | null };

/** SWR responses by key; a null key (fetch skipped) answers no data, as real SWR does. */
function mockSwr(accounts: Account[], { accountsFail = false } = {}) {
  vi.mocked(useSWR).mockImplementation(((key: string | null) => {
    const ok = (data: unknown) => ({
      data,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
      isValidating: false,
    });
    if (key && key.startsWith("trust-accounts-")) {
      return accountsFail
        ? { ...ok(undefined), error: new Error("accounts fetch failed") }
        : ok(accounts);
    }
    if (key && key.startsWith("trust-ledger-")) return ok(CLIENT_LEDGER);
    return ok(undefined);
  }) as typeof useSWR);
}

function setDate(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
}

describe("TrustBalanceCard — locked-month warning in its posting dialogs (650D)", () => {
  it("passes the primary account's lockedThrough to the deposit dialog", () => {
    mockSwr([
      { id: "acc-other", isPrimary: false, status: "ACTIVE", lockedThrough: null },
      { id: "acc-1", isPrimary: true, status: "ACTIVE", lockedThrough: "2026-03-31" },
    ]);

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" showQuickActions />));

    fireEvent.click(screen.getByRole("button", { name: "Record Deposit" }));
    const date = screen.getByLabelText("Transaction Date");

    setDate(date, "2026-04-01");
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();

    setDate(date, "2026-03-31");
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
  });

  it("passes a passed-in trustAccountId's lockedThrough (not the primary's) to all three dialogs", () => {
    // The primary has nothing locked; the account the caller names has March locked
    mockSwr([
      { id: "acc-1", isPrimary: true, status: "ACTIVE", lockedThrough: null },
      { id: "acc-2", isPrimary: false, status: "ACTIVE", lockedThrough: "2026-03-31" },
    ]);

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" trustAccountId="acc-2" slug="acme" showQuickActions />
      )
    );

    // Deposit
    fireEvent.click(screen.getByRole("button", { name: "Record Deposit" }));
    setDate(screen.getByLabelText("Transaction Date"), "2026-03-31");
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    cleanup();

    // Payment
    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" trustAccountId="acc-2" slug="acme" showQuickActions />
      )
    );
    fireEvent.click(screen.getByRole("button", { name: "Record Payment" }));
    setDate(screen.getByLabelText("Transaction Date"), "2026-03-15");
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
    cleanup();

    // Fee transfer
    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" trustAccountId="acc-2" slug="acme" showQuickActions />
      )
    );
    fireEvent.click(screen.getByRole("button", { name: "Fee Transfer" }));
    setDate(screen.getByTestId("fee-transfer-date-input"), "2026-03-20");
    expect(screen.getByTestId("locked-month-warning")).toHaveTextContent(LOCKED);
  });

  it("a passed-in trustAccountId still renders the balance when the accounts list fails", () => {
    // The accounts list only feeds lockedThrough for a named account: its failure must not
    // blank the card (no warning is the fallback)
    mockSwr([], { accountsFail: true });

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" trustAccountId="acc-2" slug="acme" showQuickActions />
      )
    );

    expect(screen.getByTestId("trust-balance-primary")).toHaveTextContent(/3[\s ,]751[,.]34/);
    expect(screen.queryByText("Unable to load trust balance")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Record Deposit" }));
    setDate(screen.getByLabelText("Transaction Date"), "2026-03-31");
    expect(screen.queryByTestId("locked-month-warning")).not.toBeInTheDocument();
  });
});
