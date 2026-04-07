"use client";

import {
  useState,
  useEffect,
  useRef,
  useCallback,
  useTransition,
} from "react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Search, ChevronRight, ChevronDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { fetchTariffItems } from "@/app/(app)/org/[slug]/legal/tariffs/actions";
import type { TariffItem } from "@/lib/types";

const UNIT_LABELS: Record<string, string> = {
  PER_ITEM: "Per Item",
  PER_PAGE: "Per Page",
  PER_FOLIO: "Per Folio",
  PER_QUARTER_HOUR: "Per 15 min",
  PER_HOUR: "Per Hour",
  PER_DAY: "Per Day",
};

function formatZAR(amount: number): string {
  return `R ${amount.toFixed(2)}`;
}

/** Group items by a simple section heuristic: first word(s) of itemNumber or description prefix */
function groupBySection(items: TariffItem[]): { section: string; items: TariffItem[] }[] {
  // Group items by the numeric prefix of itemNumber (e.g., "1", "1(a)", "2" -> sections "1", "2")
  const groups: Record<string, TariffItem[]> = {};
  const sectionOrder: string[] = [];

  for (const item of items) {
    // Use the major number as the section key (e.g., "1(a)" -> "1", "12" -> "12")
    const match = item.itemNumber.match(/^(\d+)/);
    const section = match ? `Section ${match[1]}` : "Other";
    if (!groups[section]) {
      groups[section] = [];
      sectionOrder.push(section);
    }
    groups[section].push(item);
  }

  return sectionOrder.map((section) => ({ section, items: groups[section] }));
}

interface TariffItemBrowserProps {
  scheduleId: string;
  /** Optional callback when an item is selected (used in TariffLineDialog) */
  onSelectItem?: (item: TariffItem) => void;
  /** Set of selected item IDs for highlighting */
  selectedItemIds?: Set<string>;
}

export function TariffItemBrowser({
  scheduleId,
  onSelectItem,
  selectedItemIds,
}: TariffItemBrowserProps) {
  const [items, setItems] = useState<TariffItem[]>([]);
  const [search, setSearch] = useState("");
  const [isPending, startTransition] = useTransition();
  const [collapsedSections, setCollapsedSections] = useState<Set<string>>(new Set());

  const isInitialMount = useRef(true);
  const searchTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadItems = useCallback(
    (searchQuery?: string) => {
      startTransition(async () => {
        try {
          const result = await fetchTariffItems(scheduleId, searchQuery || undefined);
          setItems(result ?? []);
        } catch (err) {
          console.error("Failed to fetch tariff items:", err);
        }
      });
    },
    [scheduleId],
  );

  // Initial load
  useEffect(() => {
    loadItems();
  }, [loadItems]);

  // Debounced search
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }
    searchTimeoutRef.current = setTimeout(() => {
      loadItems(search);
    }, 300);
    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [search, loadItems]);

  function toggleSection(section: string) {
    setCollapsedSections((prev) => {
      const next = new Set(prev);
      if (next.has(section)) {
        next.delete(section);
      } else {
        next.add(section);
      }
      return next;
    });
  }

  const grouped = groupBySection(items);

  return (
    <div data-testid="tariff-item-browser" className="space-y-3">
      {/* Search */}
      <div className="relative max-w-xs">
        <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
        <Input
          placeholder="Search items..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
          data-testid="tariff-item-search"
        />
      </div>

      {/* Items */}
      <div
        className={cn(
          "space-y-1",
          isPending && "opacity-50 transition-opacity",
        )}
      >
        {items.length === 0 && !isPending ? (
          <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
            No tariff items found.
          </p>
        ) : (
          grouped.map((group) => (
            <div key={group.section}>
              {/* Section header */}
              <button
                type="button"
                onClick={() => toggleSection(group.section)}
                className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                data-testid={`section-header-${group.section}`}
              >
                {collapsedSections.has(group.section) ? (
                  <ChevronRight className="size-3.5" />
                ) : (
                  <ChevronDown className="size-3.5" />
                )}
                {group.section}
                <span className="text-slate-400">({group.items.length})</span>
              </button>

              {/* Section items */}
              {!collapsedSections.has(group.section) && (
                <div className="ml-4 space-y-0.5">
                  {group.items.map((item) => {
                    const isSelected = selectedItemIds?.has(item.id);
                    return (
                      <div
                        key={item.id}
                        className={cn(
                          "flex items-center gap-3 rounded px-3 py-2 text-sm",
                          onSelectItem
                            ? "cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-800"
                            : "",
                          isSelected
                            ? "bg-teal-50 ring-1 ring-teal-200 dark:bg-teal-950 dark:ring-teal-800"
                            : "",
                        )}
                        onClick={() => onSelectItem?.(item)}
                        data-testid={`tariff-item-${item.id}`}
                      >
                        <span className="w-16 shrink-0 font-mono text-xs text-slate-500 dark:text-slate-400">
                          {item.itemNumber}
                        </span>
                        <span className="min-w-0 flex-1 text-slate-900 dark:text-slate-100">
                          {item.description}
                        </span>
                        <Badge variant="neutral" className="shrink-0 text-xs">
                          {UNIT_LABELS[item.unit] ?? item.unit}
                        </Badge>
                        <span className="shrink-0 font-medium text-slate-900 dark:text-slate-100">
                          {formatZAR(item.amount)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
