"use client";

import type { ColumnDef } from "@tanstack/react-table";
import type { Document } from "@/lib/types";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatDate, formatFileSize } from "@/lib/format";
import {
  FileText,
  FileImage,
  FileSpreadsheet,
  FileArchive,
  File,
  Lock,
  Globe,
} from "lucide-react";

function getFileIcon(contentType: string) {
  if (contentType.startsWith("image/")) return FileImage;
  if (contentType.includes("pdf")) return FileText;
  if (
    contentType.includes("spreadsheet") ||
    contentType.includes("excel") ||
    contentType.includes("csv")
  )
    return FileSpreadsheet;
  if (
    contentType.includes("zip") ||
    contentType.includes("gzip") ||
    contentType.includes("tar")
  )
    return FileArchive;
  return File;
}

export { getFileIcon };

export function getDocumentColumns(): ColumnDef<Document, unknown>[] {
  return [
    {
      accessorKey: "fileName",
      header: "Name",
      cell: ({ row }) => {
        const Icon = getFileIcon(row.original.contentType);
        return (
          <div className="flex items-center gap-2 min-w-0">
            <Icon className="size-4 shrink-0 text-slate-400" />
            <span className="truncate text-sm font-medium text-slate-900">
              {row.original.fileName}
            </span>
          </div>
        );
      },
      size: 280,
    },
    {
      accessorKey: "scope",
      header: "Scope",
      cell: ({ row }) => <StatusBadge status={row.original.scope} />,
      size: 100,
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => <StatusBadge status={row.original.status} />,
      size: 100,
    },
    {
      accessorKey: "size",
      header: "Size",
      cell: ({ row }) => (
        <span className="font-mono text-sm tabular-nums text-slate-600">
          {formatFileSize(row.original.size)}
        </span>
      ),
      size: 90,
    },
    {
      id: "createdAt",
      header: "Created",
      accessorFn: (row) => row.uploadedAt ?? row.createdAt,
      cell: ({ row }) => (
        <span className="text-sm text-slate-600">
          {row.original.uploadedAt
            ? formatDate(row.original.uploadedAt)
            : formatDate(row.original.createdAt)}
        </span>
      ),
      size: 120,
    },
    {
      accessorKey: "visibility",
      header: "Visibility",
      cell: ({ row }) => {
        const isShared = row.original.visibility === "SHARED";
        return (
          <span
            className={`inline-flex items-center gap-1 text-xs font-medium ${
              isShared ? "text-teal-600" : "text-slate-500"
            }`}
          >
            {isShared ? (
              <Globe className="size-3.5" />
            ) : (
              <Lock className="size-3.5" />
            )}
            {isShared ? "Shared" : "Internal"}
          </span>
        );
      },
      size: 100,
    },
  ];
}
