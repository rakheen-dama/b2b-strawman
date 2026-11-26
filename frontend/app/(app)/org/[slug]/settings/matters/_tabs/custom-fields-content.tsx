"use client";

import { useMemo, useState, type ReactNode } from "react";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Plus, Layers, Settings2 } from "lucide-react";
import { FieldDefinitionDialog } from "@/components/field-definitions/FieldDefinitionDialog";
import { FieldGroupDialog } from "@/components/field-definitions/FieldGroupDialog";
import { DeleteFieldDialog } from "@/components/field-definitions/DeleteFieldDialog";
import { DeleteGroupDialog } from "@/components/field-definitions/DeleteGroupDialog";
import type { EntityType, FieldDefinitionResponse, FieldGroupResponse } from "@/lib/types";
import {
  type PrerequisiteContext,
  PREREQUISITE_CONTEXT_LABELS,
} from "@/components/prerequisite/types";
import { EmptyState } from "@/components/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { SettingsSection } from "@/components/settings/settings-section";
import { FIELD_TYPES } from "@/components/field-definitions/field-definition-constants";
import { HelpTip } from "@/components/help-tip";
import { createMessages } from "@/lib/messages";

const ENTITY_TYPES: EntityType[] = ["PROJECT", "TASK", "CUSTOMER", "INVOICE"];

const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
  PROJECT: "Matters",
  TASK: "Tasks",
  CUSTOMER: "Clients",
  INVOICE: "Fee notes",
};

interface GroupMemberListProps {
  groupName: string;
  members: { id: string; name: string }[];
}

