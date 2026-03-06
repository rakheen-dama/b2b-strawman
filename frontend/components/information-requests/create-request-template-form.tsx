"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { createTemplateAction } from "@/app/(app)/org/[slug]/settings/request-templates/actions";

interface CreateRequestTemplateFormProps {
  slug: string;
}

export function CreateRequestTemplateForm({
  slug,
}: CreateRequestTemplateFormProps) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    if (!name.trim()) return;

    setIsSaving(true);
    setError(null);

    try {
      const result = await createTemplateAction(slug, {
        name: name.trim(),
        description: description.trim() || undefined,
      });
      if (result.success && result.data) {
        router.push(
          `/org/${slug}/settings/request-templates/${result.data.id}`,
        );
      } else {
        setError(result.error ?? "Failed to create request template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="template-name">Name</Label>
        <Input
          id="template-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g. Annual Tax Return Documents"
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="template-description">Description</Label>
        <Textarea
          id="template-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Brief description of what this template is used for"
          rows={3}
        />
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex justify-end gap-3">
        <Button
          variant="soft"
          onClick={() =>
            router.push(`/org/${slug}/settings/request-templates`)
          }
        >
          Cancel
        </Button>
        <Button onClick={handleSubmit} disabled={isSaving || !name.trim()}>
          {isSaving ? "Creating..." : "Create Template"}
        </Button>
      </div>
    </div>
  );
}
