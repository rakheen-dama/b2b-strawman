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
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { VisibilityToggle } from "@/components/documents/visibility-toggle";
import { CustomerDocumentUpload } from "@/components/documents/customer-document-upload";
import { formatDate, formatFileSize } from "@/lib/format";
import { getDownloadUrl } from "@/app/(app)/org/[slug]/projects/[id]/actions";
import type { Document, DocumentStatus } from "@/lib/types";

// --- Helpers ---

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

// --- Component ---

interface CustomerDocumentsPanelProps {
  documents: Document[];
  slug: string;
  customerId: string;
  canManage: boolean;
}

export function CustomerDocumentsPanel({
  documents,
  slug,
  customerId,
  canManage,
}: CustomerDocumentsPanelProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h2 className="font-semibold text-olive-900 dark:text-olive-100">Documents</h2>
          <span className="rounded-full bg-olive-200 px-2 py-0.5 text-xs text-olive-700 dark:bg-olive-800 dark:text-olive-300">
            {documents.length}
          </span>
        </div>
        {canManage && <CustomerDocumentUpload slug={slug} customerId={customerId} />}
      </div>

      {documents.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No customer documents yet"
          description={
            canManage
              ? "Upload your first document for this customer."
              : "No documents have been uploaded for this customer."
          }
          action={
            canManage ? (
              <CustomerDocumentUpload slug={slug} customerId={customerId} />
            ) : undefined
          }
        />
      ) : (
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
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Status
                </TableHead>
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Visibility
                </TableHead>
                <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                  Uploaded
                </TableHead>
                <TableHead className="w-[60px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => {
                const Icon = getFileIcon(doc.contentType);
                const statusBadge = STATUS_BADGE[doc.status];
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
                    <TableCell>
                      <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
                    </TableCell>
                    <TableCell>
                      {canManage ? (
                        <VisibilityToggle
                          documentId={doc.id}
                          visibility={doc.visibility}
                          slug={slug}
                          customerId={customerId}
                          disabled={doc.status !== "UPLOADED"}
                        />
                      ) : (
                        <span className="text-sm text-olive-600 dark:text-olive-400">
                          {doc.visibility === "SHARED" ? "Shared" : "Internal"}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <span className="text-sm text-olive-600 dark:text-olive-400">
                        {doc.uploadedAt ? formatDate(doc.uploadedAt) : "\u2014"}
                      </span>
                    </TableCell>
                    <TableCell>
                      {doc.status === "UPLOADED" && (
                        <DownloadButton documentId={doc.id} fileName={doc.fileName} />
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}

// --- Download button (inline client component) ---

function DownloadButton({ documentId, fileName }: { documentId: string; fileName: string }) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async () => {
    setIsLoading(true);
    setError(null);

    const result = await getDownloadUrl(documentId);
    setIsLoading(false);

    if (!result.success || !result.presignedUrl) {
      setError(result.error ?? "Download failed.");
      setTimeout(() => setError(null), 5000);
      return;
    }

    const a = document.createElement("a");
    a.href = result.presignedUrl;
    a.download = fileName;
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
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
        {isLoading ? <Loader2 className="size-4 animate-spin" /> : <Download className="size-4" />}
        <span className="sr-only">Download {fileName}</span>
      </Button>
      {error && <span className="text-destructive text-xs">{error}</span>}
    </div>
  );
}
