// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-sales-metrics.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { ChartPie } from "lucide-react";
import { Pie, PieChart } from "recharts";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { CHART_THEME } from "@/lib/chart-theme";
import { formatChartValue, type ChartValueKind } from "@/lib/chart-format";
import { ChartCard, type ChartCardSlots } from "./chart-card";
import {
  chartState,
  foldSegments,
  seriesColor,
  summarizeSegments,
  type ChartSegment,
} from "./chart-summary";
import { ChartTableToggle, usePrefersReducedMotion } from "./chart-table-toggle";

export type DonutSegment = ChartSegment;

/** Segment-name direct labels for 2–4 segments (ADR-370 §3). */
const MAX_DIRECT_LABELS = 4;
/** Rough width of one 12px label character — labels that would not fit are skipped, not clipped. */
const LABEL_CHAR_PX = 7;
const LABEL_OFFSET = 8;
const RADIAN = Math.PI / 180;

interface DonutChartProps extends ChartCardSlots {
  /**
   * Drawn in the given order; more than five fold into "Other" (largest four kept). Pin `slot`s
   * to keep colours stable across filters.
   */
  segments: DonutSegment[];
  format: ChartValueKind;
  currency?: string;
  /** Caller-formatted centre figure, e.g. "38.5h". */
  centerValue?: React.ReactNode;
  centerLabel?: React.ReactNode;
  /**
   * The legend row below the ring (default true, even for one segment — a ring with no name is
   * unreadable). False when the caller lists the segments itself (e.g. a footer list).
   */
  showLegend?: boolean;
  /**
   * Segment names beside the ring at 2–4 segments (default true). False when the caller renders
   * its own itemised list next to the chart, which already labels every segment.
   */
  directLabels?: boolean;
  /** Chart area height in px (the legend row sits below it). */
  height?: number;
  /** "View as table" header of the segment column. */
  categoryLabel?: string;
  /** "View as table" header of the value column. */
  valueLabel?: string;
}

interface SegmentNameLabelProps {
  // Injected by recharts' Pie (label render props).
  cx?: number;
  cy?: number;
  midAngle?: number;
  outerRadius?: number;
  payload?: { label?: string };
}

/**
 * Pie `label`: the segment name just outside the ring, left- or right-anchored by side, in a text
 * token. Skipped (never clipped) when it would not fit inside the chart; the legend, tooltip and
 * table still carry it.
 */
function SegmentNameLabel({ cx, cy, midAngle, outerRadius, payload }: SegmentNameLabelProps) {
  const text = payload?.label;
  if (!text || cx === undefined || cy === undefined || midAngle === undefined) return null;
  const radius = (outerRadius ?? 0) + LABEL_OFFSET;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);
  const right = x >= cx;
  const room = right ? 2 * cx - x : x;
  if (text.length * LABEL_CHAR_PX > room || y < 6 || y > 2 * cy - 6) return null;
  return (
    <text
      x={x}
      y={y}
      textAnchor={right ? "start" : "end"}
      dominantBaseline="middle"
      fill={CHART_THEME.axis}
      fontSize={12}
    >
      {text}
    </text>
  );
}

export function DonutChart({
  title,
  description,
  segments,
  format,
  currency,
  centerValue,
  centerLabel,
  showLegend,
  directLabels = true,
  height = 200,
  categoryLabel = "Segment",
  valueLabel = "Value",
  loading,
  error,
  emptyTitle,
  emptyDescription,
  footer,
  className,
  "data-testid": testId,
}: DonutChartProps) {
  const reduceMotion = usePrefersReducedMotion();

  const folded = foldSegments(segments);
  const total = folded.reduce((sum, s) => sum + s.value, 0);
  const state = chartState({ loading, error, hasValue: total > 0 });

  const rows = folded.map((segment, index) => ({
    ...segment,
    fill: seriesColor(segment, index),
  }));
  // Labels only — colours are on each row (`fill`), so ChartStyle emits no per-key CSS.
  const config: ChartConfig = Object.fromEntries(
    rows.map((row) => [row.key, { label: row.label }])
  );
  const legend = showLegend ?? true;
  const nameLabels = directLabels && rows.length >= 2 && rows.length <= MAX_DIRECT_LABELS;
  const fmt = (value: number) => formatChartValue(value, format, currency);

  return (
    <ChartCard
      title={title}
      description={description}
      height={height}
      state={state}
      emptyIcon={ChartPie}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      errorMessage={error}
      summary={
        state === "ready"
          ? summarizeSegments({ title, segments: folded, format, currency })
          : undefined
      }
      footer={footer}
      className={className}
      data-testid={testId}
      toggle={
        <ChartTableToggle
          caption={title}
          columns={[
            { key: "category", label: categoryLabel },
            { key: "value", label: valueLabel, numeric: true },
            { key: "share", label: "Share", numeric: true },
          ]}
          rows={rows.map((row) => ({
            id: row.key,
            cells: [
              row.label,
              fmt(row.value),
              formatChartValue((row.value / total) * 100, "percent"),
            ],
          }))}
        />
      }
    >
      {/* The ring and its centre overlay share one box of exactly `height`; the legend is its own
          row below, so the overlay never depends on the legend's height. */}
      <div className="relative">
        <ChartContainer config={config} className="aspect-auto w-full" style={{ height }}>
          <PieChart accessibilityLayer>
            <ChartTooltip
              content={
                <ChartTooltipContent
                  hideLabel
                  nameKey="label"
                  valueFormatter={(value) => fmt(Number(value))}
                />
              }
            />
            <Pie
              data={rows}
              dataKey="value"
              nameKey="label"
              innerRadius={CHART_THEME.donut.innerRadius}
              outerRadius={CHART_THEME.donut.outerRadius}
              cornerRadius={CHART_THEME.donut.cornerRadius}
              paddingAngle={CHART_THEME.donut.paddingAngle}
              stroke="var(--card)"
              label={nameLabels ? <SegmentNameLabel /> : false}
              labelLine={false}
              isAnimationActive={!reduceMotion}
            />
          </PieChart>
        </ChartContainer>
        {centerValue || centerLabel ? (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center"
          >
            {centerValue ? (
              <span className="text-foreground font-mono text-lg font-semibold tabular-nums">
                {centerValue}
              </span>
            ) : null}
            {centerLabel ? (
              <span className="text-muted-foreground text-xs">{centerLabel}</span>
            ) : null}
          </div>
        ) : null}
      </div>
      {legend ? (
        <ul
          data-slot="chart-legend"
          className="flex flex-wrap items-center justify-center gap-x-4 gap-y-1 pt-3 text-xs"
        >
          {rows.map((row) => (
            <li key={row.key} className="flex items-center gap-1.5">
              <span
                aria-hidden="true"
                className="size-2 shrink-0 rounded-[2px]"
                style={{ backgroundColor: row.fill }}
              />
              <span className="text-foreground">{row.label}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </ChartCard>
  );
}
