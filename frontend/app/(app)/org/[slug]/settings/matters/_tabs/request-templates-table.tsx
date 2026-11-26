"use client";

import { useMemo } from "react";
import Link from "next/link";
import { ClipboardList } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { TemplateSourceBadge } from "@/components/information-requests/template-source-badge";
import { RequestTemplateActions } from "@/components/information-requests/request-template-actions";
import type { RequestTemplateResponse } from "@/lib/api/information-requests";

const getTemplateId = (template: RequestTemplateResponse) => template.id;

interface RequestTemplatesTableProps {
  slug: string;
  templates: RequestTemplateResponse[];
}

/** Matters › Request templates list (Phase 91 665C): DataTable client mode in an untitled section. */
export function RequestTemplatesTable({ slug, templates }: RequestTemplatesTableProps) {
  // Stable columns: RequestTemplateActions owns its duplicating/deactivating state, which a
  // rebuilt `cell` would reset by remounting it.
  const columns = useMemo(
    () =>
      defineColumns<RequestTemplateResponse>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <>
              <Link
                href={`/org/${slug}/settings/matters/request-templates/${row.original.id}`}
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
          id: "source",
          meta: { label: "Source" },
          cell: ({ row }) => <TemplateSourceBadge source={row.original.source} />,
        },
        {
          id: "items",
          meta: { label: "Items", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => row.original.items.length,
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => (
            <span
              className={
                row.original.active
                  ? "text-success-foreground text-sm"
                  : "text-muted-foreground text-sm"
              }
            >
              {row.original.active ? "Active" : "Inactive"}
            </span>
          ),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) => (
            <RequestTemplateActions
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
      icon={ClipboardList}
      title="No request templates yet"
      description="Create your first request template to streamline information gathering."
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
        rowTestId="request-template-row"
        columnVisibility={false}
        pageSize={null}
        stickyHeader={false}
        frame={false}
        empty={empty}
      />
    </SettingsSection>
  );
}
