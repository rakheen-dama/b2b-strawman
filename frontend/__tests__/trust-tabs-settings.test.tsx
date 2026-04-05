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

const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/org/acme/projects/proj-1",
  useSearchParams: () => mockSearchParams(),
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

// Mock the trust transaction actions to prevent server action imports in test env
vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/transactions/actions",
  () => ({
    fetchTransactions: vi.fn().mockResolvedValue({ content: [] }),
    recordDeposit: vi.fn(),
    recordPayment: vi.fn(),
    recordFeeTransfer: vi.fn(),
  }),
);

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([]),
}));

vi.mock(
  "@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions",
  () => ({
    fetchClientLedger: vi.fn().mockResolvedValue(null),
    fetchClientHistory: vi.fn().mockResolvedValue({ content: [] }),
  }),
);

// --- Imports after mocks ---

import useSWR from "swr";
import { OrgProfileProvider } from "@/lib/org-profile";
import { ProjectTabs } from "@/components/projects/project-tabs";
import { CustomerTabs } from "@/components/customers/customer-tabs";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { NAV_GROUPS, SETTINGS_ITEMS } from "@/lib/nav-items";

afterEach(() => cleanup());
beforeEach(() => vi.clearAllMocks());

// --- Helpers ---

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

function withNoModules(ui: React.ReactElement) {
  return (
    <OrgProfileProvider
      verticalProfile={null}
      enabledModules={[]}
      terminologyNamespace={null}
    >
      {ui}
    </OrgProfileProvider>
  );
}

// Minimal placeholder panels for required ProjectTabs props
const placeholder = <div>placeholder</div>;

// --- Tests ---

describe("Project detail Trust tab", () => {
  it("shows Trust tab when trust_accounting module is enabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <ProjectTabs
          overviewPanel={placeholder}
          documentsPanel={placeholder}
          membersPanel={placeholder}
          customersPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={
            <TrustBalanceCard
              customerId="cust-1"
              slug="acme"
              showQuickActions={false}
            />
          }
        />,
      ),
    );

    expect(screen.getByRole("tab", { name: "Trust" })).toBeInTheDocument();
  });

  it("hides Trust tab when trust_accounting module is disabled", () => {
    render(
      withNoModules(
        <ProjectTabs
          overviewPanel={placeholder}
          documentsPanel={placeholder}
          membersPanel={placeholder}
          customersPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={
            <div data-testid="trust-content">Trust content</div>
          }
        />,
      ),
    );

    expect(screen.queryByRole("tab", { name: "Trust" })).not.toBeInTheDocument();
  });

  it("falls back to overview tab when ?tab=trust but trust module is disabled", () => {
    mockSearchParams.mockReturnValueOnce(new URLSearchParams("tab=trust"));

    render(
      withNoModules(
        <ProjectTabs
          overviewPanel={<div data-testid="overview-content">Overview</div>}
          documentsPanel={placeholder}
          membersPanel={placeholder}
          customersPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={<div data-testid="trust-content">Trust</div>}
        />,
      ),
    );

    // Trust tab should not be rendered
    expect(screen.queryByRole("tab", { name: "Trust" })).not.toBeInTheDocument();
    // Overview tab should be active (fallback)
    expect(screen.getByRole("tab", { name: "Overview" })).toHaveAttribute("data-state", "active");
    expect(screen.getByTestId("overview-content")).toBeInTheDocument();
  });
});

describe("Customer detail Trust tab", () => {
  it("shows Trust tab with balance card when trust_accounting is enabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <CustomerTabs
          projectsPanel={placeholder}
          documentsPanel={placeholder}
          trustPanel={
            <TrustBalanceCard
              customerId="cust-1"
              slug="acme"
              showQuickActions={true}
            />
          }
        />,
      ),
    );

    expect(screen.getByRole("tab", { name: "Trust" })).toBeInTheDocument();
  });
});

