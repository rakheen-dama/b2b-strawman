"use client";

import { useState } from "react";
import { ScrollText, Search, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/empty-state";
import type { Clause } from "@/lib/actions/clause-actions";

interface ClauseListProps {
  slug: string;
  clauses: Clause[];
  categories: string[];
  canManage: boolean;
}

export function ClauseList({
  slug,
  clauses,
  categories,
  canManage,
}: ClauseListProps) {
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(
    null,
  );

  const filtered = clauses.filter((clause) => {
    const matchesSearch =
      !search ||
      clause.title.toLowerCase().includes(search.toLowerCase()) ||
      clause.description?.toLowerCase().includes(search.toLowerCase());
    const matchesCategory =
      !selectedCategory || clause.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <Input
            placeholder="Search clauses..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          {categories.length > 0 && (
            <select
              value={selectedCategory ?? ""}
              onChange={(e) =>
                setSelectedCategory(e.target.value || null)
              }
              className="border-input bg-background h-9 rounded-md border px-3 text-sm shadow-xs"
            >
              <option value="">All categories</option>
              {categories.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
          )}
          {canManage && (
            <Button size="sm" variant="outline">
              <Plus className="mr-1.5 size-4" />
              Add clause
            </Button>
          )}
        </div>
      </div>

      {/* Clause List */}
      {filtered.length === 0 ? (
        <EmptyState
          icon={ScrollText}
          title="No clauses found"
          description={
            search
              ? "Try adjusting your search."
              : "Add your first clause to the library."
          }
        />
      ) : (
        <div className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">
          {filtered.map((clause) => (
            <div
              key={clause.id}
              className="flex items-start gap-4 p-4 transition-colors hover:bg-slate-50"
            >
              <ScrollText className="mt-0.5 size-4 shrink-0 text-slate-400" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="font-medium text-slate-900">
                    {clause.title}
                  </h3>
                  {clause.source === "SYSTEM" && (
                    <Badge variant="neutral">System</Badge>
                  )}
                  {clause.source === "CLONED" && (
                    <Badge variant="neutral">Cloned</Badge>
                  )}
                  {clause.category && (
                    <Badge variant="neutral">{clause.category}</Badge>
                  )}
                </div>
                {clause.description && (
                  <p className="mt-1 text-sm text-slate-500 line-clamp-2">
                    {clause.description}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
