"use client";

import { useState, useMemo } from "react";
import {
  ChevronDown,
  ChevronRight,
  Plus,
  Copy,
  Pencil,
  Power,
  Search,
  AlertTriangle,
  MoreHorizontal,
  FileText,
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { DocumentEditor } from "@/components/editor/DocumentEditor";
import { ClauseEditorSheet } from "@/components/clauses/clause-editor-sheet";
import {
  cloneClause,
  deactivateClause,
} from "@/lib/actions/clause-actions";
import type { Clause, ClauseSource } from "@/lib/actions/clause-actions";

const SOURCE_BADGE: Record<
  ClauseSource,
  { label: string; variant: "pro" | "lead" | "neutral" }
> = {
  SYSTEM: { label: "System", variant: "pro" },
  CLONED: { label: "Cloned", variant: "lead" },
  CUSTOM: { label: "Custom", variant: "neutral" },
};

function hasLegacyContent(body: Record<string, unknown>): boolean {
  const content = body?.content as Array<Record<string, unknown>> | undefined;
  if (!content || !Array.isArray(content)) return false;
  return content.some((node) => node.type === "legacyHtml");
}

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
  const [expandedClauses, setExpandedClauses] = useState<Set<string>>(
    () => new Set(),
  );
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [editorSheetOpen, setEditorSheetOpen] = useState(false);
  const [editingClause, setEditingClause] = useState<Clause | null>(null);

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
    setExpandedClauses((prev) => {
      const next = new Set(prev);
      if (next.has(clauseId)) {
        next.delete(clauseId);
      } else {
        next.add(clauseId);
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
          <Button
            size="sm"
            onClick={() => {
              setEditingClause(null);
              setEditorSheetOpen(true);
            }}
          >
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
                    <ClauseCard
                      key={clause.id}
                      clause={clause}
                      slug={slug}
                      categories={categories}
                      canManage={canManage}
                      isExpanded={expandedClauses.has(clause.id)}
                      onToggle={() => toggleClause(clause.id)}
                      onEdit={(c) => {
                        setEditingClause(c);
                        setEditorSheetOpen(true);
                      }}
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

      {/* Clause Editor Sheet */}
      {canManage && (
        <ClauseEditorSheet
          open={editorSheetOpen}
          onOpenChange={setEditorSheetOpen}
          slug={slug}
          clause={editingClause}
          categories={categories}
          onSuccess={(msg) => {
            clearMessages();
            setSuccess(msg);
          }}
          onError={(msg) => {
            clearMessages();
            setError(msg);
          }}
        />
      )}
    </div>
  );
}

interface ClauseCardProps {
  clause: Clause;
  slug: string;
  categories: string[];
  canManage: boolean;
  isExpanded: boolean;
  onToggle: () => void;
  onEdit: (clause: Clause) => void;
  onError: (message: string) => void;
  onSuccess: (message: string) => void;
}

function ClauseCard({
  clause,
  slug,
  categories,
  canManage,
  isExpanded,
  onToggle,
  onEdit,
  onError,
  onSuccess,
}: ClauseCardProps) {
  const sourceBadge = SOURCE_BADGE[clause.source];
  const isLegacy = hasLegacyContent(clause.body);

  const [cloneDialogOpen, setCloneDialogOpen] = useState(false);
  const [deactivateDialogOpen, setDeactivateDialogOpen] = useState(false);
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

  return (
    <>
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        {/* Card header */}
        <div className="flex items-center justify-between p-4">
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <button
              type="button"
              onClick={onToggle}
              className="mt-0.5 shrink-0 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
              aria-label={isExpanded ? "Collapse clause" : "Expand clause"}
            >
              {isExpanded ? (
                <ChevronDown className="size-4" />
              ) : (
                <ChevronRight className="size-4" />
              )}
            </button>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-medium text-slate-950 dark:text-slate-50">
                  {clause.title}
                </p>
                <Badge variant={sourceBadge.variant}>{sourceBadge.label}</Badge>
                {clause.active ? (
                  <Badge variant="success">Active</Badge>
                ) : (
                  <Badge variant="neutral">Inactive</Badge>
                )}
                {isLegacy && (
                  <Badge variant="warning">
                    <AlertTriangle className="mr-1 size-3" />
                    Migration needed
                  </Badge>
                )}
                {clause.templateUsageCount != null && (
                  <span className="inline-flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
                    <FileText className="size-3" />
                    Used in {clause.templateUsageCount}{" "}
                    {clause.templateUsageCount === 1
                      ? "template"
                      : "templates"}
                  </span>
                )}
              </div>
              {clause.description && (
                <p className="mt-1 truncate text-sm text-slate-500 dark:text-slate-400">
                  {clause.description}
                </p>
              )}
            </div>
          </div>

          {canManage && (
            <ClauseActionsMenu
              clause={clause}
              slug={slug}
              categories={categories}
              isPending={isPending}
              onEdit={() => onEdit(clause)}
              onClone={() => setCloneDialogOpen(true)}
              onDeactivate={() => setDeactivateDialogOpen(true)}
            />
          )}
        </div>

        {/* Expanded body content */}
        {isExpanded && (
          <div className="border-t border-slate-200 px-4 pb-4 pt-3 dark:border-slate-800">
            {isLegacy && (
              <div className="mb-3 rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-800 dark:bg-amber-950">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="mt-0.5 size-4 text-amber-600 dark:text-amber-400" />
                  <p className="text-sm text-amber-700 dark:text-amber-300">
                    This clause contains legacy HTML content that needs
                    migration to the new editor format.
                  </p>
                </div>
              </div>
            )}
            <DocumentEditor
              content={clause.body}
              scope="clause"
              editable={false}
            />
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
      <AlertDialog
        open={deactivateDialogOpen}
        onOpenChange={setDeactivateDialogOpen}
      >
        <AlertDialogContent className="max-w-md">
          <AlertDialogHeader>
            <AlertDialogTitle>Deactivate Clause</AlertDialogTitle>
            <AlertDialogDescription>
              This clause will be hidden from the clause picker but preserved on
              existing templates.
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
    </>
  );
}

interface ClauseActionsMenuProps {
  clause: Clause;
  slug: string;
  categories: string[];
  isPending: boolean;
  onEdit: () => void;
  onClone: () => void;
  onDeactivate: () => void;
}

function ClauseActionsMenu({
  clause,
  isPending,
  onEdit,
  onClone,
  onDeactivate,
}: ClauseActionsMenuProps) {
  const isSystem = clause.source === "SYSTEM";

  return (
    <div className="ml-4 flex items-center gap-2">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="plain"
            size="icon"
            className="size-8"
            disabled={isPending}
            aria-label="Clause actions"
          >
            <MoreHorizontal className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {isSystem ? (
            <>
              <DropdownMenuItem onClick={onEdit} disabled={isPending}>
                <Pencil className="mr-2 size-4" />
                View
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onClone} disabled={isPending}>
                <Copy className="mr-2 size-4" />
                Clone & Customize
              </DropdownMenuItem>
            </>
          ) : (
            <>
              <DropdownMenuItem onClick={onEdit} disabled={isPending}>
                <Pencil className="mr-2 size-4" />
                Edit
              </DropdownMenuItem>
              <DropdownMenuItem onClick={onClone} disabled={isPending}>
                <Copy className="mr-2 size-4" />
                Clone
              </DropdownMenuItem>
              {clause.active && (
                <DropdownMenuItem onClick={onDeactivate} disabled={isPending}>
                  <Power className="mr-2 size-4" />
                  Deactivate
                </DropdownMenuItem>
              )}
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
