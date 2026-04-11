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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2, Plus } from "lucide-react";
import {
  createRequestAction,
  sendRequestAction,
  fetchActiveTemplatesAction,
  fetchPortalContactsAction,
} from "@/app/(app)/org/[slug]/customers/[id]/request-actions";
import type { RequestTemplateResponse } from "@/lib/api/information-requests";
import type { PortalContactSummary } from "@/lib/actions/acceptance-actions";

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
    setTemplates([]);
    setContacts([]);
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

    setError(null);
    setIsSubmitting(true);
    try {
      const result = await createRequestAction(slug, customerId, {
        requestTemplateId: templateId === "ad-hoc" ? null : templateId,
        customerId,
        projectId: projectId ?? null,
        portalContactId,
        reminderIntervalDays: reminderDays,
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
            disabled={isSubmitting || loadingData || contacts.length === 0}
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
