"use client";

import { useState } from "react";
import { Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { previewTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/actions";
import type { TemplateEntityType } from "@/lib/types";

interface TemplatePreviewDialogProps {
  templateId: string;
  entityType: TemplateEntityType;
}

export function TemplatePreviewDialog({
  templateId,
  entityType,
}: TemplatePreviewDialogProps) {
  const [open, setOpen] = useState(false);
  const [entityId, setEntityId] = useState("");
  const [html, setHtml] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const entityLabel =
    entityType === "PROJECT"
      ? "Project"
      : entityType === "CUSTOMER"
        ? "Customer"
        : "Invoice";

  async function handlePreview() {
    if (!entityId.trim()) return;
    setIsLoading(true);
    setError(null);
    setHtml(null);

    try {
      const result = await previewTemplateAction(templateId, entityId.trim());
      if (result.success && result.html) {
        setHtml(result.html);
      } else {
        setError(result.error ?? "Failed to generate preview.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" variant="soft" size="sm">
          <Eye className="mr-1 size-4" />
          Preview
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Template Preview</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-end gap-3">
            <div className="flex-1">
              <Label htmlFor="preview-entity-id">{entityLabel} ID</Label>
              <Input
                id="preview-entity-id"
                placeholder={`Enter ${entityLabel.toLowerCase()} UUID...`}
                value={entityId}
                onChange={(e) => setEntityId(e.target.value)}
              />
            </div>
            <Button onClick={handlePreview} disabled={isLoading || !entityId.trim()}>
              {isLoading ? "Generating..." : "Generate Preview"}
            </Button>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          {html && (
            <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
              <iframe
                sandbox=""
                srcDoc={html}
                className="h-[500px] w-full bg-white"
                title="Template Preview"
              />
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
