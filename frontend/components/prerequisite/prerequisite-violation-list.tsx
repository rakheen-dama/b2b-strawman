"use client";

import { AlertTriangle, Info } from "lucide-react";
import {
  InlineFieldEditor,
  type InlineFieldEditorField,
  type FieldValue,
} from "@/components/prerequisite/inline-field-editor";
import type { PrerequisiteViolation } from "@/components/prerequisite/types";

interface PrerequisiteViolationListProps {
  violations: PrerequisiteViolation[];
  /** Field definitions keyed by slug, used for rendering inline editors */
  fieldDefinitions?: Record<string, InlineFieldEditorField>;
  /** Current field values keyed by slug */
  fieldValues?: Record<string, FieldValue>;
  /** Callback when a field value changes */
  onFieldChange?: (slug: string, value: FieldValue) => void;
}

/** Groups violations by entityType for display */
function groupByEntityType(violations: PrerequisiteViolation[]) {
  const groups: Record<string, PrerequisiteViolation[]> = {};
  for (const v of violations) {
    const key = v.entityType;
    if (!groups[key]) groups[key] = [];
    groups[key].push(v);
  }
  return groups;
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  CUSTOMER: "Customer",
  PROJECT: "Project",
  INVOICE: "Invoice",
  TASK: "Task",
};

export function PrerequisiteViolationList({
  violations,
  fieldDefinitions = {},
  fieldValues = {},
  onFieldChange,
}: PrerequisiteViolationListProps) {
  if (violations.length === 0) return null;

  const grouped = groupByEntityType(violations);

  return (
    <div className="space-y-4">
      {Object.entries(grouped).map(([entityType, items]) => (
        <div key={entityType} className="space-y-2">
          <h4 className="text-sm font-medium text-slate-700 dark:text-slate-300">
            {ENTITY_TYPE_LABELS[entityType] ?? entityType}
          </h4>
          <ul className="space-y-2">
            {items.map((violation, idx) => {
              const slug = violation.fieldSlug;
              const fieldDef = slug ? fieldDefinitions[slug] : undefined;

              return (
              <li
                key={`${violation.code}-${slug ?? idx}`}
                className="rounded-md border border-slate-200 p-3 dark:border-slate-700"
              >
                {slug && fieldDef ? (
                  <div className="space-y-2">
                    <div className="flex items-start gap-2">
                      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-500" />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                          {fieldDef.name}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {violation.message}
                        </p>
                      </div>
                    </div>
                    <InlineFieldEditor
                      fieldDefinition={fieldDef}
                      value={fieldValues[slug]}
                      onChange={(val) =>
                        onFieldChange?.(slug, val)
                      }
                    />
                  </div>
                ) : (
                  <div className="flex items-start gap-2">
                    <Info className="mt-0.5 size-4 shrink-0 text-slate-400" />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm text-slate-900 dark:text-slate-100">
                        {violation.message}
                      </p>
                      {violation.resolution && (
                        <p className="mt-1 text-xs text-muted-foreground">
                          {violation.resolution}
                        </p>
                      )}
                    </div>
                  </div>
                )}
              </li>
              );
            })}
          </ul>
        </div>
      ))}
    </div>
  );
}
