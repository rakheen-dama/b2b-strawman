"use client";

import { createContext, useContext, useMemo, useState, useCallback } from "react";

// ---- Context Types ----

interface AssistantContextValue {
  isOpen: boolean;
  isAiEnabled: boolean;
  toggle: () => void;
}

// ---- Context ----

const AssistantContext = createContext<AssistantContextValue | null>(null);

// ---- Provider ----

interface AssistantProviderProps {
  aiEnabled: boolean;
  children: React.ReactNode;
}

export function AssistantProvider({
  aiEnabled,
  children,
}: AssistantProviderProps) {
  const [isOpen, setIsOpen] = useState(false);

  const toggle = useCallback(() => {
    setIsOpen((prev) => !prev);
  }, []);

  const value = useMemo<AssistantContextValue>(
    () => ({
      isOpen,
      isAiEnabled: aiEnabled,
      toggle,
    }),
    [isOpen, aiEnabled, toggle],
  );

  return (
    <AssistantContext.Provider value={value}>
      {children}
    </AssistantContext.Provider>
  );
}

// ---- Hook ----

export function useAssistant(): AssistantContextValue {
  const ctx = useContext(AssistantContext);
  if (!ctx) {
    throw new Error("useAssistant must be used within an AssistantProvider");
  }
  return ctx;
}
