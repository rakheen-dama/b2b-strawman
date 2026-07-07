"use client";

import { createContext, useContext, useMemo } from "react";
import { TERMINOLOGY } from "@b2mash/shared/terminology-map";

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

// ---- Helpers ----

/**
 * Prefixes a resolved term with the correct indefinite article ("a"/"an").
 * Terminology substitution can change the initial sound of a noun
 * (e.g. legal-za `proposal` → "engagement letter"), so hardcoded articles in
 * copy break ("a engagement letter"). A simple initial-vowel test is
 * sufficient for the current term sets (LZKC-003).
 */
export function withIndefiniteArticle(term: string): string {
  return (/^[aeiou]/i.test(term) ? "an " : "a ") + term;
}
