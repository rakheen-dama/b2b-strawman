import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { TrustPositionWidget } from "@/components/dashboard/trust-position-widget";
import type { TrustPositionResponse } from "@/lib/dashboard-types";

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

const BALANCED: TrustPositionResponse = {
  trustAccountId: "ta1",
  accountName: "Trust account",
  bankName: "Standard Bank",
  cashbookBalance: 150000,
  clientLedgerTotal: 150000,
  creditorBreakdown: {
    clientLedgers: 150000,
    investmentCreditors: 0,
    fundInterestAndCharges: 0,
    unidentifiedDeposits: 0,
    otherNonClient: 0,
    interAccount: 0,
    total: 150000,
  },
  difference: 0,
  lastReconciliationPeriodEnd: "2026-09-30",
  lastReconciliationStatus: "COMPLETED",
  lastReconciliationBalanced: true,
  daysSinceReconciliation: 21,
  pendingApprovalCount: 2,
  accounts: [{ id: "ta1", accountName: "Trust account", isPrimary: true }],
  integrity: { balanced: true, flags: [] },
};

describe("TrustPositionWidget", () => {
  afterEach(() => cleanup());

  it("balanced: 'Balanced' badge, bank vs trust creditors, last reconciliation, pending approvals", () => {
    render(<TrustPositionWidget slug="acme" data={BALANCED} />);

    expect(screen.getByTestId("trust-position-widget")).toBeInTheDocument();
    expect(screen.getByText("Balanced")).toHaveAttribute("data-tone", "success");
    expect(screen.queryByText(/^Difference/)).not.toBeInTheDocument();
    expect(screen.getByText("Bank cashbook")).toBeInTheDocument();
    expect(screen.getByText("Trust creditors")).toBeInTheDocument();
    expect(screen.getByText(/Period to/)).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open reconciliation" })).toHaveAttribute(
      "href",
      "/org/acme/trust-accounting/reconciliation"
    );
    expect(screen.queryByText(/Primary of/)).not.toBeInTheDocument();
  });

  it("difference: 'Difference R x' badge carries the amount", () => {
    render(
      <TrustPositionWidget
        slug="acme"
        data={{
          ...BALANCED,
          clientLedgerTotal: 149000,
          creditorBreakdown: {
            ...BALANCED.creditorBreakdown,
            clientLedgers: 149000,
            total: 149000,
          },
          difference: 1000,
        }}
      />
    );

    const badge = screen.getByText(/^Difference/);
    expect(badge.textContent).toMatch(/R\s?1\s?000/);
    expect(badge).toHaveAttribute("data-tone", "warning");
    expect(screen.queryByText("Balanced")).not.toBeInTheDocument();
    expect(screen.getByTestId("trust-position-headline").textContent).toMatch(/1\s?000/);
  });

  it("compares the cashbook with every trust creditor (646D)", () => {
    render(
      <TrustPositionWidget
        slug="acme"
        data={{
          ...BALANCED,
          cashbookBalance: 150550,
          clientLedgerTotal: 150000,
          creditorBreakdown: {
            ...BALANCED.creditorBreakdown,
            fundInterestAndCharges: 250,
            unidentifiedDeposits: 300,
            total: 150550,
          },
          difference: 0,
        }}
      />
    );
    expect(screen.getByText("Balanced")).toBeInTheDocument();
    expect(screen.getByText("Trust creditors")).toBeInTheDocument();
    expect(screen.getByTestId("trust-position-creditors").textContent).toMatch(/150\s?550/);
    expect(screen.queryByText("Client ledgers")).not.toBeInTheDocument();
  });

  it("never reconciled: says so", () => {
    render(
      <TrustPositionWidget
        slug="acme"
        data={{
          ...BALANCED,
          lastReconciliationPeriodEnd: null,
          lastReconciliationStatus: null,
          lastReconciliationBalanced: null,
          daysSinceReconciliation: null,
        }}
      />
    );
    expect(screen.getByText("Never reconciled")).toBeInTheDocument();
  });

  it("more than one trust account: notes the primary is shown", () => {
    render(
      <TrustPositionWidget
        slug="acme"
        data={{
          ...BALANCED,
          accounts: [
            ...BALANCED.accounts,
            { id: "ta2", accountName: "Investment trust", isPrimary: false },
          ],
        }}
      />
    );
    expect(screen.getByText("Primary of 2 trust accounts")).toBeInTheDocument();
  });

  it("error: quiet error line", () => {
    render(<TrustPositionWidget slug="acme" data={null} />);
    expect(screen.getByText(/could not be loaded/)).toBeInTheDocument();
  });
});
