"use client";

import { useMemo } from "react";
import { ListChecks } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import type { ChecklistTemplateItemResponse } from "@/lib/types";

const getItemId = (item: ChecklistTemplateItemResponse) => item.id;

interface ChecklistItemsTableProps {
  /** Already in display order (sorted by `sortOrder` on the page). */
  items: ChecklistTemplateItemResponse[];
}

/** A checklist template's items (Phase 91 665C): DataTable client mode inside its section. */
export function ChecklistItemsTable({ items }: ChecklistItemsTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<ChecklistTemplateItemResponse>([
        {
          id: "position",
          meta: { label: "#", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => row.index + 1,
        },
        {
          id: "name",
          enableHiding: false,
          meta: {
            label: "Name",
            cellClassName: "text-foreground whitespace-normal text-sm font-medium",
          },
          cell: ({ row }) => row.original.name,
        },
        {
          id: "description",
          meta: {
            label: "Description",
            cellClassName: "text-muted-foreground whitespace-normal text-sm",
          },
          cell: ({ row }) => row.original.description || "—",
        },
        {
          id: "required",
          meta: { label: "Required" },
          cell: ({ row }) =>
            row.original.required ? (
              <Badge variant="destructive" data-testid="checklist-item-required">
                Required
              </Badge>
            ) : (
              <span className="text-muted-foreground text-sm">Optional</span>
            ),
        },
        {
          id: "requiresDocument",
          // The two-word header may wrap: `TableHead` is `whitespace-nowrap` by default and this
          // header alone otherwise sets the table's min width past a 1024 viewport (F-665C-15).
          meta: { label: "Document Required", headerClassName: "whitespace-normal" },
          cell: ({ row }) =>
            row.original.requiresDocument ? (
              <Badge variant="neutral">{row.original.requiredDocumentLabel || "Yes"}</Badge>
            ) : (
              <span className="text-muted-foreground text-sm">No</span>
            ),
        },
      ]),
    []
  );

  return (
    <DataTable
      columns={columns}
      data={items}
      getRowId={getItemId}
      rowTestId="checklist-item-row"
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={
        <EmptyState
          variant="inline"
          icon={ListChecks}
          title="No items in this template."
          description={null}
        />
      }
    />
  );
}
