"use client";

import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useRouter } from "next/navigation";
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
import type { Document, DocumentStatus } from "@/lib/types";

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
  { label: string; variant: "secondary" | "outline" | "destructive" }
> = {
  UPLOADED: { label: "Uploaded", variant: "secondary" },
  PENDING: { label: "Pending", variant: "outline" },
  FAILED: { label: "Failed", variant: "destructive" },
};

// --- Component ---

interface DocumentsPanelProps {
  documents: Document[];
  projectId: string;
  slug: string;
}

export function DocumentsPanel({ documents, projectId, slug }: DocumentsPanelProps) {
  const router = useRouter();
  const [uploads, dispatch] = useReducer(uploadReducer, []);
  const xhrMapRef = useRef<Map<string, XMLHttpRequest>>(new Map());
  const fileMapRef = useRef<Map<string, { file: File; mimeType: string }>>(new Map());

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
      <h2 className="text-lg font-semibold">Documents</h2>

      {documents.length === 0 && uploads.length === 0 ? (
        <div className="rounded-lg border border-dashed p-8">
          <div className="flex flex-col items-center text-center">
            <FileText className="text-muted-foreground size-10" />
            <p className="mt-3 text-sm font-medium">No documents yet</p>
            <p className="text-muted-foreground mt-1 text-xs">Upload files to get started</p>
          </div>
        </div>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>File</TableHead>
                <TableHead className="hidden sm:table-cell">Size</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="hidden sm:table-cell">Uploaded</TableHead>
                <TableHead className="w-[70px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => {
                const Icon = getFileIcon(doc.contentType);
                const badge = STATUS_BADGE[doc.status];
                return (
                  <TableRow key={doc.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Icon className="text-muted-foreground size-4 shrink-0" />
                        <span className="truncate text-sm">{doc.fileName}</span>
                      </div>
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <span className="text-muted-foreground text-sm">
                        {formatFileSize(doc.size)}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant={badge.variant}>{badge.label}</Badge>
                    </TableCell>
                    <TableCell className="hidden sm:table-cell">
                      <span className="text-muted-foreground text-sm">
                        {doc.uploadedAt ? formatDate(doc.uploadedAt) : "—"}
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
