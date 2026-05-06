"use client";

import { useState } from "react";
import { SpecialistLauncherButton } from "@/components/assistant/specialist-launcher-button";

const LOOKBACK_OPTIONS = [
  { label: "24 hours", value: "P1D" },
  { label: "7 days", value: "P7D" },
  { label: "30 days", value: "P30D" },
  { label: "90 days", value: "P90D" },
] as const;

interface LookbackPickerProps {
  specialistId: string;
  surface: string;
  contextRef: { entityType: string; entityId: string };
  ctaLabel?: string;
}

export function LookbackPicker({
  specialistId,
  surface,
  contextRef,
  ctaLabel,
}: LookbackPickerProps) {
  const [lookback, setLookback] = useState("P7D");

  const selectedOption = LOOKBACK_OPTIONS.find((o) => o.value === lookback) ?? LOOKBACK_OPTIONS[1];
  const initialPrompt = `Summarise activity for the last ${selectedOption.label} (lookback=${lookback}).`;

  return (
    <div className="flex items-center gap-2">
      <select
        value={lookback}
        onChange={(e) => setLookback(e.target.value)}
        className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-700 shadow-sm focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-slate-600 dark:bg-slate-800 dark:text-slate-300"
        data-testid="lookback-select"
      >
        {LOOKBACK_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      <SpecialistLauncherButton
        specialistId={specialistId}
        surface={surface}
        contextRef={contextRef}
        initialPrompt={initialPrompt}
        ctaLabel={ctaLabel}
      />
    </div>
  );
}
