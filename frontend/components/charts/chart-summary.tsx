// Chart kit helpers (ADR-370): series/category folding, colour slots, the "View as table" rows
// and the sr-only summary sentence. Pure and server-safe — no hooks, no "use client".
import { CHART_THEME } from "@/lib/chart-theme";
import { formatChartValue, type ChartValueKind } from "@/lib/chart-format";
import type { ChartCardState } from "./chart-card";
import type { ChartTableColumn, ChartTableRow } from "./chart-table-toggle";

/** ADR-370: at most five categorical slots; a sixth series folds into "Other". */
export const MAX_SERIES = CHART_THEME.categorical.length;
export const OTHER_LABEL = "Other";
export const OTHER_KEY = "__other";
/** "Other" is de-emphasised (dataviz: the tail is not an identity), not a sixth hue. */
export const OTHER_COLOR = "var(--muted-foreground)";

/** A categorical colour slot, `var(--chart-1..5)`. */
export type ChartSlot = 1 | 2 | 3 | 4 | 5;

export interface ChartSeries {
  key: string;
  label: string;
  /**
   * Pins the series to a colour slot so it keeps its colour when a filter removes other series
   * (ADR-370: colour follows the entity, never its position). Defaults to the series' position.
   * Pass a slot for every series or for none.
   */
  slot?: ChartSlot;
}

export type ChartRow = Record<string, string | number | null>;

/**
 * Slot colour: the item's pinned `slot`, else its position (fixed order, never cycled). The folded
 * "Other" is muted.
 */
export function seriesColor(item: { key: string; slot?: ChartSlot }, index: number): string {
  if (item.key === OTHER_KEY) return OTHER_COLOR;
  const slotIndex = item.slot ? item.slot - 1 : index;
  return CHART_THEME.categorical[Math.min(slotIndex, MAX_SERIES - 1)];
}

/**
 * Percent and points are not additive: a sum of margins or utilisations means nothing, so the
 * folds never sum them into "Other" — they truncate instead (see `foldSeries` / `foldBars`).
 */
export function isAdditive(format: ChartValueKind): boolean {
  return format !== "percent" && format !== "points";
}

/** The card state every preset derives the same way. */
export function chartState({
  loading,
  error,
  hasValue,
}: {
  loading?: boolean;
  error?: unknown;
  hasValue: boolean;
}): ChartCardState {
  if (loading) return "loading";
  if (error) return "error";
  return hasValue ? "ready" : "empty";
}

function toNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/** Sum of the non-null numbers, or null when every value is missing. */
function sumOrNull(values: unknown[]): number | null {
  let total: number | null = null;
  for (const value of values) {
    const n = toNumber(value);
    if (n !== null) total = (total ?? 0) + n;
  }
  return total;
}

/**
 * ≤ MAX_SERIES series are kept as given. Six or more keep the first MAX_SERIES − 1 and add an
 * "Other" series whose value per row is the sum of the rest. For a non-additive `format`
 * (percent, points) nothing is summed: the first MAX_SERIES are drawn and the rest only appear in
 * the table — callers pre-aggregate non-additive units if they need a total.
 */
export function foldSeries(
  rows: ChartRow[],
  series: ChartSeries[],
  format: ChartValueKind
): { rows: ChartRow[]; series: ChartSeries[] } {
  if (series.length <= MAX_SERIES) return { rows, series };
  if (!isAdditive(format)) return { rows, series: series.slice(0, MAX_SERIES) };
  const kept = series.slice(0, MAX_SERIES - 1);
  const rest = series.slice(MAX_SERIES - 1);
  return {
    series: [...kept, { key: OTHER_KEY, label: OTHER_LABEL }],
    rows: rows.map((row) => ({
      ...row,
      [OTHER_KEY]: sumOrNull(rest.map((s) => row[s.key])),
    })),
  };
}

