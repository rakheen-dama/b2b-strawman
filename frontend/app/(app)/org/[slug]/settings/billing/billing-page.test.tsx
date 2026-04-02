import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import BillingPage from "./page";
import type { BillingResponse } from "@/lib/internal-api";

vi.mock("@/app/(app)/org/[slug]/settings/billing/actions", () => ({
  getSubscription: vi.fn(),
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

afterEach(() => cleanup());

const { getSubscription } = await import(
  "@/app/(app)/org/[slug]/settings/billing/actions"
);
const mockGetSubscription = getSubscription as ReturnType<typeof vi.fn>;

function makeBilling(overrides: Partial<BillingResponse> = {}): BillingResponse {
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
  it("renders back link to settings", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    const backLink = screen.getByRole("link", { name: /Settings/ });
    expect(backLink).toHaveAttribute("href", "/org/acme/settings");
  });

  it("renders page header with status badge for ACTIVE", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Billing")).toBeInTheDocument();
    const badge = screen.getAllByTestId("badge").find(
      (el) => el.getAttribute("data-variant") === "success"
    );
    expect(badge).toBeInTheDocument();
    expect(badge?.textContent).toBe("Active");
  });

  it("shows active subscription card with member count and cancel option", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({}),
    });
    render(page);

    expect(screen.getByText("Active Subscription")).toBeInTheDocument();
    expect(screen.getByText(/3 of 10 members/)).toBeInTheDocument();
    expect(screen.getByText("R499.00")).toBeInTheDocument();
    expect(screen.getByTestId("cancel-dialog")).toBeInTheDocument();
  });

  it("shows trial card with days remaining for TRIALING status", async () => {
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
    expect(screen.getByText(/7 days/)).toBeInTheDocument();
    expect(screen.getByTestId("subscribe-button")).toBeInTheDocument();
    expect(screen.queryByTestId("cancel-dialog")).not.toBeInTheDocument();
  });

  it("shows cancellation card for PENDING_CANCELLATION status", async () => {
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

  it("shows locked card for LOCKED status", async () => {
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
  });

  it("shows success notice when result=success in searchParams", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ result: "success" }),
    });
    render(page);

    expect(screen.getByText(/Payment successful/)).toBeInTheDocument();
  });

  it("shows cancelled notice when result=cancelled in searchParams", async () => {
    mockGetSubscription.mockResolvedValue(makeBilling());

    const page = await BillingPage({
      params: Promise.resolve({ slug: "acme" }),
      searchParams: Promise.resolve({ result: "cancelled" }),
    });
    render(page);

    expect(screen.getByText(/Payment was cancelled/)).toBeInTheDocument();
  });
});
