"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { FieldDefinitionResponse } from "@/lib/types";

interface CustomFieldFilterValue {
  [slug: string]: {
    op: string;
    value: unknown;
  };
}

interface CustomFieldFilterProps {
  value: CustomFieldFilterValue;
  onChange: (value: CustomFieldFilterValue) => void;
  fieldDefinitions: FieldDefinitionResponse[];
}

export function CustomFieldFilter({
  value,
  onChange,
  fieldDefinitions,
}: CustomFieldFilterProps) {
  if (fieldDefinitions.length === 0) return null;

  function updateField(slug: string, op: string, fieldValue: unknown) {
    if (
      fieldValue === "" ||
      fieldValue === null ||
      fieldValue === undefined
    ) {
      // Remove field filter when value is cleared
      const next = { ...value };
      delete next[slug];
      onChange(next);
    } else {
      onChange({ ...value, [slug]: { op, value: fieldValue } });
    }
  }

  return (
    <div className="space-y-3">
      <Label className="text-sm font-medium text-olive-700 dark:text-olive-300">
        Custom Fields
      </Label>
      <div className="space-y-3">
        {fieldDefinitions.map((fd) => (
          <div key={fd.id} className="space-y-1">
            <label className="text-xs text-olive-600 dark:text-olive-400">
              {fd.name}
            </label>
            {renderFieldInput(fd, value[fd.slug], (op, val) =>
              updateField(fd.slug, op, val),
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function renderFieldInput(
  fd: FieldDefinitionResponse,
  current: { op: string; value: unknown } | undefined,
  update: (op: string, value: unknown) => void,
) {
  switch (fd.fieldType) {
    case "TEXT":
    case "URL":
    case "EMAIL":
    case "PHONE":
      return (
        <Input
          type="text"
          value={(current?.value as string) ?? ""}
          onChange={(e) => update("contains", e.target.value)}
          placeholder={`Contains...`}
          className="h-8 text-sm"
        />
      );

    case "NUMBER":
    case "CURRENCY":
      return (
        <div className="flex gap-2">
          <Input
            type="number"
            value={
              current?.op === "gte" || current?.op === "gt"
                ? String(current.value ?? "")
                : ""
            }
            onChange={(e) =>
              update("gte", e.target.value ? Number(e.target.value) : "")
            }
            placeholder="Min"
            className="h-8 text-sm"
          />
          <Input
            type="number"
            value={
              current?.op === "lte" || current?.op === "lt"
                ? String(current.value ?? "")
                : ""
            }
            onChange={(e) =>
              update("lte", e.target.value ? Number(e.target.value) : "")
            }
            placeholder="Max"
            className="h-8 text-sm"
          />
        </div>
      );

    case "DATE":
      return (
        <div className="flex gap-2">
          <Input
            type="date"
            value={
              current?.op === "gte"
                ? String(current.value ?? "")
                : ""
            }
            onChange={(e) => update("gte", e.target.value || "")}
            className="h-8 text-sm"
          />
          <Input
            type="date"
            value={
              current?.op === "lte"
                ? String(current.value ?? "")
                : ""
            }
            onChange={(e) => update("lte", e.target.value || "")}
            className="h-8 text-sm"
          />
        </div>
      );

    case "BOOLEAN":
      return (
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={(current?.value as boolean) ?? false}
            onChange={(e) => update("eq", e.target.checked)}
            className="rounded border-olive-300"
          />
          <span className="text-sm text-olive-600 dark:text-olive-400">
            Yes
          </span>
        </label>
      );

    case "DROPDOWN":
      if (!fd.options || fd.options.length === 0) return null;
      return (
        <select
          multiple
          value={
            Array.isArray(current?.value)
              ? (current.value as string[])
              : []
          }
          onChange={(e) => {
            const selected = Array.from(
              e.target.selectedOptions,
              (opt) => opt.value,
            );
            update("in", selected.length > 0 ? selected : "");
          }}
          className="h-auto w-full rounded-md border border-olive-200 bg-transparent px-2 py-1 text-sm dark:border-olive-700"
        >
          {fd.options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      );

    default:
      return null;
  }
}
