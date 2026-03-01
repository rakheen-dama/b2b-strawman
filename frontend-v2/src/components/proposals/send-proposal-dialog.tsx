"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { toast } from "@/lib/toast";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  sendProposal,
  getPortalContacts,
} from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import type { PortalContactSummary } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

interface SendProposalDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  proposalId: string;
  customerId: string;
  existingPortalContactId: string | null;
}

export function SendProposalDialog({
  open,
  onOpenChange,
  proposalId,
  customerId,
  existingPortalContactId,
}: SendProposalDialogProps) {
  const router = useRouter();
  const [isPending, setIsPending] = React.useState(false);
  const [contacts, setContacts] = React.useState<PortalContactSummary[]>([]);
  const [selectedContactId, setSelectedContactId] = React.useState<string>("");
  const [loaded, setLoaded] = React.useState(false);

  React.useEffect(() => {
    if (open && !loaded) {
      getPortalContacts(customerId).then((result) => {
        setContacts(result);
        // Pre-select existing portal contact or first contact
        const preselect =
          existingPortalContactId &&
          result.some((c) => c.id === existingPortalContactId)
            ? existingPortalContactId
            : "";
        setSelectedContactId(preselect);
        setLoaded(true);
      });
    }
    if (!open) {
      setLoaded(false);
    }
  }, [open, loaded, customerId, existingPortalContactId]);

  async function handleSend() {
    if (!selectedContactId) return;
    setIsPending(true);
    try {
      await sendProposal(proposalId, selectedContactId);
      toast.success("Proposal sent successfully");
      onOpenChange(false);
      router.refresh();
    } catch {
      toast.error("Failed to send proposal");
    } finally {
      setIsPending(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>Send Proposal</DialogTitle>
          <DialogDescription>
            Choose a portal contact to receive this proposal.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="portal-contact">Portal Contact</Label>
            {contacts.length === 0 && loaded ? (
              <p className="text-sm text-slate-500">
                No portal contacts found for this customer. Add a portal contact
                first.
              </p>
            ) : (
              <Select
                value={selectedContactId}
                onValueChange={setSelectedContactId}
              >
                <SelectTrigger id="portal-contact">
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
            )}
          </div>
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            onClick={handleSend}
            disabled={isPending || !selectedContactId}
          >
            {isPending ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Sending...
              </>
            ) : (
              "Send Proposal"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
