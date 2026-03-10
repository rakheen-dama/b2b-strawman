"use client";

import { createContext, useContext, useMemo } from "react";

// ---- Capability Constants ----
// Must stay in sync with backend Capability enum (com.docteams.backend.orgrole.Capability)

export const CAPABILITIES = {
  FINANCIAL_VISIBILITY: "FINANCIAL_VISIBILITY",
  INVOICING: "INVOICING",
  PROJECT_MANAGEMENT: "PROJECT_MANAGEMENT",
  TEAM_OVERSIGHT: "TEAM_OVERSIGHT",
  CUSTOMER_MANAGEMENT: "CUSTOMER_MANAGEMENT",
  AUTOMATIONS: "AUTOMATIONS",
  RESOURCE_PLANNING: "RESOURCE_PLANNING",
} as const;

// ---- Context Types ----

interface CapabilityContextValue {
  capabilities: Set<string>;
  role: string;
  isAdmin: boolean;
  isOwner: boolean;
  isLoading: boolean;
  hasCapability: (cap: string) => boolean;
}

// ---- Context ----

const CapabilityContext = createContext<CapabilityContextValue | null>(null);

// ---- Provider ----

interface CapabilityProviderProps {
  capabilities: string[];
  role: string;
  isAdmin: boolean;
  isOwner: boolean;
  children: React.ReactNode;
}

export function CapabilityProvider({
  capabilities,
  role,
  isAdmin,
  isOwner,
  children,
}: CapabilityProviderProps) {
  // Stabilize useMemo: capabilities is a string[] (new reference each render from server),
  // so we serialize it to avoid recomputation on every render.
  const capKey = JSON.stringify(capabilities);

  const value = useMemo<CapabilityContextValue>(() => {
    const capSet = new Set(capabilities);
    return {
      capabilities: capSet,
      role,
      isAdmin,
      isOwner,
      isLoading: false,
      // Defense-in-depth: backend resolves all capabilities for admin/owner,
      // but we short-circuit here too to avoid UI flicker on gated components
      hasCapability: (cap: string) => isAdmin || isOwner || capSet.has(cap),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capKey, role, isAdmin, isOwner]);

  return (
    <CapabilityContext.Provider value={value}>
      {children}
    </CapabilityContext.Provider>
  );
}

// ---- Hook ----

export function useCapabilities(): CapabilityContextValue {
  const ctx = useContext(CapabilityContext);
  if (!ctx) {
    throw new Error(
      "useCapabilities must be used within a CapabilityProvider",
    );
  }
  return ctx;
}

// ---- RequiresCapability Wrapper ----

interface RequiresCapabilityProps {
  cap: string;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export function RequiresCapability({
  cap,
  fallback = null,
  children,
}: RequiresCapabilityProps) {
  const { hasCapability, isLoading } = useCapabilities();

  if (isLoading) {
    return null;
  }

  if (!hasCapability(cap)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
