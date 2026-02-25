"use client";

import { useState } from "react";
import { X } from "lucide-react";
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
  const [requiredFields, setRequiredFields] = useState<Array<{ entity: string; field: string }>>(
    template.requiredContextFields ?? [],
  );
  const [newEntity, setNewEntity] = useState("project");
  const [newField, setNewField] = useState("");
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
        requiredContextFields: requiredFields.length > 0 ? requiredFields : null,
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

          {!readOnly && (
            <div className="space-y-3">
              <Label>Required Context Fields</Label>
              <div className="flex items-end gap-2">
                <div className="space-y-1">
                  <span className="text-xs text-slate-500">Entity</span>
                  <select
                    value={newEntity}
                    onChange={(e) => setNewEntity(e.target.value)}
                    className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
                    aria-label="Entity type"
                  >
                    <option value="customer">customer</option>
                    <option value="project">project</option>
                    <option value="task">task</option>
                    <option value="invoice">invoice</option>
                    <option value="org">org</option>
                  </select>
                </div>
                <div className="flex-1 space-y-1">
                  <span className="text-xs text-slate-500">Field slug</span>
                  <Input
                    value={newField}
                    onChange={(e) => setNewField(e.target.value)}
                    placeholder="e.g. name"
                    aria-label="Field slug"
                  />
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    if (!newField.trim()) return;
                    const exists = requiredFields.some(
                      (f) => f.entity === newEntity && f.field === newField.trim(),
                    );
                    if (!exists) {
                      setRequiredFields((prev) => [...prev, { entity: newEntity, field: newField.trim() }]);
                    }
                    setNewField("");
                  }}
                  disabled={!newField.trim()}
                >
                  Add
                </Button>
              </div>
              {requiredFields.length > 0 && (
                <ul className="space-y-1">
                  {requiredFields.map((rf, idx) => (
                    <li
                      key={`${rf.entity}-${rf.field}-${idx}`}
                      className="flex items-center justify-between rounded border border-slate-200 px-3 py-1.5 text-sm dark:border-slate-800"
                    >
                      <code className="text-xs text-teal-600 dark:text-teal-400">
                        {rf.entity}.{rf.field}
                      </code>
                      <button
                        type="button"
                        onClick={() => setRequiredFields((prev) => prev.filter((_, i) => i !== idx))}
                        className="text-slate-400 hover:text-red-600 dark:hover:text-red-400"
                        aria-label={`Remove ${rf.entity}.${rf.field}`}
                      >
                        <X className="size-3.5" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
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