/** The group card's member field labels, in membership order (G88-F3). */
function GroupMemberList({ groupName, members }: GroupMemberListProps) {
  if (members.length === 0) {
    return (
      <EmptyState
        variant="inline"
        icon={Settings2}
        title="No fields in this group yet."
        description={null}
      />
    );
  }
  return (
    <ul
      className="mt-2 flex flex-wrap gap-1.5"
      aria-label={`Fields in ${groupName}`}
      data-testid="field-group-members"
    >
      {/* Badge is nowrap by default; long labels must wrap within the narrow card column */}
      {members.map((m) => (
        <li key={m.id} className="max-w-full min-w-0">
          <Badge
            variant="neutral"
            className="max-w-full text-left wrap-break-word whitespace-normal"
          >
            {m.name}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

const getFieldId = (field: FieldDefinitionResponse) => field.id;

/** The type's existing label from the Add Field dialog (K-107a), else the raw enum. */
function fieldTypeLabel(fieldType: FieldDefinitionResponse["fieldType"]) {
  return FIELD_TYPES.find((t) => t.value === fieldType)?.label ?? fieldType;
}

interface FieldDefinitionsTableProps {
  slug: string;
  entityType: EntityType;
  fields: FieldDefinitionResponse[];
  canManage: boolean;
  empty: ReactNode;
}

/** One entity tab's field definitions (Phase 91 665C): DataTable client mode inside its section. */
function FieldDefinitionsTable({
  slug,
  entityType,
  fields,
  canManage,
  empty,
}: FieldDefinitionsTableProps) {
  // Stable columns (TanStack renders each `cell` as a component): `fields` is the RSC array, whose
  // identity only changes on navigation, when `data` changes too.
  const columns = useMemo(
    () =>
      defineColumns<FieldDefinitionResponse>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "font-medium" },
          cell: ({ row }) => (
            <>
              {row.original.name}
              {row.original.description && (
                <p className="text-muted-foreground text-xs">{row.original.description}</p>
              )}
            </>
          ),
        },
        {
          id: "type",
          meta: { label: "Type" },
          cell: ({ row }) => (
            <Badge variant="neutral">{fieldTypeLabel(row.original.fieldType)}</Badge>
          ),
        },
        {
          id: "required",
          meta: { label: "Required" },
          cell: ({ row }) =>
            row.original.required ? (
              <Badge variant="warning">Required</Badge>
            ) : (
              <span className="text-muted-foreground text-sm">Optional</span>
            ),
        },
        {
          id: "requiredFor",
          meta: { label: "Required For" },
          cell: ({ row }) =>
            row.original.requiredForContexts && row.original.requiredForContexts.length > 0 ? (
              <div className="flex flex-wrap gap-1">
                {row.original.requiredForContexts.map((ctx) => (
                  <Badge key={ctx} variant="lead">
                    {PREREQUISITE_CONTEXT_LABELS[ctx as PrerequisiteContext] ?? ctx}
                  </Badge>
                ))}
              </div>
            ) : (
              <span className="text-muted-foreground text-sm">None</span>
            ),
        },
        {
          id: "pack",
          meta: { label: "Pack" },
          cell: ({ row }) =>
            row.original.packId ? (
              <Badge variant="neutral">Pack</Badge>
            ) : (
              <span className="text-muted-foreground text-sm">Custom</span>
            ),
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) =>
            row.original.active ? (
              <StatusBadgeCell label="Active" variant="success" />
            ) : (
              <StatusBadgeCell label="Inactive" variant="neutral" />
            ),
        },
        ...(canManage
          ? defineColumns<FieldDefinitionResponse>([
              {
                id: "actions",
                enableHiding: false,
                meta: { label: "Actions", headerClassName: "w-12" },
                cell: ({ row }) => {
                  const field = row.original;
                  return (
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="plain"
                          size="icon"
                          className="size-8"
                          aria-label={`Actions for ${field.name}`}
                        >
                          <MoreHorizontal className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <FieldDefinitionDialog
                          slug={slug}
                          entityType={entityType}
                          field={field}
                          allFieldsForType={fields}
                        >
                          <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
                            Edit
                          </DropdownMenuItem>
                        </FieldDefinitionDialog>
                        <DeleteFieldDialog slug={slug} fieldId={field.id} fieldName={field.name}>
                          <DropdownMenuItem
                            variant="destructive"
                            onSelect={(e) => e.preventDefault()}
                          >
                            Delete
                          </DropdownMenuItem>
                        </DeleteFieldDialog>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  );
                },
              },
            ])
          : []),
      ]),
    [slug, entityType, fields, canManage]
  );

  return (
    <DataTable
      columns={columns}
      data={fields}
      getRowId={getFieldId}
      rowTestId="field-row"
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
    />
  );
}

interface CustomFieldsContentProps {
  slug: string;
  fieldsByType: Record<EntityType, FieldDefinitionResponse[]>;
  /** Names of inactive definitions that are still group members, by id (PR #1752 review). */
  inactiveFieldNames?: Record<string, string>;
  groupsByType: Record<EntityType, FieldGroupResponse[]>;
  canManage: boolean;
}

