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
export function isFieldVisible(
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

interface GroupSectionProps {
  group: IntakeFieldGroup;
  values: Record<string, FieldValue>;
  onChange: (slug: string, value: FieldValue) => void;
}

function GroupSection({ group, values, onChange }: GroupSectionProps) {
  const visibleFields = group.fields.filter((f) => isFieldVisible(f, values));
  const requiredFields = visibleFields.filter((f) => f.required);
  const optionalFields = visibleFields.filter((f) => !f.required);

  const hasRequiredFields = requiredFields.length > 0;
  // Derive initial open state: open if any required fields are visible.
  // The key-based remount (see below) ensures this resets when required field
  // visibility changes from 0 to non-zero.
  const [isOpen, setIsOpen] = useState(hasRequiredFields);
  const [showOptional, setShowOptional] = useState(false);

  if (visibleFields.length === 0) return null;

  return (
    <div className="border border-slate-200 rounded-lg shadow-sm">
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
      {groups.map((group) => {
        // Include whether the group has visible required fields in the key so
        // that the component remounts (and re-derives isOpen) when a required
        // field becomes visible due to a visibility condition change.
        const hasVisibleRequired = group.fields.some(
          (f) => f.required && isFieldVisible(f, values),
        );
        return (
          <GroupSection
            key={`${group.id}-${hasVisibleRequired}`}
            group={group}
            values={values}
            onChange={onChange}
          />
        );
      })}
    </div>
  );
}
