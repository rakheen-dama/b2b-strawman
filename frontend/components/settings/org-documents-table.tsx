"use client";

import { useMemo, type ReactNode } from "react";
import { File, FileArchive, FileImage, FileSpreadsheet, FileText } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, StatusBadgeCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatFileSize } from "@/lib/format";
import type { Document, DocumentScope, DocumentStatus } from "@/lib/types";

function getFileIcon(contentType: string) {
  if (contentType.startsWith("image/")) return FileImage;
  if (contentType.includes("pdf")) return FileText;
  if (
    contentType.includes("spreadsheet") ||
    contentType.includes("excel") ||
    contentType.includes("csv")
  )
    return FileSpreadsheet;
  if (contentType.includes("zip") || contentType.includes("gzip") || contentType.includes("tar"))
    return FileArchive;
  return File;
}

const STATUS_BADGE: Record<
  DocumentStatus,
  { label: string; variant: "success" | "warning" | "destructive" }
> = {
  UPLOADED: { label: "Uploaded", variant: "success" },
  PENDING: { label: "Pending", variant: "warning" },
  FAILED: { label: "Failed", variant: "destructive" },
};

// `neutral` / `default` / `lead` are outside StatusBadgeCell's variant union, so scope keeps Badge.
const SCOPE_BADGE: Record<
  DocumentScope,
  { label: string; variant: "neutral" | "default" | "lead" }
> = {
  ORG: { label: "Org", variant: "neutral" },
  PROJECT: { label: "Matter", variant: "default" },
  CUSTOMER: { label: "Client", variant: "lead" },
};

const getDocumentId = (doc: Document) => doc.id;

interface OrgDocumentsTableProps {
  /** Passed through untouched (`data` identity is navigation for DataTable). */
  documents: Document[];
  /** Pre-rendered by the server section (shared EmptyState, compact). */
  empty: ReactNode;
}

/**
 * Firm › General org documents (Phase 91 665A): DataTable client mode inside its SettingsSection.
 * Read-only list (no row link, no row actions); every column always shown (DA-9).
 */
export function OrgDocumentsTable({ documents, empty }: OrgDocumentsTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<Document>([
        {
          id: "file",
          enableHiding: false,
          meta: { label: "File" },
          cell: ({ row }) => {
            const Icon = getFileIcon(row.original.contentType);
            return (
              <div className="flex items-center gap-2">
                <Icon className="text-muted-foreground size-4 shrink-0" />
                <span className="text-foreground truncate text-sm font-medium">
                  {row.original.fileName}
                </span>
              </div>
            );
          },
        },
        {
          id: "size",
          meta: { label: "Size", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => formatFileSize(row.original.size),
        },
        {
          id: "scope",
          meta: { label: "Scope" },
          cell: ({ row }) => {
            const badge = SCOPE_BADGE[row.original.scope];
            return <Badge variant={badge.variant}>{badge.label}</Badge>;
          },
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => {
            const badge = STATUS_BADGE[row.original.status];
            return <StatusBadgeCell label={badge.label} variant={badge.variant} />;
          },
        },
        {
          id: "uploaded",
          meta: { label: "Uploaded", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => <DateCell value={row.original.uploadedAt} />,
        },
      ]),
    []
  );

  return (
    <DataTable
      columns={columns}
      data={documents}
      getRowId={getDocumentId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
    />
  );
}
