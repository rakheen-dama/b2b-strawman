"use client";

import { useState } from "react";
import { Progress } from "@/components/ui/progress";

function computeDaysRemaining(trialEndsAt: string): number {
  return Math.max(
    0,
    Math.ceil((new Date(trialEndsAt).getTime() - Date.now()) / 86_400_000)
  );
}

interface TrialCountdownProps {
  trialEndsAt: string;
}

export function TrialCountdown({ trialEndsAt }: TrialCountdownProps) {
  const [daysRemaining] = useState(() => computeDaysRemaining(trialEndsAt));
  const progressValue = Math.round((daysRemaining / 14) * 100);

  return (
    <div className="space-y-2">
      <p className="text-sm text-slate-600 dark:text-slate-400">
        <span className="font-semibold text-slate-950 dark:text-slate-50">
          {daysRemaining} days
        </span>{" "}
        remaining in your trial
      </p>
      <Progress value={Math.min(100, progressValue)} />
    </div>
  );
}
