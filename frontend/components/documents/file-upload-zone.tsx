"use client";

import { useCallback, useRef, useState } from "react";
import { Upload } from "lucide-react";
import { cn } from "@/lib/utils";
import { ACCEPT_ATTRIBUTE } from "@/lib/upload-validation";

interface FileUploadZoneProps {
  onFilesSelected: (files: File[]) => void;
  disabled?: boolean;
  /** Single-line, low-height variant for use inside forms (e.g. the comment composer). */
  compact?: boolean;
  /** Overrides the primary prompt text. */
  label?: string;
}

const DEFAULT_LABEL = "Drag and drop files here, or click to browse";

export function FileUploadZone({
  onFilesSelected,
  disabled = false,
  compact = false,
  label = DEFAULT_LABEL,
}: FileUploadZoneProps) {
  const [isDragOver, setIsDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) setIsDragOver(true);
    },
    [disabled]
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) setIsDragOver(true);
    },
    [disabled]
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);
      if (disabled) return;

      const files = Array.from(e.dataTransfer.files);
      if (files.length > 0) {
        onFilesSelected(files);
      }
    },
    [disabled, onFilesSelected]
  );

  const handleClick = useCallback(
    (e?: React.MouseEvent) => {
      // The hidden input's own click bubbles back up here; re-clicking it would recurse.
      if (e && e.target === inputRef.current) return;
      if (!disabled) inputRef.current?.click();
    },
    [disabled]
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files ?? []);
      if (files.length > 0) {
        onFilesSelected(files);
      }
      // Reset so the same file can be re-selected
      e.target.value = "";
    },
    [onFilesSelected]
  );

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled || undefined}
      onClick={handleClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          handleClick();
        }
      }}
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={cn(
        "cursor-pointer rounded-lg border-2 border-dashed",
        compact ? "px-3 py-2" : "p-6",
        "text-center transition-colors",
        isDragOver
          ? "border-primary bg-primary/5"
          : "border-muted-foreground/25 hover:border-muted-foreground/50",
        disabled && "cursor-not-allowed opacity-50"
      )}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={ACCEPT_ATTRIBUTE}
        onChange={handleInputChange}
        className="hidden"
        disabled={disabled}
      />
      {compact ? (
        <p className="text-muted-foreground flex items-center justify-center gap-2 text-sm">
          <Upload className="size-4 shrink-0" />
          {label}
        </p>
      ) : (
        <>
          <Upload className="text-muted-foreground mx-auto size-8" />
          <p className="mt-2 text-sm font-medium">{label}</p>
          <p className="text-muted-foreground mt-1 text-xs">Max 100 MB per file</p>
        </>
      )}
    </div>
  );
}
