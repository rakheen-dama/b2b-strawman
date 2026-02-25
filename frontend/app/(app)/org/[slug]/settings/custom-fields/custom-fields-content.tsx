"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Plus, Layers } from "lucide-react";
import { FieldDefinitionDialog } from "@/components/field-definitions/FieldDefinitionDialog";
import { FieldGroupDialog } from "@/components/field-definitions/FieldGroupDialog";
import { DeleteFieldDialog } from "@/components/field-definitions/DeleteFieldDialog";
import { DeleteGroupDialog } from "@/components/field-definitions/DeleteGroupDialog";
import type {
  EntityType,
  FieldDefinitionResponse,
  FieldGroupResponse,
} from "@/lib/types";

const ENTITY_TYPE_TABS: { value: EntityType; label: string }[] = [
  { value: "PROJECT", label: "Projects" },
  { value: "TASK", label: "Tasks" },
  { value: "CUSTOMER", label: "Customers" },
  { value: "INVOICE", label: "Invoices" },
];

interface CustomFieldsContentProps {
  slug: string;
  fieldsByType: Record<EntityType, FieldDefinitionResponse[]>;
  groupsByType: Record<EntityType, FieldGroupResponse[]>;
  canManage: boolean;
}

export function CustomFieldsContent({
  slug,
  fieldsByType,
  groupsByType,
  canManage,
}: CustomFieldsContentProps) {
  const [activeTab, setActiveTab] = useState<EntityType>("PROJECT");

  const allFields = [
    ...fieldsByType.PROJECT,
    ...fieldsByType.TASK,
    ...fieldsByType.CUSTOMER,
    ...fieldsByType.INVOICE,
  ];

  return (
    <Tabs
      value={activeTab}
      onValueChange={(v) => setActiveTab(v as EntityType)}
    >
      <TabsList variant="line">
        {ENTITY_TYPE_TABS.map((tab) => (
          <TabsTrigger key={tab.value} value={tab.value}>
            {tab.label}
          </TabsTrigger>
        ))}
      </TabsList>

      {ENTITY_TYPE_TABS.map((tab) => (
        <TabsContent key={tab.value} value={tab.value} className="space-y-8">
          {/* Field Definitions Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                Field Definitions
              </h2>
              {canManage && (
                <FieldDefinitionDialog slug={slug} entityType={tab.value} allFieldsForType={fieldsByType[tab.value]}>
                  <Button size="sm">
                    <Plus className="mr-1 size-4" />
                    Add Field
                  </Button>
                </FieldDefinitionDialog>
              )}
            </div>

            {fieldsByType[tab.value].length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
                No field definitions for {tab.label.toLowerCase()} yet.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Required</TableHead>
                    <TableHead>Pack</TableHead>
                    <TableHead>Status</TableHead>
                    {canManage && (
                      <TableHead className="w-12">Actions</TableHead>
                    )}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {fieldsByType[tab.value].map((field) => (
                    <TableRow key={field.id}>
                      <TableCell className="font-medium">
                        {field.name}
                        {field.description && (
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            {field.description}
                          </p>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant="neutral">{field.fieldType}</Badge>
                      </TableCell>
                      <TableCell>
                        {field.required ? (
                          <Badge variant="warning">Required</Badge>
                        ) : (
                          <span className="text-sm text-slate-400">
                            Optional
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {field.packId ? (
                          <Badge variant="pro">Pack</Badge>
                        ) : (
                          <span className="text-sm text-slate-400">
                            Custom
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {field.active ? (
                          <Badge variant="success">Active</Badge>
                        ) : (
                          <Badge variant="neutral">Inactive</Badge>
                        )}
                      </TableCell>
                      {canManage && (
                        <TableCell>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="plain"
                                size="icon"
                                className="size-8"
                              >
                                <MoreHorizontal className="size-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <FieldDefinitionDialog
                                slug={slug}
                                entityType={tab.value}
                                field={field}
                                allFieldsForType={fieldsByType[tab.value]}
                              >
                                <DropdownMenuItem
                                  onSelect={(e) => e.preventDefault()}
                                >
                                  Edit
                                </DropdownMenuItem>
                              </FieldDefinitionDialog>
                              <DeleteFieldDialog
                                slug={slug}
                                fieldId={field.id}
                                fieldName={field.name}
                              >
                                <DropdownMenuItem
                                  onSelect={(e) => e.preventDefault()}
                                  className="text-destructive focus:text-destructive"
                                >
                                  Delete
                                </DropdownMenuItem>
                              </DeleteFieldDialog>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>

          {/* Field Groups Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                Field Groups
              </h2>
              {canManage && (
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
              )}
            </div>

            {groupsByType[tab.value].length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
                No field groups for {tab.label.toLowerCase()} yet.
              </p>
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {groupsByType[tab.value].map((group) => (
                  <div
                    key={group.id}
                    className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        <div className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md bg-slate-100 dark:bg-slate-800">
                          <Layers className="size-4 text-slate-600 dark:text-slate-400" />
                        </div>
                        <div>
                          <p className="font-medium text-slate-950 dark:text-slate-50">
                            {group.name}
                          </p>
                          {group.description && (
                            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                              {group.description}
                            </p>
                          )}
                          <div className="mt-2 flex flex-wrap gap-1.5">
                            {group.packId && (
                              <Badge variant="pro">Pack</Badge>
                            )}
                            {group.active ? (
                              <Badge variant="success">Active</Badge>
                            ) : (
                              <Badge variant="neutral">Inactive</Badge>
                            )}
                          </div>
                        </div>
                      </div>
                      {canManage && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="plain"
                              size="icon"
                              className="size-8"
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
                              allGroups={groupsByType[tab.value]}
                            >
                              <DropdownMenuItem
                                onSelect={(e) => e.preventDefault()}
                              >
                                Edit
                              </DropdownMenuItem>
                            </FieldGroupDialog>
                            <DeleteGroupDialog
                              slug={slug}
                              groupId={group.id}
                              groupName={group.name}
                            >
                              <DropdownMenuItem
                                onSelect={(e) => e.preventDefault()}
                                className="text-destructive focus:text-destructive"
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
          </div>
        </TabsContent>
      ))}
    </Tabs>
  );
}
