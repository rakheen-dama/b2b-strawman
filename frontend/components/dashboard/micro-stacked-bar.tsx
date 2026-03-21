"use client";

import { cn } from "@/lib/utils";

interface MicroStackedBarProps {
  segments: Array<{ value: number; color: string; label?: string }>;
  width?: number;
  height?: number;
  className?: string;
}

export function MicroStackedBar({
  segments,
  width = 120,
  height = 8,
  className,
}: MicroStackedBarProps) {
  if (!segments || segments.length === 0) return null;

  const total = segments.reduce((sum, seg) => sum + seg.value, 0);

  return (
    <div
      data-testid="micro-stacked-bar"
      className={cn("flex overflow-hidden rounded-full", className)}
      style={{ width, height }}
    >
      {total > 0 &&
        segments.map((segment, i) => (
          <div
            key={i}
            data-testid="segment"
            title={
              segment.label
                ? `${segment.label}: ${segment.value}`
                : String(segment.value)
            }
            style={{
              flexGrow: segment.value,
              backgroundColor: segment.color,
            }}
          />
        ))}
    </div>
  );
}
