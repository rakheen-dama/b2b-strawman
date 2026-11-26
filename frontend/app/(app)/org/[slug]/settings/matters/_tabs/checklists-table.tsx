"use client";

import { useMemo } from "react";
import Link from "next/link";
import { ClipboardCheck } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { ChecklistTemplateActions } from "@/components/compliance/ChecklistTemplateActions";
import type { ChecklistTemplateResponse } from "@/lib/types";

const getTemplateId = (template: ChecklistTemplateResponse) => template.id;

interface ChecklistTemplatesTableProps {
  slug: string;
  templates: ChecklistTemplateResponse[];
}

/** Matters › Checklists list (Phase 91 665C): DataTable client mode in an untitled section. */
export function ChecklistTemplatesTable({ slug, templates }: ChecklistTemplatesTableProps) {
  // Stable columns: ChecklistTemplateActions owns its cloning/deactivating state, which a rebuilt
  // `cell` would reset by remounting it.
  const columns = useMemo(
    () =>
      defineColumns<ChecklistTemplateResponse>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <>
              <Link
                href={`/org/${slug}/settings/matters/checklists/${row.original.id}`}
                className="text-foreground font-medium hover:underline"
              >
                {row.original.name}
              </Link>
              {row.original.description && (
                <p className="text-muted-foreground text-xs">{row.original.description}</p>
              )}
            </>
          ),
        },
        {
          id: "clientType",
          meta: { label: "Client Type" },
          cell: ({ row }) => <Badge variant="neutral">{row.original.clientType}</Badge>,
        },
        {
          id: "source",
          meta: { label: "Source" },
          cell: ({ row }) => (
            <Badge variant={row.original.source === "ORG_CUSTOM" ? "success" : "neutral"}>
              {row.original.source === "PLATFORM" ? "Platform" : "Custom"}
            </Badge>
          ),
        },
        {
          id: "autoInstantiate",
          meta: { label: "Auto-Instantiate" },
          cell: ({ row }) => (
            <span
              className={
                row.original.autoInstantiate ? "text-success-foreground" : "text-muted-foreground"
              }
            >
              {row.original.autoInstantiate ? "Yes" : "No"}
            </span>
          ),
        },
        {
          id: "items",
          meta: { label: "Items", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => row.original.items.length,
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => (
            <ChecklistTemplateActions
              slug={slug}
              templateId={row.original.id}
              templateName={row.original.name}
              source={row.original.source}
            />
          ),
        },
      ]),
    [slug]
  );

  const empty = (
    <EmptyState
      variant="default"
      icon={ClipboardCheck}
      title="No checklist templates yet"
      description="Create your first checklist template to streamline client onboarding."
    />
  );
  // DA-7: an empty list is the whole tab panel's content, so no card around it.
  if (templates.length === 0) return empty;

  return (
    <SettingsSection>
      <DataTable
        columns={columns}
        data={templates}
        getRowId={getTemplateId}
        rowTestId="checklist-template-row"
        columnVisibility={false}
        pageSize={null}
        stickyHeader={false}
        frame={false}
        empty={empty}
      />
    </SettingsSection>
  );
}
