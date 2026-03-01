"use client";

import { useState, useCallback } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PrerequisiteViolationList } from "@/components/prerequisite/prerequisite-violation-list";
import type { InlineFieldEditorField } from "@/components/prerequisite/inline-field-editor";
import type { FieldValue } from "@/components/prerequisite/inline-field-editor";
import type {
  PrerequisiteContext,
  PrerequisiteViolation,
} from "@/components/prerequisite/types";
import { PREREQUISITE_CONTEXT_LABELS } from "@/components/prerequisite/types";
import type { EntityType } from "@/lib/types";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import { updateEntityCustomFieldsAction } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";

interface PrerequisiteModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  context: PrerequisiteContext;
  violations: PrerequisiteViolation[];
  /** Field definitions keyed by slug for inline editing */
  fieldDefinitions?: Record<string, InlineFieldEditorField>;
  /** Initial field values keyed by slug */
  initialFieldValues?: Record<string, FieldValue>;
  entityType: EntityType;
  entityId: string;
  /** Org slug needed for save action */
  slug: string;
  /** Called when all prerequisites pass after re-check */
  onResolved: () => void;
  /** Called when user dismisses the modal */
  onCancel?: () => void;
}

export function PrerequisiteModal({
  open,
  onOpenChange,
  context,
  violations: initialViolations,
  fieldDefinitions = {},
  initialFieldValues = {},
  entityType,
  entityId,
  slug,
  onResolved,
  onCancel,
}: PrerequisiteModalProps) {
  const [violations, setViolations] =
    useState<PrerequisiteViolation[]>(initialViolations);
  const [fieldValues, setFieldValues] =
    useState<Record<string, FieldValue>>(initialFieldValues);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFieldChange = useCallback((fieldSlug: string, value: FieldValue) => {
    setFieldValues((prev) => ({ ...prev, [fieldSlug]: value }));
  }, []);

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        onCancel?.();
      }
      onOpenChange(nextOpen);
    },
    [onOpenChange, onCancel],
  );

  const handleCheckAndContinue = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      // Step 1: Save edited field values if any
      const hasEdits = Object.keys(fieldValues).length > 0;
      if (hasEdits) {
        const saveResult = await updateEntityCustomFieldsAction(
          slug,
          entityType,
          entityId,
          fieldValues as Record<string, unknown>,
        );
        if (!saveResult.success) {
          setError(saveResult.error ?? "Failed to save field values.");
          setLoading(false);
          return;
        }
      }

      // Step 2: Re-check prerequisites
      const check = await checkPrerequisitesAction(context, entityType, entityId);

      if (check.passed) {
        onResolved();
        onOpenChange(false);
      } else {
        setViolations(check.violations);
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  }, [fieldValues, slug, entityType, entityId, context, onResolved, onOpenChange]);

  const contextLabel = PREREQUISITE_CONTEXT_LABELS[context] ?? context;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Prerequisites: {contextLabel}</DialogTitle>
          <DialogDescription>
            The following items must be resolved before proceeding.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[60vh] overflow-y-auto py-2">
          <PrerequisiteViolationList
            violations={violations}
            fieldDefinitions={fieldDefinitions}
            fieldValues={fieldValues}
            onFieldChange={handleFieldChange}
          />
        </div>

        {error && (
          <p className="text-sm text-destructive">{error}</p>
        )}

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={loading}
          >
            Cancel
          </Button>
          <Button
            onClick={handleCheckAndContinue}
            disabled={loading}
          >
            {loading ? (
              <>
                <Loader2 className="mr-2 size-4 animate-spin" />
                Checking...
              </>
            ) : (
              "Check & Continue"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
