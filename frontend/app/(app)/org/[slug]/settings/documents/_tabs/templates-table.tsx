"use client";

import { useMemo } from "react";
import Link from "next/link";
import { FileText } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { StatusBadgeCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { TemplateActionsMenu } from "@/components/templates/TemplateActionsMenu";
import { entityNounLabel } from "@/components/audit/entity-type-labels";
import { formatFileSize } from "@/lib/format";
import type { TemplateListResponse } from "@/lib/types";

const getTemplateId = (template: TemplateListResponse) => template.id;

// K-117: ORGANIZATION has no entity noun (`entityNounLabel` returns undefined), so the badge read
// the raw enum. A local map — the TemplateEntityType union is unchanged.
const ENTITY_BADGE_FALLBACK: Record<string, string> = { ORGANIZATION: "Firm" };

// K-117 (DA-3): every category is its own table, so each non-name column gets the same fixed width
// in every table (each >= the column's widest content, e.g. Format fits the "Word" badge) and the
// width-less Name column absorbs the rest — the columns line up across sections without
// `table-fixed` (the kit has no <table> class prop). Auto layout treats `width` as a hint and falls
// back to min-content when the table overflows its scroller (1024, D-44), so each width is pinned
// with the matching `min-w-*` (F-665E-A1). Width and viewport hiding are kept apart.
const FORMAT_WIDTH = "w-24 min-w-24";
const ENTITY_WIDTH = "w-28 min-w-28";
const SOURCE_WIDTH = "w-24 min-w-24";
const STATUS_WIDTH = "w-20 min-w-20";
const ACTIONS_WIDTH = "w-20 min-w-20";
const ENTITY_VISIBILITY = "hidden md:table-cell";
const SOURCE_VISIBILITY = "hidden lg:table-cell";
const ENTITY_COL = `${ENTITY_WIDTH} ${ENTITY_VISIBILITY}`;
const SOURCE_COL = `${SOURCE_WIDTH} ${SOURCE_VISIBILITY}`;

interface TemplatesTableProps {
  slug: string;
  templates: TemplateListResponse[];
  canManage: boolean;
}

/** Documents › Templates (Phase 91 665E): one category's templates, DataTable client mode. */
export function TemplatesTable({ slug, templates, canManage }: TemplatesTableProps) {
  // Stable columns: TemplateActionsMenu owns its dialog state, and a rebuilt `cell` would remount it.
  const columns = useMemo(() => {
    const cols: DataTableColumn<TemplateListResponse>[] = defineColumns<TemplateListResponse>([
      {
        id: "name",
        enableHiding: false,
        meta: {
          label: "Name",
          // G89-F8: the description and .docx file name wrap inside the card instead of inheriting
          // the cell's nowrap (a 1621 px table). wrap-anywhere, not break-words: only
          // overflow-wrap:anywhere lowers the cell's min-content width, so an unbroken file name
          // cannot widen the table.
          cellClassName: "max-w-[32rem] min-w-[16rem] wrap-anywhere whitespace-normal",
        },
        cell: ({ row }) => {
          const template = row.original;
          return (
            <>
              <Link
                href={`/org/${slug}/settings/documents/templates/${template.id}/edit`}
                className="text-foreground font-medium hover:text-teal-600 hover:underline dark:hover:text-teal-400"
              >
                {template.name}
              </Link>
              {template.description && (
                <p className="text-muted-foreground text-xs">{template.description}</p>
              )}
              {template.dedicatedFlow ? (
                <p
                  className="text-muted-foreground text-xs italic"
                  data-testid="template-dedicated-flow-hint"
                >
                  Generated from its own flow
                </p>
              ) : (
                template.hiddenFromGenericMenus && (
                  <p
                    className="text-muted-foreground text-xs italic"
                    data-testid="template-hidden-from-menus-hint"
                  >
                    Not offered in Generate menus
                  </p>
                )
              )}
              {template.format === "DOCX" && template.docxFileName && (
                <p className="text-muted-foreground text-xs">
                  {template.docxFileName}
                  {template.docxFileSize != null && ` (${formatFileSize(template.docxFileSize)})`}
                </p>
              )}
            </>
          );
        },
      },
      {
        id: "format",
        meta: { label: "Format", headerClassName: FORMAT_WIDTH, cellClassName: FORMAT_WIDTH },
        cell: ({ row }) =>
          row.original.format === "DOCX" ? (
            <Badge variant="success">
              <FileText className="size-3" />
              Word
            </Badge>
          ) : (
            <Badge variant="neutral">Tiptap</Badge>
          ),
      },
      {
        id: "entityType",
        meta: { label: "Entity Type", headerClassName: ENTITY_COL, cellClassName: ENTITY_COL },
        cell: ({ row }) => {
          const type = row.original.primaryEntityType;
          return (
            <Badge variant="neutral">
              {entityNounLabel(type) ?? ENTITY_BADGE_FALLBACK[type] ?? type}
            </Badge>
          );
        },
      },
      {
        id: "source",
        meta: { label: "Source", headerClassName: SOURCE_COL, cellClassName: SOURCE_COL },
        cell: ({ row }) =>
          row.original.source === "PLATFORM" ? (
            <Badge variant="neutral">Platform</Badge>
          ) : (
            <Badge variant="lead">Custom</Badge>
          ),
      },
      {
        id: "status",
        meta: { label: "Status", headerClassName: STATUS_WIDTH, cellClassName: STATUS_WIDTH },
        cell: ({ row }) =>
          row.original.active ? (
            <StatusBadgeCell label="Active" variant="success" />
          ) : (
            <StatusBadgeCell label="Inactive" variant="neutral" />
          ),
      },
    ]);
    if (canManage) {
      cols.push({
        id: "actions",
        enableHiding: false,
        meta: { label: "Actions", headerClassName: ACTIONS_WIDTH, cellClassName: ACTIONS_WIDTH },
        cell: ({ row }) => <TemplateActionsMenu slug={slug} template={row.original} />,
      });
    }
    return cols;
  }, [slug, canManage]);

  return (
    <DataTable
      columns={columns}
      data={templates}
      getRowId={getTemplateId}
      rowTestId="template-list-item"
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={null}
    />
  );
}
