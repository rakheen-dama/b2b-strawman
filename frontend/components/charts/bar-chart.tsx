// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-finance.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { ChartColumn } from "lucide-react";
import { useReducedMotion } from "motion/react";
import { Bar, BarChart, CartesianGrid, LabelList, ReferenceLine, XAxis, YAxis } from "recharts";
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
  foldBars,
  foldSeries,
  seriesColor,
  summarizeBars,
  type ChartRow,
  type ChartSeries,
} from "./chart-summary";
import { ChartTableToggle } from "./chart-table-toggle";

interface BarChartPresetProps extends ChartCardSlots {
  data: ChartRow[];
  /** Key of the caller-formatted category label (fee earner, month…). */
  categoryKey: string;
  /** "View as table" header of the category column. */
  categoryLabel?: string;
  /** Same unit, one axis; a sixth series folds into "Other". `layout="single"` draws the first. */
  series: ChartSeries[];
  /** "vertical" = columns (default); "horizontal" = bars, e.g. a ranking. */
  orientation?: "vertical" | "horizontal";
  layout?: "single" | "grouped" | "stacked";
  format: ChartValueKind;
  currency?: string;
  /** Single series about a zero line: negatives on --div-neg, the rest on --div-pos. */
  diverging?: boolean;
  /** At most this many categories in total; the rest fold into one "Other" (default 10). */
  maxBars?: number;
  /** Chart area height in px. */
  height?: number;
}

export function BarChartPreset({
  title,
  description,
  data,
  categoryKey,
  categoryLabel = "Category",
  series: inputSeries,
  orientation = "vertical",
  layout = "single",
  format,
  currency,
  diverging = false,
  maxBars = 10,
  height = 240,
  loading,
  error,
  emptyTitle,
  emptyDescription,
  footer,
  className,
  "data-testid": testId,
}: BarChartPresetProps) {
  const reduceMotion = useReducedMotion() ?? false;

  const singleSeries = layout === "single" || diverging;
  const folded = foldSeries(data, singleSeries ? inputSeries.slice(0, 1) : inputSeries);
  const series = folded.series;
  const rows = foldBars(
    folded.rows,
    categoryKey,
    series.map((s) => s.key),
    maxBars
  );
  const hasValue = rows.some((row) => series.some((s) => typeof row[s.key] === "number"));
  const state = loading ? "loading" : error ? "error" : hasValue ? "ready" : "empty";

  // Diverging: each row carries its own fill (recharts reads `fill` off the datum).
  const chartRows: ChartRow[] = diverging
    ? rows.map((row) => {
        const value = row[series[0]?.key ?? ""];
        const negative = typeof value === "number" && value < 0;
        return {
          ...row,
          fill: negative ? CHART_THEME.diverging.negative : CHART_THEME.diverging.positive,
        };
      })
    : rows;

  const horizontal = orientation === "horizontal";
  const stacked = layout === "stacked" && !singleSeries;
  const config: ChartConfig = Object.fromEntries(series.map((s) => [s.key, { label: s.label }]));
  const showLegend = series.length >= 2;
  // Value labels on single-series bars only: grouped/stacked would print a number on every mark.
  const valueLabels = series.length === 1;
  const radius: [number, number, number, number] = horizontal
    ? [0, CHART_THEME.bar.radius[0], CHART_THEME.bar.radius[1], 0]
    : CHART_THEME.bar.radius;
  const fmt = (value: number) => formatChartValue(value, format, currency);
  const tick = (value: number) => formatAxisTick(value, format, currency);

  return (
    <ChartCard
      title={title}
      description={description}
      height={height}
      state={state}
      emptyIcon={ChartColumn}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      errorMessage={error}
      summary={
        state === "ready"
          ? summarizeBars({ title, rows, categoryKey, series, format, currency, diverging })
          : undefined
      }
      footer={footer}
      className={className}
      data-testid={testId}
      toggle={
        <ChartTableToggle
          caption={title}
          columns={[
            { key: categoryKey, label: categoryLabel },
            ...series.map((s) => ({ key: s.key, label: s.label, numeric: true })),
          ]}
          rows={rows.map((row, index) => ({
            id: `${index}`,
            cells: [
              String(row[categoryKey] ?? ""),
              ...series.map((s) =>
                formatChartValue(
                  typeof row[s.key] === "number" ? (row[s.key] as number) : null,
                  format,
                  currency
                )
              ),
            ],
          }))}
        />
      }
    >
      <ChartContainer config={config} className="aspect-auto w-full" style={{ height }}>
        <BarChart
          accessibilityLayer
          data={chartRows}
          layout={horizontal ? "vertical" : "horizontal"}
          margin={{ top: 16, right: horizontal ? 56 : 12, bottom: 0, left: 0 }}
        >
          <CartesianGrid
            vertical={horizontal}
            horizontal={!horizontal}
            stroke={CHART_THEME.grid.stroke}
            strokeDasharray={CHART_THEME.grid.strokeDasharray}
          />
          {horizontal ? (
            <>
              <XAxis
                type="number"
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                tick={{ fill: CHART_THEME.axis }}
                tickFormatter={tick}
              />
              <YAxis
                type="category"
                dataKey={categoryKey}
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                width={120}
                tick={{ fill: CHART_THEME.axis }}
              />
            </>
          ) : (
            <>
              <XAxis
                dataKey={categoryKey}
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                tick={{ fill: CHART_THEME.axis }}
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                tickMargin={8}
                width={64}
                tick={{ fill: CHART_THEME.axis }}
                tickFormatter={tick}
              />
            </>
          )}
          <ChartTooltip
            cursor={false}
            content={<ChartTooltipContent valueFormatter={(value) => fmt(Number(value))} />}
          />
          {showLegend ? <ChartLegend content={<ChartLegendContent />} /> : null}
          {diverging ? (
            horizontal ? (
              <ReferenceLine x={0} stroke={CHART_THEME.diverging.midpoint} />
            ) : (
              <ReferenceLine y={0} stroke={CHART_THEME.diverging.midpoint} />
            )
          ) : null}
          {series.map((s, index) => (
            <Bar
              key={s.key}
              dataKey={s.key}
              name={s.label}
              fill={diverging ? undefined : seriesColor(s.key, index)}
              stackId={stacked ? "stack" : undefined}
              radius={stacked && index < series.length - 1 ? 0 : radius}
              isAnimationActive={!reduceMotion}
            >
              {valueLabels ? (
                <LabelList
                  dataKey={s.key}
                  position={horizontal ? "right" : "top"}
                  fill={CHART_THEME.axis}
                  fontSize={12}
                  formatter={(value: unknown) => (typeof value === "number" ? tick(value) : "")}
                />
              ) : null}
            </Bar>
          ))}
        </BarChart>
      </ChartContainer>
    </ChartCard>
  );
}
