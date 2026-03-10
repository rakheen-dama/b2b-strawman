"use client";

import { createContext, useContext, useState, useEffect } from "react";
import { usePathname } from "next/navigation";

export type RecentItem = {
  href: string;
  label: string;
  icon?: string;
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

  const addItem = (item: RecentItem) => {
    setItems((prev) => {
      // Deduplicate by href, then prepend, cap at 5
      const filtered = prev.filter((i) => i.href !== item.href);
      return [item, ...filtered].slice(0, 5);
    });
  };

  useEffect(() => {
    // Match /org/[slug]/projects/[id] or /org/[slug]/customers/[id]
    const projectMatch = pathname.match(/^\/org\/[^/]+\/projects\/([^/]+)$/);
    const customerMatch = pathname.match(/^\/org\/[^/]+\/customers\/([^/]+)$/);

    if (projectMatch || customerMatch) {
      // Derive label: prefer document.title, fallback to title-cased last segment
      const lastSegment = pathname.split("/").pop() ?? "";
      let label = "";
      if (typeof document !== "undefined" && document.title) {
        // document.title typically includes the page title set by Next.js metadata
        // Strip the site name suffix if present (e.g., "Project Alpha | DocTeams" → "Project Alpha")
        label = document.title.split("|")[0].trim();
      }
      if (!label || label === "") {
        // Fallback: title-case the last segment (ID-based slug)
        label = lastSegment
          .split("-")
          .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
          .join(" ");
      }
      addItem({ href: pathname, label });
    }
  }, [pathname]);

  return (
    <RecentItemsContext.Provider value={{ items, addItem }}>
      {children}
    </RecentItemsContext.Provider>
  );
}