/**
 * Bars: at most `maxBars` categories in total. Beyond that the first `maxBars − 1` (caller's
 * order) are kept and the rest are summed, per series, into one "Other" category. For a
 * non-additive `format` (percent, points) the tail is not summed: the first `maxBars` are drawn,
 * and the preset states "+N more" and keeps every row in the table. Callers that want an "Other"
 * figure for a non-additive unit pre-aggregate it (e.g. a weighted margin) and pass it as a row.
 */
export function foldBars(
  rows: ChartRow[],
  categoryKey: string,
  seriesKeys: string[],
  format: ChartValueKind,
  maxBars = 10
): ChartRow[] {
  if (rows.length <= maxBars) return rows;
  if (!isAdditive(format)) return rows.slice(0, maxBars);
  const keepCount = Math.max(maxBars - 1, 0);
  const kept = rows.slice(0, keepCount);
  const rest = rows.slice(keepCount);
  const other: ChartRow = { [categoryKey]: OTHER_LABEL };
  for (const key of seriesKeys) {
    other[key] = sumOrNull(rest.map((row) => row[key]));
  }
  return [...kept, other];
}

export interface ChartSegment {
  key: string;
  label: string;
  value: number;
  /** Pins the segment to a colour slot (see `ChartSeries.slot`). Defaults to its position. */
  slot?: ChartSlot;
}

/**
 * Donut: ≤ `max` segments are kept in the caller's order. More are sorted by value (largest
 * first), the top `max − 1` kept and the rest summed into "Other". A donut is part-to-whole, so
 * its values are additive by construction.
 */
export function foldSegments(segments: ChartSegment[], max = MAX_SERIES): ChartSegment[] {
  if (segments.length <= max) return segments;
  const sorted = [...segments].sort((a, b) => b.value - a.value);
  const kept = sorted.slice(0, max - 1);
  const otherValue = sorted.slice(max - 1).reduce((sum, s) => sum + s.value, 0);
  return [...kept, { key: OTHER_KEY, label: OTHER_LABEL, value: otherValue }];
}

interface SeriesTableArgs {
  rows: ChartRow[];
  /** Key of the first column (the row header). */
  categoryKey: string;
  categoryLabel: string;
  series: ChartSeries[];
  format: ChartValueKind;
  currency?: string;
  /** Text of the row header; defaults to the row's `categoryKey` value. */
  categoryText?: (row: ChartRow, index: number) => string;
}

/** "View as table" columns and rows for the cartesian presets: one row per category. */
export function seriesTable({
  rows,
  categoryKey,
  categoryLabel,
  series,
  format,
  currency,
  categoryText = (row) => String(row[categoryKey] ?? ""),
}: SeriesTableArgs): { columns: ChartTableColumn[]; rows: ChartTableRow[] } {
  return {
    columns: [
      { key: categoryKey, label: categoryLabel },
      ...series.map((s) => ({ key: s.key, label: s.label, numeric: true })),
    ],
    rows: rows.map((row, index) => ({
      id: `${index}`,
      cells: [
        categoryText(row, index),
        ...series.map((s) => formatChartValue(toNumber(row[s.key]), format, currency)),
      ],
    })),
  };
}

/** The x label shown for the last point of a trend whose last period is incomplete. */
export function toDateLabel(x: string): string {
  return `${x} (to date)`;
}

interface SummarizeTrendArgs {
  title: string;
  rows: ChartRow[];
  xKey: string;
  series: ChartSeries[];
  format: ChartValueKind;
  currency?: string;
  partialLastPoint?: boolean;
}

/**
 * "Trust balance: from R 120 000,00 in Dec 2025 to R 184 500,00 in Nov 2026 (to date), peak
 * R 210 000,00 in Aug 2026." — one clause per series (prefixed by its label when there are
 * several), joined by "; ".
 */
