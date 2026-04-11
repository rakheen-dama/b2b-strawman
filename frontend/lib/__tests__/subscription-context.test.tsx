import { describe, it, expect, afterEach } from "vitest";
import { cleanup, renderHook } from "@testing-library/react";
import { SubscriptionProvider, useSubscription } from "@/lib/subscription-context";
import type { BillingResponse } from "@/lib/internal-api";

afterEach(() => {
  cleanup();
});

function makeBillingResponse(
  status: string,
  overrides?: Partial<BillingResponse>
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
    billingMethod: "PAYFAST",
    adminManaged: false,
    adminNote: null,
    ...overrides,
  };
}

function wrapper(billingResponse: BillingResponse) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <SubscriptionProvider billingResponse={billingResponse}>{children}</SubscriptionProvider>
    );
  };
}

describe("SubscriptionContext", () => {
  describe("isWriteEnabled", () => {
    it("is true for TRIALING", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("TRIALING")),
      });
      expect(result.current.isWriteEnabled).toBe(true);
    });

    it("is true for ACTIVE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("ACTIVE")),
      });
      expect(result.current.isWriteEnabled).toBe(true);
    });

    it("is true for PENDING_CANCELLATION", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("PENDING_CANCELLATION")),
      });
      expect(result.current.isWriteEnabled).toBe(true);
    });

    it("is true for PAST_DUE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("PAST_DUE")),
      });
      expect(result.current.isWriteEnabled).toBe(true);
    });

    it("is false for GRACE_PERIOD", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("GRACE_PERIOD")),
      });
      expect(result.current.isWriteEnabled).toBe(false);
    });

    it("is false for EXPIRED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("EXPIRED")),
      });
      expect(result.current.isWriteEnabled).toBe(false);
    });

    it("is false for SUSPENDED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("SUSPENDED")),
      });
      expect(result.current.isWriteEnabled).toBe(false);
    });

    it("is false for LOCKED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("LOCKED")),
      });
      expect(result.current.isWriteEnabled).toBe(false);
    });
  });

  describe("canSubscribe (passthrough from API)", () => {
    it("is true for TRIALING", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("TRIALING", { canSubscribe: true })),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is true for EXPIRED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("EXPIRED", { canSubscribe: true })),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is true for GRACE_PERIOD", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("GRACE_PERIOD", { canSubscribe: true })),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is true for SUSPENDED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("SUSPENDED", { canSubscribe: true })),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is true for LOCKED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("LOCKED", { canSubscribe: true })),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is false for ACTIVE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("ACTIVE")),
      });
      expect(result.current.canSubscribe).toBe(false);
    });

    it("is false for PENDING_CANCELLATION", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("PENDING_CANCELLATION")),
      });
      expect(result.current.canSubscribe).toBe(false);
    });

    it("is false for PAST_DUE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("PAST_DUE")),
      });
      expect(result.current.canSubscribe).toBe(false);
    });
  });

  describe("canCancel (passthrough from API)", () => {
    it("is true when API says true", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("ACTIVE", { canCancel: true })),
      });
      expect(result.current.canCancel).toBe(true);
    });

    it("is false when API says false", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("TRIALING")),
      });
      expect(result.current.canCancel).toBe(false);
    });
  });

  describe("status passthrough", () => {
    it("passes status through from billingResponse", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("GRACE_PERIOD")),
      });
      expect(result.current.status).toBe("GRACE_PERIOD");
    });
  });
});
