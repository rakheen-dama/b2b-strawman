"use client";

import { useState, useMemo } from "react";
import { ChevronDown, ChevronRight, Plus, Copy, Pencil, Trash2, Ban, CheckCircle2, Search } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  cloneClause,
  deactivateClause,
  deleteClause,
} from "@/lib/actions/clause-actions";
import type { Clause, ClauseSource } from "@/lib/actions/clause-actions";

const SOURCE_BADGE: Record<ClauseSource, { label: string; variant: "pro" | "lead" | "neutral" }> = {
  SYSTEM: { label: "System", variant: "pro" },
  CLONED: { label: "Cloned", variant: "lead" },
  CUSTOM: { label: "Custom", variant: "neutral" },
};

interface ClausesContentProps {
  slug: string;
  clauses: Clause[];
  categories: string[];
  canManage: boolean;
}

export function ClausesContent({
  slug,
  clauses,
  categories,
  canManage,
}: ClausesContentProps) {
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("");
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    () => new Set(categories),
  );

  const filteredClauses = useMemo(() => {
    return clauses.filter((c) => {
      const matchesSearch =
        !search || c.title.toLowerCase().includes(search.toLowerCase());
      const matchesCategory =
        !selectedCategory || c.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
  }, [clauses, search, selectedCategory]);

  const grouped = useMemo(() => {
    const groups: Record<string, Clause[]> = {};
    for (const clause of filteredClauses) {
      if (!groups[clause.category]) groups[clause.category] = [];
      groups[clause.category].push(clause);
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

  return (
    <div className="space-y-6">
      {/* Toolbar */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 items-center gap-3">
          <div className="relative max-w-xs flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              placeholder="Search clauses..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-100"
            aria-label="Filter by category"
          >
            <option value="">All Categories</option>
            {categories.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
        </div>
        {canManage && (
          <Button size="sm" disabled>
            <Plus className="mr-1 size-4" />
            New Clause
          </Button>
        )}
      </div>

      {/* Clause List */}
      {filteredClauses.length === 0 ? (
        <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          No clauses found.
        </p>
      ) : (
        sortedCategories.map((category) => {
          const isExpanded = expandedCategories.has(category);
          return (
            <div key={category} className="space-y-2">
              <button
                type="button"
                onClick={() => toggleCategory(category)}
                className="flex w-full items-center gap-2 text-left"
              >
                {isExpanded ? (
                  <ChevronDown className="size-4 text-slate-500" />
                ) : (
                  <ChevronRight className="size-4 text-slate-500" />
                )}
                <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {category}
                </h2>
                <Badge variant="neutral">{grouped[category].length}</Badge>
              </button>
              {isExpanded && (
                <div className="ml-6 space-y-2">
                  {grouped[category]
                    .sort((a, b) => a.sortOrder - b.sortOrder)
                    .map((clause) => (
                      <ClauseRow
                        key={clause.id}
                        clause={clause}
                        slug={slug}
                        canManage={canManage}
                      />
                    ))}
                </div>
              )}
            </div>
          );
        })
      )}
    </div>
  );
}

interface ClauseRowProps {
  clause: Clause;
  slug: string;
  canManage: boolean;
}

function ClauseRow({ clause, slug, canManage }: ClauseRowProps) {
  const sourceBadge = SOURCE_BADGE[clause.source];

  async function handleClone() {
    await cloneClause(slug, clause.id);
  }

  async function handleDeactivate() {
    await deactivateClause(slug, clause.id);
  }

  async function handleDelete() {
    await deleteClause(slug, clause.id);
  }

  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="font-medium text-slate-950 dark:text-slate-50">
            {clause.title}
          </p>
          <Badge variant={sourceBadge.variant}>{sourceBadge.label}</Badge>
          {clause.active ? (
            <Badge variant="success">Active</Badge>
          ) : (
            <Badge variant="neutral">Inactive</Badge>
          )}
        </div>
        {clause.description && (
          <p className="mt-1 truncate text-sm text-slate-500 dark:text-slate-400">
            {clause.description}
          </p>
        )}
      </div>

      {canManage && (
        <div className="ml-4 flex items-center gap-1">
          {clause.source === "SYSTEM" ? (
            <Button
              size="sm"
              variant="ghost"
              title="Clone clause"
              onClick={handleClone}
            >
              <Copy className="size-4" />
            </Button>
          ) : (
            <>
              <Button size="sm" variant="ghost" title="Edit clause" disabled>
                <Pencil className="size-4" />
              </Button>
              <Button
                size="sm"
                variant="ghost"
                title={clause.active ? "Deactivate clause" : "Activate clause"}
                onClick={handleDeactivate}
              >
                {clause.active ? (
                  <Ban className="size-4" />
                ) : (
                  <CheckCircle2 className="size-4" />
                )}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                title="Delete clause"
                onClick={handleDelete}
              >
                <Trash2 className="size-4" />
              </Button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
