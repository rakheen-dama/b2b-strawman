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
}

const STANDARD_COLUMNS: Record<EntityType, { value: string; label: string }[]> = {
  PROJECT: [
    { value: "name", label: "Name" },
    { value: "description", label: "Description" },
    { value: "createdAt", label: "Created At" },
    { value: "updatedAt", label: "Updated At" },
  ],
  CUSTOMER: [
    { value: "name", label: "Name" },
    { value: "email", label: "Email" },
    { value: "phone", label: "Phone" },
    { value: "status", label: "Status" },
    { value: "createdAt", label: "Created At" },
  ],
  TASK: [
    { value: "title", label: "Title" },
    { value: "status", label: "Status" },
    { value: "priority", label: "Priority" },
    { value: "assigneeName", label: "Assignee" },
    { value: "dueDate", label: "Due Date" },
    { value: "createdAt", label: "Created At" },
  ],
};

export function CreateViewDialog({
  slug,
  entityType,
  allTags,
  fieldDefinitions,
  canCreateShared,
  onSave,
  children,
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
    standardCols.map((c) => c.value),
  );

  // Step 3: Save
  const [viewName, setViewName] = useState("");
  const [shared, setShared] = useState(false);

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
      prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col],
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
          <DialogDescription>
            Step {step} of 3
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
                <Label className="text-sm font-medium text-olive-700 dark:text-olive-300">
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
              <Label className="text-sm font-medium text-olive-700 dark:text-olive-300">
                Visible Columns
              </Label>
              <div className="max-h-64 space-y-1.5 overflow-y-auto">
                {allColumns.map((col) => (
                  <label
                    key={col.value}
                    className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-olive-50 dark:hover:bg-olive-900/50"
                  >
                    <input
                      type="checkbox"
                      checked={selectedColumns.includes(col.value)}
                      onChange={() => toggleColumn(col.value)}
                      className="rounded border-olive-300"
                    />
                    <span className="text-sm text-olive-700 dark:text-olive-300">
                      {col.label}
                    </span>
                    {col.value.startsWith("cf:") && (
                      <span className="text-xs text-olive-400">(custom)</span>
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
                  className="rounded border-olive-300"
                />
                <span className="text-sm text-olive-700 dark:text-olive-300">
                  Share with team
                </span>
                {!canCreateShared && (
                  <span className="text-xs text-olive-400">
                    (admin/owner only)
                  </span>
                )}
              </label>
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
              {saving ? "Saving..." : "Save View"}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
