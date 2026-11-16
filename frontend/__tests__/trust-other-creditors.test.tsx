import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import { formatCurrency } from "@/lib/format";
import type { TrustCreditorAccount } from "@/lib/types";

// Must mock server-only before importing components that use it
vi.mock("server-only", () => ({}));

// ── Mock org settings & capabilities ─────────────────────────────
const mockGetOrgSettings = vi.fn();
vi.mock("@/lib/api/settings", () => ({
  getOrgSettings: (...args: unknown[]) => mockGetOrgSettings(...args),
}));

const mockFetchMyCapabilities = vi.fn();
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: (...args: unknown[]) => mockFetchMyCapabilities(...args),
}));

// ── Mock parent trust actions ────────────────────────────────────
const mockFetchTrustAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: (...args: unknown[]) => mockFetchTrustAccounts(...args),
}));

// ── Mock creditor ledger actions ─────────────────────────────────
const mockFetchCreditorAccounts = vi.fn();
vi.mock("@/app/(app)/org/[slug]/trust-accounting/other-creditors/actions", () => ({
  fetchCreditorAccounts: (...args: unknown[]) => mockFetchCreditorAccounts(...args),
}));

// ── Mock next/navigation ─────────────────────────────────────────
vi.mock("next/navigation", () => ({
  notFound: () => {
    throw new Error("NEXT_NOT_FOUND");
  },
}));

import OtherTrustCreditorsPage from "@/app/(app)/org/[slug]/trust-accounting/other-creditors/page";

const PRIMARY_ACCOUNT = {
  id: "acc-1",
  accountName: "Main Trust",
  bankName: "FNB",
  branchCode: "250655",
  accountNumber: "1234567890",
  accountType: "GENERAL",
  isPrimary: true,
  requireDualApproval: false,
  paymentApprovalThreshold: null,
  status: "ACTIVE",
  openedDate: "2025-01-01",
  closedDate: null,
  notes: null,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

function creditorAccounts(fund: { balance: number; inDebit: boolean }): TrustCreditorAccount[] {
  return [
    {
      id: "ca-fund",
      trustAccountId: "acc-1",
      accountType: "FUND_INTEREST_AND_CHARGES",
      name: "Legal Practitioners' Fidelity Fund — interest and charges",
      balance: fund.balance,
      inDebit: fund.inDebit,
      asAt: "2026-11-16",
    },
    {
      id: "ca-unidentified",
      trustAccountId: "acc-1",
      accountType: "UNIDENTIFIED_DEPOSITS",
      name: "Unidentified deposits",
      balance: 1250,
      inDebit: false,
      asAt: "2026-11-16",
    },
  ];
}

async function renderPage() {
  const result = await OtherTrustCreditorsPage({
    params: Promise.resolve({ slug: "test-org" }),
  });
  render(result);
}

describe("Other trust creditors page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetOrgSettings.mockResolvedValue({
      enabledModules: ["trust_accounting"],
      defaultCurrency: "ZAR",
    });
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: ["VIEW_TRUST"],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });
    mockFetchTrustAccounts.mockResolvedValue([PRIMARY_ACCOUNT]);
    mockFetchCreditorAccounts.mockResolvedValue(
      creditorAccounts({ balance: 300.25, inDebit: false })
    );
  });

  afterEach(() => cleanup());

  it("shows both creditor ledgers with their balances, never the raw account type", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Other trust creditors");
    expect(mockFetchCreditorAccounts).toHaveBeenCalledWith("acc-1");

    const fund = screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES");
    expect(
      within(fund).getByText("Legal Practitioners' Fidelity Fund — interest and charges")
    ).toBeInTheDocument();
    // formatCurrency emits a non-breaking space; compare the raw text, not the normalised one
    expect(fund.textContent).toContain(formatCurrency(300.25, "ZAR"));

    const unidentified = screen.getByTestId("creditor-UNIDENTIFIED_DEPOSITS");
    expect(within(unidentified).getByText("Unidentified deposits")).toBeInTheDocument();
    expect(unidentified.textContent).toContain(formatCurrency(1250, "ZAR"));

    const page = screen.getByTestId("other-creditors-page");
    expect(page).not.toHaveTextContent("FUND_INTEREST_AND_CHARGES");
    expect(page).not.toHaveTextContent("UNIDENTIFIED_DEPOSITS");
  });

  it("flags a Fund ledger in debit, and shows no notice when it is not", async () => {
    mockFetchCreditorAccounts.mockResolvedValue(
      creditorAccounts({ balance: -172.5, inDebit: true })
    );
    await renderPage();

    const notice = screen.getByTestId("fund-ledger-in-debit");
    expect(notice).toHaveAttribute("role", "status");
    expect(notice.textContent).toContain(formatCurrency(172.5, "ZAR"));
    expect(notice).toHaveTextContent("in debit");
    expect(
      within(screen.getByTestId("creditor-FUND_INTEREST_AND_CHARGES")).getByTestId(
        "fund-ledger-in-debit"
      )
    ).toBeInTheDocument();

    cleanup();
    mockFetchCreditorAccounts.mockResolvedValue(creditorAccounts({ balance: 0, inDebit: false }));
    await renderPage();
    expect(screen.queryByTestId("fund-ledger-in-debit")).not.toBeInTheDocument();
  });

  it("is not found when the trust accounting module is off", async () => {
    mockGetOrgSettings.mockResolvedValue({ enabledModules: [], defaultCurrency: "ZAR" });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockFetchTrustAccounts).not.toHaveBeenCalled();
    expect(mockFetchCreditorAccounts).not.toHaveBeenCalled();
  });

  it("is not found without VIEW_TRUST for a member who is neither admin nor owner", async () => {
    mockFetchMyCapabilities.mockResolvedValue({
      capabilities: ["VIEW_PROJECTS"],
      role: "member",
      isAdmin: false,
      isOwner: false,
    });

    await expect(renderPage()).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mockFetchTrustAccounts).not.toHaveBeenCalled();
    expect(mockFetchCreditorAccounts).not.toHaveBeenCalled();
  });
});
