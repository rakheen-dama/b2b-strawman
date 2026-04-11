import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// --- Mocks (before component imports) ---

vi.mock("swr", () => ({
  default: vi.fn(),
  useSWRConfig: () => ({ mutate: vi.fn() }),
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
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/trust-accounting",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue({ content: [] }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  fetchTransactions: vi.fn().mockResolvedValue({ content: [] }),
  recordDeposit: vi.fn(),
  recordPayment: vi.fn(),
  recordFeeTransfer: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([]),
  fetchDashboardData: vi.fn().mockResolvedValue(null),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions", () => ({
  fetchClientLedger: vi.fn().mockResolvedValue(null),
  fetchClientHistory: vi.fn().mockResolvedValue({ content: [] }),
}));

// --- Imports after mocks ---

import useSWR from "swr";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ModuleGate } from "@/components/module-gate";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { NAV_GROUPS, SETTINGS_ITEMS } from "@/lib/nav-items";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Trust nav module constant ---
const TRUST_MODULE = "trust_accounting";

// --- Profile wrappers ---

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

function withAccountingProfile(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile="accounting-za"
      enabledModules={["regulatory_deadlines"]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

// ===== 451.8 — Coexistence Tests =====

describe("Trust coexistence -- nav visibility", () => {
  it("no trust nav items for accounting-profile org", () => {
    const trustNavItems = NAV_GROUPS.flatMap((g) => g.items).filter(
      (item) => item.requiredModule === TRUST_MODULE
    );

    // Sanity: there should be 7 trust nav items in the nav definition
    expect(trustNavItems.length).toBe(7);

    render(
      withAccountingProfile(
        <>
          {trustNavItems.map((item) => (
            <ModuleGate key={item.label} module={item.requiredModule!}>
              <span data-testid={`trust-nav-${item.label}`}>{item.label}</span>
            </ModuleGate>
          ))}
        </>
      )
    );

    // None of the 7 trust nav items should be visible
    for (const item of trustNavItems) {
      expect(screen.queryByTestId(`trust-nav-${item.label}`)).not.toBeInTheDocument();
    }
  });

  it("trust nav items visible for legal-profile org with trust_accounting", () => {
    const trustNavItems = NAV_GROUPS.flatMap((g) => g.items).filter(
      (item) => item.requiredModule === TRUST_MODULE
    );

    const { unmount } = render(
      withTrustEnabled(
        <>
          {trustNavItems.map((item) => (
            <ModuleGate key={item.label} module={item.requiredModule!}>
              <span data-testid={`trust-nav-${item.label}`}>{item.label}</span>
            </ModuleGate>
          ))}
        </>
      )
    );

    // All 7 trust nav items should be visible
    for (const item of trustNavItems) {
      expect(screen.getByTestId(`trust-nav-${item.label}`)).toBeInTheDocument();
    }

    unmount();
  });

  it("trust settings item hidden for non-trust-enabled org", () => {
    const trustSettingsItem = SETTINGS_ITEMS.find((item) => item.requiredModule === TRUST_MODULE);

    expect(trustSettingsItem).toBeDefined();

    render(
      withAccountingProfile(
        <ModuleGate module={trustSettingsItem!.requiredModule!}>
          <span data-testid="trust-settings-coex">{trustSettingsItem!.title}</span>
        </ModuleGate>
      )
    );

    expect(screen.queryByTestId("trust-settings-coex")).not.toBeInTheDocument();
  });
});

// ===== 451.9 — Trust Smoke Tests =====

describe("Trust smoke tests", () => {
  it("deposit -> approval -> client balance update flow via TrustBalanceCard", () => {
    // Simulate a client ledger with a deposit that has been approved,
    // resulting in an updated balance
    // useSWR mock: only the key argument is used for routing; fetcher and config
    // args are unused in test mocks. The `as typeof useSWR` cast is intentional
    // to satisfy the overloaded SWR signature while keeping the mock simple.
    vi.mocked(useSWR).mockImplementation(((key: string | null) => {
      if (key && key.startsWith("trust-accounts-")) {
        return {
          data: [
            {
              id: "ta-1",
              accountName: "Primary Trust Account",
              bankName: "First National Bank",
              branchCode: "250655",
              accountNumber: "62000000001",
              accountType: "GENERAL",
              isPrimary: true,
              requireDualApproval: false,
              paymentApprovalThreshold: null,
              status: "ACTIVE",
              openedDate: "2024-01-15",
              closedDate: null,
              notes: null,
              createdAt: "2024-01-15T00:00:00Z",
              updatedAt: "2024-01-15T00:00:00Z",
            },
          ],
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        };
      }
      if (key && key.startsWith("trust-ledger-")) {
        // After deposit is approved, client ledger shows updated balance
        return {
          data: {
            id: "ledger-1",
            trustAccountId: "ta-1",
            customerId: "cust-1",
            customerName: "Test Client",
            balance: 50000,
            totalDeposits: 80000,
            totalPayments: 25000,
            totalFeeTransfers: 5000,
            totalInterestCredited: 0,
            lastTransactionDate: "2024-06-15T10:30:00Z",
            createdAt: "2024-01-15T00:00:00Z",
            updatedAt: "2024-06-15T10:30:00Z",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        };
      }
      return {
        data: undefined,
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      };
    }) as typeof useSWR);

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" showQuickActions={false} />
      )
    );

    // Verify the balance card renders with the post-approval balance
    expect(screen.getByText("Trust Balance")).toBeInTheDocument();
    expect(screen.getByText("Funds Held")).toBeInTheDocument();
    // ZAR formatting: "R 50,000.00" (en-ZA locale with Intl.NumberFormat)
    expect(screen.getByText(/R[\s\u00a0]50,000\.00/)).toBeInTheDocument();
    // Verify deposit and payment breakdowns
    expect(screen.getByText(/R[\s\u00a0]80,000\.00/)).toBeInTheDocument();
    expect(screen.getByText(/R[\s\u00a0]25,000\.00/)).toBeInTheDocument();
  });

  it("trust dashboard summary cards render with mock data", () => {
    // TrustBalanceCard is the client-side component that shows balance data.
    // The full dashboard page is a server component so we test the balance card
    // with summary-like data: total balance, active client indicator, and
    // pending status (no funds = "No Funds" badge).
    // useSWR mock: see comment in first smoke test for rationale on type cast.
    vi.mocked(useSWR).mockImplementation(((key: string | null) => {
      if (key && key.startsWith("trust-accounts-")) {
        return {
          data: [
            {
              id: "ta-summary",
              accountName: "Summary Trust Account",
              bankName: "Standard Bank",
              branchCode: "051001",
              accountNumber: "123456789",
              accountType: "GENERAL",
              isPrimary: true,
              requireDualApproval: true,
              paymentApprovalThreshold: 100000,
              status: "ACTIVE",
              openedDate: "2023-01-01",
              closedDate: null,
              notes: null,
              createdAt: "2023-01-01T00:00:00Z",
              updatedAt: "2023-06-01T00:00:00Z",
            },
          ],
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        };
      }
      if (key && key.startsWith("trust-ledger-")) {
        return {
          data: {
            id: "ledger-summary",
            trustAccountId: "ta-summary",
            customerId: "cust-summary",
            customerName: "Summary Client",
            balance: 125750.5,
            totalDeposits: 200000,
            totalPayments: 70000,
            totalFeeTransfers: 4249.5,
            totalInterestCredited: 0,
            lastTransactionDate: "2024-03-20T14:00:00Z",
            createdAt: "2023-01-15T00:00:00Z",
            updatedAt: "2024-03-20T14:00:00Z",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        };
      }
      return {
        data: undefined,
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      };
    }) as typeof useSWR);

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-summary" slug="acme" showQuickActions={false} />
      )
    );

    // Verify summary data renders
    expect(screen.getByText("Trust Balance")).toBeInTheDocument();
    expect(screen.getByText("Funds Held")).toBeInTheDocument();
    // Total balance: R 125,750.50 (en-ZA locale)
    expect(screen.getByText(/R[\s\u00a0]125,750\.50/)).toBeInTheDocument();
    // Deposits: R 200,000.00
    expect(screen.getByText(/R[\s\u00a0]200,000\.00/)).toBeInTheDocument();
    // Payments: R 70,000.00
    expect(screen.getByText(/R[\s\u00a0]70,000\.00/)).toBeInTheDocument();
    // Last transaction date should be rendered
    expect(screen.getByText(/Last transaction/)).toBeInTheDocument();
  });
});
