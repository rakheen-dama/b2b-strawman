"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { checkDormancy, transitionCustomer } from "@/lib/actions/compliance";
import type { DormancyCandidate } from "@/lib/actions/compliance";
import { formatDate } from "@/lib/format";

interface DormancyCheckButtonProps {
  canManage: boolean;
  slug: string;
}

export function DormancyCheckButton({ canManage, slug }: DormancyCheckButtonProps) {
  const [isChecking, setIsChecking] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [thresholdDays, setThresholdDays] = useState(0);
  const [candidates, setCandidates] = useState<DormancyCandidate[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [markingIds, setMarkingIds] = useState<Set<string>>(new Set());

  if (!canManage) return null;

  async function handleCheck() {
    setIsChecking(true);
    setError(null);
    try {
      const result = await checkDormancy();
      if (result.success && result.data) {
        setThresholdDays(result.data.thresholdDays);
        setCandidates(result.data.candidates);
        setIsOpen(true);
      } else {
        setError(result.error ?? "Failed to check for dormant customers.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsChecking(false);
    }
  }

  async function handleMarkDormant(candidate: DormancyCandidate) {
    setMarkingIds((prev) => new Set(prev).add(candidate.id));
    try {
      const result = await transitionCustomer(slug, candidate.id, "DORMANT");
      if (result.success) {
        setCandidates((prev) => prev.filter((c) => c.id !== candidate.id));
      }
    } finally {
      setMarkingIds((prev) => {
        const next = new Set(prev);
        next.delete(candidate.id);
        return next;
      });
    }
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onClick={handleCheck}
        disabled={isChecking}
      >
        {isChecking ? "Checking..." : "Check for Dormant Customers"}
      </Button>
      {error && <span className="text-xs text-destructive">{error}</span>}

      <Dialog open={isOpen} onOpenChange={setIsOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Dormancy Check Results</DialogTitle>
            <DialogDescription>
              Customers with no activity for {thresholdDays}+ days
            </DialogDescription>
          </DialogHeader>

          {candidates.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-500">
              No dormant candidates found.
            </p>
          ) : (
            <div className="max-h-80 space-y-3 overflow-y-auto">
              {candidates.map((candidate) => (
                <div
                  key={candidate.id}
                  className="flex items-center justify-between rounded-lg border border-slate-200 p-3 dark:border-slate-800"
                >
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900 dark:text-slate-100">
                      {candidate.name}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      Last activity: {formatDate(candidate.lastActivityAt)} ({candidate.daysSinceActivity} days ago)
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleMarkDormant(candidate)}
                    disabled={markingIds.has(candidate.id)}
                  >
                    {markingIds.has(candidate.id) ? "Marking..." : "Mark as Dormant"}
                  </Button>
                </div>
              ))}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
