// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-total-income.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { useId } from "react";
import { ChartArea } from "lucide-react";
import { useReducedMotion } from "motion/react";
import { Area, AreaChart, CartesianGrid, LabelList, XAxis, YAxis } from "recharts";
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
  foldSeries,
  seriesColor,
  summarizeTrend,
  toDateLabel,
  type ChartRow,
  type ChartSeries,
} from "./chart-summary";
import { ChartTableToggle } from "./chart-table-toggle";

/** Suffix of the dashed companion series that draws the incomplete last period. */
const PARTIAL_SUFFIX = "__partial";
/** Direct labels at the line end for 2–4 series (one series: the title names it). */
const MAX_DIRECT_LABELS = 4;

interface TrendAreaChartProps extends ChartCardSlots {
  data: ChartRow[];
  /** Key of the caller-formatted x label ("Aug 2026"). */
  xKey: string;
  /** "View as table" header of the x column. */
  xLabel?: string;
  /** Same unit, one axis; a sixth series folds into "Other". */
  series: ChartSeries[];
  format: ChartValueKind;
  currency?: string;
  /** The last x is incomplete: drawn dashed and labelled "(to date)". */
  partialLastPoint?: boolean;
  /** Chart area height in px. */
  height?: number;
}

interface EndLabelProps {
  /** Series label drawn at the line end. */
  text: string;
  lastIndex: number;
  // Injected by recharts' LabelList.
  x?: number | string;
  y?: number | string;
  index?: number;
}

/** LabelList `content`: the series label beside the last point only (direct labelling). */
function EndLabel({ text, lastIndex, x, y, index }: EndLabelProps) {
  if (index !== lastIndex || x === undefined || y === undefined) return null;
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

export function TrendAreaChart({
  title,
  description,
  data,
  xKey,
  xLabel = "Period",
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
  const reduceMotion = useReducedMotion() ?? false;
  const gradientId = `trend-${useId().replace(/[^a-zA-Z0-9_-]/g, "")}`;

  const { rows, series } = foldSeries(data, inputSeries);
  const hasValue = rows.some((row) => series.some((s) => typeof row[s.key] === "number"));
  const state = loading ? "loading" : error ? "error" : hasValue ? "ready" : "empty";

  const lastIndex = rows.length - 1;
  const partial = partialLastPoint && rows.length >= 2;
  const lastX = String(rows[lastIndex]?.[xKey] ?? "");
  const xAt = (row: ChartRow, index: number) => {
    const x = String(row[xKey] ?? "");
    return partial && index === lastIndex ? toDateLabel(x) : x;
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
  const directLabels = series.length >= 2 && series.length <= MAX_DIRECT_LABELS;
  const fmt = (value: number) => formatChartValue(value, format, currency);

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
              partialLastPoint: partial,
            })
          : undefined
      }
      footer={footer}
      className={className}
      data-testid={testId}
      toggle={
        <ChartTableToggle
          caption={title}
          columns={[
            { key: xKey, label: xLabel },
            ...series.map((s) => ({ key: s.key, label: s.label, numeric: true })),
          ]}
          rows={rows.map((row, index) => ({
            id: `${index}`,
            cells: [
              xAt(row, index),
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
                  stopColor={seriesColor(s.key, index)}
                  stopOpacity={CHART_THEME.gradientOpacity.top}
                />
                <stop
                  offset="95%"
                  stopColor={seriesColor(s.key, index)}
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
              return (
                <ChartTooltipContent
                  active={props.active}
                  payload={payload}
                  label={props.label}
                  labelFormatter={(value) =>
                    partial && props.label === lastX ? toDateLabel(String(value)) : value
                  }
                  valueFormatter={(value) => fmt(Number(value))}
                />
              );
            }}
          />
          {showLegend ? <ChartLegend content={<ChartLegendContent />} /> : null}
          {series.map((s, index) => (
            <Area
              key={s.key}
              dataKey={s.key}
              name={s.label}
              type={CHART_THEME.area.type}
              stroke={seriesColor(s.key, index)}
              strokeWidth={2}
              fill={`url(#${gradientId}-${index})`}
              dot={CHART_THEME.area.dot}
              activeDot={CHART_THEME.area.activeDot}
              isAnimationActive={!reduceMotion}
            >
              {directLabels && !partial ? (
                <LabelList
                  dataKey={s.key}
                  content={<EndLabel text={s.label} lastIndex={lastIndex} />}
                />
              ) : null}
            </Area>
          ))}
          {partial
            ? series.map((s, index) => (
                <Area
                  key={`${s.key}${PARTIAL_SUFFIX}`}
                  dataKey={`${s.key}${PARTIAL_SUFFIX}`}
                  name={s.label}
                  type={CHART_THEME.area.type}
                  stroke={seriesColor(s.key, index)}
                  strokeWidth={2}
                  strokeDasharray="4 4"
                  fill="none"
                  legendType="none"
                  dot={CHART_THEME.area.dot}
                  activeDot={CHART_THEME.area.activeDot}
                  isAnimationActive={!reduceMotion}
                >
                  {directLabels ? (
                    <LabelList
                      dataKey={`${s.key}${PARTIAL_SUFFIX}`}
                      content={<EndLabel text={s.label} lastIndex={lastIndex} />}
                    />
                  ) : null}
                </Area>
              ))
            : null}
        </AreaChart>
      </ChartContainer>
    </ChartCard>
  );
}
