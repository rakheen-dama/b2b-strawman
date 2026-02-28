"use client";

import { useState, useCallback } from "react";
// TODO(214B): Re-add requiredContextFields management UI in the settings panel.
// The old TemplateEditorForm had UI for this; intentionally omitted in 214A.
// The save handler preserves existing values so they are not lost.
import Link from "next/link";
import {
  ChevronLeft,
  ChevronDown,
  ChevronUp,
  AlertTriangle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { DocumentEditor } from "@/components/editor/DocumentEditor";
import { TemplatePreviewDialog } from "@/components/templates/TemplatePreviewDialog";
import { updateTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/actions";
import type {
  TemplateDetailResponse,
  TemplateCategory,
  TemplateEntityType,
} from "@/lib/types";

const CATEGORIES: { value: TemplateCategory; label: string }[] = [
  { value: "ENGAGEMENT_LETTER", label: "Engagement Letter" },
  { value: "STATEMENT_OF_WORK", label: "Statement of Work" },
  { value: "COVER_LETTER", label: "Cover Letter" },
  { value: "PROJECT_SUMMARY", label: "Project Summary" },
  { value: "NDA", label: "NDA" },
];

const ENTITY_TYPES: { value: TemplateEntityType; label: string }[] = [
  { value: "PROJECT", label: "Project" },
  { value: "CUSTOMER", label: "Customer" },
  { value: "INVOICE", label: "Invoice" },
];

interface TemplateEditorClientProps {
  slug: string;
  template: TemplateDetailResponse;
  readOnly: boolean;
}

export function TemplateEditorClient({
  slug,
  template,
  readOnly,
}: TemplateEditorClientProps) {
  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(template.description ?? "");
  const category = template.category;
  const entityType = template.primaryEntityType;
  const [css, setCss] = useState(template.css ?? "");
  const [editorContent, setEditorContent] = useState<Record<string, unknown>>(
    template.content,
  );
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [legacyExpanded, setLegacyExpanded] = useState(false);

  const handleEditorUpdate = useCallback(
    (json: Record<string, unknown>) => {
      setEditorContent(json);
    },
    [],
  );

  async function handleSave() {
    setIsSaving(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const result = await updateTemplateAction(slug, template.id, {
        name,
        description: description || undefined,
        content: editorContent,
        css: css || undefined,
        requiredContextFields: template.requiredContextFields,
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

  const hasLegacyContent = template.legacyContent != null;

  return (
    <div className="flex h-full flex-col">
      {/* Top bar */}
      <div className="flex items-center justify-between border-b border-slate-200 pb-4 dark:border-slate-800">
        <div className="flex items-center gap-4">
          <Link
            href={`/org/${slug}/settings/templates`}
            className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
          >
            <ChevronLeft className="size-4" />
            Templates
          </Link>
          {readOnly ? (
            <h1 className="font-display text-lg text-slate-950 dark:text-slate-50">
              {name}
            </h1>
          ) : (
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="h-8 w-64 font-display text-lg"
              aria-label="Template name"
            />
          )}
        </div>
        <div className="flex items-center gap-3">
          <TemplatePreviewDialog
            templateId={template.id}
            entityType={template.primaryEntityType}
          />
          {successMsg && (
            <span className="text-sm text-teal-600">{successMsg}</span>
          )}
          {error && <span className="text-sm text-destructive">{error}</span>}
          {!readOnly && (
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving ? "Saving..." : "Save"}
            </Button>
          )}
        </div>
      </div>

      {/* Legacy content banner */}
      {hasLegacyContent && (
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 size-5 text-amber-600 dark:text-amber-400" />
            <div className="flex-1">
              <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
                Migration needed
              </p>
              <p className="mt-1 text-sm text-amber-700 dark:text-amber-300">
                This template was migrated from legacy HTML content. The
                original HTML is preserved below for reference.
              </p>
              <button
                type="button"
                onClick={() => setLegacyExpanded(!legacyExpanded)}
                className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-amber-800 hover:text-amber-900 dark:text-amber-200 dark:hover:text-amber-100"
              >
                {legacyExpanded ? "Hide" : "Show"} original HTML
                {legacyExpanded ? (
                  <ChevronUp className="size-4" />
                ) : (
                  <ChevronDown className="size-4" />
                )}
              </button>
              {legacyExpanded && (
                <pre className="mt-2 max-h-64 overflow-auto rounded border border-amber-200 bg-white p-3 font-mono text-xs text-slate-800 dark:border-amber-800 dark:bg-slate-900 dark:text-slate-200">
                  {template.legacyContent}
                </pre>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Collapsible settings panel */}
      <div className="mt-4">
        <button
          type="button"
          onClick={() => setSettingsOpen(!settingsOpen)}
          className="inline-flex items-center gap-2 text-sm font-medium text-slate-700 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
          data-testid="settings-toggle"
        >
          {settingsOpen ? (
            <ChevronUp className="size-4" />
          ) : (
            <ChevronDown className="size-4" />
          )}
          Settings
        </button>

        {settingsOpen && (
          <div className="mt-3 space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="settings-name">Name</Label>
                <Input
                  id="settings-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Template name"
                  disabled={readOnly}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="settings-category">Category</Label>
                <Select value={category} disabled>
                  <SelectTrigger id="settings-category">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c.value} value={c.value}>
                        {c.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="settings-entity-type">Entity Type</Label>
                <Select value={entityType} disabled>
                  <SelectTrigger id="settings-entity-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ENTITY_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="settings-description">Description</Label>
                <Textarea
                  id="settings-description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Brief description of this template"
                  rows={2}
                  disabled={readOnly}
                />
              </div>
            </div>

            {/* Advanced section */}
            <div>
              <button
                type="button"
                onClick={() => setAdvancedOpen(!advancedOpen)}
                className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
              >
                {advancedOpen ? (
                  <ChevronUp className="size-4" />
                ) : (
                  <ChevronDown className="size-4" />
                )}
                Advanced
              </button>

              {advancedOpen && (
                <div className="mt-3 space-y-2">
                  <Label htmlFor="settings-css">Custom CSS</Label>
                  <Textarea
                    id="settings-css"
                    value={css}
                    onChange={(e) => setCss(e.target.value)}
                    placeholder="/* Custom styles */"
                    rows={8}
                    className="font-mono text-sm"
                    disabled={readOnly}
                  />
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Document Editor */}
      <div className="mt-4 flex-1">
        <DocumentEditor
          content={editorContent}
          onUpdate={handleEditorUpdate}
          scope="template"
          editable={!readOnly}
          entityType={template.primaryEntityType}
        />
      </div>
    </div>
  );
}
