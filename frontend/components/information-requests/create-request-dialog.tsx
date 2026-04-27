"use client";

import { useState, useCallback } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
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
import { Loader2, Plus, Trash2 } from "lucide-react";
import {
  createRequestAction,
  sendRequestAction,
  fetchActiveTemplatesAction,
  fetchPortalContactsAction,
} from "@/app/(app)/org/[slug]/customers/[id]/request-actions";
import type {
  CreateInformationRequestItem,
  RequestTemplateResponse,
} from "@/lib/api/information-requests";
import type { PortalContactSummary } from "@/lib/actions/acceptance-actions";

type AdHocItem = {
  id: string; // local-only key for React list rendering
  name: string;
  description: string;
  responseType: CreateInformationRequestItem["responseType"];
  required: boolean;
};

function makeAdHocItem(): AdHocItem {
  // crypto.randomUUID() is available in modern browsers (secure context) and
  // in Node 19+. Fall back to a timestamp-based id in case it's missing
  // (e.g. older test runtime), so unit tests don't crash when adding items.
  const id =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `item-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return {
    id,
    name: "",
    description: "",
    responseType: "FILE_UPLOAD",
    required: true,
  };
}

interface CreateRequestDialogProps {
  slug: string;
  customerId: string;
  customerName: string;
  projectId?: string;
  projectName?: string;
  defaultReminderDays?: number;
}

export function CreateRequestDialog({
  slug,
  customerId,
  customerName,
  projectId,
  projectName,
  defaultReminderDays = 5,
}: CreateRequestDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Data loaded on dialog open
  const [templates, setTemplates] = useState<RequestTemplateResponse[]>([]);
  const [contacts, setContacts] = useState<PortalContactSummary[]>([]);
  const [loadingData, setLoadingData] = useState(false);

  // Form state
  const [templateId, setTemplateId] = useState<string>("ad-hoc");
  const [portalContactId, setPortalContactId] = useState<string>("");
  const [reminderDays, setReminderDays] = useState<number>(defaultReminderDays);
  const [dueDate, setDueDate] = useState<string>("");
  const [items, setItems] = useState<AdHocItem[]>([]);

  const isAdHoc = templateId === "ad-hoc";
  const hasValidAdHocItems = items.length > 0 && items.every((it) => it.name.trim().length > 0);

  const loadDialogData = useCallback(async () => {
    setLoadingData(true);
    try {
      const [templatesResult, contactsResult] = await Promise.all([
        fetchActiveTemplatesAction(),
        fetchPortalContactsAction(customerId),
      ]);
      if (templatesResult.success && templatesResult.data) {
        setTemplates(templatesResult.data);
      }
      if (contactsResult.success && contactsResult.data) {
        setContacts(contactsResult.data);
        // Auto-select first contact if only one
        if (contactsResult.data.length === 1) {
          setPortalContactId(contactsResult.data[0].id);
        }
      }
    } catch {
      // Non-fatal: dialog will show with empty dropdowns
    } finally {
      setLoadingData(false);
    }
  }, [customerId]);

  function resetForm() {
    setError(null);
    setTemplateId("ad-hoc");
    setPortalContactId("");
    setReminderDays(defaultReminderDays);
    setDueDate("");
    setItems([]);
    setTemplates([]);
    setContacts([]);
  }

  function addItem() {
    setItems((prev) => [...prev, makeAdHocItem()]);
  }

  function removeItem(id: string) {
    setItems((prev) => prev.filter((it) => it.id !== id));
  }

  function updateItem(id: string, patch: Partial<AdHocItem>) {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } : it)));
  }

  function handleOpenChange(newOpen: boolean) {
    setOpen(newOpen);
    if (newOpen) {
      loadDialogData();
    } else {
      resetForm();
    }
  }

  async function handleSubmit(sendImmediately: boolean) {
    if (!portalContactId) {
      setError("Please select a portal contact.");
      return;
    }

    // Ad-hoc + Send Now requires at least one item with a non-empty name.
    // Save-as-Draft is allowed with zero items; users can add items on the
    // detail page via the existing AddItemDialog.
    if (isAdHoc && sendImmediately && !hasValidAdHocItems) {
      setError("Add at least one item with a name before sending.");
      return;
    }

    const adHocPayloadItems: CreateInformationRequestItem[] | undefined =
      isAdHoc && items.length > 0
        ? items.map((it, idx) => ({
            name: it.name.trim(),
            description: it.description.trim() || undefined,
            responseType: it.responseType,
            required: it.required,
            sortOrder: idx,
          }))
        : undefined;

    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createRequestAction(slug, customerId, {
        requestTemplateId: isAdHoc ? null : templateId,
        customerId,
        projectId: projectId ?? null,
        portalContactId,
        reminderIntervalDays: reminderDays,
        dueDate: dueDate.trim() === "" ? null : dueDate,
        ...(adHocPayloadItems ? { items: adHocPayloadItems } : {}),
      });

      if (!result.success) {
        setError(result.error ?? "Failed to create request.");
        return;
      }

      // If "Send Now", immediately send the created request
      if (sendImmediately && result.data) {
        const sendResult = await sendRequestAction(slug, customerId, result.data.id);
        if (!sendResult.success) {
          // Draft was created but send failed — close dialog with warning
          setError(null);
          setOpen(false);
          resetForm();
          return;
        }
      }

      setOpen(false);
      resetForm();
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="mr-1.5 size-4" />
          New Request
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Create Information Request</DialogTitle>
          <DialogDescription>
            Request documents or information from {customerName}
            {projectName ? ` for ${projectName}` : ""}.
          </DialogDescription>
        </DialogHeader>

        {loadingData ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="size-6 animate-spin text-slate-400" />
          </div>
        ) : (
          <div className="space-y-4 py-2">
            {/* Template Selection */}
            <div className="space-y-2">
              <Label htmlFor="template-select">Template</Label>
              <Select value={templateId} onValueChange={setTemplateId}>
                <SelectTrigger id="template-select" className="w-full">
                  <SelectValue placeholder="Select a template..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ad-hoc">Ad-hoc (no template)</SelectItem>
                  {templates.map((t) => (
                    <SelectItem key={t.id} value={t.id}>
                      {t.name}
                      {t.items.length > 0 && (
                        <span className="ml-1 text-slate-400">({t.items.length} items)</span>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Portal Contact */}
            <div className="space-y-2">
              <Label htmlFor="contact-select">Portal Contact</Label>
              {contacts.length === 0 ? (
                <p className="text-sm text-slate-500">
                  No portal contacts found for this customer. Please add a portal contact first.
                </p>
              ) : (
                <Select value={portalContactId} onValueChange={setPortalContactId}>
                  <SelectTrigger id="contact-select" className="w-full">
                    <SelectValue placeholder="Select a contact..." />
                  </SelectTrigger>
                  <SelectContent>
                    {contacts.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.displayName} ({c.email})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>

            {/* Reminder Interval */}
            <div className="space-y-2">
              <Label htmlFor="reminder-days">Reminder Interval (days)</Label>
              <Input
                id="reminder-days"
                type="number"
                min={1}
                max={90}
                value={reminderDays}
                onChange={(e) => setReminderDays(parseInt(e.target.value, 10) || 5)}
                className="w-32"
              />
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Automated reminders will be sent at this interval after the request is sent.
              </p>
            </div>

            {/* Due Date */}
            <div className="space-y-2">
              <Label htmlFor="due-date">Due Date (optional)</Label>
              <Input
                id="due-date"
                data-testid="request-due-date-input"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                className="w-44"
              />
              <p className="text-xs text-slate-500 dark:text-slate-400">
                The date by which the client is expected to return the requested information.
              </p>
            </div>

            {/* Ad-hoc Items — visible only when no template is selected */}
            {isAdHoc && (
              <div className="space-y-2" data-testid="ad-hoc-items-section">
                <div className="flex items-center justify-between">
                  <Label>Items</Label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={addItem}
                    disabled={isSubmitting}
                    data-testid="add-ad-hoc-item-button"
                  >
                    <Plus className="mr-1.5 size-4" />
                    Add Item
                  </Button>
                </div>
                {items.length === 0 ? (
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Add at least one item before sending.
                  </p>
                ) : (
                  <div className="space-y-3">
                    {items.map((item, idx) => (
                      <div
                        key={item.id}
                        className="space-y-2 rounded-md border border-slate-200 p-3 dark:border-slate-800"
                        data-testid={`ad-hoc-item-row-${idx}`}
                      >
                        <div className="flex items-start gap-2">
                          <div className="flex-1 space-y-2">
                            <div className="space-y-1">
                              <Label htmlFor={`ad-hoc-item-name-${idx}`} className="text-xs">
                                Name
                              </Label>
                              <Input
                                id={`ad-hoc-item-name-${idx}`}
                                data-testid={`ad-hoc-item-name-${idx}`}
                                value={item.name}
                                onChange={(e) => updateItem(item.id, { name: e.target.value })}
                                placeholder="e.g. Latest specialist medical reports"
                              />
                            </div>
                            <div className="space-y-1">
                              <Label htmlFor={`ad-hoc-item-description-${idx}`} className="text-xs">
                                Description (optional)
                              </Label>
                              <Textarea
                                id={`ad-hoc-item-description-${idx}`}
                                data-testid={`ad-hoc-item-description-${idx}`}
                                value={item.description}
                                onChange={(e) =>
                                  updateItem(item.id, { description: e.target.value })
                                }
                                placeholder="Extra context for the client..."
                                rows={2}
                                className="resize-none"
                              />
                            </div>
                            <div className="flex flex-wrap items-end gap-3">
                              <div className="space-y-1">
                                <Label
                                  htmlFor={`ad-hoc-item-response-type-${idx}`}
                                  className="text-xs"
                                >
                                  Response type
                                </Label>
                                <Select
                                  value={item.responseType}
                                  onValueChange={(value) =>
                                    updateItem(item.id, {
                                      responseType:
                                        value as CreateInformationRequestItem["responseType"],
                                    })
                                  }
                                >
                                  <SelectTrigger
                                    id={`ad-hoc-item-response-type-${idx}`}
                                    data-testid={`ad-hoc-item-response-type-${idx}`}
                                    className="w-44"
                                  >
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="FILE_UPLOAD">File upload</SelectItem>
                                    <SelectItem value="TEXT_RESPONSE">Text response</SelectItem>
                                  </SelectContent>
                                </Select>
                              </div>
                              <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
                                <input
                                  type="checkbox"
                                  checked={item.required}
                                  onChange={(e) =>
                                    updateItem(item.id, { required: e.target.checked })
                                  }
                                  data-testid={`ad-hoc-item-required-${idx}`}
                                  className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
                                />
                                Required
                              </label>
                            </div>
                          </div>
                          <Button
                            type="button"
                            variant="plain"
                            size="sm"
                            onClick={() => removeItem(item.id)}
                            disabled={isSubmitting}
                            data-testid={`ad-hoc-item-remove-${idx}`}
                            aria-label={`Remove item ${idx + 1}`}
                          >
                            <Trash2 className="size-4" />
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Info display */}
            <div className="rounded-md bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-900 dark:text-slate-400">
              <p>
                <span className="font-medium">Customer:</span> {customerName}
              </p>
              {projectName && (
                <p>
                  <span className="font-medium">Project:</span> {projectName}
                </p>
              )}
              {templateId !== "ad-hoc" && (
                <p>
                  <span className="font-medium">Template items:</span>{" "}
                  {templates.find((t) => t.id === templateId)?.items.length ?? 0}
                </p>
              )}
            </div>

            {error && <p className="text-destructive text-sm">{error}</p>}
          </div>
        )}

        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="plain" onClick={() => setOpen(false)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="outline"
            onClick={() => handleSubmit(false)}
            disabled={isSubmitting || loadingData || contacts.length === 0}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Creating...
              </>
            ) : (
              "Save as Draft"
            )}
          </Button>
          <Button
            onClick={() => handleSubmit(true)}
            disabled={
              isSubmitting ||
              loadingData ||
              contacts.length === 0 ||
              (isAdHoc && !hasValidAdHocItems)
            }
            data-testid="create-request-send-now"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Sending...
              </>
            ) : (
              "Send Now"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
