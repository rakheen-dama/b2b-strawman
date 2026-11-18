import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";

// --- Mocks (before component imports) ---

// CustomerGroupedTabs/MatterTabs now import useAuditTabVisible from
// @/components/audit/audit-timeline-tab, which transitively pulls a
// `server-only` module via the audit data layer. Stub it for jsdom.
vi.mock("server-only", () => ({}));
vi.mock("@/lib/actions/audit-events", () => ({
  fetchEntityAuditPage: vi.fn(),
}));
// useAuditTabVisible reads from CapabilityProvider; this test doesn't
// exercise the audit gate, so stub the hook to a deterministic value.
vi.mock("@/components/audit/audit-timeline-tab", () => ({
  AuditTimelineTab: () => null,
  useAuditTabVisible: () => false,
}));

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

const mockReplace = vi.fn();
const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: mockReplace }),
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

// GroupedTabBar uses motion/react for the underline indicator
vi.mock("motion/react", () => ({
  motion: {
    span: "span",
  },
}));

// Mock the trust transaction actions to prevent server action imports in test env
vi.mock("@/app/(app)/org/[slug]/trust-accounting/transactions/actions", () => ({
  fetchTransactions: vi.fn().mockResolvedValue({ content: [] }),
  recordDeposit: vi.fn(),
  recordPayment: vi.fn(),
  recordFeeTransfer: vi.fn(),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: vi.fn().mockResolvedValue([]),
  // AddTrustAccountButton → CreateTrustAccountDialog (the settings tab renders it)
  createTrustAccount: vi.fn(),
}));

// 648C: the Fees & Trust › Trust accounting tab (an async server tab) is rendered below
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi
    .fn()
    .mockResolvedValue({ capabilities: [], role: "owner", isAdmin: false, isOwner: true }),
}));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/interest/actions", () => ({
  fetchLpffRates: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions", () => ({
  fetchClientLedger: vi.fn().mockResolvedValue(null),
  fetchClientHistory: vi.fn().mockResolvedValue({ content: [] }),
}));

// OBS-1001: trust transaction dialogs (embedded in TrustBalanceCard) now
// import the customer-projects server action for the dependent matter
// combobox. Stub it out so happy-dom doesn't try to load `server-only`.
vi.mock("@/app/(app)/org/[slug]/clients/[id]/actions", () => ({
  fetchCustomerProjects: vi.fn().mockResolvedValue([]),
}));

// --- Imports after mocks ---

import useSWR from "swr";
import { OrgProfileProvider } from "@/lib/org-profile";
import { MatterTabs } from "@/components/matters/matter-tabs";
import { CustomerGroupedTabs } from "@/components/customers/customer-grouped-tabs";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";
import { NAV_GROUPS } from "@/lib/nav-items";
import { getSettingsFamily, settingsHref } from "@/lib/settings-nav";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { renderTrustAccountingTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/trust-accounting";
import type { TrustAccount } from "@/lib/types/trust";

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
    <OrgProfileProvider verticalProfile={null} enabledModules={[]} terminologyNamespace={null}>
      {ui}
    </OrgProfileProvider>
  );
}

// Minimal placeholder panels for required MatterTabs props
const placeholder = <div>placeholder</div>;

// --- Tests ---

describe("Project detail Trust tab", () => {
  it("shows Trust tab when trust_accounting module is enabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
      isValidating: false,
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <MatterTabs
          detailsPanel={placeholder}
          fieldsPanel={placeholder}
          overviewPanel={placeholder}
          documentsPanel={placeholder}
          clientsPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={<TrustBalanceCard customerId="cust-1" slug="acme" showQuickActions={false} />}
        />
      )
    );

    // With GroupedTabBar, trust is a sub-tab within the Finance group.
    // The Finance group trigger is visible; trust is a dropdown menu item.
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
  });

  it("hides Trust tab when trust_accounting module is disabled", () => {
    render(
      withNoModules(
        <MatterTabs
          detailsPanel={placeholder}
          fieldsPanel={placeholder}
          overviewPanel={placeholder}
          documentsPanel={placeholder}
          clientsPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={<div data-testid="trust-content">Trust content</div>}
        />
      )
    );

    // Open the finance dropdown so Radix mounts the menu items
    const financeGroup = screen.getByTestId("tab-group-finance");
    fireEvent.click(financeGroup);

    expect(screen.queryByTestId("tab-item-trust")).not.toBeInTheDocument();
  });

  it("falls back to overview tab when ?tab=trust but trust module is disabled", () => {
    mockSearchParams.mockReturnValueOnce(new URLSearchParams("tab=trust"));

    render(
      withNoModules(
        <MatterTabs
          detailsPanel={placeholder}
          fieldsPanel={placeholder}
          overviewPanel={<div data-testid="overview-content">Overview</div>}
          documentsPanel={placeholder}
          clientsPanel={placeholder}
          tasksPanel={placeholder}
          timePanel={placeholder}
          activityPanel={placeholder}
          trustPanel={<div data-testid="trust-content">Trust</div>}
        />
      )
    );

    // Overview group should be active (fallback) — GroupedTabBar uses aria-selected
    expect(screen.getByTestId("tab-group-overview")).toHaveAttribute("aria-selected", "true");
    expect(screen.getByTestId("overview-content")).toBeInTheDocument();

    // Open the finance dropdown so Radix mounts the menu items
    const financeGroup = screen.getByTestId("tab-group-finance");
    fireEvent.click(financeGroup);

    // Trust tab should not be a visible sub-tab (module disabled, no trust in finance dropdown)
    expect(screen.queryByTestId("tab-item-trust")).not.toBeInTheDocument();
  });
});

