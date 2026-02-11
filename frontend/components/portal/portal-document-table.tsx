"use client";

import { useState } from "react";
import {
  FileText,
  FileImage,
  FileSpreadsheet,
  FileArchive,
  File,
  Download,
  Loader2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate, formatFileSize } from "@/lib/format";
import { portalApi } from "@/lib/portal-api";
import type { PortalDocument, PresignDownloadResponse } from "@/lib/types";

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

interface PortalDocumentTableProps {
  documents: PortalDocument[];
  title: string;
  /** Show the project name column (for all-docs view) */
  showProject?: boolean;
}

export function PortalDocumentTable({
  documents,
  title,
  showProject = false,
}: PortalDocumentTableProps) {
  if (documents.length === 0) {
    return (
      <EmptyState
        icon={FileText}
        title="No documents"
        description="There are no shared documents available at the moment"
      />
    );
  }

  return (
    <div className="space-y-3">
      <h2 className="font-semibold text-olive-900 dark:text-olive-100">
        {title}
      </h2>
      <div className="rounded-lg border border-olive-200 dark:border-olive-800">
        <Table>
          <TableHeader>
            <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                File
              </TableHead>
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Size
              </TableHead>
              {showProject && (
                <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Project
                </TableHead>
              )}
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Uploaded
              </TableHead>
              <TableHead className="w-[60px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {documents.map((doc) => {
              const Icon = getFileIcon(doc.contentType);
              return (
                <TableRow
                  key={doc.id}
                  className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                >
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Icon className="size-4 shrink-0 text-olive-400 dark:text-olive-500" />
                      <span className="truncate text-sm font-medium text-olive-950 dark:text-olive-50">
                        {doc.fileName}
                      </span>
                    </div>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {formatFileSize(doc.size)}
                    </span>
                  </TableCell>
                  {showProject && (
                    <TableCell className="hidden sm:table-cell">
                      <span className="text-sm text-olive-600 dark:text-olive-400">
                        {doc.projectName || "\u2014"}
                      </span>
                    </TableCell>
                  )}
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {doc.uploadedAt ? formatDate(doc.uploadedAt) : "\u2014"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <PortalDownloadButton
                      documentId={doc.id}
                      fileName={doc.fileName}
                    />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}

function PortalDownloadButton({
  documentId,
  fileName,
}: {
  documentId: string;
  fileName: string;
}) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const result = await portalApi.get<PresignDownloadResponse>(
        `/portal/documents/${documentId}/presign-download`,
      );

      const a = document.createElement("a");
      a.href = result.presignedUrl;
      a.download = fileName;
      a.style.display = "none";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch {
      setError("Download failed");
      setTimeout(() => setError(null), 5000);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        className="size-8"
        onClick={handleDownload}
        disabled={isLoading}
      >
        {isLoading ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <Download className="size-4" />
        )}
        <span className="sr-only">Download {fileName}</span>
      </Button>
      {error && (
        <span className="text-xs text-red-600 dark:text-red-400">{error}</span>
      )}
    </div>
  );
}
