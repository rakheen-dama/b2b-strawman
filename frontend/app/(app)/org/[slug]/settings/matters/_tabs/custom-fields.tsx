import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { HelpTip } from "@/components/help-tip";
import { CustomFieldsContent } from "./custom-fields-content";
import type { FieldDefinitionResponse, FieldGroupResponse, EntityType } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

const ENTITY_TYPES: EntityType[] = ["PROJECT", "TASK", "CUSTOMER", "INVOICE"];

/** Matters › Custom fields tab (Phase 87 S9, ADR-341) — the former custom-fields settings page's loader. */
export async function renderCustomFieldsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Custom Fields
          <HelpTip code="fields.types" />
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage custom fields. Only admins and owners can access this
          page.
        </p>
      </div>
    );
  }

  const fieldsByType: Record<EntityType, FieldDefinitionResponse[]> = {
    PROJECT: [],
    TASK: [],
    CUSTOMER: [],
    INVOICE: [],
  };
  const groupsByType: Record<EntityType, FieldGroupResponse[]> = {
    PROJECT: [],
    TASK: [],
    CUSTOMER: [],
    INVOICE: [],
  };

  try {
    const results = await Promise.all(
      ENTITY_TYPES.flatMap((et) => [
        api.get<FieldDefinitionResponse[]>(`/api/field-definitions?entityType=${et}`),
        api.get<FieldGroupResponse[]>(`/api/field-groups?entityType=${et}`),
      ])
    );

    ENTITY_TYPES.forEach((et, i) => {
      fieldsByType[et] = (results[i * 2] as FieldDefinitionResponse[]) ?? [];
      groupsByType[et] = (results[i * 2 + 1] as FieldGroupResponse[]) ?? [];
    });
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Custom Fields
          <HelpTip code="fields.types" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Define custom fields and groups for matters, tasks, clients, and fee notes.
        </p>
      </div>

      <CustomFieldsContent
        slug={slug}
        fieldsByType={fieldsByType}
        groupsByType={groupsByType}
        canManage={isAdmin}
      />
    </div>
  );
}
