"use client";

import { useEffect, useState, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Save, Loader2, ListChecks } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { updateEntityCustomFieldsAction } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";
import type {
  EntityType,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";
import { formatDate, formatCurrency } from "@/lib/format";

interface CustomFieldSectionProps {
  entityType: EntityType;
  entityId: string;
  customFields: Record<string, unknown>;
  appliedFieldGroups: string[];
  editable: boolean;
  slug: string;
  fieldDefinitions: FieldDefinitionResponse[];
  fieldGroups: FieldGroupResponse[];
  groupMembers: Record<string, FieldGroupMemberResponse[]>;
}

interface FieldWithGroup {
  definition: FieldDefinitionResponse;
  groupId: string;
  sortInGroup: number;
}

interface ValidationError {
  field: string;
  message: string;
}

function buildFieldsByGroup(
  fieldDefinitions: FieldDefinitionResponse[],
  fieldGroups: FieldGroupResponse[],
  groupMembers: Record<string, FieldGroupMemberResponse[]>,
  appliedFieldGroups: string[],
): Map<string, FieldWithGroup[]> {
  const appliedGroups = new Set(appliedFieldGroups);
  const fieldMap = new Map(fieldDefinitions.map((f) => [f.id, f]));
  const result = new Map<string, FieldWithGroup[]>();

  // Sort groups by sortOrder
  const sortedGroups = [...fieldGroups]
    .filter((g) => appliedGroups.has(g.id) && g.active)
    .sort((a, b) => a.sortOrder - b.sortOrder);

  for (const group of sortedGroups) {
    const members = groupMembers[group.id] ?? [];
    const fieldsInGroup: FieldWithGroup[] = [];

    for (const member of members) {
      const def = fieldMap.get(member.fieldDefinitionId);
      if (def && def.active) {
        fieldsInGroup.push({
          definition: def,
          groupId: group.id,
          sortInGroup: member.sortOrder,
        });
      }
    }

    fieldsInGroup.sort((a, b) => a.sortInGroup - b.sortInGroup);

    if (fieldsInGroup.length > 0) {
      result.set(group.id, fieldsInGroup);
    }
  }

  return result;
}

function validateField(
  field: FieldDefinitionResponse,
  value: unknown,
): string | null {
  const strVal = typeof value === "string" ? value : "";
  const validation = field.validation ?? {};

  if (field.required) {
    if (field.fieldType === "BOOLEAN") {
      // Booleans are always valid (true or false)
    } else if (field.fieldType === "CURRENCY") {
      const obj = value as { amount?: unknown; currency?: string } | null;
      if (!obj?.amount && obj?.amount !== 0) {
        return `${field.name} is required`;
      }
    } else if (!strVal.trim()) {
      return `${field.name} is required`;
    }
  }

  if (!strVal && field.fieldType !== "BOOLEAN" && field.fieldType !== "CURRENCY") {
    return null; // Not required and empty, skip validation
  }

  switch (field.fieldType) {
    case "TEXT": {
      const minLength = validation.minLength as number | undefined;
      const maxLength = validation.maxLength as number | undefined;
      const pattern = validation.pattern as string | undefined;
      if (minLength != null && strVal.length < minLength) {
        return `Must be at least ${minLength} characters`;
      }
      if (maxLength != null && strVal.length > maxLength) {
        return `Must be at most ${maxLength} characters`;
      }
      if (pattern) {
        try {
          if (!new RegExp(pattern).test(strVal)) {
            return `Does not match required pattern`;
          }
        } catch {
          // Invalid regex in field definition — skip
        }
      }
      break;
    }
    case "NUMBER": {
      const num = parseFloat(strVal);
      if (strVal && isNaN(num)) {
        return `Must be a valid number`;
      }
      const min = validation.min as number | undefined;
      const max = validation.max as number | undefined;
      if (min != null && num < min) {
        return `Must be at least ${min}`;
      }
      if (max != null && num > max) {
        return `Must be at most ${max}`;
      }
      break;
    }
    case "EMAIL": {
      if (strVal && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(strVal)) {
        return `Must be a valid email address`;
      }
      break;
    }
    case "URL": {
      if (strVal && !/^https?:\/\/.+/.test(strVal)) {
        return `Must be a valid URL (starts with http:// or https://)`;
      }
      break;
    }
    case "DROPDOWN": {
      if (strVal && field.options) {
        const validValues = field.options.map((o) => o.value);
        if (!validValues.includes(strVal)) {
          return `Must be one of the available options`;
        }
      }
      break;
    }
  }

  return null;
}

function isFieldVisible(
  field: FieldDefinitionResponse,
  currentValues: Record<string, unknown>,
): boolean {
  const condition = field.visibilityCondition;
  if (!condition) return true;

  const actualValue = currentValues[condition.dependsOnSlug];
  if (actualValue === null || actualValue === undefined) return false;

  switch (condition.operator) {
    case "eq":
      return String(actualValue) === String(condition.value);
    case "neq":
      return String(actualValue) !== String(condition.value);
    case "in":
      return (
        Array.isArray(condition.value) &&
        condition.value.some((v) => String(actualValue) === String(v))
      );
    default:
      return true;
  }
}

function formatDisplayValue(
  field: FieldDefinitionResponse,
  value: unknown,
): string {
  if (value == null || value === "") return "\u2014";

  switch (field.fieldType) {
    case "BOOLEAN":
      return value === true ? "Yes" : "No";
    case "DATE":
      return formatDate(String(value));
    case "DROPDOWN": {
      const opt = field.options?.find((o) => o.value === value);
      return opt?.label ?? String(value);
    }
    case "CURRENCY": {
      const obj = value as { amount?: number; currency?: string };
      if (obj?.amount != null && obj?.currency) {
        return formatCurrency(obj.amount, obj.currency);
      }
      return "\u2014";
    }
    case "NUMBER":
      return new Intl.NumberFormat("en-US").format(Number(value));
    default:
      return String(value);
  }
}

const COMMON_CURRENCIES = ["USD", "EUR", "GBP", "ZAR", "AUD", "CAD", "CHF", "JPY", "CNY", "INR"];

function FieldInput({
  field,
  value,
  onChange,
  error,
  disabled,
}: {
  field: FieldDefinitionResponse;
  value: unknown;
  onChange: (value: unknown) => void;
  error?: string;
  disabled: boolean;
}) {
  const id = `cf-${field.slug}`;

  switch (field.fieldType) {
    case "TEXT":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder={field.description ?? undefined}
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "NUMBER":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="number"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            min={field.validation?.min as number | undefined}
            max={field.validation?.max as number | undefined}
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "DATE":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="date"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "DROPDOWN":
      return (
        <div className="space-y-1">
          <select
            id={id}
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-700"
          >
            <option value="">Select...</option>
            {field.options?.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "BOOLEAN":
      return (
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <input
              id={id}
              type="checkbox"
              checked={(value as boolean) ?? false}
              onChange={(e) => onChange(e.target.checked)}
              disabled={disabled}
              className="size-4 rounded border-slate-300 text-slate-600 focus:ring-slate-500"
            />
            <Label htmlFor={id} className="text-sm font-normal">
              {field.description ?? "Enabled"}
            </Label>
          </div>
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "CURRENCY": {
      const currencyObj = (value as { amount?: number | string; currency?: string }) ?? {};
      return (
        <div className="space-y-1">
          <div className="flex gap-2">
            <Input
              id={id}
              type="number"
              step="0.01"
              placeholder="Amount"
              value={currencyObj.amount ?? ""}
              onChange={(e) =>
                onChange({ ...currencyObj, amount: e.target.value ? parseFloat(e.target.value) : "" })
              }
              disabled={disabled}
              aria-invalid={!!error}
              className="flex-1"
            />
            <select
              value={currencyObj.currency ?? ""}
              onChange={(e) => onChange({ ...currencyObj, currency: e.target.value })}
              disabled={disabled}
              className="flex h-9 w-28 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-700"
              aria-label="Currency"
            >
              <option value="">Currency</option>
              {COMMON_CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );
    }

    case "URL":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="url"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="https://..."
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "EMAIL":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="email"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="name@example.com"
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    case "PHONE":
      return (
        <div className="space-y-1">
          <Input
            id={id}
            type="tel"
            value={(value as string) ?? ""}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
            aria-invalid={!!error}
            placeholder="+1 (555) 000-0000"
          />
          {error && <p className="text-xs text-red-600 dark:text-red-400">{error}</p>}
        </div>
      );

    default:
      return null;
  }
}

export function CustomFieldSection({
  entityType,
  entityId,
  customFields: initialCustomFields,
  appliedFieldGroups,
  editable,
  slug,
  fieldDefinitions,
  fieldGroups,
  groupMembers,
}: CustomFieldSectionProps) {
  const [values, setValues] = useState<Record<string, unknown>>(initialCustomFields ?? {});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Reset values when initial data changes (e.g., after revalidation)
  useEffect(() => {
    setValues(initialCustomFields ?? {});
  }, [initialCustomFields]);

  const fieldsByGroup = buildFieldsByGroup(
    fieldDefinitions,
    fieldGroups,
    groupMembers,
    appliedFieldGroups,
  );

  const groupMap = new Map(fieldGroups.map((g) => [g.id, g]));

  const handleFieldChange = useCallback((slug: string, value: unknown) => {
    setValues((prev) => ({ ...prev, [slug]: value }));
    setErrors((prev) => {
      const next = { ...prev };
      delete next[slug];
      return next;
    });
    setSaveSuccess(false);
  }, []);

  const handleSave = async () => {
    // Validate all fields
    const newErrors: Record<string, string> = {};
    for (const [, fields] of fieldsByGroup) {
      for (const { definition } of fields) {
        // Skip validation for hidden fields
        if (!isFieldVisible(definition, values)) continue;
        const error = validateField(definition, values[definition.slug]);
        if (error) {
          newErrors[definition.slug] = error;
        }
      }
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    setIsSubmitting(true);
    setSaveError(null);
    setSaveSuccess(false);

    try {
      const result = await updateEntityCustomFieldsAction(
        slug,
        entityType,
        entityId,
        values,
      );

      if (result.success) {
        setSaveSuccess(true);
        setTimeout(() => setSaveSuccess(false), 3000);
      } else {
        setSaveError(result.error ?? "Failed to save custom fields.");
      }
    } catch {
      setSaveError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  };

  // If no groups are applied or no fields in applied groups, show empty state
  if (fieldsByGroup.size === 0) {
    return (
      <EmptyState
        icon={ListChecks}
        title="No custom fields configured"
        description="Custom fields let you track additional information specific to your workflow."
        actionLabel="Configure Fields"
        actionHref={`/org/${slug}/settings/custom-fields`}
      />
    );
  }

  return (
    <div className="space-y-4" data-testid="custom-field-section">
      {Array.from(fieldsByGroup.entries()).map(([groupId, fields]) => {
        const group = groupMap.get(groupId);
        if (!group) return null;

        return (
          <Card key={groupId} className="py-4">
            <CardHeader className="pb-0">
              <CardTitle className="text-sm font-medium text-slate-700 dark:text-slate-300">
                {group.name}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 sm:grid-cols-2">
                {fields
                  .filter(({ definition }) => isFieldVisible(definition, values))
                  .map(({ definition }) => (
                  <div key={definition.slug} className="space-y-1.5">
                    <Label htmlFor={`cf-${definition.slug}`} className="text-sm">
                      {definition.name}
                      {definition.required && (
                        <span className="ml-0.5 text-red-500" aria-label="required">
                          *
                        </span>
                      )}
                    </Label>
                    {editable ? (
                      <FieldInput
                        field={definition}
                        value={values[definition.slug]}
                        onChange={(v) => handleFieldChange(definition.slug, v)}
                        error={errors[definition.slug]}
                        disabled={isSubmitting}
                      />
                    ) : (
                      <p className="text-sm text-slate-700 dark:text-slate-300">
                        {formatDisplayValue(definition, values[definition.slug])}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        );
      })}

      {editable && (
        <div className="flex items-center gap-3">
          <Button
            type="button"
            size="sm"
            onClick={handleSave}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Saving...
              </>
            ) : (
              <>
                <Save className="mr-1.5 size-4" />
                Save Custom Fields
              </>
            )}
          </Button>
          {saveSuccess && (
            <span className="text-sm text-green-600 dark:text-green-400">
              Saved successfully
            </span>
          )}
          {saveError && (
            <span className="text-sm text-red-600 dark:text-red-400">{saveError}</span>
          )}
        </div>
      )}
    </div>
  );
}
