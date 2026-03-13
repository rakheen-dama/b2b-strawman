"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  createLeaveAction,
  updateLeaveAction,
} from "@/app/(app)/org/[slug]/resources/resource-actions";

interface LeaveBlock {
  id: string;
  startDate: string;
  endDate: string;
  note: string | null;
}

interface LeaveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slug: string;
  memberId: string;
  editingLeave?: LeaveBlock | null;
}

export function LeaveDialog({
  open,
  onOpenChange,
  slug,
  memberId,
  editingLeave,
}: LeaveDialogProps) {
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [note, setNote] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const isEditing = !!editingLeave;

  useEffect(() => {
    if (editingLeave) {
      setStartDate(editingLeave.startDate);
      setEndDate(editingLeave.endDate);
      setNote(editingLeave.note ?? "");
    } else {
      setStartDate("");
      setEndDate("");
      setNote("");
    }
    setError(null);
    setValidationError(null);
  }, [editingLeave, open]);

  function validate(): boolean {
    if (!startDate || !endDate) {
      setValidationError("Both start and end dates are required.");
      return false;
    }
    if (endDate < startDate) {
      setValidationError("End date must be on or after start date.");
      return false;
    }
    setValidationError(null);
    return true;
  }

  async function handleSubmit() {
    if (!validate()) return;
    setError(null);
    setIsSubmitting(true);

    try {
      const data = { startDate, endDate, note: note || undefined };
      const result = isEditing
        ? await updateLeaveAction(slug, memberId, editingLeave!.id, data)
        : await createLeaveAction(slug, memberId, data);

      if (result.success) {
        onOpenChange(false);
      } else {
        setError(result.error ?? "Failed to save leave block.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEditing ? "Edit Leave" : "Create Leave"}</DialogTitle>
          <DialogDescription>
            {isEditing
              ? "Update this leave block."
              : "Add a leave block for this team member."}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="leave-start-date">Start Date</Label>
            <Input
              id="leave-start-date"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="leave-end-date">End Date</Label>
            <Input
              id="leave-end-date"
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="leave-note">Note</Label>
            <Input
              id="leave-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Optional note"
            />
          </div>
          {validationError && (
            <p className="text-sm text-red-600 dark:text-red-400" role="alert">
              {validationError}
            </p>
          )}
          {error && (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="plain"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? "Saving..." : "Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
