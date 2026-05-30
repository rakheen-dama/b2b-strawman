import { FieldGroupSelector } from "@/components/field-definitions/FieldGroupSelector";
import { CustomFieldSection } from "@/components/field-definitions/CustomFieldSection";
import type {
  EntityType,
  FieldDefinitionResponse,
  FieldGroupResponse,
  FieldGroupMemberResponse,
} from "@/lib/types";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface ClientFieldsTabProps {
  entityId: string;
  appliedFieldGroups: string[];
  slug: string;
  canManage: boolean;
  allGroups: FieldGroupResponse[];
  customFields: Record<string, unknown>;
  editable: boolean;
  fieldDefinitions: FieldDefinitionResponse[];
  fieldGroups: FieldGroupResponse[];
  groupMembers: Record<string, FieldGroupMemberResponse[]>;
  promotedFieldValues: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ClientFieldsTab({
  entityId,
  appliedFieldGroups,
  slug,
  canManage,
  allGroups,
  customFields,
  editable,
  fieldDefinitions,
  fieldGroups,
  groupMembers,
  promotedFieldValues,
}: ClientFieldsTabProps) {
  const entityType: EntityType = "CUSTOMER";

  return (
    <div data-testid="client-fields-tab" className="space-y-6">
      <FieldGroupSelector
        entityType={entityType}
        entityId={entityId}
        appliedFieldGroups={appliedFieldGroups}
        slug={slug}
        canManage={canManage}
        allGroups={allGroups}
      />
      <CustomFieldSection
        entityType={entityType}
        entityId={entityId}
        customFields={customFields}
        appliedFieldGroups={appliedFieldGroups}
        editable={editable}
        slug={slug}
        fieldDefinitions={fieldDefinitions}
        fieldGroups={fieldGroups}
        groupMembers={groupMembers}
        promotedFieldValues={promotedFieldValues}
      />
    </div>
  );
}
