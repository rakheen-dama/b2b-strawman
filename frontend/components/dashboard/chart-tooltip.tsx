"use client";

import { CHART_THEME } from "@/lib/chart-theme";

interface TooltipPayloadEntry {
  name: string;
  value: number;
  color?: string;
}

interface ChartTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadEntry[];
  label?: string;
}

export function ChartTooltip({ active, payload, label }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }

  const { tooltip } = CHART_THEME;

  return (
    <div
      data-testid="chart-tooltip"
      style={{
        backgroundColor: tooltip.background,
        color: tooltip.text,
        border: tooltip.border,
        borderRadius: tooltip.borderRadius,
        boxShadow: tooltip.boxShadow,
        padding: "8px 12px",
      }}
    >
      {label && (
        <p className="mb-1 font-sans text-xs opacity-80">{label}</p>
      )}
      {payload.map((entry, i) => (
        <div key={i} className="flex items-center gap-2">
          {entry.color && (
            <span
              className="inline-block size-2 rounded-full"
              style={{ backgroundColor: entry.color }}
            />
          )}
          <span className="font-sans text-xs">{entry.name}</span>
          <span className="font-mono text-xs tabular-nums font-medium">
            {entry.value}
          </span>
        </div>
      ))}
    </div>
  );
}
