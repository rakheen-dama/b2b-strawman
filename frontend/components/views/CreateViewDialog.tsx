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
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";
import { Label } from "@b2mash/ui/label";
import { TagFilter } from "./TagFilter";
import { CustomFieldFilter } from "./CustomFieldFilter";
import { DateRangeFilter, type DateRangeValue } from "./DateRangeFilter";
import { SearchInput } from "./SearchInput";
import { STANDARD_COLUMNS } from "./constants";
import type {
  EntityType,
  TagResponse,
  FieldDefinitionResponse,
  CreateSavedViewRequest,
} from "@/lib/types";

interface CreateViewDialogProps {
  slug: string;
  entityType: EntityType;
  allTags: TagResponse[];
  fieldDefinitions: FieldDefinitionResponse[];
  canCreateShared: boolean;
  onSave: (req: CreateSavedViewRequest) => Promise<{ success: boolean; error?: string }>;
  children: React.ReactNode;
  /**
   * Filters the new view starts from (615C F-4: "Save view" from a system view). Merged into the
   * saved filters and shown read-only on step 1; survives a form reset.
   */
  initialFilters?: Record<string, unknown>;
  /** Name of the view the initial filters come from ("Carried from …"). */
  initialFiltersLabel?: string;
}

/** Read-only summary line for one carried filter. */
export function describeCarriedFilter(key: string, value: unknown): string {
  switch (key) {
    case "status": {
      const statuses = (Array.isArray(value) ? value : [value]).map((s) => {
        const str = String(s).toLowerCase();
        return str.charAt(0).toUpperCase() + str.slice(1);
      });
      return `Status: ${statuses.join(", ")}`;
    }
    case "mine":
      return "Only matters I am on";
    case "hasUnbilled":
      return "Has unbilled time";
    case "inactiveSince": {
      const rel = typeof value === "string" ? /^-(\d+)d$/.exec(value) : null;
      return rel ? `No activity in the last ${rel[1]} days` : `No activity since ${String(value)}`;
    }
    // Client system views (615D)
    case "lifecycleStatus": {
      const str = String(value).toLowerCase();
      return `Lifecycle: ${str.charAt(0).toUpperCase()}${str.slice(1)}`;
    }
    case "ficaStatus":
      return String(value).toUpperCase() === "DONE" ? "FICA done" : "FICA outstanding";
    case "hasTrustBalance":
      return "Has a trust balance";
    default:
      return `${key}: ${Array.isArray(value) ? value.join(", ") : String(value)}`;
  }
}

export function CreateViewDialog({
  slug,
  entityType,
  allTags,
  fieldDefinitions,
  canCreateShared,
  onSave,
  children,
  initialFilters,
  initialFiltersLabel,
}: CreateViewDialogProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Step 1: Filters
  const [filterTags, setFilterTags] = useState<string[]>([]);
  const [filterCustomFields, setFilterCustomFields] = useState<
    Record<string, { op: string; value: unknown }>
  >({});
  const [filterDateRange, setFilterDateRange] = useState<DateRangeValue>({
    field: "created_at",
  });
  const [filterSearch, setFilterSearch] = useState("");

  // Step 2: Columns
  const standardCols = STANDARD_COLUMNS[entityType];
  const allColumns = [
    ...standardCols,
    ...fieldDefinitions.map((fd) => ({
      value: `cf:${fd.slug}`,
      label: fd.name,
    })),
  ];
  const [selectedColumns, setSelectedColumns] = useState<string[]>(
    standardCols.map((c) => c.value)
  );

  // Step 3: Save
  const [viewName, setViewName] = useState("");
  const [shared, setShared] = useState(false);

  const carriedFilters = Object.entries(initialFilters ?? {});

  function resetForm() {
    setStep(1);
    setError(null);
    setFilterTags([]);
    setFilterCustomFields({});
    setFilterDateRange({ field: "created_at" });
    setFilterSearch("");
    setSelectedColumns(standardCols.map((c) => c.value));
    setViewName("");
    setShared(false);
  }

  function buildFilters(): Record<string, unknown> {
    const filters: Record<string, unknown> = { ...initialFilters };
    if (filterTags.length > 0) filters.tags = filterTags;
    if (Object.keys(filterCustomFields).length > 0) filters.customFields = filterCustomFields;
    if (filterDateRange.from || filterDateRange.to) filters.dateRange = filterDateRange;
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

    const req: CreateSavedViewRequest = {
      entityType,
      name: viewName.trim(),
      filters: buildFilters(),
      columns: selectedColumns,
      shared,
      sortOrder: 0,
    };

    try {
      const result = await onSave(req);
      if (result.success) {
        setOpen(false);
        resetForm();
      } else {
        setError(result.error ?? "Failed to create view.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setSaving(false);
    }
  }

  function toggleColumn(col: string) {
    setSelectedColumns((prev) =>
      prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]
    );
  }

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
            {step === 1 && "Configure Filters"}
            {step === 2 && "Select Columns"}
            {step === 3 && "Name Your View"}
          </DialogTitle>
          <DialogDescription>Step {step} of 3</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Step 1: Filters */}
          {step === 1 && (
            <div className="space-y-4">
              {carriedFilters.length > 0 && (
                <div
                  className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/50"
                  data-testid="carried-filters"
                >
                  <p className="text-xs font-medium text-slate-700 dark:text-slate-300">
                    Carried from {initialFiltersLabel ?? "the current view"}
                  </p>
                  <ul className="mt-1 space-y-0.5 text-xs text-slate-600 dark:text-slate-400">
                    {carriedFilters.map(([key, value]) => (
                      <li key={key}>{describeCarriedFilter(key, value)}</li>
                    ))}
                  </ul>
                </div>
              )}
              <TagFilter value={filterTags} onChange={setFilterTags} allTags={allTags} />
              <CustomFieldFilter
                value={filterCustomFields}
                onChange={setFilterCustomFields}
                fieldDefinitions={fieldDefinitions}
              />
              <DateRangeFilter value={filterDateRange} onChange={setFilterDateRange} />
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
                    <span className="text-sm text-slate-700 dark:text-slate-300">{col.label}</span>
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
                <Label htmlFor="view-name">View Name</Label>
                <Input
                  id="view-name"
                  value={viewName}
                  onChange={(e) => setViewName(e.target.value)}
                  placeholder="e.g. Active VIP Clients"
                  maxLength={100}
                />
              </div>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={shared}
                  onChange={(e) => setShared(e.target.checked)}
                  disabled={!canCreateShared}
                  className="rounded border-slate-300"
                />
                <span className="text-sm text-slate-700 dark:text-slate-300">Share with team</span>
                {!canCreateShared && (
                  <span className="text-xs text-slate-400">(admin/owner only)</span>
                )}
              </label>
            </div>
          )}

          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        </div>

        <DialogFooter>
          {step > 1 && (
            <Button variant="outline" onClick={() => setStep((s) => s - 1)} disabled={saving}>
              Previous
            </Button>
          )}
          {step < 3 ? (
            <Button onClick={() => setStep((s) => s + 1)}>Next</Button>
          ) : (
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "Saving..." : "Save View"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
