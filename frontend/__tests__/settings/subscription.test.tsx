import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { renderSubscriptionTab } from "@/app/(app)/org/[slug]/settings/firm/_tabs/subscription";
import type { BillingResponse } from "@/lib/internal-api";
vi.mock("@/lib/actions/subscription", () => ({
  getSubscription: vi.fn(),
}));

vi.mock("@b2mash/ui/badge", () => ({
  Badge: ({ children, variant }: { children: React.ReactNode; variant: string }) => (
    <span data-testid="badge" data-variant={variant}>
      {children}
    </span>
  ),
}));

vi.mock("@b2mash/ui/card", () => ({
  Card: ({ children, className }: { children: React.ReactNode; className?: string }) => (
    <div data-testid="card" className={className}>
      {children}
    </div>
  ),
  CardHeader: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="card-header">{children}</div>
  ),
  CardTitle: ({ children, className }: { children: React.ReactNode; className?: string }) => (
    <h2 data-testid="card-title" className={className}>
      {children}
    </h2>
  ),
  CardContent: ({ children, className }: { children: React.ReactNode; className?: string }) => (
    <div data-testid="card-content" className={className}>
      {children}
    </div>
  ),
}));

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

    expect(screen.getByText("Trial")).toBeInTheDocument();
    expect(screen.getByText("Manual")).toBeInTheDocument();
    expect(screen.getByText("Managed Account")).toBeInTheDocument();
    expect(screen.getByText(/managed by your administrator/)).toBeInTheDocument();
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
