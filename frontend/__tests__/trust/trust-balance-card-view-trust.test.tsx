import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";

// G89-F64: a member without VIEW_TRUST opened a client's Trust tab. The card's SWR fetcher called
// the `fetchTrustAccounts` server action, the backend answered 403, the action threw (the Next
// server action POST surfaced as HTTP 500) and the card then read "No trust account configured".
// Without VIEW_TRUST the card must not call any trust endpoint and must say the details are not
// available.

vi.mock("server-only", () => ({}));

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

import useSWR from "swr";
import { CapabilityProvider } from "@/lib/capabilities";
import { OrgProfileProvider } from "@/lib/org-profile";
import { TrustBalanceCard } from "@/components/trust/TrustBalanceCard";

afterEach(() => cleanup());
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSWR).mockImplementation(((key: string | null) => ({
    data: key?.startsWith("trust-accounts-") ? [] : undefined,
    error: undefined,
    isLoading: false,
    mutate: vi.fn(),
    isValidating: false,
  })) as typeof useSWR);
});

function renderAs(capabilities: string[], ui: React.ReactElement) {
  return render(
    <CapabilityProvider capabilities={capabilities} role="member" isAdmin={false} isOwner={false}>
      <OrgProfileProvider
        verticalProfile="legal-za"
        enabledModules={["trust_accounting"]}
        terminologyNamespace={null}
      >
        {ui}
      </OrgProfileProvider>
    </CapabilityProvider>
  );
}

function requestedKeys(): (string | null)[] {
  return vi.mocked(useSWR).mock.calls.map((call) => call[0] as string | null);
}

describe("TrustBalanceCard without VIEW_TRUST (G89-F64)", () => {
  it("client mode: says trust details are not available and calls no trust endpoint", () => {
    renderAs(["VIEW_LEGAL"], <TrustBalanceCard customerId="cust-1" slug="acme" />);

    expect(screen.getByText("Trust details are not available")).toBeInTheDocument();
    expect(screen.queryByText(/No trust account configured/)).toBeNull();
    expect(requestedKeys().every((key) => key === null)).toBe(true);
  });

  it("matter mode: same, and the matter balance is not fetched either", () => {
    renderAs(
      ["VIEW_LEGAL"],
      <TrustBalanceCard customerId="cust-1" projectId="proj-1" slug="acme" />
    );

    expect(screen.getByText("Trust details are not available")).toBeInTheDocument();
    expect(requestedKeys().every((key) => key === null)).toBe(true);
  });

  it("with VIEW_TRUST the card still loads the trust accounts", () => {
    renderAs(["VIEW_TRUST"], <TrustBalanceCard customerId="cust-1" slug="acme" />);

    expect(requestedKeys()).toContain("trust-accounts-acme");
    expect(screen.queryByText("Trust details are not available")).toBeNull();
  });
});
