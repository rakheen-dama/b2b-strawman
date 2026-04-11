"use client";

import { createContext, useContext, useMemo } from "react";
import { TERMINOLOGY } from "./terminology-map";

// ---- Context Types ----

interface TerminologyContextValue {
  t: (term: string) => string;
  verticalProfile: string | null;
}

// ---- Context ----

const TerminologyContext = createContext<TerminologyContextValue | null>(null);

// ---- Provider ----

interface TerminologyProviderProps {
  verticalProfile: string | null;
  children: React.ReactNode;
}

export function TerminologyProvider({ verticalProfile, children }: TerminologyProviderProps) {
  const value = useMemo<TerminologyContextValue>(() => {
    const map = verticalProfile ? (TERMINOLOGY[verticalProfile] ?? {}) : {};
    return {
      verticalProfile,
      t: (term: string) => map[term] ?? term,
    };
  }, [verticalProfile]);

  return <TerminologyContext.Provider value={value}>{children}</TerminologyContext.Provider>;
}

// ---- Hook ----

export function useTerminology(): TerminologyContextValue {
  const ctx = useContext(TerminologyContext);
  if (!ctx) {
    throw new Error("useTerminology must be used within a TerminologyProvider");
  }
  return ctx;
}
