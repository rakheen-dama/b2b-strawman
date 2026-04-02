"use client";

import { createContext, useContext, useMemo } from "react";
import type { BillingResponse } from "@/lib/internal-api";

// ---- Context Types ----

interface SubscriptionContextValue {
  status: string;
  isWriteEnabled: boolean;
  canSubscribe: boolean;
  canCancel: boolean;
}

// ---- Context ----

const SubscriptionContext = createContext<SubscriptionContextValue>({
  status: "ACTIVE",
  isWriteEnabled: true,
  canSubscribe: false,
  canCancel: false,
});

// ---- Status Sets ----

const WRITE_ENABLED_STATUSES = new Set([
  "TRIALING",
  "ACTIVE",
  "PENDING_CANCELLATION",
  "PAST_DUE",
]);

const CAN_SUBSCRIBE_STATUSES = new Set([
  "TRIALING",
  "EXPIRED",
  "GRACE_PERIOD",
  "SUSPENDED",
  "LOCKED",
]);

// ---- Provider ----

interface SubscriptionProviderProps {
  billingResponse: BillingResponse;
  children: React.ReactNode;
}

export function SubscriptionProvider({
  billingResponse,
  children,
}: SubscriptionProviderProps) {
  const status = billingResponse.status;

  const value = useMemo<SubscriptionContextValue>(
    () => ({
      status,
      isWriteEnabled: WRITE_ENABLED_STATUSES.has(status),
      canSubscribe: CAN_SUBSCRIBE_STATUSES.has(status),
      canCancel: status === "ACTIVE",
    }),
    [status],
  );

  return (
    <SubscriptionContext.Provider value={value}>
      {children}
    </SubscriptionContext.Provider>
  );
}

// ---- Hook ----

export function useSubscription(): SubscriptionContextValue {
  return useContext(SubscriptionContext);
}
