"use client";

import { Fragment, useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  FileText,
  FileImage,
  FileSpreadsheet,
  FileArchive,
  File,
  Download,
  Loader2,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import { CommentSectionClient } from "@/components/comments/comment-section-client";
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
import { FileUploadZone } from "@/components/documents/file-upload-zone";
import {
  UploadProgressItem,
  type UploadItem,
  type UploadStatus,
} from "@/components/documents/upload-progress-item";
import { formatDate, formatFileSize } from "@/lib/format";
import { validateFile } from "@/lib/upload-validation";
import {
  initiateUpload,
  confirmUpload,
  cancelUpload,
  getDownloadUrl,
} from "@/app/(app)/org/[slug]/projects/[id]/actions";
import { cn } from "@/lib/utils";
import type { Document, DocumentStatus, DocumentScope } from "@/lib/types";

// --- Upload state reducer ---

type UploadAction =
  | { type: "ADD"; items: UploadItem[] }
  | { type: "SET_STATUS"; id: string; status: UploadStatus; error?: string }
  | { type: "SET_PROGRESS"; id: string; progress: number }
  | {
      type: "SET_INIT_RESULT";
      id: string;
      documentId: string;
      presignedUrl: string;
    }
  | { type: "REMOVE"; id: string };

function uploadReducer(state: UploadItem[], action: UploadAction): UploadItem[] {
  switch (action.type) {
    case "ADD":
      return [...state, ...action.items];
    case "SET_STATUS":
      return state.map((item) =>
        item.id === action.id ? { ...item, status: action.status, error: action.error } : item
      );
    case "SET_PROGRESS":
      return state.map((item) =>
        item.id === action.id ? { ...item, progress: action.progress } : item
      );
    case "SET_INIT_RESULT":
      return state.map((item) =>
        item.id === action.id
          ? {
              ...item,
              documentId: action.documentId,
              presignedUrl: action.presignedUrl,
            }
          : item
      );
    case "REMOVE":
      return state.filter((item) => item.id !== action.id);
    default:
      return state;
  }
}

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

const SCOPE_BADGE: Record<
  DocumentScope,
  { label: string; variant: "neutral" | "default" | "lead" }
> = {
  ORG: { label: "Org", variant: "neutral" },
  PROJECT: { label: "Project", variant: "default" },
  CUSTOMER: { label: "Customer", variant: "lead" },
};

// --- Component ---

interface DocumentsPanelProps {
  documents: Document[];
  projectId: string;
  slug: string;
  /** Show scope badge column (for mixed-scope views) */
  showScope?: boolean;
  /** Current user's member ID for comment authorship */
  currentMemberId?: string | null;
  /** Whether current user can manage comment visibility (admin/owner/lead) */
  canManageVisibility?: boolean;
}

export function DocumentsPanel({
  documents,
  projectId,
  slug,
  showScope = false,
  currentMemberId,
  canManageVisibility = false,
}: DocumentsPanelProps) {
  const router = useRouter();
  const [uploads, dispatch] = useReducer(uploadReducer, []);
  const xhrMapRef = useRef<Map<string, XMLHttpRequest>>(new Map());
  const fileMapRef = useRef<Map<string, { file: File; mimeType: string }>>(new Map());
  const [expandedDocId, setExpandedDocId] = useState<string | null>(null);

  // Cleanup: abort all in-flight uploads on unmount
  useEffect(() => {
    const xhrMap = xhrMapRef.current;
    return () => {
      xhrMap.forEach((xhr) => xhr.abort());
      xhrMap.clear();
    };
  }, []);

  const processUpload = useCallback(
    async (item: UploadItem) => {
      const contentType = item.mimeType || item.file.type || "application/octet-stream";

      // Step 1: Initiate
      dispatch({ type: "SET_STATUS", id: item.id, status: "initiating" });
      const initResult = await initiateUpload(
        slug,
        projectId,
        item.file.name,
        contentType,
        item.file.size
      );

      if (!initResult.success || !initResult.documentId || !initResult.presignedUrl) {
        dispatch({
          type: "SET_STATUS",
          id: item.id,
          status: "error",
          error: initResult.error ?? "Failed to initiate upload.",
        });
        return;
      }

      dispatch({
        type: "SET_INIT_RESULT",
        id: item.id,
        documentId: initResult.documentId,
        presignedUrl: initResult.presignedUrl,
      });

      // Step 2: Upload to S3 via XHR
      dispatch({ type: "SET_STATUS", id: item.id, status: "uploading" });

      const uploadSuccess = await new Promise<boolean>((resolve) => {
        const xhr = new XMLHttpRequest();
        xhrMapRef.current.set(item.id, xhr);

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            dispatch({
              type: "SET_PROGRESS",
              id: item.id,
              progress: Math.round((e.loaded / e.total) * 100),
            });
          }
        };

        xhr.onload = () => {
          xhrMapRef.current.delete(item.id);
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(true);
          } else {
            dispatch({
              type: "SET_STATUS",
              id: item.id,
              status: "error",
              error: "Upload to storage failed.",
            });
            resolve(false);
          }
        };

        xhr.onerror = () => {
          xhrMapRef.current.delete(item.id);
          dispatch({
            type: "SET_STATUS",
            id: item.id,
            status: "error",
            error: "Network error during upload.",
          });
          resolve(false);
        };

        xhr.onabort = () => {
          xhrMapRef.current.delete(item.id);
          resolve(false);
        };

        xhr.open("PUT", initResult.presignedUrl!);
        xhr.setRequestHeader("Content-Type", contentType);
        xhr.send(item.file);
      });

      if (!uploadSuccess) {
        if (initResult.documentId) await cancelUpload(initResult.documentId);
        return;
      }

      // Step 3: Confirm
      dispatch({ type: "SET_STATUS", id: item.id, status: "confirming" });
      const confirmResult = await confirmUpload(slug, projectId, initResult.documentId);

      if (!confirmResult.success) {
        if (initResult.documentId) await cancelUpload(initResult.documentId);
        dispatch({
          type: "SET_STATUS",
          id: item.id,
          status: "error",
          error: confirmResult.error ?? "Failed to confirm upload.",
        });
        return;
      }

      dispatch({ type: "SET_STATUS", id: item.id, status: "complete" });
      router.refresh();
    },
    [slug, projectId, router]
  );

  const handleFilesSelected = useCallback(
    (files: File[]) => {
      const newItems: UploadItem[] = [];

      for (const file of files) {
        const validation = validateFile(file);
        const item: UploadItem = {
          id: crypto.randomUUID(),
          file,
          mimeType: validation.mimeType,
          status: validation.valid ? "validating" : "error",
          progress: 0,
          error: validation.valid ? undefined : validation.error,
        };
        newItems.push(item);
      }

      // Store files in ref for retry access without stale closures
      for (const item of newItems) {
        fileMapRef.current.set(item.id, {
          file: item.file,
          mimeType: item.mimeType || item.file.type || "application/octet-stream",
        });
      }

      dispatch({ type: "ADD", items: newItems });

      // Start uploads for valid files
      for (const item of newItems) {
        if (item.status !== "error") {
          processUpload(item);
        }
      }
    },
    [processUpload]
  );

  const handleRetry = useCallback(
    (id: string) => {
      const entry = fileMapRef.current.get(id);
      if (!entry) return;
      dispatch({
        type: "SET_STATUS",
        id,
        status: "validating",
        error: undefined,
      });
      dispatch({ type: "SET_PROGRESS", id, progress: 0 });
      processUpload({
        id,
        file: entry.file,
        mimeType: entry.mimeType,
        status: "validating",
        progress: 0,
      });
    },
    [processUpload]
  );

  const handleRemove = useCallback((id: string) => {
    const xhr = xhrMapRef.current.get(id);
    if (xhr) {
      xhr.abort();
      xhrMapRef.current.delete(id);
    }
    fileMapRef.current.delete(id);
    dispatch({ type: "REMOVE", id });
  }, []);

  return (
    <div className="space-y-4">
      <h2 className="font-semibold text-olive-900 dark:text-olive-100">Documents</h2>

      {documents.length === 0 && uploads.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No documents yet"
          description="Upload your first file above"
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
                {showScope && (
                  <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                    Scope
                  </TableHead>
                )}
                <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                  Status
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
                const scopeBadge = doc.scope ? SCOPE_BADGE[doc.scope] : null;
                const isExpanded = expandedDocId === doc.id;
                const colSpan = showScope ? 6 : 5;
                return (
                  <Fragment key={doc.id}>
                    <TableRow
                      className={cn(
                        "border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900",
                        isExpanded && "bg-olive-50/50 dark:bg-olive-900/50",
                      )}
                    >
                      <TableCell>
                        <button
                          type="button"
                          className="flex min-w-0 items-center gap-1.5 text-left"
                          onClick={() => setExpandedDocId(isExpanded ? null : doc.id)}
                          aria-expanded={isExpanded}
                          aria-label={`${isExpanded ? "Collapse" : "Expand"} comments for ${doc.fileName}`}
                        >
                          {isExpanded ? (
                            <ChevronDown className="size-3.5 shrink-0 text-olive-400" />
                          ) : (
                            <ChevronRight className="size-3.5 shrink-0 text-olive-400" />
                          )}
                          <Icon className="size-4 shrink-0 text-olive-400 dark:text-olive-500" />
                          <span className="truncate text-sm font-medium text-olive-950 dark:text-olive-50">
                            {doc.fileName}
                          </span>
                        </button>
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">
                        <span className="text-sm text-olive-600 dark:text-olive-400">
                          {formatFileSize(doc.size)}
                        </span>
                      </TableCell>
                      {showScope && (
                        <TableCell>
                          {scopeBadge ? (
                            <Badge variant={scopeBadge.variant}>{scopeBadge.label}</Badge>
                          ) : (
                            <span className="text-sm text-olive-400">{"\u2014"}</span>
                          )}
                        </TableCell>
                      )}
                      <TableCell>
                        <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
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
                    {isExpanded && (
                      <TableRow className="border-olive-100 dark:border-olive-800/50">
                        <TableCell colSpan={colSpan} className="bg-olive-50/30 px-6 py-4 dark:bg-olive-900/30">
                          <CommentSectionClient
                            projectId={projectId}
                            entityType="DOCUMENT"
                            entityId={doc.id}
                            orgSlug={slug}
                            currentMemberId={currentMemberId ?? ""}
                            canManageVisibility={canManageVisibility}
                          />
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      {uploads.length > 0 && (
        <div className="space-y-2">
          {uploads.map((item) => (
            <UploadProgressItem
              key={item.id}
              item={item}
              onRetry={handleRetry}
              onRemove={handleRemove}
            />
          ))}
        </div>
      )}

      <FileUploadZone onFilesSelected={handleFilesSelected} />
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
