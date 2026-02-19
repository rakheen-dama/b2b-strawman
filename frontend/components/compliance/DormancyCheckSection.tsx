"use client";

import { useState } from "react";
import { runDormancyCheck, type DormancyCandidate } from "@/app/(app)/org/[slug]/compliance/actions";
import { DormancyCandidateList } from "./DormancyCandidateList";

interface DormancyCheckSectionProps {
  orgSlug: string;
}

export function DormancyCheckSection({ orgSlug }: DormancyCheckSectionProps) {
  const [candidates, setCandidates] = useState<DormancyCandidate[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleCheck() {
    setLoading(true);
    setError(null);
    const result = await runDormancyCheck(orgSlug);
    if (result.success) {
      setCandidates(result.candidates ?? []);
    } else {
      setError(result.error ?? "Dormancy check failed.");
    }
    setLoading(false);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">Dormancy Check</h2>
        <button
          onClick={handleCheck}
          disabled={loading}
          className="rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-700 disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900"
        >
          {loading ? "Checking..." : "Check for Dormant Customers"}
        </button>
      </div>
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      {candidates !== null && <DormancyCandidateList candidates={candidates} orgSlug={orgSlug} />}
    </div>
  );
}
