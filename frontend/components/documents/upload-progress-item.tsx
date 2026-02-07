"use client";

import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { formatFileSize } from "@/lib/format";
import { CheckCircle2, AlertCircle, Loader2, X, RotateCcw } from "lucide-react";

export type UploadStatus =
  | "validating"
  | "initiating"
  | "uploading"
  | "confirming"
  | "complete"
  | "error";

export interface UploadItem {
  id: string;
  file: File;
  mimeType?: string;
  status: UploadStatus;
  progress: number;
  error?: string;
  documentId?: string;
  presignedUrl?: string;
}

interface UploadProgressItemProps {
  item: UploadItem;
  onRetry: (id: string) => void;
  onRemove: (id: string) => void;
}

const STATUS_LABELS: Record<UploadStatus, string> = {
  validating: "Validating...",
  initiating: "Preparing...",
  uploading: "Uploading...",
  confirming: "Finalizing...",
  complete: "Complete",
  error: "Failed",
};

export function UploadProgressItem({ item, onRetry, onRemove }: UploadProgressItemProps) {
  const isRetryable =
    item.status === "error" &&
    !item.error?.includes("not supported") &&
    !item.error?.includes("empty") &&
    !item.error?.includes("maximum size");

  return (
    <div className="flex items-center gap-3 rounded-md border px-3 py-2">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          {item.status === "complete" && (
            <CheckCircle2 className="size-4 shrink-0 text-emerald-600" />
          )}
          {item.status === "error" && <AlertCircle className="text-destructive size-4 shrink-0" />}
          {item.status !== "complete" && item.status !== "error" && (
            <Loader2 className="text-muted-foreground size-4 shrink-0 animate-spin" />
          )}
          <span className="truncate text-sm font-medium">{item.file.name}</span>
          <span className="text-muted-foreground shrink-0 text-xs">
            {formatFileSize(item.file.size)}
          </span>
        </div>

        {item.status === "uploading" && (
          <div className="mt-1.5 flex items-center gap-2">
            <Progress value={item.progress} className="h-1.5" />
            <span className="text-muted-foreground shrink-0 text-xs">{item.progress}%</span>
          </div>
        )}

        {(item.status === "initiating" || item.status === "confirming") && (
          <Progress className="mt-1.5 h-1.5" />
        )}

        {item.status === "error" && item.error && (
          <p className="text-destructive mt-1 text-xs">{item.error}</p>
        )}

        {item.status !== "error" && item.status !== "complete" && item.status !== "uploading" && (
          <p className="text-muted-foreground mt-1 text-xs">{STATUS_LABELS[item.status]}</p>
        )}
      </div>

      <div className="flex shrink-0 gap-1">
        {isRetryable && (
          <Button variant="ghost" size="icon" className="size-7" onClick={() => onRetry(item.id)}>
            <RotateCcw className="size-3.5" />
            <span className="sr-only">Retry</span>
          </Button>
        )}
        {(item.status === "complete" || item.status === "error") && (
          <Button variant="ghost" size="icon" className="size-7" onClick={() => onRemove(item.id)}>
            <X className="size-3.5" />
            <span className="sr-only">Dismiss</span>
          </Button>
        )}
      </div>
    </div>
  );
}
