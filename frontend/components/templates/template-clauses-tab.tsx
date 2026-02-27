"use client";

import { useEffect, useState, useCallback } from "react";
import { ArrowUp, ArrowDown, X, Plus, Save } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  getTemplateClauses,
  setTemplateClauses,
} from "@/lib/actions/template-clause-actions";
import type { TemplateClauseDetail, TemplateClauseConfig } from "@/lib/actions/template-clause-actions";
import { ClausePickerDialog } from "@/components/templates/clause-picker-dialog";

interface TemplateClausesTabProps {
  templateId: string;
  slug: string;
  readOnly?: boolean;
}

interface LocalClause {
  clauseId: string;
  title: string;
  category: string;
  description: string | null;
  required: boolean;
  sortOrder: number;
}

export function TemplateClausesTab({
  templateId,
  slug,
  readOnly,
}: TemplateClausesTabProps) {
  const [clauses, setClauses] = useState<LocalClause[]>([]);
  const [originalClauses, setOriginalClauses] = useState<LocalClause[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);

  const hasUnsavedChanges =
    JSON.stringify(clauses) !== JSON.stringify(originalClauses);

  const loadClauses = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getTemplateClauses(templateId);
      const mapped = data.map(
        (tc: TemplateClauseDetail): LocalClause => ({
          clauseId: tc.clauseId,
          title: tc.title,
          category: tc.category,
          description: tc.description,
          required: tc.required,
          sortOrder: tc.sortOrder,
        }),
      );
      const sorted = mapped.sort((a, b) => a.sortOrder - b.sortOrder);
      setClauses(sorted);
      setOriginalClauses(sorted);
    } catch {
      setError("Failed to load template clauses.");
    } finally {
      setIsLoading(false);
    }
  }, [templateId]);

  useEffect(() => {
    loadClauses();
  }, [loadClauses]);

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

  function toggleRequired(index: number) {
    setClauses((prev) =>
      prev.map((c, i) =>
        i === index ? { ...c, required: !c.required } : c,
      ),
    );
  }

  function removeClause(index: number) {
    setClauses((prev) => prev.filter((_, i) => i !== index));
  }

  function handleClausesAdded(
    newClauses: Array<{ id: string; title: string; category: string; description: string | null }>,
  ) {
    setClauses((prev) => {
      const existingIds = new Set(prev.map((c) => c.clauseId));
      const additions: LocalClause[] = newClauses
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
  }

  async function handleSave() {
    setIsSaving(true);
    setError(null);
    setSuccessMsg(null);

    const configs: TemplateClauseConfig[] = clauses.map((c, idx) => ({
      clauseId: c.clauseId,
      sortOrder: idx,
      required: c.required,
    }));

    const result = await setTemplateClauses(templateId, configs, slug);

    if (result.success) {
      setSuccessMsg("Clauses saved successfully.");
      setTimeout(() => setSuccessMsg(null), 3000);
      // Reload to get fresh data
      await loadClauses();
    } else {
      setError(result.error ?? "Failed to save clauses.");
    }

    setIsSaving(false);
  }

  if (isLoading) {
    return (
      <div className="flex h-48 items-center justify-center">
        <p className="text-sm text-slate-500">Loading clauses...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h3 className="text-sm font-semibold text-slate-950 dark:text-slate-50">
            Template Clauses
          </h3>
          {hasUnsavedChanges && (
            <Badge variant="outline" className="text-xs">
              Unsaved changes
            </Badge>
          )}
        </div>
        {!readOnly && (
          <div className="flex items-center gap-2">
            {successMsg && (
              <span className="text-sm text-teal-600">{successMsg}</span>
            )}
            {error && (
              <span className="text-sm text-destructive">{error}</span>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPickerOpen(true)}
            >
              <Plus className="mr-1 size-3.5" />
              Add Clause
            </Button>
            <Button
              size="sm"
              onClick={handleSave}
              disabled={isSaving || !hasUnsavedChanges}
            >
              <Save className="mr-1 size-3.5" />
              {isSaving ? "Saving..." : "Save"}
            </Button>
          </div>
        )}
      </div>

      {clauses.length === 0 ? (
        <div className="flex h-48 items-center justify-center rounded-lg border border-dashed border-slate-300 dark:border-slate-700">
          <p className="text-sm text-slate-500">
            No clauses configured. Add clauses to suggest default terms when
            generating documents from this template.
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {clauses.map((clause, index) => (
            <div
              key={clause.clauseId}
              className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900"
            >
              {!readOnly && (
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
              )}

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-slate-950 dark:text-slate-50">
                    {clause.title}
                  </span>
                  <Badge variant="secondary" className="text-xs">
                    {clause.category}
                  </Badge>
                </div>
                {clause.description && (
                  <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">
                    {clause.description}
                  </p>
                )}
              </div>

              {!readOnly && (
                <div className="flex items-center gap-3">
                  <label className="flex items-center gap-1.5 text-xs text-slate-500">
                    Required
                    <Switch
                      size="sm"
                      checked={clause.required}
                      onCheckedChange={() => toggleRequired(index)}
                      aria-label={`Toggle required for ${clause.title}`}
                    />
                  </label>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7 text-slate-400 hover:text-red-600 dark:hover:text-red-400"
                    onClick={() => removeClause(index)}
                    type="button"
                    aria-label={`Remove ${clause.title}`}
                  >
                    <X className="size-3.5" />
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <ClausePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        existingClauseIds={clauses.map((c) => c.clauseId)}
        onConfirm={handleClausesAdded}
      />
    </div>
  );
}
