// Chart kit helpers (ADR-370): series/category folding, colour slots and the sr-only summary
// sentence. Pure and server-safe — no hooks, no "use client".
import { CHART_THEME } from "@/lib/chart-theme";
import { formatChartValue, type ChartValueKind } from "@/lib/chart-format";

/** ADR-370: at most five categorical slots; a sixth series folds into "Other". */
export const MAX_SERIES = CHART_THEME.categorical.length;
export const OTHER_LABEL = "Other";
export const OTHER_KEY = "__other";
/** "Other" is de-emphasised (dataviz: the tail is not an identity), not a sixth hue. */
export const OTHER_COLOR = "var(--muted-foreground)";

export interface ChartSeries {
  key: string;
  label: string;
}

export type ChartRow = Record<string, string | number | null>;

/** Slot colour by position (fixed order, never cycled); the folded "Other" is muted. */
export function seriesColor(key: string, index: number): string {
  if (key === OTHER_KEY) return OTHER_COLOR;
  return CHART_THEME.categorical[Math.min(index, MAX_SERIES - 1)];
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
 * "Other" series whose value per row is the sum of the rest.
 */
export function foldSeries(
  rows: ChartRow[],
  series: ChartSeries[]
): { rows: ChartRow[]; series: ChartSeries[] } {
  if (series.length <= MAX_SERIES) return { rows, series };
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
 * order) are kept and the rest are summed, per series, into one "Other" category.
 */
export function foldBars(
  rows: ChartRow[],
  categoryKey: string,
  seriesKeys: string[],
  maxBars = 10
): ChartRow[] {
  if (rows.length <= maxBars) return rows;
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
}

/**
 * Donut: ≤ `max` segments are kept in the caller's order. More are sorted by value (largest
 * first), the top `max − 1` kept and the rest summed into "Other".
 */
export function foldSegments(segments: ChartSegment[], max = MAX_SERIES): ChartSegment[] {
  if (segments.length <= max) return segments;
  const sorted = [...segments].sort((a, b) => b.value - a.value);
  const kept = sorted.slice(0, max - 1);
  const otherValue = sorted.slice(max - 1).reduce((sum, s) => sum + s.value, 0);
  return [...kept, { key: OTHER_KEY, label: OTHER_LABEL, value: otherValue }];
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
  return `${title}: ${count}; ${clauses.join("; ")}.`;
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