export function summarizeTrend({
  title,
  rows,
  xKey,
  series,
  format,
  currency,
  partialLastPoint = false,
}: SummarizeTrendArgs): string {
  const fmt = (value: number) => formatChartValue(value, format, currency);
  const lastIndex = rows.length - 1;
  const xAt = (index: number) => {
    const x = String(rows[index]?.[xKey] ?? "");
    return partialLastPoint && index === lastIndex ? toDateLabel(x) : x;
  };

  const clauses = series.map((s) => {
    const points = rows
      .map((row, index) => ({ index, value: toNumber(row[s.key]) }))
      .filter((p): p is { index: number; value: number } => p.value !== null);
    const prefix = series.length > 1 ? `${s.label} ` : "";
    if (points.length === 0) return `${prefix}no data`;
    const first = points[0];
    const last = points[points.length - 1];
    const peak = points.reduce((best, p) => (p.value > best.value ? p : best), first);
    return (
      `${prefix}from ${fmt(first.value)} in ${xAt(first.index)} to ${fmt(last.value)} in ` +
      `${xAt(last.index)}, peak ${fmt(peak.value)} in ${xAt(peak.index)}`
    );
  });

  return `${title}: ${clauses.join("; ")}.`;
}

interface SummarizeBarsArgs {
  title: string;
  rows: ChartRow[];
  categoryKey: string;
  series: ChartSeries[];
  format: ChartValueKind;
  currency?: string;
  diverging?: boolean;
  /** Categories left off the chart (non-additive truncation); stated as "+N more in the table". */
  hiddenCount?: number;
}

/** "WIP by fee earner: 6 bars; highest Alice R 84 000,00, lowest Carol R 3 200,00." */
export function summarizeBars({
  title,
  rows,
  categoryKey,
  series,
  format,
  currency,
  diverging = false,
  hiddenCount = 0,
}: SummarizeBarsArgs): string {
  const fmt = (value: number) => formatChartValue(value, format, currency);
  const single = series.length === 1;

  const clauses = series.map((s) => {
    const points = rows
      .map((row) => ({ category: String(row[categoryKey] ?? ""), value: toNumber(row[s.key]) }))
      .filter((p): p is { category: string; value: number } => p.value !== null);
    const prefix = single ? "" : `${s.label} `;
    if (points.length === 0) return `${prefix}no data`;
    const high = points.reduce((best, p) => (p.value > best.value ? p : best), points[0]);
    const low = points.reduce((best, p) => (p.value < best.value ? p : best), points[0]);
    let clause = `${prefix}highest ${high.category} ${fmt(high.value)}, lowest ${low.category} ${fmt(low.value)}`;
    if (diverging) {
      const below = points.filter((p) => p.value < 0).length;
      clause += `, ${below} below zero`;
    }
    return clause;
  });

  const count = `${rows.length} ${single ? "bars" : "categories"}`;
  const more = hiddenCount > 0 ? `; ${moreLabel(hiddenCount)}` : "";
  return `${title}: ${count}; ${clauses.join("; ")}${more}.`;
}

/** "+3 more in the table" — the visible note and summary clause for truncated bars. */
export function moreLabel(count: number): string {
  return `+${count} more in the table`;
}

interface SummarizeSegmentsArgs {
  title: string;
  segments: ChartSegment[];
  format: ChartValueKind;
  currency?: string;
}

/** "Time Breakdown: 38,5h total — Website Redesign 20,0h (51,9%), Mobile App 12,5h (32,5%)." */
export function summarizeSegments({
  title,
  segments,
  format,
  currency,
}: SummarizeSegmentsArgs): string {
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  const parts = segments.map((s) => {
    const share = total > 0 ? (s.value / total) * 100 : 0;
    return `${s.label} ${formatChartValue(s.value, format, currency)} (${formatChartValue(share, "percent")})`;
  });
  return `${title}: ${formatChartValue(total, format, currency)} total — ${parts.join(", ")}.`;
}

/** The sr-only sentence. One element, one sentence. */
export function ChartSummary({ children }: { children: string }) {
  return (
    <p className="sr-only" data-slot="chart-summary">
      {children}
    </p>
  );
}
