"use client";

import { useState } from "react";
import { Progress } from "@/components/ui/progress";
import { computeDaysRemaining } from "@/lib/billing-utils";

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