describe("Customer detail Trust tab", () => {
  it("shows Trust tab group when trust_accounting is enabled", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
      isValidating: false,
    } as ReturnType<typeof useSWR>);

    render(
      withTrustEnabled(
        <CustomerGroupedTabs
          detailsPanel={placeholder}
          fieldsPanel={placeholder}
          tagsPanel={placeholder}
          overviewPanel={placeholder}
          mattersPanel={placeholder}
          documentsPanel={placeholder}
          trustPanel={<TrustBalanceCard customerId="cust-1" slug="acme" showQuickActions={true} />}
        />
      )
    );

    // With GroupedTabBar, trust is a sub-tab within the Finance group.
    expect(screen.getByTestId("tab-group-finance")).toBeInTheDocument();
  });
});

describe("Trust settings page", () => {
  it("is the Fees & Trust › Trust accounting tab with module gating", () => {
    const trustTab = getSettingsFamily("fees").tabs.find((t) => t.id === "trust-accounting");
    expect(trustTab).toBeDefined();
    expect(trustTab!.adminOnly).toBe(true);
    expect(trustTab!.requiredModule).toBe("trust_accounting");
    expect(settingsHref("acme", "fees", "trust-accounting")).toBe(
      "/org/acme/settings/fees?tab=trust-accounting"
    );
    expect(trustTab!.description).toContain("trust accounts");
  });

  // 648C (ADR-379): the section in the Act's words; an investment account's basis; no raw enum
  function account(overrides: Partial<TrustAccount>): TrustAccount {
    return {
      id: "acc",
      accountName: "Account",
      bankName: "Standard Bank",
      branchCode: "051001",
      accountNumber: "1234567890",
      accountType: "GENERAL",
      investmentBasis: null,
      section: "S86_2",
      isPrimary: false,
      requireDualApproval: false,
      paymentApprovalThreshold: null,
      status: "ACTIVE",
      openedDate: "2026-01-01",
      closedDate: null,
      notes: null,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      ...overrides,
    };
  }

  const RAW_ENUMS = [
    "GENERAL",
    "INVESTMENT",
    "SECTION_86",
    "FIRM_DISCRETION",
    "CLIENT_INSTRUCTION",
    "S86_2",
    "S86_3",
    "S86_4",
  ];

  // Case-sensitive, whole-word: "Section 86(3) investment account" is fine, "INVESTMENT" is not
  function expectNoRawEnum(el: HTMLElement) {
    for (const raw of RAW_ENUMS) {
      expect(el.textContent).not.toMatch(new RegExp(`\\b${raw}\\b`));
    }
  }

  it("shows each account's section in words — never the raw type", async () => {
    vi.mocked(fetchTrustAccounts).mockResolvedValueOnce([
      account({ id: "g", accountName: "Main Trust", isPrimary: true }),
      account({ id: "s", accountName: "Section 86 Trust", accountType: "SECTION_86" }),
    ]);

    render(await renderTrustAccountingTab());

    const card = within(screen.getByTestId("trust-accounts-section"));
    expect(card.getAllByText("Section 86(2) trust banking account")).toHaveLength(2);
    expectNoRawEnum(screen.getByTestId("trust-accounts-section"));
  });

  it("shows the investment basis on investment accounts only", async () => {
    vi.mocked(fetchTrustAccounts).mockResolvedValueOnce([
      account({ id: "g", accountName: "Main Trust", isPrimary: true }),
      account({
        id: "i3",
        accountName: "Call Account",
        accountType: "INVESTMENT",
        investmentBasis: "FIRM_DISCRETION",
        section: "S86_3",
      }),
      account({
        id: "i4",
        accountName: "Client Deposit",
        accountType: "INVESTMENT",
        investmentBasis: "CLIENT_INSTRUCTION",
        section: "S86_4",
      }),
    ]);

    render(await renderTrustAccountingTab());

    const card = within(screen.getByTestId("trust-accounts-section"));
    expect(card.getByText("Section 86(3) investment account")).toBeInTheDocument();
    expect(card.getByText("Invested at the firm's discretion")).toBeInTheDocument();
    expect(card.getByText("Section 86(4) investment account")).toBeInTheDocument();
    expect(card.getByText("Invested on a client's instruction")).toBeInTheDocument();
    // One basis line per investment account; the general account has none
    expect(card.getAllByTestId("trust-account-basis")).toHaveLength(2);
    const generalSection = card
      .getAllByTestId("trust-account-section")
      .find((el) => el.textContent === "Section 86(2) trust banking account");
    expect(generalSection).toBeDefined();
    expectNoRawEnum(screen.getByTestId("trust-accounts-section"));
  });
});

