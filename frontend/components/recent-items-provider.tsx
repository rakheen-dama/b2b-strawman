"use client";

import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { usePathname } from "next/navigation";

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

export function RecentItemsProvider({ children }: RecentItemsProviderProps) {
  const [items, setItems] = useState<RecentItem[]>([]);
  const pathname = usePathname();

  const addItem = useCallback((item: RecentItem) => {
    setItems((prev) => {
      // Deduplicate by href, then prepend, cap at 5
      const filtered = prev.filter((i) => i.href !== item.href);
      return [item, ...filtered].slice(0, 5);
    });
  }, []);

  useEffect(() => {
    // Match /org/[slug]/projects/[id] or /org/[slug]/customers/[id]
    const projectMatch = pathname.match(/^\/org\/[^/]+\/projects\/([^/]+)$/);
    const customerMatch = pathname.match(/^\/org\/[^/]+\/customers\/([^/]+)$/);

    if (projectMatch || customerMatch) {
      // Derive label: title-case the last path segment (reliable in App Router)
      const lastSegment = pathname.split("/").pop() ?? "";
      const label = lastSegment
        .split("-")
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(" ");
      // eslint-disable-next-line react-hooks/set-state-in-effect -- route-change tracking requires sync state update; refactor tracked separately
      addItem({ href: pathname, label });
    }
  }, [pathname, addItem]);

  return (
    <RecentItemsContext.Provider value={{ items, addItem }}>
      {children}
    </RecentItemsContext.Provider>
  );
}
