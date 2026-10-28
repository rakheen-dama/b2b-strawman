"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { validateFile } from "@/lib/upload-validation";
import {
  initiateUpload,
  confirmUpload,
  cancelUpload,
} from "@/app/(app)/org/[slug]/matters/[id]/actions";

/** Backend cap (CommentService): a comment carries at most 5 documents. */
export const MAX_COMMENT_ATTACHMENTS = 5;
export const ATTACHMENT_LIMIT_MESSAGE = `A comment can have at most ${MAX_COMMENT_ATTACHMENTS} attachments.`;

export type StagedAttachmentStatus = "uploading" | "ready" | "confirming" | "error";

/**
 * A file chosen in the comment composer. Its bytes are uploaded on drop (upload-init + S3 PUT), so
 * the matter document exists but stays PENDING until the comment is submitted and it is confirmed.
 */
export interface StagedAttachment {
  localId: string;
  fileName: string;
  size: number;
  status: StagedAttachmentStatus;
  progress: number;
  documentId?: string;
  error?: string;
  /** Confirmed (UPLOADED) by an earlier submit attempt: never re-confirmed, never cancelled. */
  confirmed: boolean;
}

export type ConfirmAllResult = { ok: true; documentIds: string[] } | { ok: false; error: string };

/** PUTs a file to a presigned S3 URL. Resolves true on a 2xx, false on failure or abort. */
export function putToPresignedUrl(
  url: string,
  file: File,
  contentType: string,
  onProgress?: (percent: number) => void,
  onStart?: (xhr: XMLHttpRequest) => void
): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && e.total > 0) {
        onProgress?.(Math.round((e.loaded / e.total) * 100));
      }
    };
    xhr.onload = () => resolve(xhr.status >= 200 && xhr.status < 300);
    xhr.onerror = () => resolve(false);
    xhr.onabort = () => resolve(false);
    onStart?.(xhr);
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", contentType);
    xhr.send(file);
  });
}

function hasItem(items: StagedAttachment[], localId: string): boolean {
  return items.some((i) => i.localId === localId);
}

/** Aborts in-flight PUTs and cancels every staged document that was never confirmed. */
function releaseItems(items: StagedAttachment[], xhrs: Map<string, XMLHttpRequest>) {
  xhrs.forEach((xhr) => xhr.abort());
  xhrs.clear();
  for (const item of items) {
    if (item.documentId && !item.confirmed) {
      cancelUpload(item.documentId).catch(() => {});
    }
  }
}

interface UseCommentAttachmentsOptions {
  slug: string;
  /** The comment's matter: every attachment is a PROJECT-scoped document on it. */
  projectId: string;
  /** Attachments already on the comment (edit dialog) that count towards the cap. */
  existingCount?: number;
}