describe("TrustBalanceCard", () => {
  it("renders loading state", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: true,
      mutate: vi.fn(),
      isValidating: false,
    } as ReturnType<typeof useSWR>);

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" />));

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
          isValidating: false,
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE", accountName: "Trust" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      } as ReturnType<typeof useSWR>;
    });

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" />));

    expect(screen.getByText("Unable to load trust balance")).toBeInTheDocument();
  });

  it("renders no-account empty state when no trust accounts exist", () => {
    vi.mocked(useSWR).mockReturnValue({
      data: undefined,
      error: undefined,
      isLoading: false,
      mutate: vi.fn(),
      isValidating: false,
    } as ReturnType<typeof useSWR>);

    render(withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" />));

    expect(screen.getByText(/No trust account configured/)).toBeInTheDocument();
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
            totalRefunds: 0,
            totalTransfersIn: 0,
            totalTransfersOut: 0,
            totalInvestmentPlacements: 0,
            totalInvestmentReturns: 0,
            investedBalance: 0,
            lastTransactionDate: "2026-03-15",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" trustAccountId="acc-1" />)
    );

    expect(screen.getByText("Funds Held")).toBeInTheDocument();
    // locale-agnostic: accepts en-ZA "R 50 000,00" or en-US "R 50,000.00"
    expect(
      within(screen.getByTestId("trust-balance-primary")).getByText(
        /R[\s\u00a0]50[\s\u00a0,]000[,.]00/
      )
    ).toBeInTheDocument();
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
            totalRefunds: 0,
            totalTransfersIn: 0,
            totalTransfersOut: 0,
            totalInvestmentPlacements: 0,
            totalInvestmentReturns: 0,
            investedBalance: 0,
            lastTransactionDate: "2026-03-15",
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(<TrustBalanceCard customerId="cust-1" slug="acme" trustAccountId="acc-1" />)
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
            totalRefunds: 0,
            totalTransfersIn: 0,
            totalTransfersOut: 0,
            totalInvestmentPlacements: 0,
            totalInvestmentReturns: 0,
            investedBalance: 0,
            lastTransactionDate: null,
          },
          error: undefined,
          isLoading: false,
          mutate: vi.fn(),
          isValidating: false,
        } as ReturnType<typeof useSWR>;
      }
      return {
        data: [{ id: "acc-1", isPrimary: true, status: "ACTIVE" }],
        error: undefined,
        isLoading: false,
        mutate: vi.fn(),
        isValidating: false,
      } as ReturnType<typeof useSWR>;
    });

    render(
      withTrustEnabled(
        <TrustBalanceCard
          customerId="cust-1"
          slug="acme"
          trustAccountId="acc-1"
          showQuickActions={true}
        />
      )
    );

    expect(screen.getByRole("button", { name: /Record Deposit/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Record Payment/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Fee Transfer/ })).toBeInTheDocument();
  });
});

describe("Sidebar nav items", () => {
  it("has 8 trust items in the trust zone with correct module and capability", () => {
    const trustZone = NAV_GROUPS.find((g) => g.id === "trust");
    expect(trustZone).toBeDefined();

    const trustItems = trustZone!.items.filter(
      (item) => item.requiredModule === "trust_accounting"
    );
    expect(trustItems).toHaveLength(8);

    const expectedLabels = [
      "Overview",
      "Transactions",
      "Client ledgers",
      "Other trust creditors",
      "Reconciliation",
      "Interest",
      "Investments",
      "Trust reports",
    ];

    for (const label of expectedLabels) {
      const item = trustItems.find((i) => i.label === label);
      expect(item).toBeDefined();
      expect(item!.requiredCapability).toBe("VIEW_TRUST");
      expect(item!.requiredModule).toBe("trust_accounting");
    }
  });
});
