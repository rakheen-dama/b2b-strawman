"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Send, XCircle } from "lucide-react";
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
import { Label } from "@/components/ui/label";
import {
  sendProposalAction,
  withdrawProposalAction,
  fetchPortalContactsAction,
} from "@/app/(app)/org/[slug]/proposals/actions";
import type { PortalContactSummary } from "@/app/(app)/org/[slug]/proposals/actions";
import type { ProposalStatus } from "@/lib/types/proposal";

interface ProposalDetailActionsProps {
  proposalId: string;
  customerId: string;
  status: ProposalStatus;
  slug: string;
}

export function ProposalDetailActions({
  proposalId,
  customerId,
  status,
  slug,
}: ProposalDetailActionsProps) {
  const router = useRouter();
  const [sendDialogOpen, setSendDialogOpen] = useState(false);
  const [contacts, setContacts] = useState<PortalContactSummary[]>([]);
  const [isLoadingContacts, setIsLoadingContacts] = useState(false);
  const [selectedContactId, setSelectedContactId] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!sendDialogOpen) {
      setContacts([]);
      setSelectedContactId("");
      setError(null);
      setIsLoadingContacts(false);
      setIsSending(false);
      return;
    }

    async function loadContacts() {
      setIsLoadingContacts(true);
      setError(null);
      try {
        const result = await fetchPortalContactsAction(customerId);
        setContacts(result);
      } catch {
        setError(
          "Could not load portal contacts. Please configure them in the customer's portal settings."
        );
      } finally {
        setIsLoadingContacts(false);
      }
    }

    loadContacts();
  }, [sendDialogOpen, customerId]);

  async function handleSend() {
    if (!selectedContactId) return;
    setIsSending(true);
    setError(null);

    const result = await sendProposalAction(slug, proposalId, selectedContactId);
    if (result.success) {
      setSendDialogOpen(false);
      router.refresh();
    } else {
      setError(result.error ?? "Failed to send proposal.");
    }
    setIsSending(false);
  }

  async function handleWithdraw() {
    setIsWithdrawing(true);
    setError(null);

    const result = await withdrawProposalAction(slug, proposalId);
    if (result.success) {
      router.refresh();
    } else {
      setError(result.error ?? "Failed to withdraw proposal.");
    }
    setIsWithdrawing(false);
  }

  const noContacts = !isLoadingContacts && contacts.length === 0 && sendDialogOpen;

  return (
    <>
      <div className="flex gap-2">
        {status === "DRAFT" && (
          <Button variant="accent" size="sm" onClick={() => setSendDialogOpen(true)}>
            <Send className="mr-1.5 size-4" />
            Send Proposal
          </Button>
        )}
        {status === "SENT" && (
          <Button variant="outline" size="sm" onClick={handleWithdraw} disabled={isWithdrawing}>
            <XCircle className="mr-1.5 size-4" />
            {isWithdrawing ? "Withdrawing..." : "Withdraw"}
          </Button>
        )}
      </div>

      {error && status !== "DRAFT" && <p className="text-destructive mt-2 text-sm">{error}</p>}

      {/* Send Dialog */}
      <Dialog open={sendDialogOpen} onOpenChange={setSendDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Send Proposal</DialogTitle>
          </DialogHeader>

          {isLoadingContacts && (
            <div className="flex items-center justify-center py-8">
              <p className="text-sm text-slate-500">Loading contacts...</p>
            </div>
          )}

          {noContacts && !error && (
            <p className="py-4 text-sm text-slate-500">
              No portal contacts configured for this customer. Please add a portal contact first.
            </p>
          )}

          {!isLoadingContacts && contacts.length > 0 && (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="send-recipient">Recipient</Label>
                <Select value={selectedContactId} onValueChange={setSelectedContactId}>
                  <SelectTrigger id="send-recipient">
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
            </div>
          )}

          {error && <p className="text-destructive text-sm">{error}</p>}

          <DialogFooter>
            <Button variant="plain" onClick={() => setSendDialogOpen(false)} disabled={isSending}>
              Cancel
            </Button>
            <Button
              variant="accent"
              onClick={handleSend}
              disabled={isLoadingContacts || isSending || !selectedContactId || noContacts}
            >
              {isSending ? "Sending..." : "Send"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
