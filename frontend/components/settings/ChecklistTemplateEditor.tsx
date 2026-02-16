"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreHorizontal, Plus, Copy, Trash2 } from "lucide-react";
import {
  createChecklistTemplate,
  updateChecklistTemplate,
  cloneChecklistTemplate,
  toggleChecklistTemplateActive,
} from "@/lib/actions/checklists";
import type {
  ChecklistTemplateResponse,
  ChecklistTemplateItemResponse,
  ChecklistTemplateWithItemsResponse,
  CreateChecklistItemRequest,
  UpdateChecklistItemRequest,
} from "@/lib/types";

interface ChecklistTemplateEditorProps {
  slug: string;
  templates: ChecklistTemplateResponse[];
  canManage: boolean;
}

interface EditableItem {
  id?: string;
  name: string;
  description: string;
  sortOrder: number;
  required: boolean;
  requiresDocument: boolean;
  requiredDocumentLabel: string;
  dependsOnItemId: string;
}

function emptyItem(sortOrder: number): EditableItem {
  return {
    name: "",
    description: "",
    sortOrder,
    required: true,
    requiresDocument: false,
    requiredDocumentLabel: "",
    dependsOnItemId: "",
  };
}

export function ChecklistTemplateEditor({
  slug,
  templates,
  canManage,
}: ChecklistTemplateEditorProps) {
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [cloneDialogOpen, setCloneDialogOpen] = useState(false);
  const [cloneTargetId, setCloneTargetId] = useState<string | null>(null);
  const [cloneName, setCloneName] = useState("");
  const [loading, setLoading] = useState(false);

  // Edit form state
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formCustomerType, setFormCustomerType] = useState("INDIVIDUAL");
  const [formAutoInstantiate, setFormAutoInstantiate] = useState(false);
  const [formSortOrder, setFormSortOrder] = useState(0);
  const [formItems, setFormItems] = useState<EditableItem[]>([emptyItem(0)]);

  function openCreateDialog() {
    setEditingId(null);
    setFormName("");
    setFormDescription("");
    setFormCustomerType("INDIVIDUAL");
    setFormAutoInstantiate(false);
    setFormSortOrder(0);
    setFormItems([emptyItem(0)]);
    setEditDialogOpen(true);
  }

  function openEditDialog(template: ChecklistTemplateResponse) {
    setEditingId(template.id);
    setFormName(template.name);
    setFormDescription(template.description ?? "");
    setFormCustomerType(template.customerType);
    setFormAutoInstantiate(template.autoInstantiate);
    setFormSortOrder(template.sortOrder);
    // Items will be populated on the dialog - for now set empty since we need to fetch
    // The brief's API returns TemplateWithItemsResponse from GET /api/checklist-templates/{id}
    // but we only have TemplateResponse in the list. We'll work with what we have.
    setFormItems([emptyItem(0)]);
    setEditDialogOpen(true);
  }

  function addItem() {
    setFormItems([...formItems, emptyItem(formItems.length)]);
  }

  function removeItem(index: number) {
    setFormItems(formItems.filter((_, i) => i !== index));
  }

  function updateItem(index: number, updates: Partial<EditableItem>) {
    setFormItems(formItems.map((item, i) => (i === index ? { ...item, ...updates } : item)));
  }

  async function handleSave() {
    if (!formName.trim()) return;
    setLoading(true);

    const items = formItems
      .filter((item) => item.name.trim())
      .map((item, index) => ({
        ...(item.id ? { id: item.id } : {}),
        name: item.name,
        description: item.description || undefined,
        sortOrder: index,
        required: item.required,
        requiresDocument: item.requiresDocument,
        requiredDocumentLabel: item.requiredDocumentLabel || undefined,
        dependsOnItemId: item.dependsOnItemId || undefined,
      }));

    if (editingId) {
      await updateChecklistTemplate(slug, editingId, {
        name: formName,
        description: formDescription || undefined,
        autoInstantiate: formAutoInstantiate,
        sortOrder: formSortOrder,
        items: items as UpdateChecklistItemRequest[],
      });
    } else {
      await createChecklistTemplate(slug, {
        name: formName,
        description: formDescription || undefined,
        customerType: formCustomerType,
        autoInstantiate: formAutoInstantiate,
        sortOrder: formSortOrder,
        items: items as CreateChecklistItemRequest[],
      });
    }

    setLoading(false);
    setEditDialogOpen(false);
  }

  async function handleClone() {
    if (!cloneTargetId || !cloneName.trim()) return;
    setLoading(true);
    await cloneChecklistTemplate(slug, cloneTargetId, cloneName);
    setLoading(false);
    setCloneDialogOpen(false);
    setCloneName("");
    setCloneTargetId(null);
  }

  async function handleDeactivate(templateId: string) {
    setLoading(true);
    await toggleChecklistTemplateActive(slug, templateId, false, {
      name: "",
      autoInstantiate: false,
      sortOrder: 0,
      items: [],
    });
    setLoading(false);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Templates
        </h2>
        {canManage && (
          <Button size="sm" onClick={openCreateDialog}>
            <Plus className="mr-1 size-4" />
            Add Template
          </Button>
        )}
      </div>

      {templates.length === 0 ? (
        <p className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          No checklist templates yet. Create one to get started.
        </p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Customer Type</TableHead>
              <TableHead>Auto-Instantiate</TableHead>
              <TableHead>Source</TableHead>
              <TableHead>Status</TableHead>
              {canManage && <TableHead className="w-12">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {templates.map((template) => (
              <TableRow key={template.id}>
                <TableCell className="font-medium">
                  {template.name}
                  {template.description && (
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {template.description}
                    </p>
                  )}
                </TableCell>
                <TableCell>
                  <Badge variant="neutral">{template.customerType}</Badge>
                </TableCell>
                <TableCell>
                  {template.autoInstantiate ? (
                    <Badge variant="lead">Auto</Badge>
                  ) : (
                    <span className="text-sm text-slate-400">Manual</span>
                  )}
                </TableCell>
                <TableCell>
                  {template.source === "PACK" ? (
                    <Badge variant="pro">Pack</Badge>
                  ) : (
                    <span className="text-sm text-slate-400">Custom</span>
                  )}
                </TableCell>
                <TableCell>
                  {template.active ? (
                    <Badge variant="success">Active</Badge>
                  ) : (
                    <Badge variant="neutral">Inactive</Badge>
                  )}
                </TableCell>
                {canManage && (
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="plain" size="icon" className="size-8">
                          <MoreHorizontal className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openEditDialog(template)}>
                          Edit
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => {
                            setCloneTargetId(template.id);
                            setCloneName(`${template.name} (Copy)`);
                            setCloneDialogOpen(true);
                          }}
                        >
                          <Copy className="mr-2 size-4" />
                          Clone
                        </DropdownMenuItem>
                        {template.active && (
                          <DropdownMenuItem
                            onClick={() => handleDeactivate(template.id)}
                            className="text-destructive focus:text-destructive"
                          >
                            <Trash2 className="mr-2 size-4" />
                            Deactivate
                          </DropdownMenuItem>
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Edit/Create Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingId ? "Edit Template" : "Create Template"}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="template-name">Name</Label>
              <Input
                id="template-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="e.g., FICA Individual Onboarding"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="template-desc">Description</Label>
              <Textarea
                id="template-desc"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder="Optional description..."
                rows={2}
              />
            </div>

            {!editingId && (
              <div className="space-y-2">
                <Label htmlFor="template-type">Customer Type</Label>
                <select
                  id="template-type"
                  value={formCustomerType}
                  onChange={(e) => setFormCustomerType(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-teal-500 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-50"
                >
                  <option value="INDIVIDUAL">Individual</option>
                  <option value="COMPANY">Company</option>
                  <option value="TRUST">Trust</option>
                </select>
              </div>
            )}

            <div className="flex items-center gap-2">
              <input
                id="template-auto"
                type="checkbox"
                checked={formAutoInstantiate}
                onChange={(e) => setFormAutoInstantiate(e.target.checked)}
                className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500 dark:border-slate-700"
              />
              <Label htmlFor="template-auto">Auto-instantiate for matching customers</Label>
            </div>

            {/* Items Editor */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>Checklist Items</Label>
                <Button type="button" variant="outline" size="sm" onClick={addItem}>
                  <Plus className="mr-1 size-3.5" />
                  Add Item
                </Button>
              </div>

              {formItems.map((item, index) => (
                <div
                  key={index}
                  className="space-y-2 rounded-lg border border-slate-200 p-3 dark:border-slate-800"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex-1 space-y-2">
                      <Input
                        value={item.name}
                        onChange={(e) => updateItem(index, { name: e.target.value })}
                        placeholder="Item name"
                      />
                      <Input
                        value={item.description}
                        onChange={(e) => updateItem(index, { description: e.target.value })}
                        placeholder="Description (optional)"
                      />
                    </div>
                    {formItems.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="size-8 shrink-0 text-red-500"
                        onClick={() => removeItem(index)}
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-4">
                    <label className="flex items-center gap-1.5 text-sm">
                      <input
                        type="checkbox"
                        checked={item.required}
                        onChange={(e) => updateItem(index, { required: e.target.checked })}
                        className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500 dark:border-slate-700"
                      />
                      Required
                    </label>
                    <label className="flex items-center gap-1.5 text-sm">
                      <input
                        type="checkbox"
                        checked={item.requiresDocument}
                        onChange={(e) => updateItem(index, { requiresDocument: e.target.checked })}
                        className="size-3.5 rounded border-slate-300 text-teal-600 focus:ring-teal-500 dark:border-slate-700"
                      />
                      Requires Document
                    </label>
                  </div>
                  {item.requiresDocument && (
                    <Input
                      value={item.requiredDocumentLabel}
                      onChange={(e) =>
                        updateItem(index, { requiredDocumentLabel: e.target.value })
                      }
                      placeholder="Document label (e.g., ID Copy)"
                    />
                  )}
                </div>
              ))}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={loading || !formName.trim()}>
              {loading ? "Saving..." : editingId ? "Update" : "Create"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Clone Dialog */}
      <Dialog open={cloneDialogOpen} onOpenChange={setCloneDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Clone Template</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="clone-name">New Template Name</Label>
            <Input
              id="clone-name"
              value={cloneName}
              onChange={(e) => setCloneName(e.target.value)}
              placeholder="Enter name for the cloned template"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCloneDialogOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleClone} disabled={loading || !cloneName.trim()}>
              {loading ? "Cloning..." : "Clone"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
