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

/**
 * Returns the active terminology. When no `TerminologyProvider` is mounted
 * (e.g. in unit tests that don't wrap the component under test), falls back
 * to an identity translation so the default (English, generic) terms render.
 */
const DEFAULT_TERMINOLOGY: TerminologyContextValue = {
  verticalProfile: null,
  t: (term: string) => term,
};

export function useTerminology(): TerminologyContextValue {
  const ctx = useContext(TerminologyContext);
  return ctx ?? DEFAULT_TERMINOLOGY;
}
