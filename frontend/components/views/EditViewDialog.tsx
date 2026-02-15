"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TagFilter } from "./TagFilter";
import { CustomFieldFilter } from "./CustomFieldFilter";
import { DateRangeFilter, type DateRangeValue } from "./DateRangeFilter";
import { SearchInput } from "./SearchInput";
import { STANDARD_COLUMNS } from "./constants";
import type {
  EntityType,
  TagResponse,
  FieldDefinitionResponse,
  SavedViewResponse,
  UpdateSavedViewRequest,
} from "@/lib/types";

interface EditViewDialogProps {
  slug: string;
  view: SavedViewResponse;
  allTags: TagResponse[];
  fieldDefinitions: FieldDefinitionResponse[];
  canEdit: boolean;
  onSave: (
    viewId: string,
    req: UpdateSavedViewRequest,
  ) => Promise<{ success: boolean; error?: string }>;
  children: React.ReactNode;
}

export function EditViewDialog({
  view,
  allTags,
  fieldDefinitions,
  canEdit,
  onSave,
  children,
}: EditViewDialogProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const entityType = view.entityType as EntityType;

  // Initialize from existing view data
  const initFilters = view.filters ?? {};
  const [filterTags, setFilterTags] = useState<string[]>(
    (initFilters.tags as string[]) ?? [],
  );
  const [filterCustomFields, setFilterCustomFields] = useState<
    Record<string, { op: string; value: unknown }>
  >(
    (initFilters.customFields as Record<string, { op: string; value: unknown }>) ?? {},
  );
  const [filterDateRange, setFilterDateRange] = useState<DateRangeValue>({
    field:
      (initFilters.dateRange as { field?: string })?.field ?? "created_at",
    from: (initFilters.dateRange as { from?: string })?.from,
    to: (initFilters.dateRange as { to?: string })?.to,
  });
  const [filterSearch, setFilterSearch] = useState(
    (initFilters.search as string) ?? "",
  );

  const standardCols = STANDARD_COLUMNS[entityType];
  const allColumns = [
    ...standardCols,
    ...fieldDefinitions.map((fd) => ({
      value: `cf:${fd.slug}`,
      label: fd.name,
    })),
  ];
  const [selectedColumns, setSelectedColumns] = useState<string[]>(
    view.columns ?? standardCols.map((c) => c.value),
  );

  const [viewName, setViewName] = useState(view.name);

  function resetForm() {
    setStep(1);
    setError(null);
    // Re-initialize from view data
    const f = view.filters ?? {};
    setFilterTags((f.tags as string[]) ?? []);
    setFilterCustomFields(
      (f.customFields as Record<string, { op: string; value: unknown }>) ?? {},
    );
    setFilterDateRange({
      field: (f.dateRange as { field?: string })?.field ?? "created_at",
      from: (f.dateRange as { from?: string })?.from,
      to: (f.dateRange as { to?: string })?.to,
    });
    setFilterSearch((f.search as string) ?? "");
    setSelectedColumns(view.columns ?? standardCols.map((c) => c.value));
    setViewName(view.name);
  }

  function buildFilters(): Record<string, unknown> {
    const filters: Record<string, unknown> = {};
    if (filterTags.length > 0) filters.tags = filterTags;
    if (Object.keys(filterCustomFields).length > 0)
      filters.customFields = filterCustomFields;
    if (filterDateRange.from || filterDateRange.to)
      filters.dateRange = filterDateRange;
    if (filterSearch.trim()) filters.search = filterSearch.trim();
    return filters;
  }

  async function handleSave() {
    if (!viewName.trim()) {
      setError("View name is required.");
      return;
    }

    setSaving(true);
    setError(null);

    const req: UpdateSavedViewRequest = {
      name: viewName.trim(),
      filters: buildFilters(),
      columns: selectedColumns,
      sortOrder: view.sortOrder,
    };

    try {
      const result = await onSave(view.id, req);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to update view.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setSaving(false);
    }
  }

  function toggleColumn(col: string) {
    setSelectedColumns((prev) =>
      prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col],
    );
  }

  if (!canEdit) return <>{children}</>;

  return (
    <Dialog
      open={open}
      onOpenChange={(isOpen) => {
        setOpen(isOpen);
        if (!isOpen) resetForm();
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {step === 1 && "Edit Filters"}
            {step === 2 && "Edit Columns"}
            {step === 3 && "Save Changes"}
          </DialogTitle>
          <DialogDescription>
            Step {step} of 3 — Editing &quot;{view.name}&quot;
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Step 1: Filters */}
          {step === 1 && (
            <div className="space-y-4">
              <TagFilter
                value={filterTags}
                onChange={setFilterTags}
                allTags={allTags}
              />
              <CustomFieldFilter
                value={filterCustomFields}
                onChange={setFilterCustomFields}
                fieldDefinitions={fieldDefinitions}
              />
              <DateRangeFilter
                value={filterDateRange}
                onChange={setFilterDateRange}
              />
              <div className="space-y-2">
                <Label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  Search
                </Label>
                <SearchInput
                  value={filterSearch}
                  onChange={setFilterSearch}
                  placeholder="Search keyword..."
                />
              </div>
            </div>
          )}

          {/* Step 2: Columns */}
          {step === 2 && (
            <div className="space-y-2">
              <Label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                Visible Columns
              </Label>
              <div className="max-h-64 space-y-1.5 overflow-y-auto">
                {allColumns.map((col) => (
                  <label
                    key={col.value}
                    className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-slate-50 dark:hover:bg-slate-900/50"
                  >
                    <input
                      type="checkbox"
                      checked={selectedColumns.includes(col.value)}
                      onChange={() => toggleColumn(col.value)}
                      className="rounded border-slate-300"
                    />
                    <span className="text-sm text-slate-700 dark:text-slate-300">
                      {col.label}
                    </span>
                    {col.value.startsWith("cf:") && (
                      <span className="text-xs text-slate-400">(custom)</span>
                    )}
                  </label>
                ))}
              </div>
            </div>
          )}

          {/* Step 3: Save */}
          {step === 3 && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="edit-view-name">View Name</Label>
                <Input
                  id="edit-view-name"
                  value={viewName}
                  onChange={(e) => setViewName(e.target.value)}
                  placeholder="e.g. Active VIP Clients"
                  maxLength={100}
                />
              </div>
            </div>
          )}

          {error && (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>

        <DialogFooter>
          {step > 1 && (
            <Button
              variant="outline"
              onClick={() => setStep((s) => s - 1)}
              disabled={saving}
            >
              Previous
            </Button>
          )}
          {step < 3 ? (
            <Button onClick={() => setStep((s) => s + 1)}>Next</Button>
          ) : (
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "Saving..." : "Save Changes"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
