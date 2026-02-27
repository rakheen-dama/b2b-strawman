"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  fetchPortalContacts,
  sendForAcceptance,
} from "@/lib/actions/acceptance-actions";
import type { PortalContactSummary } from "@/lib/actions/acceptance-actions";

interface SendForAcceptanceDialogProps {
  generatedDocumentId: string;
  customerId: string;
  documentName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function SendForAcceptanceDialog({
  generatedDocumentId,
  customerId,
  documentName,
  open,
  onOpenChange,
}: SendForAcceptanceDialogProps) {
  const [contacts, setContacts] = useState<PortalContactSummary[]>([]);
  const [isLoadingContacts, setIsLoadingContacts] = useState(false);
  const [selectedContactId, setSelectedContactId] = useState<string>("");
  const [expiryDays, setExpiryDays] = useState<string>("");
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      // Reset state on close
      setContacts([]);
      setSelectedContactId("");
      setExpiryDays("");
      setError(null);
      setSuccessMessage(null);
      setIsLoadingContacts(false);
      setIsSending(false);
      return;
    }

    async function loadContacts() {
      setIsLoadingContacts(true);
      setError(null);
      try {
        const result = await fetchPortalContacts(customerId);
        setContacts(result);
      } catch {
        setError("Could not load portal contacts. Please configure them in the customer's portal settings.");
      } finally {
        setIsLoadingContacts(false);
      }
    }

    loadContacts();
  }, [open, customerId]);

  async function handleSend() {
    if (!selectedContactId) return;
    setIsSending(true);
    setError(null);
    setSuccessMessage(null);

    const parsedExpiry = expiryDays ? parseInt(expiryDays, 10) : undefined;
    const result = await sendForAcceptance(
      generatedDocumentId,
      selectedContactId,
      parsedExpiry,
    );

    if (result.success) {
      setSuccessMessage("Acceptance request sent.");
      window.dispatchEvent(new Event("acceptance-requests-refresh"));
      setTimeout(() => {
        onOpenChange(false);
      }, 800);
    } else {
      setError(result.error ?? "Failed to send acceptance request.");
    }
    setIsSending(false);
  }

  const noContacts = !isLoadingContacts && contacts.length === 0 && open;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Send for Acceptance: {documentName}</DialogTitle>
        </DialogHeader>

        {isLoadingContacts && (
          <div className="flex items-center justify-center py-8">
            <p className="text-sm text-slate-500">Loading contacts...</p>
          </div>
        )}

        {noContacts && !error && (
          <p className="py-4 text-sm text-slate-500">
            No portal contacts configured for this customer.
          </p>
        )}

        {!isLoadingContacts && contacts.length > 0 && (
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="recipient">Recipient</Label>
              <Select
                value={selectedContactId}
                onValueChange={setSelectedContactId}
              >
                <SelectTrigger id="recipient">
                  <SelectValue placeholder="Select a contact" />
                </SelectTrigger>
                <SelectContent>
                  {contacts.map((contact) => (
                    <SelectItem key={contact.id} value={contact.id}>
                      {contact.displayName} ({contact.email})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="expiryDays">Expiry (days)</Label>
              <Input
                id="expiryDays"
                type="number"
                min={1}
                max={365}
                placeholder="Org default (~30 days)"
                value={expiryDays}
                onChange={(e) => setExpiryDays(e.target.value)}
              />
            </div>
          </div>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}
        {successMessage && (
          <p className="text-sm text-green-600 dark:text-green-400">
            {successMessage}
          </p>
        )}

        <DialogFooter>
          <Button
            variant="accent"
            onClick={handleSend}
            disabled={
              isLoadingContacts ||
              isSending ||
              !selectedContactId ||
              noContacts
            }
          >
            {isSending ? "Sending..." : "Send"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
