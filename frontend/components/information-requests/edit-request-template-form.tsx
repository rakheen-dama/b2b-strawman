"use client";

import { useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2, ChevronUp, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ResponseTypeBadge } from "@/components/information-requests/response-type-badge";
import {
  updateTemplateAction,
  duplicateTemplateAction,
} from "@/app/(app)/org/[slug]/settings/request-templates/actions";
import type {
  RequestTemplateResponse,
  UpdateRequestTemplateRequest,
} from "@/lib/api/information-requests";

type ResponseType = "FILE_UPLOAD" | "TEXT_RESPONSE";

interface TemplateItem {
  key: string;
  name: string;
  description: string;
  responseType: ResponseType;
  required: boolean;
  fileTypeHints: string;
}

interface EditRequestTemplateFormProps {
  slug: string;
  template: RequestTemplateResponse;
}

export function EditRequestTemplateForm({ slug, template }: EditRequestTemplateFormProps) {
  const router = useRouter();
  const nextKeyRef = useRef(0);
  const isPlatform = template.source === "PLATFORM";

  function newItem(): TemplateItem {
    return {
      key: `item-${nextKeyRef.current++}`,
      name: "",
      description: "",
      responseType: "FILE_UPLOAD",
      required: false,
      fileTypeHints: "",
    };
  }

  const [name, setName] = useState(template.name);
  const [description, setDescription] = useState(template.description ?? "");

  const sortedItems = [...template.items].sort((a, b) => a.sortOrder - b.sortOrder);
  const [items, setItems] = useState<TemplateItem[]>(
    sortedItems.map((item) => ({
      key: `existing-${nextKeyRef.current++}`,
      name: item.name,
      description: item.description ?? "",
      responseType: item.responseType,
      required: item.required,
      fileTypeHints: item.fileTypeHints ?? "",
    }))
  );
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function addItem() {
    setItems((prev) => [...prev, newItem()]);
  }

  function removeItem(key: string) {
    setItems((prev) => prev.filter((item) => item.key !== key));
  }

  function updateItem(key: string, updates: Partial<TemplateItem>) {
    setItems((prev) => prev.map((item) => (item.key === key ? { ...item, ...updates } : item)));
  }

  function moveItem(index: number, direction: "up" | "down") {
    setItems((prev) => {
      const newItems = [...prev];
      const targetIndex = direction === "up" ? index - 1 : index + 1;
      if (targetIndex < 0 || targetIndex >= newItems.length) return prev;
      [newItems[index], newItems[targetIndex]] = [newItems[targetIndex], newItems[index]];
      return newItems;
    });
  }

  async function handleSave() {
    if (!name.trim()) return;

    setIsSaving(true);
    setError(null);

    const validItems = items.filter((item) => item.name.trim());

    const input: UpdateRequestTemplateRequest = {
      name: name.trim(),
      description: description.trim() || undefined,
      items: validItems.map((item, index) => ({
        name: item.name.trim(),
        description: item.description.trim() || undefined,
        responseType: item.responseType,
        required: item.required,
        fileTypeHints: item.fileTypeHints.trim() || undefined,
        sortOrder: index,
      })),
    };

    try {
      const result = await updateTemplateAction(slug, template.id, input);
      if (result.success) {
        router.push(`/org/${slug}/settings/request-templates`);
      } else {
        setError(result.error ?? "Failed to update request template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDuplicate() {
    setIsSaving(true);
    setError(null);
    try {
      const result = await duplicateTemplateAction(slug, template.id);
      if (result.success && result.data) {
        router.push(`/org/${slug}/settings/request-templates/${result.data.id}`);
      } else {
        setError(result.error ?? "Failed to duplicate template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSaving(false);
    }
  }

  if (isPlatform) {
    return (
      <div className="space-y-6">
        <div className="space-y-2">
          <Label>Name</Label>
          <p className="text-sm text-slate-900 dark:text-slate-100">{template.name}</p>
        </div>

        {template.description && (
          <div className="space-y-2">
            <Label>Description</Label>
            <p className="text-sm text-slate-600 dark:text-slate-400">{template.description}</p>
          </div>
        )}

        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Template Items
          </h2>

          {sortedItems.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">No items in this template.</p>
          ) : (
            <div className="space-y-3">
              {sortedItems.map((item, index) => (
                <div
                  key={item.id}
                  data-testid="request-item-row"
                  className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-medium text-slate-400">{index + 1}</span>
                    <div className="flex-1">
                      <p className="font-medium text-slate-900 dark:text-slate-100">{item.name}</p>
                      {item.description && (
                        <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                          {item.description}
                        </p>
                      )}
                    </div>
                    <ResponseTypeBadge responseType={item.responseType} />
                    <input
                      type="checkbox"
                      checked={item.required}
                      readOnly
                      data-testid="request-item-required"
                      className="sr-only"
                      aria-hidden="true"
                      tabIndex={-1}
                    />
                    {item.required && (
                      <span className="text-xs font-medium text-red-600 dark:text-red-400">
                        Required
                      </span>
                    )}
                  </div>
                  {item.fileTypeHints && (
                    <p className="mt-1 ml-7 text-xs text-slate-400">
                      Accepted: {item.fileTypeHints}
                    </p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {error && <p className="text-destructive text-sm">{error}</p>}

        <div className="flex justify-end gap-3">
          <Button
            variant="soft"
            onClick={() => router.push(`/org/${slug}/settings/request-templates`)}
          >
            Back
          </Button>
          <Button onClick={handleDuplicate} disabled={isSaving}>
            {isSaving ? "Duplicating..." : "Duplicate to Customize"}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Template metadata */}
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
          rows={2}
        />
      </div>

      {/* Items */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Template Items
          </h2>
          <Button variant="outline" size="sm" onClick={addItem}>
            <Plus className="mr-1.5 size-4" />
            Add Item
          </Button>
        </div>

        {items.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-500">
            No items yet. Add items to define what information to request.
          </p>
        )}

        <div className="space-y-3">
          {items.map((item, index) => (
            <div
              key={item.key}
              data-testid="request-item-row"
              className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
            >
              <div className="flex items-start gap-3">
                <div className="mt-2 flex flex-col gap-0.5">
                  <button
                    type="button"
                    onClick={() => moveItem(index, "up")}
                    disabled={index === 0}
                    className="text-slate-400 hover:text-slate-600 disabled:opacity-30 dark:hover:text-slate-300"
                    aria-label={`Move item ${index + 1} up`}
                  >
                    <ChevronUp className="size-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => moveItem(index, "down")}
                    disabled={index === items.length - 1}
                    className="text-slate-400 hover:text-slate-600 disabled:opacity-30 dark:hover:text-slate-300"
                    aria-label={`Move item ${index + 1} down`}
                  >
                    <ChevronDown className="size-4" />
                  </button>
                </div>
                <div className="flex-1 space-y-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-slate-400">{index + 1}</span>
                    <Input
                      value={item.name}
                      onChange={(e) => updateItem(item.key, { name: e.target.value })}
                      placeholder="Item name"
                      className="flex-1"
                    />
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-8 shrink-0 text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950"
                      onClick={() => removeItem(item.key)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>

                  <Input
                    value={item.description}
                    onChange={(e) =>
                      updateItem(item.key, {
                        description: e.target.value,
                      })
                    }
                    placeholder="Description (optional)"
                    className="text-sm"
                  />

                  <div className="flex flex-wrap items-center gap-4">
                    <div className="flex items-center gap-2">
                      <Label htmlFor={`response-type-${item.key}`} className="text-sm">
                        Response Type
                      </Label>
                      <select
                        id={`response-type-${item.key}`}
                        value={item.responseType}
                        onChange={(e) =>
                          updateItem(item.key, {
                            responseType: e.target.value as ResponseType,
                          })
                        }
                        className="flex h-8 rounded-md border border-slate-200 bg-transparent px-2 py-1 text-sm shadow-sm transition-colors focus-visible:ring-1 focus-visible:ring-slate-500 focus-visible:outline-none dark:border-slate-800"
                      >
                        <option value="FILE_UPLOAD">File Upload</option>
                        <option value="TEXT_RESPONSE">Text Response</option>
                      </select>
                    </div>

                    <label className="flex items-center gap-1.5 text-sm">
                      <input
                        type="checkbox"
                        checked={item.required}
                        onChange={(e) =>
                          updateItem(item.key, {
                            required: e.target.checked,
                          })
                        }
                        className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                        data-testid="request-item-required"
                      />
                      Required
                    </label>

                    {item.responseType === "FILE_UPLOAD" && (
                      <Input
                        value={item.fileTypeHints}
                        onChange={(e) =>
                          updateItem(item.key, {
                            fileTypeHints: e.target.value,
                          })
                        }
                        placeholder="File types (e.g. PDF, JPG)"
                        className="w-48 text-sm"
                      />
                    )}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {error && <p className="text-destructive text-sm">{error}</p>}

      <div className="flex justify-end gap-3">
        <Button
          variant="soft"
          onClick={() => router.push(`/org/${slug}/settings/request-templates`)}
        >
          Cancel
        </Button>
        <Button onClick={handleSave} disabled={isSaving || !name.trim()}>
          {isSaving ? "Saving..." : "Save Changes"}
        </Button>
      </div>
    </div>
  );
}
