"use client";

import { createContext, useContext, useState, useCallback } from "react";

export type RecentItem = {
  href: string;
  label: string;
};

interface RecentItemsContextValue {
  items: RecentItem[];
  addItem: (item: RecentItem) => void;
}

const RecentItemsContext = createContext<RecentItemsContextValue | null>(null);

export function useRecentItems(): RecentItemsContextValue {
  const ctx = useContext(RecentItemsContext);
  if (!ctx) {
    throw new Error("useRecentItems must be used within a RecentItemsProvider");
  }
  return ctx;
}

interface RecentItemsProviderProps {
  children: React.ReactNode;
}

/**
 * In-memory "Recent" list for the command palette (max 5, deduped by href). Detail pages record
 * themselves by rendering `<RecordRecentItem href label />` with the name they already loaded
 * (Phase 87 610E, §12.6.5) — there is no pathname matching here, so each entity has exactly one
 * recording path and the label is the human name, not the URL id.
 */
export function RecentItemsProvider({ children }: RecentItemsProviderProps) {
  const [items, setItems] = useState<RecentItem[]>([]);

  const addItem = useCallback((item: RecentItem) => {
    setItems((prev) => {
      // Deduplicate by href, then prepend, cap at 5
      const filtered = prev.filter((i) => i.href !== item.href);
      return [item, ...filtered].slice(0, 5);
    });
  }, []);

  return (
    <RecentItemsContext.Provider value={{ items, addItem }}>{children}</RecentItemsContext.Provider>
  );
}
