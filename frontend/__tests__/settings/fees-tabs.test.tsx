import React from "react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// Phase 91 665D: the six Settings › Fees & Trust tab loaders render their h1 through PageHeader in
// every branch (K-124: h1 = the tab label), so each page keeps exactly one level-1 heading; Tax
// configuration and Collections keep their per-card save (now in the card footer), Time recording
// and Fee runs keep their one Save for two sections (a page-level SettingsActionBar), and the
// Trust accounting cards become titled sections that carry their existing testids.

vi.mock("server-only", () => ({}));
const h = vi.hoisted(() => ({
  mockCaps: vi.fn(),
  mockApiGet: vi.fn(),
  mockGetCollections: vi.fn(),
  mockFetchTrustAccounts: vi.fn(),
  mockUpdateTax: vi.fn(),
  mockUpdateTime: vi.fn(),
  mockUpdateBatch: vi.fn(),
  mockUpdateCollections: vi.fn(),
  mockUpdateCurrency: vi.fn(),
}));

vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => h.mockCaps() }));
vi.mock("@/lib/api", () => ({
  api: { get: (...args: unknown[]) => h.mockApiGet(...args) },
}));
vi.mock("@/lib/api/collections", () => ({
  getCollectionsSettings: () => h.mockGetCollections(),
}));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchTrustAccounts: () => h.mockFetchTrustAccounts(),
  // AddTrustAccountButton → CreateTrustAccountDialog (the settings tab renders it)
  createTrustAccount: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/tax-actions", () => ({
  updateTaxSettings: (...args: unknown[]) => h.mockUpdateTax(...args),
  createTaxRate: vi.fn(),
  updateTaxRate: vi.fn(),
  deactivateTaxRate: vi.fn(),
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/time-recording-actions", () => ({
  updateTimeTrackingSettings: (...args: unknown[]) => h.mockUpdateTime(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs-actions", () => ({
  updateBatchBillingSettings: (...args: unknown[]) => h.mockUpdateBatch(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/collections-actions", () => ({
  updateCollectionsSettings: (...args: unknown[]) => h.mockUpdateCollections(...args),
}));
vi.mock("@/app/(app)/org/[slug]/settings/fees/_tabs/tariffs-actions", () => ({
  updateDefaultCurrency: (...args: unknown[]) => h.mockUpdateCurrency(...args),
  createBillingRate: vi.fn(),
  updateBillingRate: vi.fn(),
  deleteBillingRate: vi.fn(),
  createCostRate: vi.fn(),
  updateCostRate: vi.fn(),
  deleteCostRate: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn(), replace: vi.fn() }),
}));
vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    ...props
  }: {
    children: React.ReactNode;
    href: string;
    [k: string]: unknown;
  }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));
vi.mock("@/components/help-tip", () => ({
  HelpTip: ({ code }: { code: string }) => <span data-testid={`help-tip-${code}`} />,
}));

import { renderTariffsTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/tariffs";
import { renderTaxTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/tax";
import { renderTimeRecordingTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/time-recording";
import { renderFeeRunsTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs";
import { renderCollectionsTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/collections";
import { renderTrustAccountingTab } from "@/app/(app)/org/[slug]/settings/fees/_tabs/trust-accounting";
import type { TrustAccount } from "@/lib/types/trust";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const MEMBER = { capabilities: [], role: "member", isAdmin: false, isOwner: false };
const TAB = { slug: "acme", searchParams: {} };

const SETTINGS = {
  defaultCurrency: "ZAR",
  taxRegistrationNumber: "4000000000",
  taxRegistrationLabel: "VAT Number",
  taxLabel: "VAT",
  taxInclusive: false,
  timeReminderEnabled: false,
  timeReminderDays: "MON,TUE,WED,THU,FRI",
  timeReminderTime: "17:00",
  timeReminderMinHours: 4,
  defaultExpenseMarkupPercent: null,
  billingBatchAsyncThreshold: 50,
  billingEmailRateLimit: 5,
  defaultBillingRunCurrency: null,
  defaultPaymentTermsDays: 30,
};

const MEMBERS = [
  { id: "m1", name: "Alice Owner", email: "alice@example.com", avatarUrl: null, orgRole: "owner" },
];

function trustAccount(overrides: Partial<TrustAccount>): TrustAccount {
  return {
    id: "acc",
    accountName: "Main Trust",
    bankName: "Standard Bank",
    branchCode: "051001",
    accountNumber: "1234567890",
    accountType: "GENERAL",
    investmentBasis: null,
    section: "S86_2",
    isPrimary: true,
    requireDualApproval: false,
    paymentApprovalThreshold: null,
    status: "ACTIVE",
    openedDate: "2026-01-01",
    closedDate: null,
    notes: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    lockedThrough: null,
    takeOnCutoverDate: null,
    ...overrides,
  };
}

function expectOneH1(text: string) {
  const h1s = screen.getAllByRole("heading", { level: 1 });
  expect(h1s).toHaveLength(1);
  expect(h1s[0]).toHaveTextContent(new RegExp(`^${text}$`));
  expect(h1s[0].closest('[data-slot="page-header"]')).not.toBeNull();
  return h1s[0];
}

function cardFooters() {
  return Array.from(document.querySelectorAll<HTMLElement>('[data-slot="card-footer"]'));
}

function actionBars() {
  return Array.from(document.querySelectorAll<HTMLElement>('[data-slot="settings-action-bar"]'));
}

beforeEach(() => {
  h.mockApiGet.mockImplementation((url: string) => {
    if (url === "/api/members") return Promise.resolve(MEMBERS);
    if (url === "/api/billing-rates" || url === "/api/cost-rates")
      return Promise.resolve({ content: [] });
    if (url.startsWith("/api/tax-rates")) return Promise.resolve([]);
    return Promise.resolve(SETTINGS);
  });
  h.mockGetCollections.mockResolvedValue({
    collectionsEnabled: false,
    stage1DaysOverdue: 7,
    stage2DaysOverdue: 21,
    stage3DaysOverdue: 45,
    escalateDaysOverdue: 60,
  });
  h.mockFetchTrustAccounts.mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

type Loader = (tab: typeof TAB) => Promise<React.ReactNode>;
const LOADERS: Array<[string, Loader, string]> = [
  ["Rates", renderTariffsTab, "Rates"],
  ["Tax", renderTaxTab, "Tax"],
  ["Time recording", renderTimeRecordingTab, "Time recording"],
  ["Fee runs", renderFeeRunsTab, "Fee runs"],
  ["Collections", renderCollectionsTab, "Collections"],
  ["Trust accounting", () => renderTrustAccountingTab(), "Trust accounting"],
];

describe("Fees & Trust tab loaders: one h1 (the tab label, K-124) through PageHeader", () => {
  it.each(LOADERS)("%s permission branch: one h1 and the permission line", async (_t, load, h1) => {
    h.mockCaps.mockResolvedValue(MEMBER);
    render(await load(TAB));

    expectOneH1(h1);
    expect(screen.getByText(/You do not have permission/)).toBeInTheDocument();
    expect(document.querySelectorAll('[data-slot="settings-section"]')).toHaveLength(0);
  });

  it.each(LOADERS)("%s owner branch: one h1", async (_t, load, h1) => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await load(TAB));

    expectOneH1(h1);
    expect(screen.queryByText(/You do not have permission/)).toBeNull();
  });

  it("Rates: the HelpTip sits in the owner h1 only, and the description is the header's", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTariffsTab(TAB));
    const h1 = expectOneH1("Rates");
    expect(within(h1).getByTestId("help-tip-rates.hierarchy")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Manage billing rates, cost rates, and the default currency for your organization."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
    cleanup();

    h.mockCaps.mockResolvedValue(MEMBER);
    render(await renderTariffsTab(TAB));
    expect(screen.queryByTestId("help-tip-rates.hierarchy")).toBeNull();
    expect(
      screen.getByText(
        "You do not have permission to manage rates and currency settings. Only admins and owners can access this page."
      )
    ).toBeInTheDocument();
  });

  it("Rates: a FINANCIAL_VISIBILITY holder gets the rates content", async () => {
    h.mockCaps.mockResolvedValue({ ...MEMBER, capabilities: ["FINANCIAL_VISIBILITY"] });
    render(await renderTariffsTab(TAB));

    expectOneH1("Rates");
    expect(screen.getByTestId("billing-rate-owner")).toBeInTheDocument();
  });

  it("Fee runs: the description text is unchanged", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderFeeRunsTab(TAB));

    expect(
      screen.getByText(
        "Set the default payment terms for fee notes, and configure async thresholds, email rate limits, and default billing currency for batch billing runs."
      )
    ).toHaveAttribute("data-slot", "page-header-description");
  });
});

describe("Save models are preserved", () => {
  it("Tax configuration: per-card 'Save Settings' in the footer, same payload, message there", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockUpdateTax.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(await renderTaxTab(TAB));

    expect(screen.getByRole("heading", { level: 2, name: "Tax Configuration" })).toBeVisible();
    expect(screen.getByRole("heading", { level: 2, name: "Tax Rates" })).toBeVisible();
    expect(actionBars()).toHaveLength(0);
    const footers = cardFooters();
    expect(footers).toHaveLength(1);
    const save = within(footers[0]).getByRole("button", { name: "Save Settings" });

    await user.click(save);
    expect(h.mockUpdateTax).toHaveBeenCalledWith("acme", {
      taxRegistrationNumber: "4000000000",
      taxRegistrationLabel: "VAT Number",
      taxLabel: "VAT",
      taxInclusive: false,
    });
    expect(await within(footers[0]).findByText("Tax settings updated.")).toHaveClass(
      "text-success-foreground"
    );
  });

  it.each([
    ["Time recording", renderTimeRecordingTab, ["Time Reminders", "Default Disbursement Markup"]],
    ["Fee runs", renderFeeRunsTab, ["Fee note defaults", "Fee run processing"]],
  ] as const)(
    "%s: two sections and one page-level Save (no card footer)",
    async (_t, load, titles) => {
      h.mockCaps.mockResolvedValue(OWNER);
      render(await load(TAB));

      const sections = document.querySelectorAll<HTMLElement>('[data-slot="settings-section"]');
      expect(sections).toHaveLength(2);
      titles.forEach((title, i) => {
        expect(within(sections[i]).getByRole("heading", { level: 2, name: title })).toBeVisible();
      });
      expect(cardFooters()).toHaveLength(0);
      const bars = actionBars();
      expect(bars).toHaveLength(1);
      expect(screen.getAllByRole("button", { name: "Save Settings" })).toHaveLength(1);
      expect(within(bars[0]).getByRole("button", { name: "Save Settings" })).toBeEnabled();
    }
  );

  it("Time recording: the page-level Save still sends both sections' values", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockUpdateTime.mockResolvedValue({ success: true });
    const user = userEvent.setup();
    render(await renderTimeRecordingTab(TAB));

    const bar = actionBars()[0];
    await user.click(within(bar).getByRole("button", { name: "Save Settings" }));
    expect(h.mockUpdateTime).toHaveBeenCalledWith("acme", {
      timeReminderEnabled: false,
      timeReminderDays: "MON,TUE,WED,THU,FRI",
      timeReminderTime: "17:00",
      timeReminderMinHours: 4,
      defaultExpenseMarkupPercent: null,
    });
    expect(await within(bar).findByText("Time tracking settings updated.")).toBeInTheDocument();
  });

  it("Collections: per-card Save in the footer; a validation error renders there as an alert", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderCollectionsTab(TAB));

    expect(
      screen.getByRole("heading", { level: 2, name: "Overdue fee-note reminders" })
    ).toBeVisible();
    expect(actionBars()).toHaveLength(0);
    const footers = cardFooters();
    expect(footers).toHaveLength(1);
    expect(screen.getByRole("switch")).toHaveAttribute("id", "collections-toggle");

    fireEvent.change(screen.getByLabelText("Stage 2 (days overdue)"), { target: { value: "5" } });
    fireEvent.click(within(footers[0]).getByRole("button", { name: "Save Settings" }));

    const alert = await within(footers[0]).findByRole("alert");
    expect(alert).toHaveTextContent(
      "Thresholds must strictly increase: stage 1 < stage 2 < stage 3 < escalation."
    );
    expect(alert).toHaveAttribute("aria-live", "polite");
    expect(h.mockUpdateCollections).not.toHaveBeenCalled();
  });
});

