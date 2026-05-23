"use client";

import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import type {
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

interface MatterFieldsTabProps {
  projectId: string;
  customFields: Record<string, unknown>;
  appliedFieldGroups: string[];
  fieldDefinitions: FieldDefinitionResponse[];
  fieldGroups: FieldGroupResponse[];
  groupMembers: Record<string, FieldGroupMemberResponse[]>;
  canEdit: boolean;
  canManage: boolean;
  slug: string;
}

export function MatterFieldsTab({
  projectId,
  customFields,
  appliedFieldGroups,
  fieldDefinitions,
  fieldGroups,
  groupMembers,
  canEdit,
  canManage,
  slug,
}: MatterFieldsTabProps) {
  const hasContent =
    fieldDefinitions.length > 0 || fieldGroups.length > 0 || appliedFieldGroups.length > 0;

  if (!hasContent) {
    return (
      <div className="py-12 text-center" data-testid="matter-fields-tab">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No custom field groups have been configured for this organisation.
        </p>
        <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
          Admins can create field groups in Settings.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="matter-fields-tab">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          Field Groups
        </h3>
        <FieldGroupSelector
          entityType="PROJECT"
          entityId={projectId}
          appliedFieldGroups={appliedFieldGroups}
          slug={slug}
          canManage={canManage}
          allGroups={fieldGroups}
        />
      </div>

      <CustomFieldSection
        entityType="PROJECT"
        entityId={projectId}
        customFields={customFields}
        appliedFieldGroups={appliedFieldGroups}
        editable={canEdit}
        slug={slug}
        fieldDefinitions={fieldDefinitions}
        fieldGroups={fieldGroups}
        groupMembers={groupMembers}
      />
    </div>
  );
}
