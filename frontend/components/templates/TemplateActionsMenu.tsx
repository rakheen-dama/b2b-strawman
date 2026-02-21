"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal, Copy, Eye, Pencil, RotateCcw, Power } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ResetTemplateDialog } from "@/components/templates/ResetTemplateDialog";
import {
  cloneTemplateAction,
  deactivateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/templates/actions";
import type { TemplateListResponse } from "@/lib/types";

interface TemplateActionsMenuProps {
  slug: string;
  template: TemplateListResponse;
}

export function TemplateActionsMenu({
  slug,
  template,
}: TemplateActionsMenuProps) {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClone() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await cloneTemplateAction(slug, template.id);
      if (result.success && result.data) {
        router.push(`/org/${slug}/settings/templates/${result.data.id}/edit`);
      } else if (!result.success) {
        setError(result.error ?? "Failed to clone template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleDeactivate() {
    setIsLoading(true);
    setError(null);
    try {
      const result = await deactivateTemplateAction(slug, template.id);
      if (!result.success) {
        setError(result.error ?? "Failed to deactivate template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  const isPlatform = template.source === "PLATFORM";
  const isCustom = template.source === "ORG_CUSTOM";
  const hasSource = !!template.sourceTemplateId;

  return (
    <div className="flex items-center gap-2">
      {error && (
        <span className="text-xs text-destructive">{error}</span>
      )}
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="plain"
          size="icon"
          className="size-8"
          disabled={isLoading}
        >
          <MoreHorizontal className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {isCustom && (
          <DropdownMenuItem
            onClick={() =>
              router.push(`/org/${slug}/settings/templates/${template.id}/edit`)
            }
          >
            <Pencil className="mr-2 size-4" />
            Edit
          </DropdownMenuItem>
        )}

        {isPlatform && (
          <DropdownMenuItem
            onClick={() =>
              router.push(`/org/${slug}/settings/templates/${template.id}/edit`)
            }
          >
            <Eye className="mr-2 size-4" />
            View
          </DropdownMenuItem>
        )}

        {isPlatform && (
          <DropdownMenuItem onClick={handleClone} disabled={isLoading}>
            <Copy className="mr-2 size-4" />
            Clone & Customize
          </DropdownMenuItem>
        )}

        {isCustom && hasSource && (
          <ResetTemplateDialog
            slug={slug}
            templateId={template.id}
            templateName={template.name}
          >
            <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
              <RotateCcw className="mr-2 size-4" />
              Reset to Default
            </DropdownMenuItem>
          </ResetTemplateDialog>
        )}

        {template.active && (
          <DropdownMenuItem onClick={handleDeactivate} disabled={isLoading}>
            <Power className="mr-2 size-4" />
            Deactivate
          </DropdownMenuItem>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
    </div>
  );
}
