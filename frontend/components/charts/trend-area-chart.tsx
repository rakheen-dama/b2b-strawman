// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-total-income.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { useId, useLayoutEffect, useState } from "react";
import { ChartArea } from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  LabelList,
  XAxis,
  YAxis,
  getNiceTickValues,
  usePlotArea,
  useYAxisDomain,
} from "recharts";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { CHART_THEME } from "@/lib/chart-theme";
import { formatAxisTick, formatChartValue, type ChartValueKind } from "@/lib/chart-format";
import { ChartCard, type ChartCardSlots } from "./chart-card";
import {
  chartState,
  foldSeries,
  hasFiniteValue,
  isAdditive,
  moreText,
  ChartMoreNote,
  seriesColor,
  seriesTable,
  summarizeTrend,
  toDateLabel,
  type ChartRow,
  type ChartSeries,
} from "./chart-summary";
import { ChartTableToggle, usePrefersReducedMotion } from "./chart-table-toggle";

/** Suffix of the dashed companion series that draws the incomplete last period. */
const PARTIAL_SUFFIX = "__partial";
/** Direct labels at the line end for 2–4 series (one series: the title names it). */
const MAX_DIRECT_LABELS = 4;
/** The y axis' tick count (recharts' default, stated so the collision check below mirrors it). */
const Y_TICK_COUNT = 5;
/** Line box of a 12px end label: two end labels closer than this (chart px) overlap. */
const END_LABEL_HEIGHT = 14;

interface TrendAreaChartProps extends ChartCardSlots {
  data: ChartRow[];
  /** Key of the caller-formatted x label ("Aug 2026"). */
  xKey: string;
  /** "View as table" header of the x column. */
  categoryLabel?: string;
  /**
   * Same unit, one axis; a sixth series folds into "Other" (summed only for additive formats;
   * percent/points draw the first five and the table keeps the rest). Pin `slot`s to keep
   * colours stable across filters.
   */
  series: ChartSeries[];
  format: ChartValueKind;
  currency?: string;
  /** The last x is incomplete: labelled "(to date)" and, from two points on, drawn dashed. */
  partialLastPoint?: boolean;
  /** Chart area height in px. */
  height?: number;
}

interface EndLabelProps {
  /** Series label drawn at the line end. */
  text: string;
  /** Row index of the series' last valued point (`lastValuedIndex`). */
  lastIndex: number;
  // Injected by recharts' LabelList (one entry per row; a missing value arrives as y = 0).
  x?: number | string;
  y?: number | string;
  index?: number;
  value?: unknown;
}

/** LabelList `content`: the series label beside its last valued point only (direct labelling). */
function EndLabel({ text, lastIndex, x, y, index, value }: EndLabelProps) {
  if (index !== lastIndex || !Number.isFinite(value) || x === undefined || y === undefined) {
    return null;
  }
  return (
    <text
      x={Number(x) + 8}
      y={Number(y)}
      dominantBaseline="middle"
      fill={CHART_THEME.axis}
      fontSize={12}
    >
      {text}
    </text>
  );
}

/**
 * True when two of `values` (each direct-labelled series' last valued point) land within an end
 * label's height of each other. Pixels follow recharts' linear y scale: the data domain widened to
 * its nice ticks, across the plot height. Labels at different x are judged on y alone — erring
 * towards the legend, never towards an overlap.
 */
function endLabelsCollide(
  values: number[],
  domain: readonly unknown[] | undefined,
  plotHeight: number | undefined
): boolean {
  if (values.length < 2 || !plotHeight || !domain || domain.length !== 2) return false;
  const [min, max] = domain.map(Number);
  if (!Number.isFinite(min) || !Number.isFinite(max)) return false;
  const ticks = getNiceTickValues([min, max], Y_TICK_COUNT, true);
  const lo = Math.min(min, ticks[0] ?? min);
  const hi = Math.max(max, ticks[ticks.length - 1] ?? max);
  const pxPerUnit = hi > lo ? plotHeight / (hi - lo) : 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted.some((v, i) => i > 0 && (v - sorted[i - 1]) * pxPerUnit < END_LABEL_HEIGHT);
}

/**
 * Chart child (inside recharts' context): reports whether the direct end labels would overlap.
 * Converging lines fall back to the always-present legend + tooltip rather than nudged labels
 * that detach from their lines (dataviz: "when end-labels collide, don't stack them"). The y scale
 * does not depend on the right margin the parent drops, so the report is stable.
 */
function EndLabelCollision({
  values,
  onChange,
}: {
  values: number[];
  onChange: (collide: boolean) => void;
}) {
  const domain = useYAxisDomain();
  const plot = usePlotArea();
  const collide = endLabelsCollide(values, domain, plot?.height);
  // Layout effect: settles before paint, so overlapping labels never flash.
  useLayoutEffect(() => onChange(collide), [collide, onChange]);
  return null;
}

