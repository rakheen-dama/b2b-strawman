import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";

// --- Mocks (before component imports) ---

vi.mock("server-only", () => ({}));

// G89-F64: TrustBalanceCard reads VIEW_TRUST from the capability context; these tests render it
// for a trust reader.
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
  usePathname: () => "/org/acme/matters/proj-1",
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
  totalDeposits: 5000,
  totalInterestCredited: 1.34,
  totalTransfersIn: 0,
  totalPayments: 1250,
  totalFeeTransfers: 0,
  totalRefunds: 0,
  totalTransfersOut: 0,
  lastTransactionDate: "2026-11-02",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-11-02T00:00:00Z",
};

/** SWR responses by key prefix; the matter balance is the matter's sub-ledger (Overview tile). */
function mockSwr(matterBalance: number, { ledgerFails = false } = {}) {
  vi.mocked(useSWR).mockImplementation(((key: string | null) => {
    const ok = (data: unknown) => ({
      data,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
      isValidating: false,
    });
    if (key && key.startsWith("trust-accounts-")) {
      return ok([{ id: "acc-1", isPrimary: true, status: "ACTIVE" }]);
    }
    if (key && key.startsWith("trust-ledger-")) {
      return ledgerFails
        ? { ...ok(undefined), error: new Error("ledger fetch failed") }
        : ok(CLIENT_LEDGER);
    }
    if (key && key.startsWith("matter-trust-balance-")) {
      return ok({
        state: "ok",
        data: { matterId: "proj-1", balance: matterBalance, currency: "ZAR" },
      });
    }
    return ok(undefined);
  }) as typeof useSWR);
}

describe("TrustBalanceCard — matter mode (G89-F14)", () => {
  it("shows the matter balance as the primary figure, not the client card", () => {
    mockSwr(3750);

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />)
    );

    const primary = screen.getByTestId("trust-balance-primary");
    expect(within(primary).getByText(/R[\s ]3[\s ,]750[,.]00/)).toBeInTheDocument();
    expect(primary.textContent).not.toMatch(/751[,.]34/);
    expect(screen.getByText("Funds Held")).toBeInTheDocument();
  });

  it("badge follows the matter: no funds on the matter although the client holds money", () => {
    mockSwr(0);

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />)
    );

    expect(screen.getByText("No Funds")).toBeInTheDocument();
    expect(screen.queryByText("Funds Held")).toBeNull();
  });

  it("renders the client's balance across all matters as a secondary line", () => {
    mockSwr(3750);

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />)
    );

    const clientLine = screen.getByTestId("trust-balance-client-line");
    expect(clientLine.textContent).toContain("Moroka Holdings — trust balance across all matters:");
    expect(clientLine.textContent).toMatch(/R[\s ]3[\s ,]751[,.]34/);
    // The client-ledger breakdown belongs to client mode only
    expect(screen.queryByTestId("ledger-totals-breakdown")).toBeNull();
  });

  it("fetches the matter balance under the matter-trust-balance SWR key", () => {
    mockSwr(3750);

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />)
    );

    const keys = vi.mocked(useSWR).mock.calls.map((call) => call[0]);
    expect(keys).toContain("matter-trust-balance-acme-proj-1");
  });

  it("client mode (no matter) keeps the client card with its breakdown", () => {
    mockSwr(3750);

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" />));

    const primary = screen.getByTestId("trust-balance-primary");
    expect(within(primary).getByText(/R[\s ]3[\s ,]751[,.]34/)).toBeInTheDocument();
    expect(screen.getByTestId("ledger-totals-breakdown")).toBeInTheDocument();
    expect(screen.queryByTestId("trust-balance-client-line")).toBeNull();
    const keys = vi.mocked(useSWR).mock.calls.map((call) => call[0]);
    expect(keys).not.toContain("matter-trust-balance-acme-proj-1");
  });

  it("a failed client-ledger fetch keeps the matter figure and drops only the client line", () => {
    // PR #1746 review: in matter mode the client card is secondary, so its error must not blank
    // the card.
    mockSwr(3750, { ledgerFails: true });

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />)
    );

    const primary = screen.getByTestId("trust-balance-primary");
    expect(within(primary).getByText(/R[\s ]3[\s ,]750[,.]00/)).toBeInTheDocument();
    expect(screen.queryByTestId("trust-balance-client-line")).toBeNull();
    expect(screen.queryByText("Unable to load trust balance")).toBeNull();
  });

  it("client mode still reports a failed client-ledger fetch", () => {
    mockSwr(3750, { ledgerFails: true });

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" />));

    expect(screen.getByText("Unable to load trust balance")).toBeInTheDocument();
    expect(screen.queryByTestId("trust-balance-primary")).toBeNull();
  });
});
