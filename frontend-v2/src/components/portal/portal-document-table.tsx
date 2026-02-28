"use client";

import { Download, FileText, File, Image, FileSpreadsheet } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDate, formatFileSize } from "@/lib/format";
import type { PortalDocument } from "@/lib/types";

interface PortalDocumentTableProps {
  documents: PortalDocument[];
  onDownload?: (documentId: string) => void;
}

function getFileIcon(contentType: string) {
  if (contentType.startsWith("image/")) return Image;
  if (contentType.includes("pdf")) return FileText;
  if (
    contentType.includes("spreadsheet") ||
    contentType.includes("excel") ||
    contentType.includes("csv")
  )
    return FileSpreadsheet;
  return File;
}

export function PortalDocumentTable({
  documents,
  onDownload,
}: PortalDocumentTableProps) {
  if (documents.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 py-12 dark:border-slate-800">
        <FileText className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
        <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
          No documents shared yet
        </p>
        <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
          Documents shared with you will appear here.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 dark:border-slate-800">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead className="hidden sm:table-cell">Project</TableHead>
            <TableHead className="hidden md:table-cell">Size</TableHead>
            <TableHead className="hidden md:table-cell">Uploaded</TableHead>
            <TableHead className="w-[80px]" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {documents.map((doc) => {
            const Icon = getFileIcon(doc.contentType);
            return (
              <TableRow key={doc.id}>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Icon className="size-4 shrink-0 text-slate-400" />
                    <span className="truncate font-medium text-slate-900 dark:text-slate-100">
                      {doc.fileName}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="hidden sm:table-cell">
                  {doc.projectName ? (
                    <Badge variant="neutral" className="text-xs">
                      {doc.projectName}
                    </Badge>
                  ) : (
                    <span className="text-sm text-slate-400">--</span>
                  )}
                </TableCell>
                <TableCell className="hidden text-sm text-slate-500 md:table-cell">
                  {formatFileSize(doc.size)}
                </TableCell>
                <TableCell className="hidden text-sm text-slate-500 md:table-cell">
                  {doc.uploadedAt
                    ? formatDate(doc.uploadedAt)
                    : formatDate(doc.createdAt)}
                </TableCell>
                <TableCell>
                  {onDownload && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onDownload(doc.id)}
                      className="size-8 p-0 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200"
                      aria-label={`Download ${doc.fileName}`}
                    >
                      <Download className="size-4" />
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
