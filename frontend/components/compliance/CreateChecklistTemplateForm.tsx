"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Plus, Trash2, GripVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  createChecklistTemplate,
  type CreateChecklistTemplateInput,
} from "@/app/(app)/org/[slug]/settings/checklists/actions";
import type { CustomerType } from "@/lib/types";

const CUSTOMER_TYPES: { value: CustomerType; label: string }[] = [
  { value: "INDIVIDUAL", label: "Individual" },
  { value: "COMPANY", label: "Company" },
  { value: "TRUST", label: "Trust" },
];

interface ChecklistItem {
  key: string;
  name: string;
  description: string;
  required: boolean;
  requiresDocument: boolean;
  requiredDocumentLabel: string;
}

let nextKey = 0;
function newItem(): ChecklistItem {
  return {
    key: `item-${nextKey++}`,
    name: "",
    description: "",
    required: false,
    requiresDocument: false,
    requiredDocumentLabel: "",
  };
}

interface CreateChecklistTemplateFormProps {
  slug: string;
}

export function CreateChecklistTemplateForm({
  slug,
}: CreateChecklistTemplateFormProps) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [customerType, setCustomerType] = useState<CustomerType>("COMPANY");
  const [autoInstantiate, setAutoInstantiate] = useState(false);
  const [items, setItems] = useState<ChecklistItem[]>([newItem()]);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function addItem() {
    setItems((prev) => [...prev, newItem()]);
  }

  function removeItem(key: string) {
    setItems((prev) => prev.filter((item) => item.key !== key));
  }

  function updateItem(key: string, updates: Partial<ChecklistItem>) {
    setItems((prev) =>
      prev.map((item) =>
        item.key === key ? { ...item, ...updates } : item,
      ),
    );
  }

  async function handleCreate() {
    if (!name.trim()) return;

    const validItems = items.filter((item) => item.name.trim());
    if (validItems.length === 0) {
      setError("Add at least one checklist item.");
      return;
    }

    setIsCreating(true);
    setError(null);

    const input: CreateChecklistTemplateInput = {
      name: name.trim(),
      description: description.trim() || undefined,
      customerType,
      autoInstantiate,
      items: validItems.map((item, index) => ({
        name: item.name.trim(),
        description: item.description.trim() || undefined,
        sortOrder: index,
        required: item.required,
        requiresDocument: item.requiresDocument,
        requiredDocumentLabel: item.requiredDocumentLabel.trim() || undefined,
      })),
    };

    try {
      const result = await createChecklistTemplate(slug, input);
      if (result.success) {
        router.push(`/org/${slug}/settings/checklists`);
      } else {
        setError(result.error ?? "Failed to create checklist template.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Template metadata */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="checklist-name">Name</Label>
          <Input
            id="checklist-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Individual Tax Return Onboarding"
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="checklist-customer-type">Customer Type</Label>
          <select
            id="checklist-customer-type"
            value={customerType}
            onChange={(e) =>
              setCustomerType(e.target.value as CustomerType)
            }
            className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-slate-500 dark:border-slate-800"
          >
            {CUSTOMER_TYPES.map((ct) => (
              <option key={ct.value} value={ct.value}>
                {ct.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="checklist-description">Description</Label>
        <Textarea
          id="checklist-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Brief description of when this checklist should be used"
          rows={2}
        />
      </div>

      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="checklist-auto-instantiate"
          checked={autoInstantiate}
          onChange={(e) => setAutoInstantiate(e.target.checked)}
          className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
        />
        <Label htmlFor="checklist-auto-instantiate" className="cursor-pointer">
          Auto-instantiate when a matching customer is created
        </Label>
      </div>

      {/* Items */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Checklist Items
          </h2>
          <Button variant="outline" size="sm" onClick={addItem}>
            <Plus className="mr-1.5 size-4" />
            Add Item
          </Button>
        </div>

        {items.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-500">
            No items yet. Add at least one checklist item.
          </p>
        )}

        <div className="space-y-3">
          {items.map((item, index) => (
            <div
              key={item.key}
              className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950"
            >
              <div className="flex items-start gap-3">
                <GripVertical className="mt-2.5 size-4 shrink-0 text-slate-400" />
                <div className="flex-1 space-y-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-slate-400">
                      {index + 1}
                    </span>
                    <Input
                      value={item.name}
                      onChange={(e) =>
                        updateItem(item.key, { name: e.target.value })
                      }
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
                      />
                      Required
                    </label>

                    <label className="flex items-center gap-1.5 text-sm">
                      <input
                        type="checkbox"
                        checked={item.requiresDocument}
                        onChange={(e) =>
                          updateItem(item.key, {
                            requiresDocument: e.target.checked,
                          })
                        }
                        className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                      />
                      Requires document
                    </label>

                    {item.requiresDocument && (
                      <Input
                        value={item.requiredDocumentLabel}
                        onChange={(e) =>
                          updateItem(item.key, {
                            requiredDocumentLabel: e.target.value,
                          })
                        }
                        placeholder="Document label"
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

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex justify-end gap-3">
        <Button
          variant="soft"
          onClick={() => router.push(`/org/${slug}/settings/checklists`)}
        >
          Cancel
        </Button>
        <Button
          onClick={handleCreate}
          disabled={isCreating || !name.trim()}
        >
          {isCreating ? "Creating..." : "Create Template"}
        </Button>
      </div>
    </div>
  );
}
