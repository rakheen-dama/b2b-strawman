import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { renderSubscriptionTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/subscription";
import type { BillingResponse } from "@/lib/internal-api";
vi.mock("@/lib/actions/subscription", () => ({
  getSubscription: vi.fn(),
}));

// Real Badge / Card / SettingsSection (server-safe): the assertions read roles and text.

afterEach(() => cleanup());

const { getSubscription } = await import("@/lib/actions/subscription");
const mockGetSubscription = getSubscription as ReturnType<typeof vi.fn>;

function makeBilling(overrides: Partial<BillingResponse> = {}): BillingResponse {
  return {
    status: "ACTIVE",
    trialEndsAt: null,
    currentPeriodEnd: null,
    graceEndsAt: null,
    monthlyAmountCents: 49900,
    currency: "ZAR",
    limits: { maxMembers: 10, currentMembers: 3 },
    billingMethod: "MANUAL",
    adminNote: null,
    ...overrides,
  };
}

async function renderPage() {
  const page = await renderSubscriptionTab();
  render(page);
}

describe("Firm › Subscription tab loader (manual firm-level billing)", () => {
  it("shows status badge, method badge and managed-account card for TRIALING", async () => {
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "TRIALING",
        trialEndsAt: new Date(Date.now() + 7 * 86_400_000).toISOString(),
      })
    );

    await renderPage();

    // K-106 (founder-approved copy): h1 matches the tab label; chips read as words.
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: /^Subscription/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Billing/ })).not.toBeInTheDocument();
    expect(screen.getByText("Trial period")).toBeInTheDocument();
    expect(screen.getByText("Invoiced manually")).toBeInTheDocument();
    expect(screen.queryByText("Manual")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Managed Account" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "Your Kazi subscription is managed by b2mash. Contact support to change your plan or billing details."
      )
    ).toBeInTheDocument();
    expect(screen.queryByText(/managed by your administrator/)).not.toBeInTheDocument();
  });

  it("puts the status and method chips in the page header toolbar, not inside the h1", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling({ status: "ACTIVE" }));

    await renderPage();

    const h1 = screen.getByRole("heading", { level: 1 });
    const chip = screen.getByText("Invoiced manually");
    expect(h1.contains(chip)).toBe(false);
    expect(chip.closest('[data-slot="page-header-toolbar"]')).not.toBeNull();
    expect(screen.getByText("Active").closest('[data-slot="page-header-toolbar"]')).not.toBeNull();
    // Read-only: the Managed Account section has no footer.
    const section = screen
      .getByRole("heading", { name: "Managed Account" })
      .closest('[data-slot="settings-section"]') as HTMLElement;
    expect(section).not.toBeNull();
    expect(section.querySelector('[data-slot="card-footer"]')).toBeNull();
  });

  it("keeps non-manual billing methods on the shared MethodBadge", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling({ billingMethod: "COMPLIMENTARY" }));

    await renderPage();

    expect(screen.getByText("Complimentary")).toBeInTheDocument();
    expect(screen.queryByText("Invoiced manually")).not.toBeInTheDocument();
  });

  it("shows the restore-access hint for GRACE_PERIOD", async () => {
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "GRACE_PERIOD",
        graceEndsAt: new Date(Date.now() + 30 * 86_400_000).toISOString(),
      })
    );

    await renderPage();

    expect(screen.getByText("Grace Period")).toBeInTheDocument();
    expect(screen.getByText(/Contact your administrator to restore access\./)).toBeInTheDocument();
  });

  it("shows the account-locked card for LOCKED", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling({ status: "LOCKED" }));

    await renderPage();

    expect(screen.getByText("Locked")).toBeInTheDocument();
    expect(screen.getByText("Account Locked")).toBeInTheDocument();
    // "Account Locked" is a CardTitle div, not a heading: the page keeps exactly one h1.
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.queryByRole("heading", { name: "Account Locked" })).not.toBeInTheDocument();
  });

  it("renders no self-serve subscription UI", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling({ status: "ACTIVE" }));

    await renderPage();

    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.queryByText(/subscribe/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/PayFast/)).not.toBeInTheDocument();
    expect(screen.queryByText("Payment History")).not.toBeInTheDocument();
    expect(screen.queryByText(/Cancel Subscription/)).not.toBeInTheDocument();
    expect(screen.queryByText("R499.00")).not.toBeInTheDocument();
  });
});
