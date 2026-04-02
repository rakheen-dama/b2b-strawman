import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen, fireEvent } from "@testing-library/react";
import { SubscriptionBanner } from "@/components/billing/subscription-banner";
import type { BillingResponse } from "@/lib/internal-api";

afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

function makeBillingResponse(
  status: BillingResponse["status"],
  overrides?: Partial<BillingResponse>,
): BillingResponse {
  return {
    status,
    trialEndsAt: null,
    currentPeriodEnd: null,
    graceEndsAt: null,
    nextBillingAt: null,
    monthlyAmountCents: 49900,
    currency: "ZAR",
    limits: { maxMembers: 5, currentMembers: 1 },
    canSubscribe: false,
    canCancel: false,
    ...overrides,
  };
}

const SLUG = "test-org";

describe("SubscriptionBanner", () => {
  it("renders nothing for ACTIVE status", () => {
    const { container } = render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("ACTIVE")}
        slug={SLUG}
      />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders nothing for TRIALING with more than 7 days remaining", () => {
    // Set trialEndsAt to 10 days from now
    const futureDate = new Date(
      Date.now() + 10 * 86_400_000,
    ).toISOString();
    const { container } = render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("TRIALING", {
          trialEndsAt: futureDate,
        })}
        slug={SLUG}
      />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("shows info banner for TRIALING with 7 or fewer days remaining", () => {
    // Set trialEndsAt to 5 days from now
    const futureDate = new Date(
      Date.now() + 5 * 86_400_000,
    ).toISOString();
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("TRIALING", {
          trialEndsAt: futureDate,
        })}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Trial ending soon")).toBeInTheDocument();
    expect(screen.getByText("Subscribe now")).toBeInTheDocument();
  });

  it("shows warning banner for PENDING_CANCELLATION", () => {
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("PENDING_CANCELLATION", {
          currentPeriodEnd: "2026-05-01T00:00:00Z",
        })}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Subscription ending")).toBeInTheDocument();
    expect(screen.getByText("Resubscribe")).toBeInTheDocument();
  });

  it("shows warning banner for PAST_DUE and is not dismissible", () => {
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("PAST_DUE")}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Payment failed")).toBeInTheDocument();
    expect(
      screen.getByText("update your payment method"),
    ).toBeInTheDocument();
    // PAST_DUE requires payment action — must not be dismissible
    expect(
      screen.queryByRole("button", { name: "Dismiss banner" }),
    ).not.toBeInTheDocument();
  });

  it("shows error banner for GRACE_PERIOD", () => {
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("GRACE_PERIOD")}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Read-only mode")).toBeInTheDocument();
    expect(
      screen.getByText("subscribe to regain full access"),
    ).toBeInTheDocument();
  });

  it("shows error banner for EXPIRED", () => {
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("EXPIRED")}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Read-only mode")).toBeInTheDocument();
  });

  it("allows dismissing info/warning banners", () => {
    // PENDING_CANCELLATION is dismissible (warning variant)
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("PENDING_CANCELLATION")}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Subscription ending")).toBeInTheDocument();

    const dismissButton = screen.getByRole("button", {
      name: "Dismiss banner",
    });
    expect(dismissButton).toBeInTheDocument();
    fireEvent.click(dismissButton);

    // Banner should be dismissed
    expect(
      screen.queryByText("Subscription ending"),
    ).not.toBeInTheDocument();
  });

  it("does not show dismiss button for error banners", () => {
    render(
      <SubscriptionBanner
        billingResponse={makeBillingResponse("GRACE_PERIOD")}
        slug={SLUG}
      />,
    );
    expect(screen.getByText("Read-only mode")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Dismiss banner" }),
    ).not.toBeInTheDocument();
  });
});
