"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { FieldDefinitionResponse } from "@/lib/types";

export interface CustomFieldFilterValue {
  [slug: string]: {
    op: string;
    value: unknown;
  };
}

/** Field types that use range (min/max) inputs with gte/lte operators. */
const RANGE_FIELD_TYPES = new Set(["NUMBER", "CURRENCY", "DATE"]);

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

  function updateField(
    slug: string,
    op: string,
    fieldValue: unknown,
    fieldType: string,
  ) {
    const isRange = RANGE_FIELD_TYPES.has(fieldType);
    const isEmpty =
      fieldValue === "" || fieldValue === null || fieldValue === undefined;

    if (isRange) {
      // For range types, merge both bounds into { op: "range", value: { gte?: X, lte?: Y } }
      const existing = value[slug];
      const rangeValue: Record<string, unknown> =
        existing?.op === "range" && typeof existing.value === "object" && existing.value !== null
          ? { ...(existing.value as Record<string, unknown>) }
          : {};

      if (isEmpty) {
        delete rangeValue[op];
      } else {
        rangeValue[op] = fieldValue;
      }

      if (Object.keys(rangeValue).length === 0) {
        const next = { ...value };
        delete next[slug];
        onChange(next);
      } else {
        onChange({ ...value, [slug]: { op: "range", value: rangeValue } });
      }
    } else {
      // Non-range types: single op/value pair
      if (isEmpty) {
        const next = { ...value };
        delete next[slug];
        onChange(next);
      } else {
        onChange({ ...value, [slug]: { op, value: fieldValue } });
      }
    }
  }

  return (
    <div className="space-y-3">
      <Label className="text-sm font-medium text-slate-700 dark:text-slate-300">
        Custom Fields
      </Label>
      <div className="space-y-3">
        {fieldDefinitions.map((fd) => (
          <div key={fd.id} className="space-y-1">
            <label className="text-xs text-slate-600 dark:text-slate-400">
              {fd.name}
            </label>
            {renderFieldInput(fd, value[fd.slug], (op, val) =>
              updateField(fd.slug, op, val, fd.fieldType),
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/** Extract a bound (gte/lte) from a range entry or a legacy single-op entry. */
function getRangeBound(
  current: { op: string; value: unknown } | undefined,
  bound: "gte" | "lte",
): string {
  if (!current) return "";
  if (current.op === "range" && typeof current.value === "object" && current.value !== null) {
    const v = (current.value as Record<string, unknown>)[bound];
    return v != null ? String(v) : "";
  }
  // Legacy single-op format fallback
  if (current.op === bound) return String(current.value ?? "");
  return "";
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
            value={getRangeBound(current, "gte")}
            onChange={(e) =>
              update("gte", e.target.value ? Number(e.target.value) : "")
            }
            placeholder="Min"
            className="h-8 text-sm"
          />
          <Input
            type="number"
            value={getRangeBound(current, "lte")}
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
            value={getRangeBound(current, "gte")}
            onChange={(e) => update("gte", e.target.value || "")}
            className="h-8 text-sm"
          />
          <Input
            type="date"
            value={getRangeBound(current, "lte")}
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
            className="rounded border-slate-300"
          />
          <span className="text-sm text-slate-600 dark:text-slate-400">
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
          className="h-auto w-full rounded-md border border-slate-200 bg-transparent px-2 py-1 text-sm dark:border-slate-700"
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