describe("Trust accounting: titled sections with their testids", () => {
  it("each card is a section with an h2 title; Add Account sits in Trust Accounts", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockFetchTrustAccounts.mockResolvedValue([trustAccount({})]);
    render(await renderTrustAccountingTab());

    const page = screen.getByTestId("trust-settings-page");
    expect(within(page).getByRole("heading", { level: 1 })).toHaveTextContent(/^Trust accounting$/);
    for (const [testId, title] of [
      ["trust-accounts-section", "Trust Accounts"],
      ["approval-settings-section", "Approval Settings"],
      ["reminder-settings-section", "Reminder Settings"],
    ]) {
      const section = screen.getByTestId(testId);
      expect(section).toHaveAttribute("data-slot", "settings-section");
      expect(within(section).getByRole("heading", { level: 2 })).toHaveTextContent(
        new RegExp(`^${title}$`)
      );
    }
    const accounts = screen.getByTestId("trust-accounts-section");
    expect(within(accounts).getByTestId("add-trust-account-button")).toBeInTheDocument();
    expect(within(accounts).getByText("Main Trust")).toBeInTheDocument();
    expect(within(accounts).getByTestId("section-86-6-settings-advisory")).toBeInTheDocument();
  });

  it("an ACTIVE account reads 'Active' (K-124) — never the raw enum", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    h.mockFetchTrustAccounts.mockResolvedValue([trustAccount({})]);
    render(await renderTrustAccountingTab());

    const accounts = screen.getByTestId("trust-accounts-section");
    expect(within(accounts).getByText("Active")).toHaveAttribute("data-variant", "success");
    expect(screen.getByTestId("trust-settings-page").textContent).not.toMatch(/\bACTIVE\b/);
  });

  it("no accounts: the two sentences stay plain text inside their sections", async () => {
    h.mockCaps.mockResolvedValue(OWNER);
    render(await renderTrustAccountingTab());

    expect(
      within(screen.getByTestId("trust-accounts-section")).getByText(
        "No trust accounts configured. Add a trust account to start tracking client funds."
      )
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId("approval-settings-section")).getByText(
        "Add a trust account to configure approval settings."
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId("section-86-6-settings-advisory")).toBeNull();
  });
});
