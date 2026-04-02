"use client";

import { useState } from "react";
import { computeDaysRemaining } from "@/lib/billing-utils";

interface GraceCountdownProps {
  graceEndsAt: string;
}

export function GraceCountdown({ graceEndsAt }: GraceCountdownProps) {
  const [daysRemaining] = useState(() => computeDaysRemaining(graceEndsAt));

  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950">
      <p className="text-sm font-semibold text-red-800 dark:text-red-200">
        <span className="text-lg">{daysRemaining} days</span> remaining to
        resubscribe
      </p>
      <p className="mt-1 text-xs text-red-600 dark:text-red-400">
        After this period, your account will be locked.
      </p>
    </div>
  );
}
