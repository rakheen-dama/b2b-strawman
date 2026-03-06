"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  Upload,
  FileText,
  Type,
  Check,
  X,
  Clock,
} from "lucide-react";
import { PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { PortalRequestStatusBadge } from "@/components/portal/portal-request-status-badge";
import { PortalRequestProgressBar } from "@/components/portal/portal-request-progress-bar";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { ACCEPT_ATTRIBUTE, validateFile } from "@/lib/upload-validation";
import {
  getPortalRequest,
  initiateUpload,
  submitItem,
} from "@/lib/api/portal-requests";
import type {
  PortalRequestDetail,
  PortalRequestItemDetail,
} from "@/lib/api/portal-requests";

// --- Item status badge ---

function ItemStatusBadge({ status }: { status: PortalRequestItemDetail["status"] }) {
  switch (status) {
    case "PENDING":
      return <Badge variant="neutral">Pending</Badge>;
    case "SUBMITTED":
      return <Badge variant="warning">Submitted</Badge>;
    case "ACCEPTED":
      return <Badge variant="success">Accepted</Badge>;
    case "REJECTED":
      return <Badge variant="destructive">Rejected</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}

// --- Item background color ---

function getItemBgClass(status: string): string {
  switch (status) {
    case "PENDING":
      return "bg-slate-50 dark:bg-slate-900/50";
    case "SUBMITTED":
      return "bg-amber-50 dark:bg-amber-950/30";
    case "ACCEPTED":
      return "bg-green-50 dark:bg-green-950/20";
    case "REJECTED":
      return "bg-red-50 dark:bg-red-950/20";
    default:
      return "";
  }
}

export default function PortalRequestDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [request, setRequest] = useState<PortalRequestDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Upload/submit state
  const [uploadingItemId, setUploadingItemId] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [submittingItemId, setSubmittingItemId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // Text response state per item
  const [textInputs, setTextInputs] = useState<Record<string, string>>({});

  const requestId = params.id;

  const fetchRequest = useCallback(async () => {
    try {
      const data = await getPortalRequest(requestId);
      setRequest(data);
      setError(null);
    } catch (err) {
      if (err instanceof PortalApiError && err.status === 401) {
        clearPortalAuth();
        router.replace("/portal");
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to load request");
    } finally {
      setIsLoading(false);
    }
  }, [requestId, router]);

  useEffect(() => {
    fetchRequest();
  }, [fetchRequest]);

  // --- File upload handler ---

  async function handleFileUpload(itemId: string, file: File) {
    const validation = validateFile(file);
    if (!validation.valid) {
      setActionError(validation.error ?? "Invalid file");
      return;
    }

    setUploadingItemId(itemId);
    setUploadProgress(0);
    setActionError(null);

    try {
      const { documentId, uploadUrl } = await initiateUpload(requestId, itemId, {
        fileName: file.name,
        contentType: file.type || "application/octet-stream",
        size: file.size,
      });

      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            setUploadProgress(Math.round((e.loaded / e.total) * 100));
          }
        };
        xhr.onload = () =>
          xhr.status >= 200 && xhr.status < 300
            ? resolve()
            : reject(new Error("Upload failed"));
        xhr.onerror = () => reject(new Error("Network error"));
        xhr.open("PUT", uploadUrl);
        xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");
        xhr.send(file);
      });

      await submitItem(requestId, itemId, { documentId });
      await fetchRequest();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Upload failed");
    } finally {
      setUploadingItemId(null);
    }
  }

  // --- Text submit handler ---

  async function handleTextSubmit(itemId: string) {
    const text = textInputs[itemId]?.trim();
    if (!text) return;

    setSubmittingItemId(itemId);
    setActionError(null);

    try {
      await submitItem(requestId, itemId, { textResponse: text });
      setTextInputs((prev) => {
        const next = { ...prev };
        delete next[itemId];
        return next;
      });
      await fetchRequest();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Submit failed");
    } finally {
      setSubmittingItemId(null);
    }
  }

  // --- Loading state ---

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  // --- Error state ---

  if (error || !request) {
    return (
      <div className="space-y-4">
        <Link
          href="/portal/requests"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Requests
        </Link>
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {error || "Request not found"}
        </div>
      </div>
    );
  }

  const sortedItems = [...request.items].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          href="/portal/requests"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Requests
        </Link>
      </div>

      {/* Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            {request.requestNumber}
          </h1>
          <PortalRequestStatusBadge status={request.status} />
        </div>
        {request.projectName && (
          <p className="text-sm text-slate-600 dark:text-slate-400">
            {request.projectName}
          </p>
        )}
      </div>

      {/* Progress summary */}
      <div className="rounded-lg border border-slate-200 p-4 dark:border-slate-700">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <PortalRequestProgressBar
            totalItems={request.totalItems}
            acceptedItems={request.acceptedItems}
          />
          <div className="flex gap-4 text-xs text-slate-500 dark:text-slate-400">
            <span>{request.submittedItems} submitted</span>
            <span>{request.acceptedItems} accepted</span>
            {request.rejectedItems > 0 && (
              <span className="text-red-600 dark:text-red-400">
                {request.rejectedItems} rejected
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Action error */}
      {actionError && (
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {actionError}
        </div>
      )}

      {/* Items list */}
      <div className="space-y-3">
        <h2 className="font-display text-lg text-slate-900 dark:text-slate-100">
          Items
        </h2>
        {sortedItems.map((item) => (
          <ItemRow
            key={item.id}
            item={item}
            uploadingItemId={uploadingItemId}
            uploadProgress={uploadProgress}
            submittingItemId={submittingItemId}
            textValue={textInputs[item.id] ?? ""}
            onTextChange={(val) =>
              setTextInputs((prev) => ({ ...prev, [item.id]: val }))
            }
            onFileUpload={(file) => handleFileUpload(item.id, file)}
            onTextSubmit={() => handleTextSubmit(item.id)}
          />
        ))}
      </div>
    </div>
  );
}

