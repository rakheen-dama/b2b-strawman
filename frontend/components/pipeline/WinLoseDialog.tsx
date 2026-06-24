"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@b2mash/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Loader2 } from "lucide-react";
import { transitionDealAction } from "@/app/(app)/org/[slug]/pipeline/actions";
import type { DealResponse, StageDto } from "@/lib/api/crm";

export interface WinLoseDialogProps {
  slug: string;
  deal: DealResponse | null;
  targetStage: StageDto | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function WinLoseDialog({ slug, deal, targetStage, open, onOpenChange }: WinLoseDialogProps) {
  const router = useRouter();
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isLost = targetStage?.stageType === "LOST";

  function handleOpenChange(o: boolean) {
    onOpenChange(o);
    if (!o) {
      setReason("");
      setError(null);
    }
  }

  async function handleConfirm() {
    if (!deal || !targetStage) return;
    setError(null);

    if (isLost && reason.trim().length === 0) {
      setError("A reason is required to mark this deal as lost.");
      return;
    }

    setSubmitting(true);
    try {
      const result = await transitionDealAction(slug, deal.id, {
        targetStageId: targetStage.id,
        lostReason: isLost ? reason.trim() : undefined,
      });
      if (result.success) {
        handleOpenChange(false);
        router.refresh();
      } else {
        // Keep dialog open and surface backend message (e.g. 400 lost-reason).
        setError(result.error ?? "Something went wrong.");
      }
    } catch {
      setError("A network error occurred.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isLost ? "Mark deal as lost" : "Mark deal as won"}</DialogTitle>
          <DialogDescription>
            {deal ? deal.title : ""}
            {targetStage ? ` → ${targetStage.name}` : ""}
          </DialogDescription>
        </DialogHeader>

        {isLost && (
          <div className="space-y-2 py-2">
            <Label htmlFor="lost-reason">Reason</Label>
            <Textarea
              id="lost-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Why was this deal lost?"
              rows={3}
              autoFocus
            />
          </div>
        )}

        {error && <p className="text-destructive text-sm">{error}</p>}

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={submitting}>
            {submitting && <Loader2 className="mr-1.5 size-4 animate-spin" />}
            {isLost ? "Mark as Lost" : "Mark as Won"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
