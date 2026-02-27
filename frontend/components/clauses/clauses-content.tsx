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
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ClauseFormDialog } from "@/components/clauses/clause-form-dialog";
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
          <ClauseFormDialog slug={slug} categories={categories}>
            <Button size="sm">
              <Plus className="mr-1 size-4" />
              New Clause
            </Button>
          </ClauseFormDialog>
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
                      categories={categories}
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
  categories: string[];
  canManage: boolean;
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}

function ClauseRow({ clause, slug, categories, canManage, onError, onSuccess }: ClauseRowProps) {
  const sourceBadge = SOURCE_BADGE[clause.source];

  const [cloneDialogOpen, setCloneDialogOpen] = useState(false);
  const [deactivateDialogOpen, setDeactivateDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [isPending, setIsPending] = useState(false);

  async function handleCloneConfirm() {
    setIsPending(true);
    try {
      const result = await cloneClause(slug, clause.id);
      if (result.success) {
        onSuccess(`Clause "${clause.title}" cloned successfully.`);
      } else {
        onError(result.error ?? "Failed to clone clause.");
      }
    } catch {
      onError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
      setCloneDialogOpen(false);
    }
  }

  async function handleDeactivateConfirm() {
    setIsPending(true);
    try {
      const result = await deactivateClause(slug, clause.id);
      if (result.success) {
        onSuccess("Clause deactivated.");
      } else {
        onError(result.error ?? "Failed to deactivate clause.");
      }
    } catch {
      onError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
      setDeactivateDialogOpen(false);
    }
  }

  async function handleDeleteConfirm() {
    setIsPending(true);
    try {
      const result = await deleteClause(slug, clause.id);
      if (result.success) {
        onSuccess("Clause deleted.");
      } else {
        onError(result.error ?? "Failed to delete clause.");
      }
    } catch {
      onError("An unexpected error occurred.");
    } finally {
      setIsPending(false);
      setDeleteDialogOpen(false);
    }
  }

  return (
    <>
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
                onClick={() => setCloneDialogOpen(true)}
              >
                <Copy className="size-4" />
              </Button>
            ) : (
              <>
                <ClauseFormDialog slug={slug} clause={clause} categories={categories}>
                  <Button size="sm" variant="ghost" title="Edit clause">
                    <Pencil className="size-4" />
                  </Button>
                </ClauseFormDialog>
                {clause.active && (
                  <Button
                    size="sm"
                    variant="ghost"
                    title="Deactivate clause"
                    onClick={() => setDeactivateDialogOpen(true)}
                  >
                    <Ban className="size-4" />
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="ghost"
                  title="Delete clause"
                  onClick={() => setDeleteDialogOpen(true)}
                >
                  <Trash2 className="size-4" />
                </Button>
              </>
            )}
          </div>
        )}
      </div>

      {/* Clone Confirmation Dialog */}
      <AlertDialog open={cloneDialogOpen} onOpenChange={setCloneDialogOpen}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>Clone Clause</AlertDialogTitle>
            <AlertDialogDescription>
              Clone this clause to create an editable copy?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel variant="plain" disabled={isPending}>
              Cancel
            </AlertDialogCancel>
            <Button onClick={handleCloneConfirm} disabled={isPending}>
              {isPending ? "Cloning..." : "Clone"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Deactivate Confirmation Dialog */}
      <AlertDialog open={deactivateDialogOpen} onOpenChange={setDeactivateDialogOpen}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>Deactivate Clause</AlertDialogTitle>
            <AlertDialogDescription>
              This clause will be hidden from the clause picker but preserved on existing templates.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel variant="plain" disabled={isPending}>
              Cancel
            </AlertDialogCancel>
            <Button onClick={handleDeactivateConfirm} disabled={isPending}>
              {isPending ? "Deactivating..." : "Deactivate"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Clause</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete this clause? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel variant="plain" disabled={isPending}>
              Cancel
            </AlertDialogCancel>
            <Button variant="destructive" onClick={handleDeleteConfirm} disabled={isPending}>
              {isPending ? "Deleting..." : "Delete"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
