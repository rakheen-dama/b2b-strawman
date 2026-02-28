"use client";

import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { FileUploadZone } from "@/components/documents/file-upload-zone";
import {
  UploadProgressItem,
  type UploadItem,
  type UploadStatus,
} from "@/components/documents/upload-progress-item";
import { validateFile } from "@/lib/upload-validation";
import {
  initiateOrgUpload,
  confirmOrgUpload,
  cancelOrgUpload,
} from "@/app/(app)/org/[slug]/documents/actions";

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

function uploadReducer(
  state: UploadItem[],
  action: UploadAction
): UploadItem[] {
  switch (action.type) {
    case "ADD":
      return [...state, ...action.items];
    case "SET_STATUS":
      return state.map((item) =>
        item.id === action.id
          ? { ...item, status: action.status, error: action.error }
          : item
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

// --- Component ---

interface DocumentUploadDialogProps {
  slug: string;
}

export function DocumentUploadDialog({ slug }: DocumentUploadDialogProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [uploads, dispatch] = useReducer(uploadReducer, []);
  const xhrMapRef = useRef<Map<string, XMLHttpRequest>>(new Map());
  const fileMapRef = useRef<Map<string, { file: File; mimeType: string }>>(
    new Map()
  );

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
      const contentType =
        item.mimeType || item.file.type || "application/octet-stream";

      // Step 1: Initiate
      dispatch({ type: "SET_STATUS", id: item.id, status: "initiating" });
      const initResult = await initiateOrgUpload(
        slug,
        item.file.name,
        contentType,
        item.file.size
      );

      if (
        !initResult.success ||
        !initResult.documentId ||
        !initResult.presignedUrl
      ) {
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
        if (initResult.documentId) await cancelOrgUpload(initResult.documentId);
        return;
      }

      // Step 3: Confirm
      dispatch({ type: "SET_STATUS", id: item.id, status: "confirming" });
      const confirmResult = await confirmOrgUpload(slug, initResult.documentId);

      if (!confirmResult.success) {
        if (initResult.documentId) await cancelOrgUpload(initResult.documentId);
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
    [slug, router]
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

      // Store files in ref for retry access
      for (const item of newItems) {
        fileMapRef.current.set(item.id, {
          file: item.file,
          mimeType:
            item.mimeType || item.file.type || "application/octet-stream",
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
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Upload className="size-4" />
          Upload
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Upload Document</DialogTitle>
          <DialogDescription>
            Upload files to the organization document library.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <FileUploadZone onFilesSelected={handleFilesSelected} />
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
        </div>
      </DialogContent>
    </Dialog>
  );
}
