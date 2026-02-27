"use client";

import { useEffect, useState, useCallback } from "react";
import { ArrowUp, ArrowDown, Plus, ArrowRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { getTemplateClauses } from "@/lib/actions/template-clause-actions";
import type { TemplateClauseDetail } from "@/lib/actions/template-clause-actions";
import { ClausePickerDialog } from "@/components/templates/clause-picker-dialog";

export interface SelectedClause {
  clauseId: string;
  sortOrder: number;
  required: boolean;
  title: string;
  category: string;
  description: string | null;
}

interface GenerationClauseStepProps {
  templateId: string;
  preloadedClauses?: TemplateClauseDetail[];
  initialClauses?: SelectedClause[];
  onNext: (clauses: SelectedClause[]) => void;
}

export function GenerationClauseStep({
  templateId,
  preloadedClauses,
  initialClauses,
  onNext,
}: GenerationClauseStepProps) {
  const [clauses, setClauses] = useState<SelectedClause[]>(
    initialClauses ?? [],
  );
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set());
  const [isLoading, setIsLoading] = useState(!initialClauses);
  const [error, setError] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const mapClauseData = useCallback((data: TemplateClauseDetail[]): SelectedClause[] => {
    return [...data]
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(
        (tc, idx): SelectedClause => ({
          clauseId: tc.clauseId,
          title: tc.title,
          category: tc.category,
          description: tc.description,
          required: tc.required,
          sortOrder: idx,
        }),
      );
  }, []);

  const loadClauses = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getTemplateClauses(templateId);
      const mapped = mapClauseData(data);
      setClauses(mapped);
      setCheckedIds(new Set(mapped.map((c) => c.clauseId)));
    } catch {
      setError("Failed to load template clauses.");
    } finally {
      setIsLoading(false);
    }
  }, [templateId, mapClauseData]);

  useEffect(() => {
    if (initialClauses) {
      // Restore checked state from initial clauses
      setCheckedIds(new Set(initialClauses.map((c) => c.clauseId)));
      return;
    }
    // Use preloaded data from parent if available, otherwise fetch
    if (preloadedClauses && preloadedClauses.length > 0) {
      const mapped = mapClauseData(preloadedClauses);
      setClauses(mapped);
      setCheckedIds(new Set(mapped.map((c) => c.clauseId)));
      setIsLoading(false);
      return;
    }
    loadClauses();
  }, [loadClauses, initialClauses, preloadedClauses, mapClauseData]);

  function toggleChecked(clauseId: string) {
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(clauseId)) {
        next.delete(clauseId);
      } else {
        next.add(clauseId);
      }
      return next;
    });
  }

  function moveClauseUp(index: number) {
    setClauses((prev) => {
      if (index <= 0) return prev;
      const next = [...prev];
      [next[index - 1], next[index]] = [next[index], next[index - 1]];
      return next;
    });
  }

  function moveClauseDown(index: number) {
    setClauses((prev) => {
      if (index >= prev.length - 1) return prev;
      const next = [...prev];
      [next[index], next[index + 1]] = [next[index + 1], next[index]];
      return next;
    });
  }

  function handleClausesAdded(
    newClauses: Array<{
      id: string;
      title: string;
      category: string;
      description: string | null;
    }>,
  ) {
    setClauses((prev) => {
      const existingIds = new Set(prev.map((c) => c.clauseId));
      const additions: SelectedClause[] = newClauses
        .filter((nc) => !existingIds.has(nc.id))
        .map((nc, idx) => ({
          clauseId: nc.id,
          title: nc.title,
          category: nc.category,
          description: nc.description,
          required: false,
          sortOrder: prev.length + idx,
        }));
      return [...prev, ...additions];
    });
    // Check newly added clauses
    setCheckedIds((prev) => {
      const next = new Set(prev);
      for (const nc of newClauses) {
        next.add(nc.id);
      }
      return next;
    });
  }

  function handleNext() {
    const selected = clauses
      .filter((c) => checkedIds.has(c.clauseId))
      .map((c, idx) => ({ ...c, sortOrder: idx }));
    onNext(selected);
  }

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <p className="text-sm text-slate-500">Loading clauses...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex h-48 items-center justify-center">
        <p className="text-sm text-destructive">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
          Select Clauses
        </h3>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setPickerOpen(true)}
        >
          <Plus className="mr-1 size-3.5" />
          Browse Library
        </Button>
      </div>

      {clauses.length === 0 ? (
        <div className="flex h-32 items-center justify-center rounded-lg border border-dashed border-slate-300 dark:border-slate-700">
          <p className="text-sm text-slate-500">
            No clauses available for this template.
          </p>
        </div>
      ) : (
        <div className="max-h-[400px] space-y-2 overflow-y-auto">
          {clauses.map((clause, index) => {
            const isChecked = checkedIds.has(clause.clauseId);
            return (
              <div
                key={clause.clauseId}
                className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900"
              >
                <Checkbox
                  checked={isChecked}
                  disabled={clause.required}
                  onCheckedChange={() => {
                    if (!clause.required) toggleChecked(clause.clauseId);
                  }}
                  aria-label={`Select ${clause.title}`}
                />

                <div className="flex flex-col gap-0.5">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-6"
                    onClick={() => moveClauseUp(index)}
                    disabled={index === 0}
                    type="button"
                    title="Move up"
                  >
                    <ArrowUp className="size-3" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-6"
                    onClick={() => moveClauseDown(index)}
                    disabled={index === clauses.length - 1}
                    type="button"
                    title="Move down"
                  >
                    <ArrowDown className="size-3" />
                  </Button>
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                      {clause.title}
                    </span>
                    <Badge variant="secondary" className="text-xs">
                      {clause.category}
                    </Badge>
                    {clause.required && (
                      <Badge variant="outline" className="text-xs">
                        Required
                      </Badge>
                    )}
                  </div>
                  {clause.description && (
                    <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">
                      {clause.description}
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="flex justify-end">
        <Button onClick={handleNext}>
          Next: Preview
          <ArrowRight className="ml-1.5 size-4" />
        </Button>
      </div>

      <ClausePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        existingClauseIds={clauses.map((c) => c.clauseId)}
        onConfirm={handleClausesAdded}
      />
    </div>
  );
}
