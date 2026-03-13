"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  CONDITION_OPERATORS,
  type ConditionOperator,
} from "./field-definition-constants";
import type { FieldDefinitionResponse } from "@/lib/types";

interface VisibilitySectionProps {
  showConditionally: boolean;
  onShowConditionallyChange: (value: boolean) => void;
  conditionFieldSlug: string;
  onConditionFieldSlugChange: (value: string) => void;
  conditionOperator: ConditionOperator;
  onConditionOperatorChange: (value: ConditionOperator) => void;
  conditionValue: string;
  onConditionValueChange: (value: string) => void;
  onClearCondition: () => void;
  availableControllingFields: FieldDefinitionResponse[];
}

export function VisibilityConditionSection({
  showConditionally,
  onShowConditionallyChange,
  conditionFieldSlug,
  onConditionFieldSlugChange,
  conditionOperator,
  onConditionOperatorChange,
  conditionValue,
  onConditionValueChange,
  onClearCondition,
  availableControllingFields,
}: VisibilitySectionProps) {
  const controllingField = availableControllingFields.find(
    (f) => f.slug === conditionFieldSlug,
  );
  const isControllingDropdown = controllingField?.fieldType === "DROPDOWN";

  const conditionPreview = getConditionPreview();

  function getConditionPreview(): string | null {
    if (!showConditionally || !conditionFieldSlug || !conditionValue) return null;
    const ctrlField = availableControllingFields.find(
      (f) => f.slug === conditionFieldSlug,
    );
    const fieldName = ctrlField?.name ?? conditionFieldSlug;
    const operatorLabel =
      CONDITION_OPERATORS.find((o) => o.value === conditionOperator)?.label ??
      conditionOperator;

    if (conditionOperator === "in") {
      const values = conditionValue
        .split(",")
        .map((v) => v.trim())
        .filter(Boolean);
      return `Show this field when ${fieldName} ${operatorLabel} ${values.join(", ")}`;
    }

    let displayValue = conditionValue;
    if (ctrlField?.fieldType === "DROPDOWN" && ctrlField.options) {
      const opt = ctrlField.options.find((o) => o.value === conditionValue);
      if (opt) displayValue = opt.label;
    }

    return `Show this field when ${fieldName} ${operatorLabel} ${displayValue}`;
  }

  return (
    <div className="space-y-3 rounded-md border border-slate-200 p-3 dark:border-slate-700">
      <div className="flex items-center gap-2">
        <input
          id="fd-show-conditionally"
          type="checkbox"
          checked={showConditionally}
          onChange={(e) => {
            onShowConditionallyChange(e.target.checked);
            if (!e.target.checked) {
              onConditionFieldSlugChange("");
              onConditionOperatorChange("eq");
              onConditionValueChange("");
            }
          }}
          className="size-4 rounded border-slate-300 text-slate-600 focus:ring-slate-500"
        />
        <Label
          htmlFor="fd-show-conditionally"
          className="text-sm font-medium text-slate-700 dark:text-slate-300"
        >
          Show conditionally
        </Label>
      </div>

      {showConditionally && (
        <div className="space-y-3">
          {/* Controlling field selector */}
          <div className="space-y-1">
            <Label htmlFor="fd-condition-field">Show this field when</Label>
            <select
              id="fd-condition-field"
              value={conditionFieldSlug}
              onChange={(e) => {
                onConditionFieldSlugChange(e.target.value);
                onConditionValueChange("");
              }}
              className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 dark:border-slate-700"
            >
              <option value="">Select a field...</option>
              {availableControllingFields.map((f) => (
                <option key={f.id} value={f.slug}>
                  {f.name}
                </option>
              ))}
            </select>
          </div>

          {/* Operator */}
          <div className="space-y-1">
            <Label htmlFor="fd-condition-operator">Condition</Label>
            <select
              id="fd-condition-operator"
              value={conditionOperator}
              onChange={(e) => onConditionOperatorChange(e.target.value as ConditionOperator)}
              className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 dark:border-slate-700"
            >
              {CONDITION_OPERATORS.map((op) => (
                <option key={op.value} value={op.value}>
                  {op.label}
                </option>
              ))}
            </select>
          </div>

          {/* Value input */}
          <div className="space-y-1">
            <Label htmlFor="fd-condition-value">Value</Label>
            {isControllingDropdown &&
            conditionOperator !== "in" &&
            controllingField?.options ? (
              <select
                id="fd-condition-value"
                value={conditionValue}
                onChange={(e) => onConditionValueChange(e.target.value)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-400 dark:border-slate-700"
              >
                <option value="">Select a value...</option>
                {controllingField.options.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : (
              <>
                <Input
                  id="fd-condition-value"
                  value={conditionValue}
                  onChange={(e) => onConditionValueChange(e.target.value)}
                  placeholder={
                    conditionOperator === "in"
                      ? "Comma-separated values (e.g. val1, val2)"
                      : "Enter value"
                  }
                />
                {conditionOperator === "in" && conditionValue && (
                  <div className="mt-1 flex flex-wrap gap-1">
                    {conditionValue
                      .split(",")
                      .map((v) => v.trim())
                      .filter(Boolean)
                      .map((v, i) => (
                        <span
                          key={i}
                          className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                        >
                          {v}
                        </span>
                      ))}
                  </div>
                )}
              </>
            )}
          </div>

          {/* Preview */}
          {conditionPreview && (
            <p className="text-sm text-slate-600 dark:text-slate-400">
              {conditionPreview}
            </p>
          )}

          {/* Clear button */}
          <Button type="button" variant="outline" size="sm" onClick={onClearCondition}>
            Clear condition
          </Button>
        </div>
      )}
    </div>
  );
}
