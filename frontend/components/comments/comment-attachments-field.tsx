"use client";

import { FileUploadZone } from "@/components/documents/file-upload-zone";
import { CommentAttachmentChip } from "@/components/comments/comment-attachment-chip";
import type { CommentAttachmentsState } from "@/components/comments/use-comment-attachments";
import type { CommentAttachment } from "@/lib/types";

// Must stay clear of /send|post|submit|comment/i: the zone is a role="button" element and the
// legal-lifecycle E2E clicks the first button matching that pattern on the matter page.
const DROPZONE_LABEL = "Attach files (drop or click to browse)";

const SHARED_ATTACHMENTS_HINT =
  "Attachments on a client-visible comment are shared with the client.";

interface CommentAttachmentsFieldProps {
  attachments: CommentAttachmentsState;
  /** Attachments already on the comment (edit dialog only). */
  kept?: CommentAttachment[];
  onRemoveKept?: (documentId: string) => void;
  disabled?: boolean;
  /** True when the comment is set to client-visible (SHARED). */
  shared?: boolean;
}

export function CommentAttachmentsField({
  attachments,
  kept = [],
  onRemoveKept,
  disabled = false,
  shared = false,
}: CommentAttachmentsFieldProps) {
  const { items, limitMessage, hasErrors, atLimit, addFiles, remove } = attachments;
  const hasChips = kept.length > 0 || items.length > 0;

  return (
    <div className="space-y-2">
      {hasChips && (
        <ul aria-label="Selected attachments" className="flex flex-wrap gap-2">
          {kept.map((attachment) => (
            <li key={attachment.documentId} className="max-w-full">
              <CommentAttachmentChip
                mode="composer"
                fileName={attachment.fileName}
                size={attachment.size}
                status="ready"
                onRemove={() => onRemoveKept?.(attachment.documentId)}
                removeDisabled={disabled}
              />
            </li>
          ))}
          {items.map((item) => (
            <li key={item.localId} className="max-w-full">
              <CommentAttachmentChip
                mode="composer"
                fileName={item.fileName}
                size={item.size}
                status={item.status}
                progress={item.progress}
                error={item.error}
                onRemove={() => remove(item.localId)}
                removeDisabled={disabled}
              />
            </li>
          ))}
        </ul>
      )}

      <FileUploadZone
        compact
        label={DROPZONE_LABEL}
        onFilesSelected={addFiles}
        disabled={disabled || atLimit}
      />

      {limitMessage && <p className="text-xs text-slate-600 dark:text-slate-400">{limitMessage}</p>}
      {shared && hasChips && (
        <p className="text-xs text-slate-600 dark:text-slate-400">{SHARED_ATTACHMENTS_HINT}</p>
      )}
      {hasErrors && (
        <p className="text-destructive text-xs">Remove failed attachments to continue.</p>
      )}
    </div>
  );
}
