"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { X } from "lucide-react";
import {
  createFieldDefinitionAction,
  updateFieldDefinitionAction,
} from "@/app/(app)/org/[slug]/settings/custom-fields/actions";
import type {
  EntityType,
  FieldType,
  FieldDefinitionResponse,
} from "@/lib/types";

const ENTITY_TYPES: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
];

const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: "TEXT", label: "Text" },
  { value: "NUMBER", label: "Number" },
  { value: "DATE", label: "Date" },
  { value: "DROPDOWN", label: "Dropdown" },
  { value: "BOOLEAN", label: "Boolean" },
  { value: "CURRENCY", label: "Currency" },
  { value: "URL", label: "URL" },
  { value: "EMAIL", label: "Email" },
  { value: "PHONE", label: "Phone" },
];

interface FieldDefinitionDialogProps {
  slug: string;
  entityType?: EntityType;
  field?: FieldDefinitionResponse;
  children: React.ReactNode;
}

export function FieldDefinitionDialog({
  slug,
  entityType: initialEntityType,
  field,
  children,
}: FieldDefinitionDialogProps) {
  const isEditing = !!field;
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [entityType, setEntityType] = useState<EntityType>(
    field?.entityType ?? initialEntityType ?? "PROJECT",
  );
  const [name, setName] = useState(field?.name ?? "");
  const [slugField, setSlugField] = useState(field?.slug ?? "");
  const [fieldType, setFieldType] = useState<FieldType>(
    field?.fieldType ?? "TEXT",
  );
  const [description, setDescription] = useState(field?.description ?? "");
  const [required, setRequired] = useState(field?.required ?? false);
  const [sortOrder, setSortOrder] = useState(field?.sortOrder ?? 0);

  // Conditional validation fields
  const [minLength, setMinLength] = useState(
    field?.validation?.minLength?.toString() ?? "",
  );
  const [maxLength, setMaxLength] = useState(
    field?.validation?.maxLength?.toString() ?? "",
  );
  const [pattern, setPattern] = useState(
    (field?.validation?.pattern as string) ?? "",
  );
  const [minNumber, setMinNumber] = useState(
    field?.validation?.min?.toString() ?? "",
  );
  const [maxNumber, setMaxNumber] = useState(
    field?.validation?.max?.toString() ?? "",
  );
  const [minDate, setMinDate] = useState(
    (field?.validation?.min as string) ?? "",
  );
  const [maxDate, setMaxDate] = useState(
    (field?.validation?.max as string) ?? "",
  );

  // Dropdown options
  const [options, setOptions] = useState<
    Array<{ value: string; label: string }>
  >(field?.options ?? [{ value: "", label: "" }]);

  function resetForm() {
    setEntityType(initialEntityType ?? "PROJECT");
    setName("");
    setSlugField("");
    setFieldType("TEXT");
    setDescription("");
    setRequired(false);
    setSortOrder(0);
    setMinLength("");
    setMaxLength("");
    setPattern("");
    setMinNumber("");
    setMaxNumber("");
    setMinDate("");
    setMaxDate("");
    setOptions([{ value: "", label: "" }]);
    setError(null);
  }

  function populateFromField(f: FieldDefinitionResponse) {
    setEntityType(f.entityType);
    setName(f.name);
    setSlugField(f.slug);
    setFieldType(f.fieldType);
    setDescription(f.description ?? "");
    setRequired(f.required);
    setSortOrder(f.sortOrder);
    setMinLength(f.validation?.minLength?.toString() ?? "");
    setMaxLength(f.validation?.maxLength?.toString() ?? "");
    setPattern((f.validation?.pattern as string) ?? "");
    setMinNumber(f.validation?.min?.toString() ?? "");
    setMaxNumber(f.validation?.max?.toString() ?? "");
    if (f.fieldType === "DATE") {
      setMinDate((f.validation?.min as string) ?? "");
      setMaxDate((f.validation?.max as string) ?? "");
    }
    setOptions(f.options ?? [{ value: "", label: "" }]);
    setError(null);
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen && isEditing && field) {
      populateFromField(field);
    } else if (!newOpen) {
      if (!isEditing) resetForm();
    }
    setOpen(newOpen);
  }

  function buildValidation(): Record<string, unknown> | undefined {
    if (fieldType === "TEXT") {
      const v: Record<string, unknown> = {};
      if (minLength) v.minLength = parseInt(minLength, 10);
      if (maxLength) v.maxLength = parseInt(maxLength, 10);
      if (pattern) v.pattern = pattern;
      return Object.keys(v).length > 0 ? v : undefined;
    }
    if (fieldType === "NUMBER") {
      const v: Record<string, unknown> = {};
      if (minNumber) v.min = parseFloat(minNumber);
      if (maxNumber) v.max = parseFloat(maxNumber);
      return Object.keys(v).length > 0 ? v : undefined;
    }
    if (fieldType === "DATE") {
      const v: Record<string, unknown> = {};
      if (minDate) v.min = minDate;
      if (maxDate) v.max = maxDate;
      return Object.keys(v).length > 0 ? v : undefined;
    }
    return undefined;
  }

  function buildOptions():
    | Array<{ value: string; label: string }>
    | undefined {
    if (fieldType !== "DROPDOWN") return undefined;
    const filtered = options.filter((o) => o.value.trim() !== "");
    return filtered.length > 0 ? filtered : undefined;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Name is required.");
      return;
    }

    if (fieldType === "DROPDOWN") {
      const validOptions = options.filter((o) => o.value.trim() !== "");
      if (validOptions.length === 0) {
        setError("At least one dropdown option is required.");
        return;
      }
    }

    setIsSubmitting(true);

    try {
      if (isEditing && field) {
        const result = await updateFieldDefinitionAction(slug, field.id, {
          name: name.trim(),
          slug: slugField.trim() || undefined,
          fieldType,
          description: description.trim() || undefined,
          required,
          validation: buildValidation(),
          options: buildOptions(),
          sortOrder,
        });

        if (result.success) {
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to update field definition.");
        }
      } else {
        const result = await createFieldDefinitionAction(slug, {
          entityType,
          name: name.trim(),
          slug: slugField.trim() || undefined,
          fieldType,
          description: description.trim() || undefined,
          required,
          validation: buildValidation(),
          options: buildOptions(),
          sortOrder,
        });

        if (result.success) {
          resetForm();
          setOpen(false);
        } else {
          setError(result.error ?? "Failed to create field definition.");
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function addOption() {
    setOptions([...options, { value: "", label: "" }]);
  }

  function removeOption(index: number) {
    setOptions(options.filter((_, i) => i !== index));
  }

  function updateOption(
    index: number,
    key: "value" | "label",
    val: string,
  ) {
    const updated = [...options];
    updated[index] = { ...updated[index], [key]: val };
    setOptions(updated);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? "Edit Field Definition" : "Add Field Definition"}
          </DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update the field definition settings."
              : "Create a new custom field definition."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Entity Type */}
          <div className="space-y-2">
            <Label htmlFor="fd-entity-type">Entity Type</Label>
            {isEditing ? (
              <Input
                id="fd-entity-type"
                value={
                  ENTITY_TYPES.find((t) => t.value === entityType)?.label ??
                  entityType
                }
                readOnly
                className="bg-olive-50 dark:bg-olive-900"
              />
            ) : (
              <select
                id="fd-entity-type"
                value={entityType}
                onChange={(e) => setEntityType(e.target.value as EntityType)}
                className="flex h-9 w-full rounded-md border border-olive-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-olive-400 dark:border-olive-700"
              >
                {ENTITY_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            )}
          </div>

          {/* Name */}
          <div className="space-y-2">
            <Label htmlFor="fd-name">Name</Label>
            <Input
              id="fd-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Tax Number"
              maxLength={100}
              required
            />
          </div>

          {/* Slug */}
          <div className="space-y-2">
            <Label htmlFor="fd-slug">Slug</Label>
            <Input
              id="fd-slug"
              value={slugField}
              onChange={(e) => setSlugField(e.target.value)}
              placeholder="Auto-generated from name if left blank"
              pattern="^[a-z][a-z0-9_]*$"
            />
            <p className="text-xs text-olive-500 dark:text-olive-400">
              Auto-generated from name if left blank. Must match: a-z, 0-9,
              underscores.
            </p>
          </div>

          {/* Field Type */}
          <div className="space-y-2">
            <Label htmlFor="fd-field-type">Field Type</Label>
            <select
              id="fd-field-type"
              value={fieldType}
              onChange={(e) => setFieldType(e.target.value as FieldType)}
              className="flex h-9 w-full rounded-md border border-olive-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-olive-400 dark:border-olive-700"
            >
              {FIELD_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label htmlFor="fd-description">Description</Label>
            <Textarea
              id="fd-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional description"
              rows={2}
            />
          </div>

          {/* Required */}
          <div className="flex items-center gap-2">
            <input
              id="fd-required"
              type="checkbox"
              checked={required}
              onChange={(e) => setRequired(e.target.checked)}
              className="size-4 rounded border-olive-300 text-olive-600 focus:ring-olive-500"
            />
            <Label htmlFor="fd-required" className="text-sm font-normal">
              Required field
            </Label>
          </div>

          {/* Sort Order */}
          <div className="space-y-2">
            <Label htmlFor="fd-sort-order">Sort Order</Label>
            <Input
              id="fd-sort-order"
              type="number"
              value={sortOrder}
              onChange={(e) => setSortOrder(parseInt(e.target.value, 10) || 0)}
              min={0}
            />
          </div>

          {/* Conditional: DROPDOWN options */}
          {fieldType === "DROPDOWN" && (
            <div className="space-y-2">
              <Label>Options</Label>
              <div className="space-y-2">
                {options.map((opt, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <Input
                      placeholder="Value"
                      value={opt.value}
                      onChange={(e) =>
                        updateOption(index, "value", e.target.value)
                      }
                      className="flex-1"
                    />
                    <Input
                      placeholder="Label"
                      value={opt.label}
                      onChange={(e) =>
                        updateOption(index, "label", e.target.value)
                      }
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="plain"
                      size="icon"
                      onClick={() => removeOption(index)}
                      disabled={options.length <= 1}
                    >
                      <X className="size-4" />
                    </Button>
                  </div>
                ))}
              </div>
              <Button type="button" variant="outline" size="sm" onClick={addOption}>
                Add Option
              </Button>
            </div>
          )}

          {/* Conditional: TEXT validation */}
          {fieldType === "TEXT" && (
            <div className="space-y-3 rounded-md border border-olive-200 p-3 dark:border-olive-700">
              <p className="text-sm font-medium text-olive-700 dark:text-olive-300">
                Text Validation
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label htmlFor="fd-min-length">Min Length</Label>
                  <Input
                    id="fd-min-length"
                    type="number"
                    min={0}
                    value={minLength}
                    onChange={(e) => setMinLength(e.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="fd-max-length">Max Length</Label>
                  <Input
                    id="fd-max-length"
                    type="number"
                    min={0}
                    value={maxLength}
                    onChange={(e) => setMaxLength(e.target.value)}
                  />
                </div>
              </div>
              <div className="space-y-1">
                <Label htmlFor="fd-pattern">Pattern (regex)</Label>
                <Input
                  id="fd-pattern"
                  value={pattern}
                  onChange={(e) => setPattern(e.target.value)}
                  placeholder="e.g. ^[A-Z].*"
                />
              </div>
            </div>
          )}

          {/* Conditional: NUMBER validation */}
          {fieldType === "NUMBER" && (
            <div className="space-y-3 rounded-md border border-olive-200 p-3 dark:border-olive-700">
              <p className="text-sm font-medium text-olive-700 dark:text-olive-300">
                Number Validation
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label htmlFor="fd-min-number">Min</Label>
                  <Input
                    id="fd-min-number"
                    type="number"
                    value={minNumber}
                    onChange={(e) => setMinNumber(e.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="fd-max-number">Max</Label>
                  <Input
                    id="fd-max-number"
                    type="number"
                    value={maxNumber}
                    onChange={(e) => setMaxNumber(e.target.value)}
                  />
                </div>
              </div>
            </div>
          )}

          {/* Conditional: DATE validation */}
          {fieldType === "DATE" && (
            <div className="space-y-3 rounded-md border border-olive-200 p-3 dark:border-olive-700">
              <p className="text-sm font-medium text-olive-700 dark:text-olive-300">
                Date Validation
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label htmlFor="fd-min-date">Min Date</Label>
                  <Input
                    id="fd-min-date"
                    type="date"
                    value={minDate}
                    onChange={(e) => setMinDate(e.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="fd-max-date">Max Date</Label>
                  <Input
                    id="fd-max-date"
                    type="date"
                    value={maxDate}
                    onChange={(e) => setMaxDate(e.target.value)}
                  />
                </div>
              </div>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? isEditing
                  ? "Saving..."
                  : "Creating..."
                : isEditing
                  ? "Save Changes"
                  : "Create Field"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
