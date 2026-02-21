"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { TemplateVariableReference } from "@/components/templates/TemplateVariableReference";
import { TemplatePreviewDialog } from "@/components/templates/TemplatePreviewDialog";
import { updateTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/actions";
import type { TemplateDetailResponse } from "@/lib/types";

interface TemplateEditorFormProps {
  slug: string;
  template: TemplateDetailResponse;
  readOnly?: boolean;
}

export function TemplateEditorForm({ slug, template, readOnly }: TemplateEditorFormProps) {
  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(template.description ?? "");
  const [content, setContent] = useState(template.content);
  const [css, setCss] = useState(template.css ?? "");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  async function handleSave() {
    setIsSaving(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const result = await updateTemplateAction(slug, template.id, {
        name,
        description: description || undefined,
        content,
        css: css || undefined,
      });

      if (result.success) {
        setSuccessMsg("Template saved successfully.");
        setTimeout(() => setSuccessMsg(null), 3000);
      } else {
        setError(result.error ?? "Failed to save template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <TemplatePreviewDialog
            templateId={template.id}
            entityType={template.primaryEntityType}
          />
        </div>
        {!readOnly && (
          <div className="flex items-center gap-3">
            {successMsg && (
              <span className="text-sm text-teal-600">{successMsg}</span>
            )}
            {error && <span className="text-sm text-destructive">{error}</span>}
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving ? "Saving..." : "Save Changes"}
            </Button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <div className="space-y-2">
            <Label htmlFor="template-name">Name</Label>
            <Input
              id="template-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Template name"
              disabled={readOnly}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-description">Description</Label>
            <Textarea
              id="template-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of this template"
              rows={2}
              disabled={readOnly}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-content">Content (HTML)</Label>
            <Textarea
              id="template-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="<html>...</html>"
              rows={20}
              className="font-mono text-sm"
              disabled={readOnly}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="template-css">Custom CSS</Label>
            <Textarea
              id="template-css"
              value={css}
              onChange={(e) => setCss(e.target.value)}
              placeholder="/* Custom styles */"
              rows={10}
              className="font-mono text-sm"
              disabled={readOnly}
            />
          </div>
        </div>

        <div className="space-y-4">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-2 text-sm font-semibold text-slate-950 dark:text-slate-50">
              Template Info
            </h3>
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">Category</dt>
                <dd className="text-slate-950 dark:text-slate-50">
                  {template.category.replace(/_/g, " ")}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">Entity Type</dt>
                <dd className="text-slate-950 dark:text-slate-50">
                  {template.primaryEntityType}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">Source</dt>
                <dd className="text-slate-950 dark:text-slate-50">
                  {template.source === "PLATFORM" ? "Platform" : "Custom"}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500 dark:text-slate-400">Slug</dt>
                <dd className="font-mono text-xs text-slate-950 dark:text-slate-50">
                  {template.slug}
                </dd>
              </div>
            </dl>
          </div>

          <TemplateVariableReference entityType={template.primaryEntityType} />
        </div>
      </div>
    </div>
  );
}
