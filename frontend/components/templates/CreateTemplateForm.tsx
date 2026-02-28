"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { createTemplateAction } from "@/app/(app)/org/[slug]/settings/templates/actions";
import type { TemplateCategory, TemplateEntityType } from "@/lib/types";

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

interface CreateTemplateFormProps {
  slug: string;
}

export function CreateTemplateForm({ slug }: CreateTemplateFormProps) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<TemplateCategory>("ENGAGEMENT_LETTER");
  const [entityType, setEntityType] = useState<TemplateEntityType>("PROJECT");
  const [contentText, setContentText] = useState("");
  const [css, setCss] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleCreate() {
    if (!name.trim() || !contentText.trim()) return;

    setIsCreating(true);
    setError(null);

    try {
      const result = await createTemplateAction(slug, {
        name: name.trim(),
        description: description.trim() || undefined,
        category,
        primaryEntityType: entityType,
        content: {
          type: "doc",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: contentText }],
            },
          ],
        },
        css: css.trim() || undefined,
      });

      if (result.success && result.data) {
        router.push(`/org/${slug}/settings/templates/${result.data.id}/edit`);
      } else {
        setError(result.error ?? "Failed to create template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="new-template-name">Name</Label>
              <Input
                id="new-template-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Template name"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="new-template-category">Category</Label>
              <select
                id="new-template-category"
                value={category}
                onChange={(e) =>
                  setCategory(e.target.value as TemplateCategory)
                }
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
              >
                {CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>
                    {c.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="new-template-entity-type">Primary Entity Type</Label>
              <select
                id="new-template-entity-type"
                value={entityType}
                onChange={(e) =>
                  setEntityType(e.target.value as TemplateEntityType)
                }
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
              >
                {ENTITY_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="new-template-description">Description</Label>
              <Input
                id="new-template-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Brief description"
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="new-template-content">Content</Label>
            <Textarea
              id="new-template-content"
              value={contentText}
              onChange={(e) => setContentText(e.target.value)}
              placeholder="<html>...</html>"
              rows={20}
              className="font-mono text-sm"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="new-template-css">Custom CSS</Label>
            <Textarea
              id="new-template-css"
              value={css}
              onChange={(e) => setCss(e.target.value)}
              placeholder="/* Custom styles */"
              rows={10}
              className="font-mono text-sm"
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex justify-end gap-3">
            <Button
              variant="soft"
              onClick={() => router.push(`/org/${slug}/settings/templates`)}
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreate}
              disabled={isCreating || !name.trim() || !contentText.trim()}
            >
              {isCreating ? "Creating..." : "Create Template"}
            </Button>
          </div>
        </div>

        <div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900">
            <h3 className="mb-2 text-sm font-semibold text-slate-950 dark:text-slate-50">
              Template Variables
            </h3>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Variables will be available in the editor after the template is created.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
