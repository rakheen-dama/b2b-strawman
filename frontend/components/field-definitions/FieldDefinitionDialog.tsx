"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { X } from "lucide-react";
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
import { Checkbox } from "@/components/ui/checkbox";
import { ENTITY_TYPES, FIELD_TYPES } from "./field-definition-constants";
import { useFieldDefinitionForm } from "./use-field-definition-form";
import {
  TextValidationSection,
  NumberValidationSection,
  DateValidationSection,
} from "./field-definition-validation-sections";
import { VisibilityConditionSection } from "./field-definition-visibility-section";
import { FieldUsageSection } from "./field-definition-usage-section";
import type { EntityType, FieldDefinitionResponse } from "@/lib/types";
import {
  type PrerequisiteContext,
  PREREQUISITE_CONTEXT_LABELS,
} from "@/components/prerequisite/types";

interface FieldDefinitionDialogProps {
  slug: string;
  entityType?: EntityType;
  field?: FieldDefinitionResponse;
  allFieldsForType?: FieldDefinitionResponse[];
  children: React.ReactNode;
}

export function FieldDefinitionDialog({
  slug,
  entityType: initialEntityType,
  field,
  allFieldsForType,
  children,
}: FieldDefinitionDialogProps) {
  const [open, setOpen] = useState(false);

  const form = useFieldDefinitionForm({
    slug,
    field,
    initialEntityType,
    open,
  });

  const availableControllingFields = (allFieldsForType ?? []).filter(
    (f) => f.active && f.id !== field?.id
  );

  function handleOpenChange(newOpen: boolean) {
    if (newOpen && form.isEditing && field) {
      form.populateFromField(field);
    } else if (!newOpen) {
      if (!form.isEditing) form.resetForm();
    }
    setOpen(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {form.isEditing ? "Edit Field Definition" : "Add Field Definition"}
          </DialogTitle>
          <DialogDescription>
            {form.isEditing
              ? "Update the field definition settings."
              : "Create a new custom field definition."}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={(e) => form.handleSubmit(e, () => setOpen(false))} className="space-y-4">
          {/* Entity Type */}
          <div className="space-y-2">
            <Label htmlFor="fd-entity-type">Entity Type</Label>
            {form.isEditing ? (
              <Input
                id="fd-entity-type"
                value={
                  ENTITY_TYPES.find((t) => t.value === form.entityType)?.label ?? form.entityType
                }
                readOnly
                className="bg-slate-50 dark:bg-slate-900"
              />
            ) : (
              <select
                id="fd-entity-type"
                value={form.entityType}
                onChange={(e) => form.setEntityType(e.target.value as EntityType)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-400 focus-visible:outline-none dark:border-slate-700"
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
              value={form.name}
              onChange={(e) => form.setName(e.target.value)}
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
              value={form.slugField}
              onChange={(e) => form.setSlugField(e.target.value)}
              placeholder="Auto-generated from name if left blank"
              pattern="^[a-z][a-z0-9_]*$"
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Auto-generated from name if left blank. Must match: a-z, 0-9, underscores.
            </p>
          </div>

          {/* Field Type */}
          <div className="space-y-2">
            <Label htmlFor="fd-field-type">Field Type</Label>
            <select
              id="fd-field-type"
              value={form.fieldType}
              onChange={(e) => form.setFieldType(e.target.value as import("@/lib/types").FieldType)}
              className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-400 focus-visible:outline-none dark:border-slate-700"
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
              value={form.description}
              onChange={(e) => form.setDescription(e.target.value)}
              placeholder="Optional description"
              rows={2}
            />
          </div>

          {/* Required */}
          <div className="flex items-center gap-2">
            <input
              id="fd-required"
              type="checkbox"
              checked={form.required}
              onChange={(e) => form.setRequired(e.target.checked)}
              className="size-4 rounded border-slate-300 text-slate-600 focus:ring-slate-500"
            />
            <Label htmlFor="fd-required" className="text-sm font-normal">
              Required field
            </Label>
          </div>

          {/* Required For Contexts */}
          <div className="space-y-3 rounded-md border border-slate-200 p-3 dark:border-slate-700">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">Required For</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Select which actions require this field to be filled.
            </p>
            <div className="space-y-2">
              {(Object.entries(PREREQUISITE_CONTEXT_LABELS) as [PrerequisiteContext, string][]).map(
                ([ctx, label]) => (
                  <div key={ctx} className="flex items-center gap-2">
                    <Checkbox
                      id={`fd-ctx-${ctx}`}
                      checked={form.requiredForContexts.includes(ctx)}
                      onCheckedChange={() => form.toggleContext(ctx)}
                    />
                    <Label htmlFor={`fd-ctx-${ctx}`} className="text-sm font-normal">
                      {label}
                    </Label>
                  </div>
                )
              )}
            </div>
            {form.isEditing && field?.packId && (
              <p className="text-xs text-slate-500 italic dark:text-slate-400">
                Set by field pack — override by changing selections.
              </p>
            )}
          </div>

          {/* Sort Order */}
          <div className="space-y-2">
            <Label htmlFor="fd-sort-order">Sort Order</Label>
            <Input
              id="fd-sort-order"
              type="number"
              value={form.sortOrder}
              onChange={(e) => form.setSortOrder(parseInt(e.target.value, 10) || 0)}
              min={0}
            />
          </div>

          {/* Conditional: DROPDOWN options */}
          {form.fieldType === "DROPDOWN" && (
            <div className="space-y-2">
              <Label>Options</Label>
              <div className="space-y-2">
                {form.options.map((opt, index) => (
                  <div key={index} className="flex items-center gap-2">
                    <Input
                      placeholder="Value"
                      value={opt.value}
                      onChange={(e) => form.updateOption(index, "value", e.target.value)}
                      className="flex-1"
                    />
                    <Input
                      placeholder="Label"
                      value={opt.label}
                      onChange={(e) => form.updateOption(index, "label", e.target.value)}
                      className="flex-1"
                    />
                    <Button
                      type="button"
                      variant="plain"
                      size="icon"
                      onClick={() => form.removeOption(index)}
                      disabled={form.options.length <= 1}
                    >
                      <X className="size-4" />
                    </Button>
                  </div>
                ))}
              </div>
              <Button type="button" variant="outline" size="sm" onClick={form.addOption}>
                Add Option
              </Button>
            </div>
          )}

          {/* Visibility Condition */}
          <VisibilityConditionSection
            showConditionally={form.showConditionally}
            onShowConditionallyChange={form.setShowConditionally}
            conditionFieldSlug={form.conditionFieldSlug}
            onConditionFieldSlugChange={form.setConditionFieldSlug}
            conditionOperator={form.conditionOperator}
            onConditionOperatorChange={form.setConditionOperator}
            conditionValue={form.conditionValue}
            onConditionValueChange={form.setConditionValue}
            onClearCondition={form.clearCondition}
            availableControllingFields={availableControllingFields}
          />

          {/* Conditional: TEXT validation */}
          {form.fieldType === "TEXT" && (
            <TextValidationSection
              minLength={form.minLength}
              onMinLengthChange={form.setMinLength}
              maxLength={form.maxLength}
              onMaxLengthChange={form.setMaxLength}
              pattern={form.pattern}
              onPatternChange={form.setPattern}
            />
          )}

          {/* Conditional: NUMBER validation */}
          {form.fieldType === "NUMBER" && (
            <NumberValidationSection
              min={form.minNumber}
              onMinChange={form.setMinNumber}
              max={form.maxNumber}
              onMaxChange={form.setMaxNumber}
            />
          )}

          {/* Conditional: DATE validation */}
          {form.fieldType === "DATE" && (
            <DateValidationSection
              minDate={form.minDate}
              onMinDateChange={form.setMinDate}
              maxDate={form.maxDate}
              onMaxDateChange={form.setMaxDate}
            />
          )}

          {/* Used In section (only for editing) */}
          {form.isEditing && form.fieldUsage && (
            <FieldUsageSection
              fieldUsage={form.fieldUsage}
              usageOpen={form.usageOpen}
              onUsageOpenChange={form.setUsageOpen}
              totalUsageCount={form.totalUsageCount}
            />
          )}

          {form.error && <p className="text-destructive text-sm">{form.error}</p>}

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={form.isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={form.isSubmitting}>
              {form.isSubmitting
                ? form.isEditing
                  ? "Saving..."
                  : "Creating..."
                : form.isEditing
                  ? "Save Changes"
                  : "Create Field"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
