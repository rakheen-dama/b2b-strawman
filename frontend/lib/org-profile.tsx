"use client";

import { createContext, useContext, useMemo } from "react";

// ---- Context Types ----

interface OrgProfileContextValue {
  verticalProfile: string | null;
  enabledModules: string[];
  terminologyNamespace: string | null;
  isModuleEnabled: (moduleId: string) => boolean;
}

// ---- Context ----

const OrgProfileContext = createContext<OrgProfileContextValue | null>(null);

// ---- Provider ----

interface OrgProfileProviderProps {
  verticalProfile: string | null;
  enabledModules: string[];
  terminologyNamespace: string | null;
  children: React.ReactNode;
}

export function OrgProfileProvider({
  verticalProfile,
  enabledModules,
  terminologyNamespace,
  children,
}: OrgProfileProviderProps) {
  const modulesKey = JSON.stringify(enabledModules);

  const value = useMemo<OrgProfileContextValue>(() => {
    const moduleSet = new Set(enabledModules);
    return {
      verticalProfile,
      enabledModules,
      terminologyNamespace,
      isModuleEnabled: (moduleId: string) => moduleSet.has(moduleId),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [verticalProfile, modulesKey, terminologyNamespace]);

  return <OrgProfileContext.Provider value={value}>{children}</OrgProfileContext.Provider>;
}

// ---- Hook ----

export function useOrgProfile(): OrgProfileContextValue {
  const ctx = useContext(OrgProfileContext);
  if (!ctx) {
    throw new Error("useOrgProfile must be used within an OrgProfileProvider");
  }
  return ctx;
}