// --- Item Row Component ---

interface ItemRowProps {
  item: PortalRequestItemDetail;
  uploadingItemId: string | null;
  uploadProgress: number;
  submittingItemId: string | null;
  textValue: string;
  onTextChange: (val: string) => void;
  onFileUpload: (file: File) => void;
  onTextSubmit: () => void;
}

function ItemRow({
  item,
  uploadingItemId,
  uploadProgress,
  submittingItemId,
  textValue,
  onTextChange,
  onFileUpload,
  onTextSubmit,
}: ItemRowProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const isUploading = uploadingItemId === item.id;
  const isAnotherUploading = uploadingItemId !== null && uploadingItemId !== item.id;
  const isSubmitting = submittingItemId === item.id;
  const canRespond = item.status === "PENDING" || item.status === "REJECTED";

  return (
    <div
      className={cn(
        "rounded-lg border border-slate-200 p-4 dark:border-slate-700",
        getItemBgClass(item.status),
      )}
    >
      {/* Item header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          {item.responseType === "FILE_UPLOAD" ? (
            <FileText className="mt-0.5 size-4 text-slate-500 dark:text-slate-400" />
          ) : (
            <Type className="mt-0.5 size-4 text-slate-500 dark:text-slate-400" />
          )}
          <div>
            <p className="font-medium text-slate-900 dark:text-slate-100">
              {item.name}
            </p>
            {item.description && (
              <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
                {item.description}
              </p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          {item.required && (
            <Badge variant="neutral" className="text-xs">
              Required
            </Badge>
          )}
          <ItemStatusBadge status={item.status} />
        </div>
      </div>

      {/* Status-specific content */}
      <div className="mt-3">
        {/* PENDING or REJECTED: show input controls */}
        {canRespond && (
          <>
            {item.status === "REJECTED" && item.rejectionReason && (
              <div className="mb-3 flex items-start gap-2 rounded-md bg-red-100 px-3 py-2 text-sm text-red-700 dark:bg-red-900/40 dark:text-red-300">
                <X className="mt-0.5 size-4 shrink-0" />
                <span>
                  <strong>Rejected:</strong> {item.rejectionReason}
                </span>
              </div>
            )}

            {item.responseType === "FILE_UPLOAD" && (
              <FileUploadArea
                fileInputRef={fileInputRef}
                isUploading={isUploading}
                disabled={isAnotherUploading}
                uploadProgress={uploadProgress}
                fileTypeHints={item.fileTypeHints}
                onFileSelected={onFileUpload}
              />
            )}

            {item.responseType === "TEXT_RESPONSE" && (
              <div className="space-y-2">
                <Textarea
                  placeholder="Enter your response..."
                  value={textValue}
                  onChange={(e) => onTextChange(e.target.value)}
                  disabled={isSubmitting || isAnotherUploading}
                  rows={3}
                />
                <Button
                  size="sm"
                  onClick={onTextSubmit}
                  disabled={isSubmitting || isAnotherUploading || !textValue.trim()}
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="mr-1.5 size-4 animate-spin" />
                      Submitting...
                    </>
                  ) : (
                    "Submit Response"
                  )}
                </Button>
              </div>
            )}
          </>
        )}

        {/* SUBMITTED: awaiting review */}
        {item.status === "SUBMITTED" && (
          <div className="flex items-center gap-2 text-sm text-amber-700 dark:text-amber-300">
            <Clock className="size-4" />
            <span>Awaiting review</span>
            {item.documentId && (
              <span className="ml-2 text-slate-500 dark:text-slate-400">
                File uploaded
              </span>
            )}
            {item.textResponse && (
              <span className="ml-2 text-slate-500 dark:text-slate-400">
                &quot;{item.textResponse}&quot;
              </span>
            )}
          </div>
        )}

        {/* ACCEPTED: green check + read-only content */}
        {item.status === "ACCEPTED" && (
          <div className="flex items-center gap-2 text-sm text-green-700 dark:text-green-300">
            <Check className="size-4" />
            <span>Accepted</span>
            {item.documentId && (
              <span className="ml-2 text-slate-500 dark:text-slate-400">
                File uploaded
              </span>
            )}
            {item.textResponse && (
              <span className="ml-2 text-slate-500 dark:text-slate-400">
                &quot;{item.textResponse}&quot;
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// --- Inline file upload area ---

interface FileUploadAreaProps {
  fileInputRef: React.RefObject<HTMLInputElement | null>;
  isUploading: boolean;
  disabled?: boolean;
  uploadProgress: number;
  fileTypeHints: string | null;
  onFileSelected: (file: File) => void;
}

function FileUploadArea({
  fileInputRef,
  isUploading,
  disabled = false,
  uploadProgress,
  fileTypeHints,
  onFileSelected,
}: FileUploadAreaProps) {
  const [isDragOver, setIsDragOver] = useState(false);

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
    if (isUploading || disabled) return;
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) {
      onFileSelected(files[0]);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    if (files.length > 0) {
      onFileSelected(files[0]);
    }
    e.target.value = "";
  }

  if (isUploading) {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
          <Loader2 className="size-4 animate-spin" />
          <span>Uploading... {uploadProgress}%</span>
        </div>
        <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            className="h-full rounded-full bg-teal-500 transition-all"
            style={{ width: `${uploadProgress}%` }}
          />
        </div>
      </div>
    );
  }

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      onClick={() => !disabled && fileInputRef.current?.click()}
      onKeyDown={(e) => {
        if (!disabled && (e.key === "Enter" || e.key === " ")) {
          e.preventDefault();
          fileInputRef.current?.click();
        }
      }}
      onDragEnter={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(true);
      }}
      onDragOver={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(true);
      }}
      onDragLeave={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragOver(false);
      }}
      onDrop={handleDrop}
      className={cn(
        "rounded-lg border-2 border-dashed p-4 text-center transition-colors",
        disabled
          ? "cursor-not-allowed opacity-50 border-slate-300 dark:border-slate-600"
          : "cursor-pointer",
        !disabled && isDragOver
          ? "border-teal-500 bg-teal-50 dark:bg-teal-950/20"
          : !disabled
            ? "border-slate-300 hover:border-slate-400 dark:border-slate-600 dark:hover:border-slate-500"
            : "",
      )}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPT_ATTRIBUTE}
        onChange={handleFileChange}
        className="hidden"
      />
      <Upload className="mx-auto size-6 text-slate-400" />
      <p className="mt-1 text-sm font-medium text-slate-700 dark:text-slate-300">
        Drop a file here, or click to browse
      </p>
      {fileTypeHints && (
        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
          Accepted: {fileTypeHints}
        </p>
      )}
    </div>
  );
}
