"use client";

import { useState } from "react";
import Link from "next/link";
import { Copy, Pencil, XCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  duplicateTemplateAction,
  deactivateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/request-templates/actions";

interface RequestTemplateActionsProps {
  slug: string;
  templateId: string;
  templateName: string;
  source: "PLATFORM" | "CUSTOM";
}

export function RequestTemplateActions({
  slug,
  templateId,
  templateName,
  source,
}: RequestTemplateActionsProps) {
  const [duplicating, setDuplicating] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDuplicate() {
    setDuplicating(true);
    setError(null);
    const result = await duplicateTemplateAction(slug, templateId);
    if (!result.success) {
      setError(result.error ?? "Failed to duplicate template.");
    }
    setDuplicating(false);
  }

  async function handleDeactivate() {
    if (
      !window.confirm(
        `Deactivate request template "${templateName}"?`,
      )
    )
      return;
    setDeactivating(true);
    setError(null);
    const result = await deactivateTemplateAction(slug, templateId);
    if (!result.success) {
      setError(result.error ?? "Failed to deactivate template.");
    }
    setDeactivating(false);
  }

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-end gap-1">
        {source === "CUSTOM" && (
          <Link
            href={`/org/${slug}/settings/request-templates/${templateId}`}
          >
            <Button variant="ghost" size="sm">
              <Pencil className="size-4" />
              <span className="sr-only">Edit {templateName}</span>
            </Button>
          </Link>
        )}
        <Button
          variant="ghost"
          size="sm"
          disabled={duplicating}
          onClick={handleDuplicate}
        >
          {duplicating ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Copy className="size-4" />
          )}
          <span className="sr-only">Duplicate {templateName}</span>
        </Button>
        {source === "CUSTOM" && (
          <Button
            variant="ghost"
            size="sm"
            disabled={deactivating}
            onClick={handleDeactivate}
            className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
          >
            {deactivating ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <XCircle className="size-4" />
            )}
            <span className="sr-only">Deactivate {templateName}</span>
          </Button>
        )}
      </div>
      {error && (
        <p className="text-right text-xs text-red-600 dark:text-red-400">
          {error}
        </p>
      )}
    </div>
  );
}
