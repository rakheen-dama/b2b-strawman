"use client";

import { useEffect, useState, useMemo } from "react";
import { ChevronDown, ChevronRight, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { getClauses } from "@/lib/actions/clause-actions";
import type { Clause } from "@/lib/actions/clause-actions";

interface ClausePickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  existingClauseIds: string[];
  onConfirm: (
    clauses: Array<{
      id: string;
      title: string;
      category: string;
      description: string | null;
      legacyBody: string | null;
    }>,
  ) => void;
}

export function ClausePickerDialog({
  open,
  onOpenChange,
  existingClauseIds,
  onConfirm,
}: ClausePickerDialogProps) {
  const [allClauses, setAllClauses] = useState<Clause[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(),
  );

  useEffect(() => {
    if (!open) {
      setSearch("");
      setSelectedIds(new Set());
      return;
    }

    async function load() {
      setIsLoading(true);
      try {
        const data = await getClauses(false);
        setAllClauses(data.filter((c) => c.active));
        // Expand all categories by default
        const cats = new Set(data.filter((c) => c.active).map((c) => c.category));
        setExpandedCategories(cats);
      } catch {
        setAllClauses([]);
      } finally {
        setIsLoading(false);
      }
    }

    load();
  }, [open]);

  const existingSet = useMemo(
    () => new Set(existingClauseIds),
    [existingClauseIds],
  );

  const filteredClauses = useMemo(() => {
    if (!search) return allClauses;
    const lower = search.toLowerCase();
    return allClauses.filter(
      (c) =>
        c.title.toLowerCase().includes(lower) ||
        (c.description && c.description.toLowerCase().includes(lower)),
    );
  }, [allClauses, search]);

  const grouped = useMemo(() => {
    const groups: Record<string, Clause[]> = {};
    for (const clause of filteredClauses) {
      if (!groups[clause.category]) groups[clause.category] = [];
      groups[clause.category].push(clause);
    }
    for (const key of Object.keys(groups)) {
      groups[key] = [...groups[key]].sort((a, b) => a.sortOrder - b.sortOrder);
    }
    return groups;
  }, [filteredClauses]);

  const sortedCategories = useMemo(
    () => Object.keys(grouped).sort(),
    [grouped],
  );

  function toggleCategory(category: string) {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  }

  function toggleClause(clauseId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(clauseId)) {
        next.delete(clauseId);
      } else {
        next.add(clauseId);
      }
      return next;
    });
  }

  function handleConfirm() {
    const selected = allClauses
      .filter((c) => selectedIds.has(c.id))
      .map((c) => ({
        id: c.id,
        title: c.title,
        category: c.category,
        description: c.description,
        legacyBody: c.legacyBody,
      }));
    onConfirm(selected);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>Add Clauses</DialogTitle>
        </DialogHeader>

        <div className="relative">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="Search clauses..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>

        <div className="flex-1 overflow-y-auto min-h-0 max-h-[50vh] space-y-1">
          {isLoading ? (
            <div className="flex h-32 items-center justify-center">
              <p className="text-sm text-slate-500">Loading clauses...</p>
            </div>
          ) : sortedCategories.length === 0 ? (
            <div className="flex h-32 items-center justify-center">
              <p className="text-sm text-slate-500">No clauses found.</p>
            </div>
          ) : (
            sortedCategories.map((category) => (
              <div key={category}>
                <button
                  type="button"
                  className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                  onClick={() => toggleCategory(category)}
                >
                  {expandedCategories.has(category) ? (
                    <ChevronDown className="size-4" />
                  ) : (
                    <ChevronRight className="size-4" />
                  )}
                  {category}
                  <Badge variant="secondary" className="ml-1 text-xs">
                    {grouped[category].length}
                  </Badge>
                </button>

                {expandedCategories.has(category) && (
                  <div className="ml-6 space-y-1 pb-2">
                    {grouped[category].map((clause) => {
                      const isExisting = existingSet.has(clause.id);
                      const isSelected = selectedIds.has(clause.id);

                      return (
                        <label
                          key={clause.id}
                          className={`flex items-start gap-3 rounded-md px-3 py-2 ${
                            isExisting
                              ? "cursor-not-allowed opacity-50"
                              : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/50"
                          }`}
                        >
                          <Checkbox
                            checked={isExisting || isSelected}
                            disabled={isExisting}
                            onCheckedChange={() => {
                              if (!isExisting) toggleClause(clause.id);
                            }}
                            aria-label={`Select ${clause.title}`}
                          />
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                                {clause.title}
                              </span>
                              {isExisting && (
                                <Badge
                                  variant="outline"
                                  className="text-xs"
                                >
                                  Already added
                                </Badge>
                              )}
                            </div>
                            {clause.description && (
                              <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">
                                {clause.description}
                              </p>
                            )}
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={selectedIds.size === 0}
          >
            Add Selected ({selectedIds.size})
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