/** Row index of the last finite value of `key`, or -1. */
function lastValuedIndex(rows: ChartRow[], key: string): number {
  return rows.map((row) => Number.isFinite(row[key])).lastIndexOf(true);
}

/**
 * Row indices of `key`'s isolated values (no valued neighbour): an area draws no path through a
 * lone point, so these get a dot. (recharts already dots a series of exactly one point.)
 */
function isolatedIndices(rows: ChartRow[], key: string): Set<number> {
  const valued = (index: number) => Number.isFinite(rows[index]?.[key]);
  const isolated = new Set<number>();
  rows.forEach((_, index) => {
    if (valued(index) && !valued(index - 1) && !valued(index + 1)) isolated.add(index);
  });
  return isolated;
}

/** Area `dot`: the theme default (none), plus a dot on each of `indices`. */
function isolatedDot(indices: Set<number>, color: string) {
  if (indices.size === 0) return CHART_THEME.area.dot;
  function IsolatedDot({ index, cx, cy }: { index?: number; cx?: number; cy?: number }) {
    if (index === undefined || !indices.has(index) || cx === undefined || cy === undefined) {
      return null;
    }
    return <circle cx={cx} cy={cy} r={3} fill={color} stroke={color} />;
  }
  return IsolatedDot;
}

export function TrendAreaChart({
  title,
  description,
  data,
  xKey,
  categoryLabel = "Period",
  series: inputSeries,
  format,
  currency,
  partialLastPoint = false,
  height = 240,
  loading,
  error,
  emptyTitle,
  emptyDescription,
  footer,
  className,
  "data-testid": testId,
}: TrendAreaChartProps) {
  const reduceMotion = usePrefersReducedMotion();
  const [endLabelsOverlap, setEndLabelsOverlap] = useState(false);
  const gradientId = `trend-${useId().replace(/[^a-zA-Z0-9_-]/g, "")}`;

  const { rows, series } = foldSeries(data, inputSeries, format);
  // Ready vs empty from the full data: a non-additive truncation that leaves only empty series on
  // the chart must not hide the table that still holds the values.
  const state = chartState({ loading, error, hasValue: hasFiniteValue(data, inputSeries) });
  // Series a non-additive truncation leaves off the chart: stated in the note and the summary.
  const hiddenSeries = isAdditive(format) ? 0 : inputSeries.length - series.length;
  const more = moreText(0, hiddenSeries);

  const lastIndex = rows.length - 1;
  // "(to date)" labels the last x whenever it is incomplete; the dashed segment needs two points.
  const toDate = partialLastPoint && rows.length >= 1;
  const partial = partialLastPoint && rows.length >= 2;
  const xAt = (row: ChartRow, index: number) => {
    const x = String(row[xKey] ?? "");
    return toDate && index === lastIndex ? toDateLabel(x) : x;
  };

  // Partial last point: the solid series stops one point early and a dashed companion series
  // spans the last two points (no legend entry; the tooltip shows it only where the solid one
  // has no value).
  const chartRows: ChartRow[] = partial
    ? rows.map((row, index) => {
        const next: ChartRow = { ...row };
        for (const s of series) {
          next[`${s.key}${PARTIAL_SUFFIX}`] = index >= lastIndex - 1 ? row[s.key] : null;
          if (index === lastIndex) next[s.key] = null;
        }
        return next;
      })
    : rows;

  const config: ChartConfig = Object.fromEntries(series.map((s) => [s.key, { label: s.label }]));
  const showLegend = series.length >= 2;
  const labelable = series.length >= 2 && series.length <= MAX_DIRECT_LABELS;
  const directLabels = labelable && !endLabelsOverlap;
  // Each labelled series' last valued point (where its end label sits; the dashed companion of a
  // partial last period ends on the same value).
  const endValues = series
    .map((s) => rows[lastValuedIndex(rows, s.key)]?.[s.key])
    .filter((value): value is number => Number.isFinite(value));
  const fmt = (value: number) => formatChartValue(value, format, currency);
  const table = seriesTable({
    rows,
    categoryKey: xKey,
    categoryLabel,
    // Non-additive truncation leaves series off the chart, never off the table.
    series: isAdditive(format) ? series : inputSeries,
    format,
    currency,
    categoryText: xAt,
  });

  return (
    <ChartCard
      title={title}
      description={description}
      height={height}
      state={state}
      emptyIcon={ChartArea}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      errorMessage={error}
      summary={
        state === "ready"
          ? summarizeTrend({
              title,
              rows,
              xKey,
              series,
              format,
              currency,
              partialLastPoint: toDate,
              hiddenSeries,
            })
          : undefined
      }
      footer={footer}
      className={className}
      data-testid={testId}
      toggle={<ChartTableToggle caption={title} columns={table.columns} rows={table.rows} />}
    >
      <ChartContainer config={config} className="aspect-auto w-full" style={{ height }}>
        <AreaChart
          accessibilityLayer
          data={chartRows}
          margin={{ top: 8, right: directLabels ? 88 : 12, bottom: 0, left: 0 }}
        >
          <defs>
            {series.map((s, index) => (
              <linearGradient key={s.key} id={`${gradientId}-${index}`} x1="0" y1="0" x2="0" y2="1">
                <stop
                  offset="5%"
                  stopColor={seriesColor(s, index)}
                  stopOpacity={CHART_THEME.gradientOpacity.top}
                />
                <stop
                  offset="95%"
                  stopColor={seriesColor(s, index)}
                  stopOpacity={CHART_THEME.gradientOpacity.bottom}
                />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid
            vertical={false}
            stroke={CHART_THEME.grid.stroke}
            strokeDasharray={CHART_THEME.grid.strokeDasharray}
          />
          <XAxis
            dataKey={xKey}
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            tick={{ fill: CHART_THEME.axis }}
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            tickCount={Y_TICK_COUNT}
            width={64}
            tick={{ fill: CHART_THEME.axis }}
            tickFormatter={(value: number) => formatAxisTick(value, format, currency)}
          />
          <ChartTooltip
            content={(props) => {
              // Hide the dashed companion where its solid series already has a value.
              const payload = props.payload?.filter((item) => {
                const key = String(item.dataKey ?? "");
                if (!key.endsWith(PARTIAL_SUFFIX)) return true;
                const base = key.slice(0, -PARTIAL_SUFFIX.length);
                return !props.payload?.some(
                  (other) =>
                    other.dataKey === base && other.value !== null && other.value !== undefined
                );
              });
              // The incomplete period by position (recharts' active index), never by label text:
              // an earlier period may carry the same x label.
              const activeLast =
                props.activeIndex != null && Number(props.activeIndex) === lastIndex;
              return (
                <ChartTooltipContent
                  active={props.active}
                  payload={payload}
                  label={props.label}
                  labelFormatter={(value) =>
                    toDate && activeLast ? toDateLabel(String(value)) : value
                  }
                  valueFormatter={(value) => fmt(Number(value))}
                />
              );
            }}
          />
          {showLegend ? <ChartLegend content={<ChartLegendContent />} /> : null}
          {labelable ? (
            <EndLabelCollision values={endValues} onChange={setEndLabelsOverlap} />
          ) : null}
          {series.map((s, index) => {
            // Isolation is judged on the unsplit rows: with a dashed last period, the solid series
            // dots every isolated point but the last, which its dashed companion draws.
            const isolated = isolatedIndices(rows, s.key);
            const solidDots = partial
              ? new Set([...isolated].filter((i) => i !== lastIndex))
              : isolated;
            return (
              <Area
                key={s.key}
                dataKey={s.key}
                name={s.label}
                type={CHART_THEME.area.type}
                stroke={seriesColor(s, index)}
                strokeWidth={2}
                fill={`url(#${gradientId}-${index})`}
                dot={isolatedDot(solidDots, seriesColor(s, index))}
                activeDot={CHART_THEME.area.activeDot}
                isAnimationActive={!reduceMotion}
              >
                {directLabels && !partial ? (
                  <LabelList
                    dataKey={s.key}
                    content={<EndLabel text={s.label} lastIndex={lastValuedIndex(rows, s.key)} />}
                  />
                ) : null}
              </Area>
            );
          })}
          {partial
            ? series.map((s, index) => {
                const key = `${s.key}${PARTIAL_SUFFIX}`;
                const lastDot = isolatedIndices(rows, s.key).has(lastIndex)
                  ? new Set([lastIndex])
                  : new Set<number>();
                return (
                  <Area
                    key={key}
                    dataKey={key}
                    name={s.label}
                    type={CHART_THEME.area.type}
                    stroke={seriesColor(s, index)}
                    strokeWidth={2}
                    strokeDasharray="4 4"
                    fill="none"
                    legendType="none"
                    dot={isolatedDot(lastDot, seriesColor(s, index))}
                    activeDot={CHART_THEME.area.activeDot}
                    isAnimationActive={!reduceMotion}
                  >
                    {directLabels ? (
                      <LabelList
                        dataKey={key}
                        content={
                          <EndLabel text={s.label} lastIndex={lastValuedIndex(chartRows, key)} />
                        }
                      />
                    ) : null}
                  </Area>
                );
              })
            : null}
        </AreaChart>
      </ChartContainer>
      {more ? <ChartMoreNote>{more}</ChartMoreNote> : null}
    </ChartCard>
  );
}
