"use client";

import { useState } from "react";
import Link from "next/link";
import { Copy, Eye, XCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  cloneChecklistTemplate,
  deactivateChecklistTemplate,
} from "@/app/(app)/org/[slug]/settings/checklists/actions";

interface ChecklistTemplateActionsProps {
  slug: string;
  templateId: string;
  templateName: string;
  source: string;
}

export function ChecklistTemplateActions({
  slug,
  templateId,
  templateName,
  source,
}: ChecklistTemplateActionsProps) {
  const [cloning, setCloning] = useState(false);
  const [deactivating, setDeactivating] = useState(false);

  const [error, setError] = useState<string | null>(null);

  async function handleClone() {
    setCloning(true);
    setError(null);
    const result = await cloneChecklistTemplate(slug, templateId);
    if (!result.success) {
      setError(result.error ?? "Failed to clone template.");
    }
    setCloning(false);
  }

  async function handleDeactivate() {
    if (!window.confirm(`Deactivate checklist template "${templateName}"?`)) return;
    setDeactivating(true);
    await deactivateChecklistTemplate(slug, templateId);
    setDeactivating(false);
  }

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-end gap-1">
        <Link href={`/org/${slug}/settings/checklists/${templateId}`}>
          <Button variant="ghost" size="sm">
            <Eye className="size-4" />
            <span className="sr-only">View {templateName}</span>
          </Button>
        </Link>
        <Button variant="ghost" size="sm" disabled={cloning} onClick={handleClone}>
          {cloning ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Copy className="size-4" />
          )}
          <span className="sr-only">Clone {templateName}</span>
        </Button>
        {source !== "PLATFORM" && (
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
        <p className="text-right text-xs text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}
