// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/statistics/statistics-card-03.tsx and statistics-impression-card.tsx / statistics-income-card.tsx (sparkline KPI cards).
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
import * as React from "react";

import { Card } from "./card";
import { cn } from "./cn";
import { Skeleton } from "./skeleton";

type StatCardState = "ready" | "loading" | "redacted" | "error";

interface StatCardDelta {
  /** Signed change; null → no delta row. Percent: % change. Points: current − previous. */
  change: number | null;
  unit: "percent" | "points";
  polarity: "up-is-good" | "down-is-good" | "neutral";
  /** e.g. "previous 30 days" → sr "Up 12,5% vs previous 30 days". */
  comparisonLabel: string;
}

interface StatCardProps {
  /** The tile's accessible name (the root group is labelled by it). */
  label: string;
  /** Caller-formatted (formatCurrency, hours, %). null → "—" + sr-only "Not available". */
  value: React.ReactNode | null;
  state?: StatCardState;
  delta?: StatCardDelta;
  /**
   * Slot (the frontend passes <SparklineTrend>). Not hidden from assistive tech: the slot's content
   * owns its semantics (SparklineTrend = role="img" + name + sr summary, ADR-370). Not rendered in
   * compact, loading, redacted or error.
   */
  sparkline?: React.ReactNode;
  /** Decorative; wrapped aria-hidden. */
  icon?: React.ReactNode;
  /** One muted line under the value. */
  hint?: React.ReactNode;
  /** Link slot (the caller passes next/link). */
  footer?: React.ReactNode;
  /** compact = DetailHero strip: tighter padding, smaller value, no sparkline. */
  variant?: "default" | "compact";
  className?: string;
  "data-testid"?: string;
}

type DeltaTone = "positive" | "negative" | "neutral";

const TONE_CLASS: Record<DeltaTone, string> = {
  positive: "text-success-foreground",
  negative: "text-danger-foreground",
  neutral: "text-muted-foreground",
};

/** U+2212 MINUS SIGN for the visible negative figure. */
const MINUS = "−";

function Chevron({ direction }: { direction: "up" | "down" }) {
  return (
    <svg
      viewBox="0 0 16 16"
      aria-hidden="true"
      focusable="false"
      className="size-3.5 shrink-0"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={direction === "up" ? "M4 10l4-4 4 4" : "M4 6l4 4 4-4"} />
    </svg>
  );
}

/**
 * Deterministic one-decimal magnitude with the en-ZA decimal comma. No Intl here: the package
 * has no Intl polyfill, and Node's small-icu would render "." on the server while the browser
 * renders "," (the GAP-L-12 hydration mismatch).
 */
function formatMagnitude(change: number): string {
  return Math.abs(change).toFixed(1).replace(".", ",");
}

function DeltaRow({ delta, compact }: { delta: StatCardDelta; compact: boolean }) {
  const { change, unit, polarity, comparisonLabel } = delta;
  if (change === null || !Number.isFinite(change)) return null;

  const magnitude = formatMagnitude(change);
  const flat = magnitude === "0,0";
  const direction: "up" | "down" | null = flat ? null : change > 0 ? "up" : "down";

  let tone: DeltaTone = "neutral";
  if (direction && polarity !== "neutral") {
    const good = polarity === "up-is-good" ? direction === "up" : direction === "down";
    tone = good ? "positive" : "negative";
  }

  const visibleUnit = unit === "percent" ? "%" : " pp";
  const sign = direction === "up" ? "+" : direction === "down" ? MINUS : "";
  const figure = `${sign}${magnitude}${visibleUnit}`;

  const spokenUnit = unit === "percent" ? "%" : " percentage points";
  const srText = direction
    ? `${direction === "up" ? "Up" : "Down"} ${magnitude}${spokenUnit} vs ${comparisonLabel}`
    : `No change vs ${comparisonLabel}`;

  return (
    <p
      data-slot="stat-card-delta"
      data-tone={tone}
      className="mt-1.5 flex flex-wrap items-center gap-x-1.5 text-xs"
    >
      <span
        aria-hidden="true"
        data-slot="stat-card-delta-figure"
        className={cn("inline-flex items-center gap-0.5 font-medium", TONE_CLASS[tone])}
      >
        {direction ? <Chevron direction={direction} /> : null}
        <span className="font-mono tabular-nums">{figure}</span>
      </span>
      {!compact ? (
        <span aria-hidden="true" className="text-muted-foreground">
          vs {comparisonLabel}
        </span>
      ) : null}
      <span className="sr-only">{srText}</span>
    </p>
  );
}

function Unavailable() {
  return (
    <>
      <span aria-hidden="true">—</span>
      <span className="sr-only">Not available</span>
    </>
  );
}

/**
 * KPI tile (Phase 90 §15.2.3). Server-safe (no "use client"; `useId` is allowed in Server
 * Components). The caller formats the value; the card only renders the delta figure.
 */
function StatCard({
  label,
  value,
  state = "ready",
  delta,
  sparkline,
  icon,
  hint,
  footer,
  variant = "default",
  className,
  "data-testid": testId,
}: StatCardProps) {
  const labelId = React.useId();
  const compact = variant === "compact";
  const ready = state === "ready";

  let valueContent: React.ReactNode;
  if (state === "loading") {
    valueContent = (
      <Skeleton
        data-slot="stat-card-value-skeleton"
        className={compact ? "h-5 w-16" : "h-7 w-24"}
      />
    );
  } else if (!ready || value === null || value === undefined) {
    valueContent = <Unavailable />;
  } else {
    valueContent = value;
  }

  return (
    <Card
      role="group"
      aria-labelledby={labelId}
      aria-busy={state === "loading" ? true : undefined}
      data-slot="stat-card"
      data-state={state}
      data-variant={variant}
      data-testid={testId}
      className={cn("gap-0", compact ? "p-3" : "p-5", className)}
    >
      <div className="flex items-start justify-between gap-3">
        <div className={cn("flex min-w-0 flex-col", compact ? "gap-0.5" : "gap-1")}>
          <span id={labelId} data-slot="stat-card-label" className="text-muted-foreground text-sm">
            {label}
          </span>
          <div
            data-slot="stat-card-value"
            className={cn(
              "text-foreground font-mono font-semibold tabular-nums",
              compact ? "text-lg" : "text-2xl"
            )}
          >
            {valueContent}
          </div>
        </div>
        {icon ? (
          <div
            aria-hidden="true"
            data-slot="stat-card-icon"
            className="bg-muted text-muted-foreground flex size-9 shrink-0 items-center justify-center rounded-md [&>svg]:size-4"
          >
            {icon}
          </div>
        ) : null}
      </div>

      {state === "loading" && delta ? (
        <Skeleton data-slot="stat-card-delta-skeleton" className="mt-1.5 h-4 w-20" />
      ) : null}
      {ready && delta ? <DeltaRow delta={delta} compact={compact} /> : null}

      {state === "error" ? (
        <p role="status" className="text-danger-foreground mt-1.5 text-xs">
          Couldn&apos;t load
        </p>
      ) : hint ? (
        <div data-slot="stat-card-hint" className="text-muted-foreground mt-1.5 text-xs">
          {hint}
        </div>
      ) : null}

      {ready && !compact && sparkline ? (
        <div data-slot="stat-card-sparkline" className="mt-3">
          {sparkline}
        </div>
      ) : null}

      {footer ? (
        <div data-slot="stat-card-footer" className="mt-3 text-sm">
          {footer}
        </div>
      ) : null}
    </Card>
  );
}

export { StatCard, type StatCardProps, type StatCardDelta, type StatCardState };