describe("Trust settings page", () => {
  it("has a settings entry in SETTINGS_ITEMS with module gating", () => {
    const trustSetting = SETTINGS_ITEMS.find(
      (s) => s.title === "Trust Accounting",
    );
    expect(trustSetting).toBeDefined();
    expect(trustSetting!.adminOnly).toBe(true);
    expect(trustSetting!.requiredModule).toBe("trust_accounting");
    expect(trustSetting!.href("acme")).toBe(
      "/org/acme/settings/trust-accounting",
    );
    expect(trustSetting!.description).toContain("trust accounts");
  });
});

describe("TrustBalanceCard", () => {
  it("renders loading state", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" />,
      ),
    );

    expect(screen.getByText("Loading trust balance...")).toBeInTheDocument();
  });

  it("renders error state", () => {
    vi.mocked(useSWR).mockImplementation((key) => {
      if (key && typeof key === "string" && key.startsWith("trust-ledger-")) {
        return {
          data: undefined,
          error: new Error("Network error"),
          isLoading: false,
          mutate: vi.fn(),
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE", accountName: "Trust" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" />,
      ),
    );

    expect(screen.getByText("Unable to load trust balance")).toBeInTheDocument();
  });

  it("renders no-account empty state when no trust accounts exist", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" />,
      ),
    );

    expect(
      screen.getByText(/No trust account configured/),
    ).toBeInTheDocument();
  });

  it("renders balance data with Funds Held badge", () => {
    vi.mocked(useSWR).mockImplementation((key) => {
      if (key && typeof key === "string" && key.startsWith("trust-ledger-")) {
        return {
          data: {
            balance: 50000,
            totalDeposits: 80000,
            totalPayments: 25000,
            totalFeeTransfers: 5000,
            lastTransactionDate: "2026-03-15",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" trustAccountId="acc-1" />,
      ),
    );

    expect(screen.getByText("Funds Held")).toBeInTheDocument();
    // Balance is rendered by formatCurrency (ZAR) — "R 50,000.00" or "R\u00a050,000.00"
    expect(screen.getByText(/R[\s\u00a0]?50[,.]000/)).toBeInTheDocument();
  });

  it("renders Overdrawn badge for negative balance", () => {
    vi.mocked(useSWR).mockImplementation((key) => {
      if (key && typeof key === "string" && key.startsWith("trust-ledger-")) {
        return {
          data: {
            balance: -1000,
            totalDeposits: 5000,
            totalPayments: 6000,
            totalFeeTransfers: 0,
            lastTransactionDate: "2026-03-15",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(
        <TrustBalanceCard customerId="cust-1" slug="acme" trustAccountId="acc-1" />,
      ),
    );

    expect(screen.getByText("Overdrawn")).toBeInTheDocument();
  });

  it("renders quick action buttons when showQuickActions is true", () => {
    vi.mocked(useSWR).mockImplementation((key) => {
      if (key && typeof key === "string" && key.startsWith("trust-ledger-")) {
        return {
          data: {
            balance: 10000,
            totalDeposits: 10000,
            totalPayments: 0,
            totalFeeTransfers: 0,
            lastTransactionDate: null,
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(
        <TrustBalanceCard
          customerId="cust-1"
          slug="acme"
          trustAccountId="acc-1"
          showQuickActions={true}
        />,
      ),
    );

    expect(screen.getByRole("button", { name: /Record Deposit/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record Payment/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Fee Transfer/ })).toBeInTheDocument();
  });
});

describe("Sidebar nav items", () => {
  it("has 7 trust items in finance group with correct module and capability", () => {
    const financeGroup = NAV_GROUPS.find((g) => g.id === "finance");
    expect(financeGroup).toBeDefined();

    const trustItems = financeGroup!.items.filter(
      (item) => item.requiredModule === "trust_accounting",
    );
    expect(trustItems).toHaveLength(7);

    const expectedLabels = [
      "Trust Accounting",
      "Transactions",
      "Client Ledgers",
      "Reconciliation",
      "Interest",
      "Investments",
      "Trust Reports",
    ];

    for (const label of expectedLabels) {
      const item = trustItems.find((i) => i.label === label);
      expect(item).toBeDefined();
      expect(item!.requiredCapability).toBe("VIEW_TRUST");
      expect(item!.requiredModule).toBe("trust_accounting");
    }
  });
});
