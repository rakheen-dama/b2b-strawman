"use client";

import { useEffect, useRef, useState } from "react";
import { AlertCircle, Check, Loader2, Paperclip, X } from "lucide-react";
import { getDownloadUrl } from "@/app/(app)/org/[slug]/matters/[id]/actions";
import { formatFileSize } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { StagedAttachmentStatus } from "@/components/comments/use-comment-attachments";

interface ComposerChipProps {
  /** A file in the comment composer (staged upload or an attachment kept while editing). */
  mode: "composer";
  fileName: string;
  size: number;
  status: StagedAttachmentStatus;
  progress?: number;
  error?: string;
  onRemove: () => void;
  removeDisabled?: boolean;
}

interface PostedChipProps {
  /** An attachment on a posted comment: opens the document through a presigned download. */
  mode: "posted";
  documentId: string;
  fileName: string;
  size: number;
}

export type CommentAttachmentChipProps = ComposerChipProps | PostedChipProps;

const CHIP_BASE =
  "inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs";
const CHIP_NEUTRAL =
  "border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";

export function CommentAttachmentChip(props: CommentAttachmentChipProps) {
  return props.mode === "posted" ? <PostedChip {...props} /> : <ComposerChip {...props} />;
}

function ComposerChip({
  fileName,
  size,
  status,
  progress = 0,
  error,
  onRemove,
  removeDisabled = false,
}: ComposerChipProps) {
  const isError = status === "error";
  return (
    <span
      className={cn(
        CHIP_BASE,
        isError
          ? "border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300"
          : CHIP_NEUTRAL
      )}
    >
      {isError ? (
        <AlertCircle className="size-3 shrink-0" aria-hidden="true" />
      ) : (
        <Paperclip className="size-3 shrink-0 text-slate-400" aria-hidden="true" />
      )}
      <span className="truncate font-medium">{fileName}</span>
      <span className="shrink-0 text-slate-500 dark:text-slate-400">{formatFileSize(size)}</span>
      {status === "uploading" && (
        <span className="inline-flex shrink-0 items-center gap-1 text-slate-500" role="status">
          <Loader2 className="size-3 animate-spin" aria-hidden="true" />
          {progress}%
        </span>
      )}
      {status === "confirming" && (
        <Loader2 className="size-3 shrink-0 animate-spin text-slate-500" aria-label="Finishing" />
      )}
      {status === "ready" && (
        <Check className="size-3 shrink-0 text-teal-600 dark:text-teal-400" aria-label="Ready" />
      )}
      {isError && error && <span className="truncate">{error}</span>}
      <button
        type="button"
        onClick={onRemove}
        disabled={removeDisabled}
        aria-label={`Remove ${fileName}`}
        className="shrink-0 rounded-full p-0.5 text-slate-500 hover:bg-slate-200 hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-slate-800 dark:hover:text-slate-100"
      >
        <X className="size-3" aria-hidden="true" />
      </button>
    </span>
  );
}

function PostedChip({ documentId, fileName, size }: PostedChipProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const errorTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (errorTimer.current) clearTimeout(errorTimer.current);
    };
  }, []);

  async function handleDownload() {
    setIsLoading(true);
    setError(null);
    const result = await getDownloadUrl(documentId);
    setIsLoading(false);

    if (!result.success || !result.presignedUrl) {
      setError(result.error ?? "Download failed.");
      if (errorTimer.current) clearTimeout(errorTimer.current);
      errorTimer.current = setTimeout(() => setError(null), 5000);
      return;
    }

    const a = document.createElement("a");
    a.href = result.presignedUrl;
    a.download = fileName;
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  return (
    <span className="inline-flex max-w-full items-center gap-1.5">
      <button
        type="button"
        onClick={handleDownload}
        disabled={isLoading}
        aria-label={`Download ${fileName}`}
        className={cn(
          CHIP_BASE,
          CHIP_NEUTRAL,
          "hover:bg-slate-100 disabled:opacity-60 dark:hover:bg-slate-800"
        )}
      >
        {isLoading ? (
          <Loader2 className="size-3 shrink-0 animate-spin" aria-hidden="true" />
        ) : (
          <Paperclip className="size-3 shrink-0 text-slate-400" aria-hidden="true" />
        )}
        <span className="truncate font-medium">{fileName}</span>
        <span className="shrink-0 text-slate-500 dark:text-slate-400">{formatFileSize(size)}</span>
      </button>
      {error && <span className="text-destructive text-xs">{error}</span>}
    </span>
  );
}
