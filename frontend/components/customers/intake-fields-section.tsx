"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Label } from "@/components/ui/label";
import {
  InlineFieldEditor,
  type FieldValue,
} from "@/components/prerequisite/inline-field-editor";
import type {
  IntakeField,
  IntakeFieldGroup,
} from "@/components/prerequisite/types";

/**
 * Evaluates whether a field should be visible based on its visibility condition.
 * Fields without a condition are always visible.
 */
function isFieldVisible(
  field: IntakeField,
  currentValues: Record<string, FieldValue>,
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

interface IntakeFieldsSectionProps {
  groups: IntakeFieldGroup[];
  values: Record<string, FieldValue>;
  onChange: (slug: string, value: FieldValue) => void;
}

function GroupSection({
  group,
  values,
  onChange,
}: {
  group: IntakeFieldGroup;
  values: Record<string, FieldValue>;
  onChange: (slug: string, value: FieldValue) => void;
}) {
  const visibleFields = group.fields.filter((f) => isFieldVisible(f, values));
  const requiredFields = visibleFields.filter((f) => f.required);
  const optionalFields = visibleFields.filter((f) => !f.required);

  const hasRequiredFields = requiredFields.length > 0;
  const [isOpen, setIsOpen] = useState(hasRequiredFields);
  const [showOptional, setShowOptional] = useState(false);

  if (visibleFields.length === 0) return null;

  return (
    <div className="border border-slate-200 rounded-lg">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium text-slate-900 hover:bg-slate-50"
        onClick={() => setIsOpen(!isOpen)}
      >
        {isOpen ? (
          <ChevronDown className="h-4 w-4 text-slate-500" />
        ) : (
          <ChevronRight className="h-4 w-4 text-slate-500" />
        )}
        {group.name}
      </button>

      {isOpen && (
        <div className="space-y-4 px-4 pb-4">
          {requiredFields.map((field) => (
            <div key={field.id} className="space-y-1.5">
              <Label htmlFor={`inline-${field.slug}`} className="text-sm">
                {field.name}
                <span className="text-red-500 ml-0.5">*</span>
              </Label>
              {field.description && (
                <p className="text-xs text-slate-500">{field.description}</p>
              )}
              <InlineFieldEditor
                fieldDefinition={field}
                value={values[field.slug] ?? null}
                onChange={(val) => onChange(field.slug, val)}
              />
            </div>
          ))}

          {optionalFields.length > 0 && (
            <div>
              <button
                type="button"
                className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700"
                onClick={() => setShowOptional(!showOptional)}
              >
                {showOptional ? (
                  <ChevronDown className="h-3 w-3" />
                ) : (
                  <ChevronRight className="h-3 w-3" />
                )}
                Additional Information ({optionalFields.length})
              </button>

              {showOptional && (
                <div className="mt-3 space-y-4">
                  {optionalFields.map((field) => (
                    <div key={field.id} className="space-y-1.5">
                      <Label
                        htmlFor={`inline-${field.slug}`}
                        className="text-sm"
                      >
                        {field.name}
                      </Label>
                      {field.description && (
                        <p className="text-xs text-slate-500">
                          {field.description}
                        </p>
                      )}
                      <InlineFieldEditor
                        fieldDefinition={field}
                        value={values[field.slug] ?? null}
                        onChange={(val) => onChange(field.slug, val)}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function IntakeFieldsSection({
  groups,
  values,
  onChange,
}: IntakeFieldsSectionProps) {
  if (groups.length === 0) return null;

  return (
    <div className="space-y-3">
      {groups.map((group) => (
        <GroupSection
          key={group.id}
          group={group}
          values={values}
          onChange={onChange}
        />
      ))}
    </div>
  );
}
