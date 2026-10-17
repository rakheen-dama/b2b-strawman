"use client";

import { createContext, useContext, useMemo } from "react";
import type { BillingResponse } from "@/lib/internal-api";

// ---- Context Types ----

interface SubscriptionContextValue {
  status: string;
  isWriteEnabled: boolean;
}

// ---- Context ----

const SubscriptionContext = createContext<SubscriptionContextValue>({
  status: "ACTIVE",
  isWriteEnabled: true,
});

// ---- Status Sets ----

const WRITE_ENABLED_STATUSES = new Set(["TRIALING", "ACTIVE", "PENDING_CANCELLATION", "PAST_DUE"]);

// ---- Provider ----

interface SubscriptionProviderProps {
  billingResponse: BillingResponse;
  children: React.ReactNode;
}

export function SubscriptionProvider({ billingResponse, children }: SubscriptionProviderProps) {
  const { status } = billingResponse;

  const value = useMemo<SubscriptionContextValue>(
    () => ({
      status,
      isWriteEnabled: WRITE_ENABLED_STATUSES.has(status),
    }),
    [status]
  );

  return <SubscriptionContext.Provider value={value}>{children}</SubscriptionContext.Provider>;
}

// ---- Hook ----

export function useSubscription(): SubscriptionContextValue {
  return useContext(SubscriptionContext);
}
