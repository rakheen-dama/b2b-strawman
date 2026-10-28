"use client";

import { createContext, useContext, useMemo } from "react";

// ---- Capability Constants ----
// Must stay in sync with backend Capability enum (io.b2mash.b2b.b2bstrawman.orgrole.Capability)

export const CAPABILITIES = {
  FINANCIAL_VISIBILITY: "FINANCIAL_VISIBILITY",
  INVOICING: "INVOICING",
  PROJECT_MANAGEMENT: "PROJECT_MANAGEMENT",
  TEAM_OVERSIGHT: "TEAM_OVERSIGHT",
  CUSTOMER_MANAGEMENT: "CUSTOMER_MANAGEMENT",
  AUTOMATIONS: "AUTOMATIONS",
  VIEW_TRUST: "VIEW_TRUST",
  AI_MANAGE: "AI_MANAGE",
  AI_EXECUTE: "AI_EXECUTE",
  AI_REVIEW: "AI_REVIEW",
} as const;

/** Capability metadata — single source of truth for labels, descriptions, and enum values. */
export const CAPABILITY_META = [
  {
    value: CAPABILITIES.FINANCIAL_VISIBILITY,
    label: "Financial Visibility",
    description: "View financial data including rates, fee estimates, and profitability reports",
  },
  {
    value: CAPABILITIES.INVOICING,
    label: "Billing",
    description: "Create, edit, and send fee notes to clients",
  },
  {
    value: CAPABILITIES.PROJECT_MANAGEMENT,
    label: "Matter Management",
    description: "Create and manage matters, tasks, and documents",
  },
  {
    value: CAPABILITIES.TEAM_OVERSIGHT,
    label: "Team Oversight",
    description: "View team members' work, time entries, and assignments",
  },
  {
    value: CAPABILITIES.CUSTOMER_MANAGEMENT,
    label: "Client Management",
    description: "Create and manage client records and relationships",
  },
  {
    value: CAPABILITIES.AUTOMATIONS,
    label: "Automations",
    description: "Configure workflow automations and scheduling rules",
  },
  {
    value: CAPABILITIES.VIEW_TRUST,
    label: "View Trust",
    description: "View trust account balances, transactions, and client ledgers",
  },
  {
    value: CAPABILITIES.AI_MANAGE,
    label: "AI Management",
    description: "Configure AI profile, model preference, and budget settings",
  },
  {
    value: CAPABILITIES.AI_EXECUTE,
    label: "AI Execute",
    description: "Invoke AI skills on entities (FICA verification, matter intake)",
  },
  {
    value: CAPABILITIES.AI_REVIEW,
    label: "AI Review",
    description: "Review and approve or reject AI-proposed actions",
  },
] as const;

const CAPABILITY_LABELS: ReadonlyMap<string, string> = new Map(
  CAPABILITY_META.map((cap) => [cap.value, cap.label])
);

/** Display label for a capability enum value ("INVOICING" → "Billing"); unknown values are spaced. */
export function capabilityLabel(cap: string): string {
  return CAPABILITY_LABELS.get(cap) ?? cap.replace(/_/g, " ");
}

// ---- Owner-only capabilities ----
// Must stay in sync with Capability.OWNER_ONLY (backend orgrole/Capability.java) — pinned by
// __tests__/capabilities.test.tsx, which reads Capability.java at test time (ADR-355).
// The backend gives an admin every capability EXCEPT these (OrgRoleService.resolveCapabilities);
// the client mirror must never be wider than the backend.
export const OWNER_ONLY_CAPABILITIES: ReadonlySet<string> = new Set([
  "MANAGE_COMPLIANCE_DESTRUCTIVE",
  "OVERRIDE_MATTER_CLOSURE",
  "FINANCIAL_RECONCILE",
]);

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
      // Mirrors the backend (owner = all, admin = all − OWNER_ONLY) so gated UI renders without
      // waiting on the capability set (no flicker) — but never wider than the backend: admins are
      // denied the owner-only capabilities unless explicitly granted (ADR-355).
      hasCapability: (cap: string) =>
        isOwner || (isAdmin && !OWNER_ONLY_CAPABILITIES.has(cap)) || capSet.has(cap),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [capKey, role, isAdmin, isOwner]);

  return <CapabilityContext.Provider value={value}>{children}</CapabilityContext.Provider>;
}

// ---- Hook ----

export function useCapabilities(): CapabilityContextValue {
  const ctx = useContext(CapabilityContext);
  if (!ctx) {
    throw new Error("useCapabilities must be used within a CapabilityProvider");
  }
  return ctx;
}

// ---- RequiresCapability Wrapper ----

interface RequiresCapabilityProps {
  cap: string;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export function RequiresCapability({ cap, fallback = null, children }: RequiresCapabilityProps) {
  const { hasCapability, isLoading } = useCapabilities();

  if (isLoading) {
    return null;
  }

  if (!hasCapability(cap)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
