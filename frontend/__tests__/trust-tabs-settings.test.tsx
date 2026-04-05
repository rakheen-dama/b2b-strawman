import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// --- Mocks (before component imports) ---

vi.mock("swr", () => ({ default: vi.fn() }));

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
  usePathname: () => "/org/acme/projects/proj-1",
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
  it("has a settings entry in SETTINGS_ITEMS", () => {
    const trustSetting = SETTINGS_ITEMS.find(
      (s) => s.title === "Trust Accounting",
    );
    expect(trustSetting).toBeDefined();
    expect(trustSetting!.adminOnly).toBe(true);
    expect(trustSetting!.href("acme")).toBe(
      "/org/acme/settings/trust-accounting",
    );
    expect(trustSetting!.description).toContain("trust accounts");
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
