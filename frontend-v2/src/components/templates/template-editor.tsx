"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { ArrowLeft, Eye, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { TemplateDetailResponse } from "@/lib/types";

interface TemplateEditorProps {
  slug: string;
  template: TemplateDetailResponse;
  canEdit: boolean;
}

export function TemplateEditor({
  slug,
  template,
  canEdit,
}: TemplateEditorProps) {
  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(
    template.description ?? "",
  );
  const [content, setContent] = useState(template.legacyContent ?? "");
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);

  // Render preview in a sandboxed iframe via blob URL to avoid XSS
  const previewSrc = useMemo(() => {
    if (!showPreview) return "";
    const blob = new Blob([content], { type: "text/html" });
    return URL.createObjectURL(blob);
  }, [content, showPreview]);

  async function handleSave() {
    setIsSaving(true);
    setMessage(null);
    try {
      const res = await fetch(`/api/templates/${template.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, description, content }),
      });
      if (res.ok) {
        setMessage("Template saved.");
      } else {
        setMessage("Failed to save template.");
      }
    } catch {
      setMessage("Failed to save template.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/org/${slug}/settings/templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to templates
      </Link>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Editor */}
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="template-name">Name</Label>
            <Input
              id="template-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={!canEdit}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-description">Description</Label>
            <Input
              id="template-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={!canEdit}
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="template-content">Content (HTML)</Label>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowPreview(!showPreview)}
              >
                <Eye className="mr-1.5 size-3.5" />
                {showPreview ? "Hide preview" : "Preview"}
              </Button>
            </div>
            <Textarea
              id="template-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              disabled={!canEdit}
              rows={20}
              className="font-mono text-sm"
            />
          </div>

          {canEdit && (
            <div className="flex items-center gap-4">
              <Button onClick={handleSave} disabled={isSaving} size="sm">
                <Save className="mr-1.5 size-4" />
                {isSaving ? "Saving..." : "Save template"}
              </Button>
              {message && (
                <p
                  className={`text-sm ${
                    message.includes("saved")
                      ? "text-emerald-600"
                      : "text-red-600"
                  }`}
                >
                  {message}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Preview (sandboxed iframe) */}
        {showPreview && (
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <h3 className="mb-4 text-sm font-medium text-slate-500">
              Preview
            </h3>
            <iframe
              src={previewSrc}
              sandbox=""
              title="Template preview"
              className="h-[600px] w-full rounded border border-slate-100"
            />
          </div>
        )}
      </div>
    </div>
  );
}
