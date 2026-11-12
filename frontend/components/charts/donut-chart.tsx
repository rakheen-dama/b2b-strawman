// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-sales-metrics.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { ChartPie } from "lucide-react";
import { useReducedMotion } from "motion/react";
import { Pie, PieChart } from "recharts";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { CHART_THEME } from "@/lib/chart-theme";
import { formatChartValue, type ChartValueKind } from "@/lib/chart-format";
import { ChartCard, type ChartCardSlots } from "./chart-card";
import { foldSegments, seriesColor, summarizeSegments, type ChartSegment } from "./chart-summary";
import { ChartTableToggle } from "./chart-table-toggle";

export type DonutSegment = ChartSegment;

interface DonutChartProps extends ChartCardSlots {
  /** Drawn in the given order; more than five fold into "Other" (largest four kept). */
  segments: DonutSegment[];
  format: ChartValueKind;
  currency?: string;
  /** Caller-formatted centre figure, e.g. "38.5h". */
  centerValue?: React.ReactNode;
  centerLabel?: React.ReactNode;
  /** Defaults to true at two or more segments; false when the caller lists the segments itself. */
  showLegend?: boolean;
  /** Chart area height in px. */
  height?: number;
  /** "View as table" column headers. */
  tableLabels?: { category: string; value: string };
}

const DEFAULT_TABLE_LABELS = { category: "Segment", value: "Value" };

export function DonutChart({
  title,
  description,
  segments,
  format,
  currency,
  centerValue,
  centerLabel,
  showLegend,
  height = 200,
  tableLabels = DEFAULT_TABLE_LABELS,
  loading,
  error,
  emptyTitle,
  emptyDescription,
  footer,
  className,
  "data-testid": testId,
}: DonutChartProps) {
  const reduceMotion = useReducedMotion() ?? false;

  const folded = foldSegments(segments);
  const total = folded.reduce((sum, s) => sum + s.value, 0);
  const state = loading ? "loading" : error ? "error" : total > 0 ? "ready" : "empty";

  const rows = folded.map((segment, index) => ({
    ...segment,
    fill: seriesColor(segment.key, index),
  }));
  // Labels only — colours are on each row (`fill`), so ChartStyle emits no per-key CSS.
  const config: ChartConfig = Object.fromEntries(
    rows.map((row) => [row.key, { label: row.label }])
  );
  const legend = showLegend ?? rows.length >= 2;
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
            { key: "category", label: tableLabels.category },
            { key: "value", label: tableLabels.value, numeric: true },
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
              label={false}
              labelLine={false}
              isAnimationActive={!reduceMotion}
            />
            {legend ? (
              <ChartLegend verticalAlign="bottom" content={<ChartLegendContent nameKey="key" />} />
            ) : null}
          </PieChart>
        </ChartContainer>
        {centerValue || centerLabel ? (
          <div
            aria-hidden="true"
            className={
              legend
                ? "pointer-events-none absolute inset-x-0 top-0 bottom-10 flex flex-col items-center justify-center"
                : "pointer-events-none absolute inset-0 flex flex-col items-center justify-center"
            }
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
    </ChartCard>
  );
}
