"use client";

import { useState, useEffect } from "react";
import {
  createFieldDefinitionAction,
  updateFieldDefinitionAction,
  fetchFieldUsageAction,
  type FieldUsageInfo,
} from "@/app/(app)/org/[slug]/settings/custom-fields/actions";
import type { EntityType, FieldType, FieldDefinitionResponse } from "@/lib/types";
import type { PrerequisiteContext } from "@/components/prerequisite/types";
import type { ConditionOperator } from "./field-definition-constants";

interface UseFieldDefinitionFormOptions {
  slug: string;
  field?: FieldDefinitionResponse;
  initialEntityType?: EntityType;
  open: boolean;
}

export function useFieldDefinitionForm({
  slug,
  field,
  initialEntityType,
  open,
}: UseFieldDefinitionFormOptions) {
  const isEditing = !!field;
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [entityType, setEntityType] = useState<EntityType>(
    field?.entityType ?? initialEntityType ?? "PROJECT"
  );
  const [name, setName] = useState(field?.name ?? "");
  const [slugField, setSlugField] = useState(field?.slug ?? "");
  const [fieldType, setFieldType] = useState<FieldType>(field?.fieldType ?? "TEXT");
  const [description, setDescription] = useState(field?.description ?? "");
  const [required, setRequired] = useState(field?.required ?? false);
  const [requiredForContexts, setRequiredForContexts] = useState<PrerequisiteContext[]>(
    (field?.requiredForContexts as PrerequisiteContext[]) ?? []
  );
  const [sortOrder, setSortOrder] = useState(field?.sortOrder ?? 0);
  const [portalVisibleDeadline, setPortalVisibleDeadline] = useState(
    field?.portalVisibleDeadline ?? false
  );

  // Conditional validation fields
  const [minLength, setMinLength] = useState(field?.validation?.minLength?.toString() ?? "");
  const [maxLength, setMaxLength] = useState(field?.validation?.maxLength?.toString() ?? "");
  const [pattern, setPattern] = useState((field?.validation?.pattern as string) ?? "");
  const [minNumber, setMinNumber] = useState(field?.validation?.min?.toString() ?? "");
  const [maxNumber, setMaxNumber] = useState(field?.validation?.max?.toString() ?? "");
  const [minDate, setMinDate] = useState((field?.validation?.min as string) ?? "");
  const [maxDate, setMaxDate] = useState((field?.validation?.max as string) ?? "");

  // Dropdown options
  const [options, setOptions] = useState<Array<{ value: string; label: string }>>(
    field?.options ?? [{ value: "", label: "" }]
  );

  // Visibility condition state
  const [showConditionally, setShowConditionally] = useState(!!field?.visibilityCondition);
  const [conditionFieldSlug, setConditionFieldSlug] = useState(
    field?.visibilityCondition?.dependsOnSlug ?? ""
  );
  const [conditionOperator, setConditionOperator] = useState<ConditionOperator>(
    (field?.visibilityCondition?.operator as ConditionOperator) ?? "eq"
  );
  const [conditionValue, setConditionValue] = useState<string>(() => {
    const v = field?.visibilityCondition?.value;
    if (Array.isArray(v)) return v.join(", ");
    return typeof v === "string" ? v : "";
  });

  // Field usage info (only for editing existing fields)
  const [fieldUsage, setFieldUsage] = useState<FieldUsageInfo | null>(null);
  const [usageOpen, setUsageOpen] = useState(false);

  useEffect(() => {
    if (open && isEditing && field?.id) {
      fetchFieldUsageAction(field.id).then((usage) => {
        setFieldUsage(usage);
      });
    } else if (!open) {
      setFieldUsage(null);
      setUsageOpen(false);
    }
  }, [open, isEditing, field?.id]);

  const totalUsageCount = (fieldUsage?.templates.length ?? 0) + (fieldUsage?.clauses.length ?? 0);

  function toggleContext(ctx: PrerequisiteContext) {
    setRequiredForContexts((prev) =>
      prev.includes(ctx) ? prev.filter((c) => c !== ctx) : [...prev, ctx]
    );
  }

  function resetForm() {
    setEntityType(initialEntityType ?? "PROJECT");
    setName("");
    setSlugField("");
    setFieldType("TEXT");
    setDescription("");
    setRequired(false);
    setRequiredForContexts([]);
    setSortOrder(0);
    setMinLength("");
    setMaxLength("");
    setPattern("");
    setMinNumber("");
    setMaxNumber("");
    setMinDate("");
    setMaxDate("");
    setOptions([{ value: "", label: "" }]);
    setShowConditionally(false);
    setConditionFieldSlug("");
    setConditionOperator("eq");
    setConditionValue("");
    setPortalVisibleDeadline(false);
    setError(null);
  }

  function populateFromField(f: FieldDefinitionResponse) {
    setEntityType(f.entityType);
    setName(f.name);
    setSlugField(f.slug);
    setFieldType(f.fieldType);
    setDescription(f.description ?? "");
    setRequired(f.required);
    setRequiredForContexts((f.requiredForContexts as PrerequisiteContext[]) ?? []);
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
    setShowConditionally(!!f.visibilityCondition);
    setConditionFieldSlug(f.visibilityCondition?.dependsOnSlug ?? "");
    setConditionOperator((f.visibilityCondition?.operator as ConditionOperator) ?? "eq");
    const v = f.visibilityCondition?.value;
    setConditionValue(Array.isArray(v) ? v.join(", ") : typeof v === "string" ? v : "");
    setPortalVisibleDeadline(f.portalVisibleDeadline ?? false);
    setError(null);
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

  function buildOptions(): Array<{ value: string; label: string }> | undefined {
    if (fieldType !== "DROPDOWN") return undefined;
    const filtered = options.filter((o) => o.value.trim() !== "");
    return filtered.length > 0 ? filtered : undefined;
  }

  function buildVisibilityCondition() {
    if (!showConditionally || !conditionFieldSlug) return undefined;
    if (conditionOperator === "in") {
      const values = conditionValue
        .split(",")
        .map((v) => v.trim())
        .filter(Boolean);
      if (values.length === 0) return undefined;
      return { dependsOnSlug: conditionFieldSlug, operator: conditionOperator, value: values };
    }
    if (!conditionValue.trim()) return undefined;
    return {
      dependsOnSlug: conditionFieldSlug,
      operator: conditionOperator,
      value: conditionValue.trim(),
    };
  }

  function clearCondition() {
    setShowConditionally(false);
    setConditionFieldSlug("");
    setConditionOperator("eq");
    setConditionValue("");
  }

  function addOption() {
    setOptions([...options, { value: "", label: "" }]);
  }

  function removeOption(index: number) {
    setOptions(options.filter((_, i) => i !== index));
  }

  function updateOption(index: number, key: "value" | "label", val: string) {
    const updated = [...options];
    updated[index] = { ...updated[index], [key]: val };
    setOptions(updated);
  }

  async function handleSubmit(e: React.FormEvent, onClose: () => void) {
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
          visibilityCondition: buildVisibilityCondition() ?? null,
          requiredForContexts,
          portalVisibleDeadline,
        });

        if (result.success) {
          onClose();
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
          visibilityCondition: buildVisibilityCondition() ?? null,
          requiredForContexts,
          portalVisibleDeadline,
        });

        if (result.success) {
          resetForm();
          onClose();
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

  return {
    isEditing,
    isSubmitting,
    error,
    // Core fields
    entityType,
    setEntityType,
    name,
    setName,
    slugField,
    setSlugField,
    fieldType,
    setFieldType,
    description,
    setDescription,
    required,
    setRequired,
    requiredForContexts,
    toggleContext,
    sortOrder,
    setSortOrder,
    // Validation fields
    minLength,
    setMinLength,
    maxLength,
    setMaxLength,
    pattern,
    setPattern,
    minNumber,
    setMinNumber,
    maxNumber,
    setMaxNumber,
    minDate,
    setMinDate,
    maxDate,
    setMaxDate,
    // Dropdown options
    options,
    addOption,
    removeOption,
    updateOption,
    // Visibility condition
    showConditionally,
    setShowConditionally,
    conditionFieldSlug,
    setConditionFieldSlug,
    conditionOperator,
    setConditionOperator,
    conditionValue,
    setConditionValue,
    clearCondition,
    // Portal deadline visibility (only used when fieldType === "DATE")
    portalVisibleDeadline,
    setPortalVisibleDeadline,
    // Field usage
    fieldUsage,
    usageOpen,
    setUsageOpen,
    totalUsageCount,
    // Form lifecycle
    resetForm,
    populateFromField,
    handleSubmit,
  };
}
