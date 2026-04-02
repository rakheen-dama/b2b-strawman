import { describe, it, expect, afterEach } from "vitest";
import { cleanup, renderHook } from "@testing-library/react";
import {
  SubscriptionProvider,
  useSubscription,
} from "@/lib/subscription-context";
import type { BillingResponse } from "@/lib/internal-api";

afterEach(() => {
  cleanup();
});

function makeBillingResponse(status: string): BillingResponse {
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
  };
}

function wrapper(billingResponse: BillingResponse) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <SubscriptionProvider billingResponse={billingResponse}>
        {children}
      </SubscriptionProvider>
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

  describe("canSubscribe", () => {
    it("is true for TRIALING", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("TRIALING")),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is true for EXPIRED", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("EXPIRED")),
      });
      expect(result.current.canSubscribe).toBe(true);
    });

    it("is false for ACTIVE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("ACTIVE")),
      });
      expect(result.current.canSubscribe).toBe(false);
    });
  });

  describe("canCancel", () => {
    it("is true only for ACTIVE", () => {
      const { result } = renderHook(() => useSubscription(), {
        wrapper: wrapper(makeBillingResponse("ACTIVE")),
      });
      expect(result.current.canCancel).toBe(true);
    });

    it("is false for TRIALING", () => {
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
