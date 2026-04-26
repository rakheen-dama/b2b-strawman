"use client";

import { useState } from "react";
import { Loader2, Plus } from "lucide-react";
import { toast } from "sonner";
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
import { addItemAction } from "@/app/(app)/org/[slug]/information-requests/[id]/actions";
import type {
  CreateInformationRequestItem,
  InformationRequestResponse,
} from "@/lib/api/information-requests";

type ResponseType = CreateInformationRequestItem["responseType"];

interface AddItemDialogProps {
  slug: string;
  requestId: string;
  onSuccess?: (data: InformationRequestResponse) => void;
  children?: React.ReactNode;
}

/**
 * AddItemDialog (GAP-L-67)
 *
 * Allows firm users to append a free-form ad-hoc item to a DRAFT
 * information request from the detail page. Backend already supports
 * `POST /api/information-requests/{id}/items` (gated by CUSTOMER_MANAGEMENT);
 * this is the firm-side UI surface.
 */
export function AddItemDialog({ slug, requestId, onSuccess, children }: AddItemDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [responseType, setResponseType] = useState<ResponseType>("FILE_UPLOAD");
  const [required, setRequired] = useState(true);

  function resetForm() {
    setName("");
    setDescription("");
    setResponseType("FILE_UPLOAD");
    setRequired(true);
    setError(null);
  }

  function handleOpenChange(nextOpen: boolean) {
    if (isSubmitting) return;
    setOpen(nextOpen);
    if (!nextOpen) resetForm();
  }

  async function handleSubmit() {
    setError(null);
    if (!name.trim()) {
      setError("Item name is required.");
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: CreateInformationRequestItem = {
        name: name.trim(),
        description: description.trim() || undefined,
        responseType,
        required,
      };
      const result = await addItemAction(slug, requestId, payload);
      if (result.success && result.data) {
        toast.success("Item added.");
        onSuccess?.(result.data);
        setOpen(false);
        resetForm();
      } else {
        const msg = result.error ?? "Failed to add item.";
        setError(msg);
        toast.error(msg);
      }
    } catch {
      const msg = "An unexpected error occurred.";
      setError(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        {children ?? (
          <Button variant="outline" size="sm">
            <Plus className="mr-1.5 size-4" />
            Add Item
          </Button>
        )}
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add Item</DialogTitle>
          <DialogDescription>
            Add a free-form item to this draft request. The client will see it once the request is
            sent.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="add-item-name">Name</Label>
            <Input
              id="add-item-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Proof of address"
              data-testid="add-item-name-input"
              autoFocus
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="add-item-description">Description (optional)</Label>
            <Textarea
              id="add-item-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Extra context for the client..."
              rows={3}
              className="resize-none"
              data-testid="add-item-description-input"
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="add-item-response-type">Response type</Label>
            <Select
              value={responseType}
              onValueChange={(value) => setResponseType(value as ResponseType)}
            >
              <SelectTrigger id="add-item-response-type" data-testid="add-item-response-type">
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
              checked={required}
              onChange={(e) => setRequired(e.target.checked)}
              data-testid="add-item-required-input"
              className="size-4 rounded border-slate-300 text-teal-600 focus:ring-teal-500"
            />
            Required
          </label>

          {error && <p className="text-destructive text-sm">{error}</p>}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button variant="plain" onClick={() => handleOpenChange(false)} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={isSubmitting || !name.trim()}>
            {isSubmitting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Adding...
              </>
            ) : (
              "Add Item"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