export function useCommentAttachments({
  slug,
  projectId,
  existingCount = 0,
}: UseCommentAttachmentsOptions) {
  const [items, setItems] = useState<StagedAttachment[]>([]);
  const [limitMessage, setLimitMessage] = useState<string | null>(null);
  // Mirror of `items` that is current synchronously: async upload steps and the unmount cleanup
  // read it, so they never act on a stale closure.
  const itemsRef = useRef<StagedAttachment[]>([]);
  const xhrRef = useRef<Map<string, XMLHttpRequest>>(new Map());

  const commit = useCallback((next: (prev: StagedAttachment[]) => StagedAttachment[]) => {
    itemsRef.current = next(itemsRef.current);
    setItems(itemsRef.current);
  }, []);

  const patch = useCallback(
    (localId: string, changes: Partial<StagedAttachment>) =>
      commit((prev) => prev.map((i) => (i.localId === localId ? { ...i, ...changes } : i))),
    [commit]
  );

  // On unmount, release whatever is still staged (read through the ref, not this closure).
  useEffect(() => {
    const store = itemsRef;
    const xhrs = xhrRef.current;
    return () => {
      const pending = store.current;
      store.current = [];
      releaseItems(pending, xhrs);
    };
  }, []);

  const upload = useCallback(
    async (localId: string, file: File, contentType: string) => {
      const init = await initiateUpload(slug, projectId, file.name, contentType, file.size);
      if (!init.success || !init.documentId || !init.presignedUrl) {
        if (hasItem(itemsRef.current, localId)) {
          patch(localId, { status: "error", error: init.error ?? "Failed to start the upload." });
        }
        return;
      }
      const documentId = init.documentId;
      if (!hasItem(itemsRef.current, localId)) {
        // Removed (or the form went away) while upload-init was in flight.
        cancelUpload(documentId).catch(() => {});
        return;
      }
      patch(localId, { documentId });

      const ok = await putToPresignedUrl(
        init.presignedUrl,
        file,
        contentType,
        (progress) => {
          if (hasItem(itemsRef.current, localId)) patch(localId, { progress });
        },
        (xhr) => xhrRef.current.set(localId, xhr)
      );
      xhrRef.current.delete(localId);

      // Removal already aborted the PUT and cancelled the pending document.
      if (!hasItem(itemsRef.current, localId)) return;

      if (!ok) {
        cancelUpload(documentId).catch(() => {});
        patch(localId, {
          status: "error",
          error: "Upload to storage failed.",
          documentId: undefined,
          progress: 0,
        });
        return;
      }
      patch(localId, { status: "ready", progress: 100 });
    },
    [slug, projectId, patch]
  );

  const addFiles = useCallback(
    (files: File[]) => {
      const slots = Math.max(0, MAX_COMMENT_ATTACHMENTS - existingCount - itemsRef.current.length);
      const accepted = files.slice(0, slots);
      setLimitMessage(files.length > accepted.length ? ATTACHMENT_LIMIT_MESSAGE : null);
      if (accepted.length === 0) return;

      const created = accepted.map((file) => {
        const validation = validateFile(file);
        const item: StagedAttachment = {
          localId: crypto.randomUUID(),
          fileName: file.name,
          size: file.size,
          status: validation.valid ? "uploading" : "error",
          progress: 0,
          error: validation.valid ? undefined : validation.error,
          confirmed: false,
        };
        const contentType = validation.mimeType || file.type || "application/octet-stream";
        return { file, item, contentType };
      });

      commit((prev) => [...prev, ...created.map((c) => c.item)]);
      for (const { file, item, contentType } of created) {
        if (item.status === "uploading") void upload(item.localId, file, contentType);
      }
    },
    [existingCount, commit, upload]
  );

  const remove = useCallback(
    (localId: string) => {
      const item = itemsRef.current.find((i) => i.localId === localId);
      if (!item) return;
      commit((prev) => prev.filter((i) => i.localId !== localId));
      xhrRef.current.get(localId)?.abort();
      xhrRef.current.delete(localId);
      // A confirmed document is a normal UPLOADED matter document now; cancel would 409.
      if (item.documentId && !item.confirmed) {
        cancelUpload(item.documentId).catch(() => {});
      }
      setLimitMessage(null);
    },
    [commit]
  );

  /** Confirms every not-yet-confirmed staged document, in order; returns their ids. */
  const confirmAll = useCallback(async (): Promise<ConfirmAllResult> => {
    const documentIds: string[] = [];
    for (const item of itemsRef.current) {
      if (item.status === "uploading" || item.status === "confirming") {
        return { ok: false, error: "Wait for the attachments to finish uploading." };
      }
      if (item.status === "error" || !item.documentId) {
        return { ok: false, error: "Remove failed attachments to continue." };
      }
      if (!item.confirmed) {
        patch(item.localId, { status: "confirming" });
        const result = await confirmUpload(slug, projectId, item.documentId);
        if (!result.success) {
          const error = result.error ?? "Failed to finish the upload.";
          patch(item.localId, { status: "error", error });
          return { ok: false, error: `${item.fileName}: ${error}` };
        }
        patch(item.localId, { status: "ready", confirmed: true });
      }
      documentIds.push(item.documentId);
    }
    return { ok: true, documentIds };
  }, [slug, projectId, patch]);

  /** Marks the staged chip holding `documentId` as rejected. Returns false if none matches. */
  const markError = useCallback(
    (documentId: string, error: string): boolean => {
      const item = itemsRef.current.find((i) => i.documentId === documentId);
      if (!item) return false;
      patch(item.localId, { status: "error", error });
      return true;
    },
    [patch]
  );

  /** Clears the staged list without cancelling anything (the documents were attached). */
  const reset = useCallback(() => {
    xhrRef.current.clear();
    commit(() => []);
    setLimitMessage(null);
  }, [commit]);

  /** Abandons the staged list: aborts PUTs and cancels every unconfirmed pending document. */
  const releaseStaged = useCallback(() => {
    const pending = itemsRef.current;
    commit(() => []);
    releaseItems(pending, xhrRef.current);
    setLimitMessage(null);
  }, [commit]);

  const isBusy = items.some((i) => i.status === "uploading" || i.status === "confirming");
  const hasErrors = items.some((i) => i.status === "error");
  const atLimit = existingCount + items.length >= MAX_COMMENT_ATTACHMENTS;

  return {
    items,
    limitMessage,
    isBusy,
    hasErrors,
    atLimit,
    addFiles,
    remove,
    confirmAll,
    markError,
    reset,
    releaseStaged,
  };
}

export type CommentAttachmentsState = ReturnType<typeof useCommentAttachments>;

/** Replaces the raw document UUID in a backend attachment error with the chip's file name. */
export function nameAttachmentInError(message: string, documentId: string, fileName: string) {
  return message.split(documentId).join(fileName);
}

/** Chip label for a staged document the backend refused to attach. */
export const ATTACHMENT_REJECTED_LABEL = "Not attached";