export function CustomFieldsContent({
  slug,
  fieldsByType,
  inactiveFieldNames = {},
  groupsByType,
  canManage,
}: CustomFieldsContentProps) {
  const { t: tMsg } = createMessages("empty-states");
  const [activeTab, setActiveTab] = useState<EntityType>("PROJECT");

  const entityTypeTabs = ENTITY_TYPES.map((value) => ({
    value,
    label: ENTITY_TYPE_LABELS[value],
  }));

  const allFields = [
    ...fieldsByType.PROJECT,
    ...fieldsByType.TASK,
    ...fieldsByType.CUSTOMER,
    ...fieldsByType.INVOICE,
  ];
  // Member labels for the group cards; ids with no active definition are skipped, never shown raw
  const fieldNameById = new Map(allFields.map((f) => [f.id, f.name]));
  const groupMembers = (group: FieldGroupResponse) =>
    group.fieldDefinitionIds.flatMap((id) => {
      const name = fieldNameById.get(id);
      return name === undefined ? [] : [{ id, name }];
    });

  return (
    <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as EntityType)}>
      <div className="flex items-center gap-2">
        <TabsList variant="line">
          {entityTypeTabs.map((tab) => (
            <TabsTrigger key={tab.value} value={tab.value}>
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <HelpTip code="fields.scoping" />
      </div>

      {entityTypeTabs.map((tab) => (
        <TabsContent key={tab.value} value={tab.value} className="space-y-8">
          <SettingsSection
            title="Field Definitions"
            actions={
              canManage && (
                <FieldDefinitionDialog
                  slug={slug}
                  entityType={tab.value}
                  allFieldsForType={fieldsByType[tab.value]}
                >
                  <Button size="sm">
                    <Plus className="mr-1 size-4" />
                    Add Field
                  </Button>
                </FieldDefinitionDialog>
              )
            }
          >
            <FieldDefinitionsTable
              slug={slug}
              entityType={tab.value}
              fields={fieldsByType[tab.value]}
              canManage={canManage}
              empty={
                <EmptyState
                  variant="compact"
                  icon={Settings2}
                  title={tMsg("customFields.settings.heading")}
                  description={tMsg("customFields.settings.description")}
                />
              }
            />
          </SettingsSection>

          <SettingsSection
            title="Field Groups"
            actions={
              canManage && (
                <FieldGroupDialog
                  slug={slug}
                  entityType={tab.value}
                  availableFields={allFields}
                  allGroups={groupsByType[tab.value]}
                >
                  <Button size="sm" variant="outline">
                    <Plus className="mr-1 size-4" />
                    Add Group
                  </Button>
                </FieldGroupDialog>
              )
            }
          >
            {groupsByType[tab.value].length === 0 ? (
              <EmptyState
                variant="inline"
                icon={Layers}
                title={`No field groups for ${tab.label.toLowerCase()} yet.`}
                description={null}
              />
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {groupsByType[tab.value].map((group) => (
                  <div
                    key={group.id}
                    className="border-border bg-card rounded-lg border p-4"
                    data-testid={`field-group-${group.slug}`}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex min-w-0 items-start gap-3">
                        <div className="bg-muted mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md">
                          <Layers className="text-muted-foreground size-4" />
                        </div>
                        <div className="min-w-0">
                          {/* K-107b: the Pack / status badges sit with the group name, not under
                              the member chips, so "Pack" no longer reads as a member field. */}
                          <div className="flex flex-wrap items-center gap-1.5">
                            <p className="text-foreground font-medium">{group.name}</p>
                            {group.packId && <Badge variant="neutral">Pack</Badge>}
                            {group.active ? (
                              <Badge variant="success">Active</Badge>
                            ) : (
                              <Badge variant="neutral">Inactive</Badge>
                            )}
                          </div>
                          {group.description && (
                            <p className="text-muted-foreground mt-0.5 text-sm">
                              {group.description}
                            </p>
                          )}
                          <GroupMemberList groupName={group.name} members={groupMembers(group)} />
                        </div>
                      </div>
                      {canManage && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="plain"
                              size="icon"
                              className="size-8"
                              aria-label={`Actions for ${group.name}`}
                            >
                              <MoreHorizontal className="size-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <FieldGroupDialog
                              slug={slug}
                              entityType={tab.value}
                              group={group}
                              availableFields={allFields}
                              initialFieldIds={group.fieldDefinitionIds}
                              inactiveFieldNames={inactiveFieldNames}
                              allGroups={groupsByType[tab.value]}
                            >
                              <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
                                Edit
                              </DropdownMenuItem>
                            </FieldGroupDialog>
                            <DeleteGroupDialog
                              slug={slug}
                              groupId={group.id}
                              groupName={group.name}
                            >
                              <DropdownMenuItem
                                variant="destructive"
                                onSelect={(e) => e.preventDefault()}
                              >
                                Delete
                              </DropdownMenuItem>
                            </DeleteGroupDialog>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </SettingsSection>
        </TabsContent>
      ))}
    </Tabs>
  );
}
