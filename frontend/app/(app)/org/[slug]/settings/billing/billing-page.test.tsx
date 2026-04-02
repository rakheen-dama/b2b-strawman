import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import BillingPage from "./page";
import type { BillingResponse } from "@/lib/internal-api";

vi.mock("@/app/(app)/org/[slug]/settings/billing/actions", () => ({
  getSubscription: vi.fn(),
  getPayments: vi.fn(),
}));

vi.mock("@/components/ui/badge", () => ({
  Badge: ({
    children,
    variant,
  }: {
    children: React.ReactNode;
    variant: string;
  }) => (
    <span data-testid="badge" data-variant={variant}>
      {children}
    </span>
  ),
}));

vi.mock("@/components/ui/card", () => ({
  Card: ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => (
    <div data-testid="card" className={className}>
      {children}
    </div>
  ),
  CardHeader: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="card-header">{children}</div>
  ),
  CardTitle: ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => (
    <h2 data-testid="card-title" className={className}>
      {children}
    </h2>
  ),
  CardContent: ({
    children,
    className,
  }: {
    children: React.ReactNode;
    className?: string;
  }) => (
    <div data-testid="card-content" className={className}>
      {children}
    </div>
  ),
}));

vi.mock("@/components/billing/subscribe-button", () => ({
  SubscribeButton: () => (
    <button data-testid="subscribe-button">Subscribe</button>
  ),
}));

vi.mock("@/components/billing/cancel-confirm-dialog", () => ({
  CancelConfirmDialog: ({
    currentPeriodEnd,
  }: {
    currentPeriodEnd: string;
  }) => (
    <button data-testid="cancel-dialog" data-period-end={currentPeriodEnd}>
      Cancel Subscription
    </button>
  ),
}));

vi.mock("@/components/billing/payment-history", () => ({
  PaymentHistory: () => (
    <div data-testid="payment-history">Payment History Table</div>
  ),
}));

vi.mock("@/components/billing/trial-countdown", () => ({
  TrialCountdown: ({ trialEndsAt }: { trialEndsAt: string }) => (
    <div data-testid="trial-countdown" data-trial-ends-at={trialEndsAt}>
      Trial Countdown
    </div>
  ),
}));

vi.mock("@/components/billing/grace-countdown", () => ({
  GraceCountdown: ({ graceEndsAt }: { graceEndsAt: string }) => (
    <div data-testid="grace-countdown" data-grace-ends-at={graceEndsAt}>
      Grace Countdown
    </div>
  ),
}));

vi.mock("@/components/billing/payfast-result-poller", () => ({
  PayFastResultPoller: ({ initialStatus }: { initialStatus: string }) => (
    <div data-testid="payfast-result-poller" data-status={initialStatus}>
      PayFast Poller
    </div>
  ),
}));

afterEach(() => cleanup());

const { getSubscription } = await import(
  "@/app/(app)/org/[slug]/settings/billing/actions"
);
const mockGetSubscription = getSubscription as ReturnType<typeof vi.fn>;

function makeBilling(
  overrides: Partial<BillingResponse> = {}
): BillingResponse {
  return {
    status: "ACTIVE",
    trialEndsAt: null,
    currentPeriodEnd: "2026-05-01T00:00:00Z",
    graceEndsAt: null,
    nextBillingAt: "2026-05-01T00:00:00Z",
    monthlyAmountCents: 49900,
    currency: "ZAR",
    limits: { maxMembers: 10, currentMembers: 3 },
    canSubscribe: false,
    canCancel: true,
    ...overrides,
  };
}

describe("BillingPage", () => {
  it("renders trial countdown for TRIALING status", async () => {
    const trialEnd = new Date(Date.now() + 7 * 86_400_000).toISOString();
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "TRIALING",
        trialEndsAt: trialEnd,
        currentPeriodEnd: null,
        nextBillingAt: null,
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Trial Period")).toBeInTheDocument();
    expect(screen.getByTestId("trial-countdown")).toBeInTheDocument();
    expect(
      screen.getByTestId("trial-countdown").getAttribute("data-trial-ends-at")
    ).toBe(trialEnd);
  });

  it("renders subscribe CTA for TRIALING", async () => {
    const trialEnd = new Date(Date.now() + 7 * 86_400_000).toISOString();
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "TRIALING",
        trialEndsAt: trialEnd,
        currentPeriodEnd: null,
        nextBillingAt: null,
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByTestId("subscribe-button")).toBeInTheDocument();
    expect(screen.queryByTestId("cancel-dialog")).not.toBeInTheDocument();
  });

  it("renders active subscription details for ACTIVE", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Active Subscription")).toBeInTheDocument();
    expect(screen.getByText(/3 of 10 members/)).toBeInTheDocument();
    expect(screen.getByText("R499.00")).toBeInTheDocument();
    const badge = screen
      .getAllByTestId("badge")
      .find((el) => el.getAttribute("data-variant") === "success");
    expect(badge).toBeInTheDocument();
    expect(badge?.textContent).toBe("Active");
  });

  it("renders cancel link for ACTIVE", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByTestId("cancel-dialog")).toBeInTheDocument();
  });

  it("renders cancellation notice for PENDING_CANCELLATION", async () => {
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "PENDING_CANCELLATION",
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Subscription Cancelling")).toBeInTheDocument();
    expect(screen.getByText(/Changed your mind/)).toBeInTheDocument();
    expect(screen.getByTestId("subscribe-button")).toBeInTheDocument();
  });

  it("renders payment failed warning for PAST_DUE", async () => {
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "PAST_DUE",
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Payment Failed")).toBeInTheDocument();
    expect(screen.getByText(/latest payment failed/)).toBeInTheDocument();
    expect(screen.getByTestId("subscribe-button")).toBeInTheDocument();
  });

  it("renders grace countdown for GRACE_PERIOD", async () => {
    const graceEnd = new Date(Date.now() + 14 * 86_400_000).toISOString();
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "GRACE_PERIOD",
        graceEndsAt: graceEnd,
        currentPeriodEnd: null,
        nextBillingAt: null,
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    // "Grace Period" appears in both the badge and card title
    expect(screen.getAllByText("Grace Period").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId("grace-countdown")).toBeInTheDocument();
    expect(
      screen.getByTestId("grace-countdown").getAttribute("data-grace-ends-at")
    ).toBe(graceEnd);
    expect(screen.getByText(/read-only mode/)).toBeInTheDocument();
  });

  it("renders full-page resubscribe for LOCKED", async () => {
    mockGetSubscription.mockResolvedValue(
      makeBilling({
        status: "LOCKED",
        currentPeriodEnd: null,
        nextBillingAt: null,
        canSubscribe: true,
        canCancel: false,
      })
    );

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Account Locked")).toBeInTheDocument();
    expect(screen.getByText(/data is preserved/)).toBeInTheDocument();
    expect(screen.getByTestId("subscribe-button")).toBeInTheDocument();
    // Payment history should NOT render for LOCKED status
    expect(screen.queryByTestId("payment-history")).not.toBeInTheDocument();
  });
});
