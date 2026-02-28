"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { ChevronDown, ChevronRight, FileText } from "lucide-react";
import { getClauses, type Clause } from "@/lib/actions/clause-actions";
import { extractTextFromBody } from "@/lib/tiptap-utils";

interface ClausePickerProps {
  onSelect: (clause: {
    id: string;
    slug: string;
    title: string;
    required: boolean;
  }) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ClausePicker({
  onSelect,
  open,
  onOpenChange,
}: ClausePickerProps) {
  const [clauses, setClauses] = useState<Clause[]>([]);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(
    new Set(),
  );

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (nextOpen) {
        setSearch("");
        setSelectedId(null);
        setCollapsedCategories(new Set());
      }
      onOpenChange(nextOpen);
    },
    [onOpenChange],
  );

  useEffect(() => {
    if (open) {
      getClauses()
        .then(setClauses)
        .catch(() => setClauses([]));
    }
  }, [open]);

  const filteredClauses = useMemo(() => {
    if (!search.trim()) return clauses;
    const query = search.toLowerCase();
    return clauses.filter(
      (c) =>
        c.title.toLowerCase().includes(query) ||
        c.slug.toLowerCase().includes(query) ||
        c.category.toLowerCase().includes(query),
    );
  }, [clauses, search]);

  const grouped = useMemo(() => {
    const groups = new Map<string, Clause[]>();
    for (const clause of filteredClauses) {
      const category = clause.category || "Uncategorized";
      const existing = groups.get(category) ?? [];
      existing.push(clause);
      groups.set(category, existing);
    }
    return groups;
  }, [filteredClauses]);

  const selectedClause = clauses.find((c) => c.id === selectedId) ?? null;

  const toggleCategory = (category: string) => {
    setCollapsedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  };

  const handleInsert = () => {
    if (!selectedClause) return;
    onSelect({
      id: selectedClause.id,
      slug: selectedClause.slug,
      title: selectedClause.title,
      required: false,
    });
    handleOpenChange(false);
  };

  // Extract text from Tiptap JSON for preview
  const previewText = selectedClause?.body
    ? extractTextFromBody(selectedClause.body)
    : null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[600px] overflow-hidden p-0 sm:max-w-3xl">
        <DialogHeader className="px-4 pt-4">
          <DialogTitle>Insert Clause</DialogTitle>
          <DialogDescription>
            Select a clause from the library to insert into the template.
          </DialogDescription>
        </DialogHeader>

        <div className="flex min-h-[400px] border-t border-slate-200 dark:border-slate-800">
          {/* Left panel - clause list */}
          <div className="flex w-2/5 flex-col border-r border-slate-200 dark:border-slate-800">
            <div className="p-3">
              <Input
                placeholder="Search clauses..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            <div className="flex-1 overflow-y-auto px-1 pb-2">
              {filteredClauses.length === 0 ? (
                <div className="px-3 py-8 text-center text-sm text-slate-400">
                  No clauses found.
                </div>
              ) : (
                Array.from(grouped.entries()).map(
                  ([category, categoryClauses]) => (
                    <div key={category} className="mb-1">
                      <button
                        type="button"
                        className="flex w-full items-center gap-1 px-2 py-1.5 text-xs font-semibold uppercase tracking-wider text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                        onClick={() => toggleCategory(category)}
                      >
                        {collapsedCategories.has(category) ? (
                          <ChevronRight className="h-3 w-3" />
                        ) : (
                          <ChevronDown className="h-3 w-3" />
                        )}
                        {category}
                        <span className="ml-auto font-normal text-slate-400">
                          {categoryClauses.length}
                        </span>
                      </button>

                      {!collapsedCategories.has(category) &&
                        categoryClauses.map((clause) => (
                          <button
                            key={clause.id}
                            type="button"
                            className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors ${
                              selectedId === clause.id
                                ? "bg-teal-50 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                                : "text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                            }`}
                            onClick={() => setSelectedId(clause.id)}
                            onDoubleClick={() => {
                              onSelect({
                                id: clause.id,
                                slug: clause.slug,
                                title: clause.title,
                                required: false,
                              });
                              handleOpenChange(false);
                            }}
                          >
                            <FileText className="h-4 w-4 shrink-0 text-slate-400" />
                            <span className="truncate">{clause.title}</span>
                          </button>
                        ))}
                    </div>
                  ),
                )
              )}
            </div>
          </div>

          {/* Right panel - preview */}
          <div className="flex w-3/5 flex-col">
            {selectedClause ? (
              <div className="flex flex-1 flex-col">
                <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-800">
                  <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
                    {selectedClause.title}
                  </h3>
                  {selectedClause.description && (
                    <p className="mt-1 text-xs text-slate-500">
                      {selectedClause.description}
                    </p>
                  )}
                  <div className="mt-2 flex items-center gap-2">
                    <Badge variant="neutral" className="text-xs">
                      {selectedClause.category}
                    </Badge>
                    <Badge variant="neutral" className="text-xs">
                      {selectedClause.source}
                    </Badge>
                  </div>
                </div>

                <div className="flex-1 overflow-y-auto px-4 py-3">
                  {previewText ? (
                    <div className="rounded bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                      {previewText}
                    </div>
                  ) : (
                    <div className="text-sm text-slate-400">
                      No content available
                    </div>
                  )}
                </div>

                <Separator />
                <div className="flex justify-end p-3">
                  <Button variant="accent" size="sm" onClick={handleInsert}>
                    Insert
                  </Button>
                </div>
              </div>
            ) : (
              <div className="flex flex-1 items-center justify-center text-sm text-slate-400">
                Select a clause to preview
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
