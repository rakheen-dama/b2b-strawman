"use client";

import { useState, useMemo } from "react";
import {
  ChevronDown,
  ChevronRight,
  Plus,
  Copy,
  Pencil,
  Trash2,
  Ban,
  Search,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    () => new Set(categories),
  );
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const filteredClauses = useMemo(() => {
    return clauses.filter((c) => {
      const matchesSearch =
        !search || c.title.toLowerCase().includes(search.toLowerCase());
      const matchesCategory =
        selectedCategory === "all" || c.category === selectedCategory;
      return matchesSearch && matchesCategory;
    });
  }, [clauses, search, selectedCategory]);

  const grouped = useMemo(() => {
    const groups: Record<string, Clause[]> = {};
    for (const clause of filteredClauses) {
      if (!groups[clause.category]) groups[clause.category] = [];
      groups[clause.category].push(clause);
    }
    // Sort clauses within each group
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

  function clearMessages() {
    setError(null);
    setSuccess(null);
  }

  return (
    <div className="space-y-6">
      {/* Feedback messages */}
      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </div>
      )}
      {success && (
        <div className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-800 dark:border-green-800 dark:bg-green-950 dark:text-green-200">
          {success}
        </div>
      )}

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
          <Select
            value={selectedCategory}
            onValueChange={setSelectedCategory}
          >
            <SelectTrigger aria-label="Filter by category">
              <SelectValue placeholder="All Categories" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Categories</SelectItem>
              {categories.map((cat) => (
                <SelectItem key={cat} value={cat}>
                  {cat}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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
                  {grouped[category].map((clause) => (
                    <ClauseRow
                      key={clause.id}
                      clause={clause}
                      slug={slug}
                      canManage={canManage}
                      onError={(msg) => {
                        clearMessages();
                        setError(msg);
                      }}
                      onSuccess={(msg) => {
                        clearMessages();
                        setSuccess(msg);
                      }}
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
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}

function ClauseRow({ clause, slug, canManage, onError, onSuccess }: ClauseRowProps) {
  const sourceBadge = SOURCE_BADGE[clause.source];

  async function handleClone() {
    const result = await cloneClause(slug, clause.id);
    if (result.success) {
      onSuccess("Clause cloned.");
    } else {
      onError(result.error ?? "Failed to clone clause.");
    }
  }

  async function handleDeactivate() {
    const result = await deactivateClause(slug, clause.id);
    if (result.success) {
      onSuccess("Clause deactivated.");
    } else {
      onError(result.error ?? "Failed to deactivate clause.");
    }
  }

  async function handleDelete() {
    const result = await deleteClause(slug, clause.id);
    if (result.success) {
      onSuccess("Clause deleted.");
    } else {
      onError(result.error ?? "Failed to delete clause.");
    }
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
              {clause.active && (
                <Button
                  size="sm"
                  variant="ghost"
                  title="Deactivate clause"
                  onClick={handleDeactivate}
                >
                  <Ban className="size-4" />
                </Button>
              )}
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
