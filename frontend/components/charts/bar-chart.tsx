// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/dashboards/charts/chart-finance.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { ChartColumn } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ReferenceLine,
  Text,
  XAxis,
  YAxis,
  type YAxisTickContentProps,
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
  foldBars,
  foldSeries,
  hasFiniteValue,
  isAdditive,
  moreText,
  ChartMoreNote,
  seriesColor,
  seriesTable,
  summarizeBars,
  type ChartRow,
  type ChartSeries,
} from "./chart-summary";
import { ChartTableToggle, usePrefersReducedMotion } from "./chart-table-toggle";
import {
  BAR_CHART_TOP_MARGIN_PX,
  BAR_CHART_VALUE_AXIS_PX,
  HORIZONTAL_CATEGORY_AXIS_PX,
} from "./layout";

/** Series-name direct labels for 2–4 series (ADR-370 §3); one series gets value labels instead. */
const MAX_DIRECT_LABELS = 4;
/** Rough width of one 12px label character — labels that would not fit are skipped, not clipped. */
const LABEL_CHAR_PX = 7;
const LABEL_GAP = 8;
/** Gap between an axis and its tick labels. */
const TICK_MARGIN = 8;
/** recharts' default `tickSize`: it offsets the tick label even with `tickLine={false}`. */
const TICK_SIZE = 6;
/**
 * Characters (ellipsis included) a horizontal chart's category label may use: the label column
 * less the tick offset, at the 12px label character width. Deterministic — no text measuring.
 */
export const CATEGORY_TICK_MAX_CHARS = Math.floor(
  (HORIZONTAL_CATEGORY_AXIS_PX - TICK_SIZE - TICK_MARGIN) / LABEL_CHAR_PX
);

/** `text` cut to `maxChars` characters, the last one an ellipsis; unchanged when it fits. */
export function truncateLabel(text: string, maxChars: number): string {
  const chars = Array.from(text);
  if (chars.length <= maxChars) return text;
  return `${chars
    .slice(0, Math.max(0, maxChars - 1))
    .join("")
    .trimEnd()}…`;
}

/**
 * A horizontal chart's category tick: one line, truncated to the label column (recharts would
 * otherwise wrap a long name onto lines taller than its bar band, overlapping its neighbours). A
 * truncated name keeps the full text as an SVG `<title>` (native hover); the summary, tooltip and
 * table carry it too.
 */
export function CategoryTick({
  x,
  y,
  textAnchor,
  verticalAnchor,
  className,
  payload,
  index,
  tickFormatter,
}: YAxisTickContentProps) {
  const value = payload?.value;
  const full = String(typeof tickFormatter === "function" ? tickFormatter(value, index) : value);
  const text = truncateLabel(full, CATEGORY_TICK_MAX_CHARS);
  return (
    <g>
      {text !== full ? <title>{full}</title> : null}
      {/* No `width`: recharts would wrap the label onto more lines than its band holds. */}
      <Text
        x={x}
        y={y}
        textAnchor={textAnchor}
        verticalAnchor={verticalAnchor}
        className={className}
        fill={CHART_THEME.axis}
      >
        {text}
      </Text>
    </g>
  );
}

function renderCategoryTick(props: YAxisTickContentProps) {
  return <CategoryTick {...props} />;
}

interface BarChartPresetProps extends ChartCardSlots {
  data: ChartRow[];
  /** Key of the caller-formatted category label (fee earner, month…). */
  categoryKey: string;
  /** "View as table" header of the category column. */
  categoryLabel?: string;
  /**
   * Same unit, one axis; a sixth series folds into "Other" (summed only for additive formats).
   * `layout="single"` draws the first. Pin `slot`s to keep colours stable across filters.
   */
  series: ChartSeries[];
  /** "vertical" = columns (default); "horizontal" = bars, e.g. a ranking. */
  orientation?: "vertical" | "horizontal";
  layout?: "single" | "grouped" | "stacked";
  format: ChartValueKind;
  currency?: string;
  /** Single series about a zero line: negatives on --div-neg, the rest on --div-pos. */
  diverging?: boolean;
  /**
   * At most this many categories in total (default 10). Additive formats fold the rest into one
   * summed "Other"; percent/points draw the first `maxBars` and state "+N more" (the table keeps
   * every row) — pre-aggregate a non-additive "Other" yourself if you need one.
   */
  maxBars?: number;
  /** Chart area height in px. */
  height?: number;
  /** No visible header — the caller's card carries the title (see ChartCard). Default off. */
  hideHeader?: boolean;
}

type LabelPlacement = "margin" | "tip" | "above";

interface SeriesNameLabelProps {
  /** Series label drawn beside its mark. */
  text: string;
  /** Index (recharts' LabelList index, see `labelTarget`) of the mark that carries the label. */
  targetIndex: number;
  /**
   * margin: columns — in the right margin at the bar's value end (grouped) or segment middle
   * (stacked), like a line-end label. tip: horizontal grouped — beyond the bar end.
   * above: horizontal stacked — centred above the segment, only where it fits.
   */
  placement: LabelPlacement;
  stacked: boolean;
  /** Right margin in px (the "margin" placement's free space). */
  rightMargin: number;
  // Injected by recharts' LabelList → Label.
  index?: number;
  viewBox?: { x?: number; y?: number; width?: number; height?: number };
  parentViewBox?: { width?: number };
}

/**
 * The LabelList index of the mark that carries a series' name: its last category with a value
 * (the first for "above"). recharts indexes a Bar's LabelList over the rectangles it draws: a
 * grouped bar with no value draws no rectangle, so the index counts only valued categories; a
 * stacked bar draws every category (a missing value stacks as zero), so it is the row index.
 * -1 when the series has no value at all (no label).
 */
function labelTarget(
  rows: ChartRow[],
  key: string,
  placement: LabelPlacement,
  stacked: boolean
): number {
  const valued = rows.flatMap((row, index) => (Number.isFinite(row[key]) ? [index] : []));
  if (valued.length === 0) return -1;
  if (!stacked) return valued.length - 1;
  return placement === "above" ? valued[0] : valued[valued.length - 1];
}

/** LabelList `content`: the series name on one category's mark only (selective direct label). */
function SeriesNameLabel({
  text,
  targetIndex,
  placement,
  stacked,
  rightMargin,
  index,
  viewBox,
  parentViewBox,
}: SeriesNameLabelProps) {
  if (index !== targetIndex || !viewBox) return null;
  const { x = 0, y = 0, width = 0, height = 0 } = viewBox;
  const textWidth = text.length * LABEL_CHAR_PX;
  let position: { x: number; y: number; anchor: "start" | "middle" | "end" } | null = null;

  if (placement === "margin") {
    if (parentViewBox?.width === undefined || textWidth > rightMargin - LABEL_GAP) return null;
    position = {
      x: parentViewBox.width - rightMargin + LABEL_GAP,
      y: stacked ? y + height / 2 : y,
      anchor: "start",
    };
  } else if (placement === "tip") {
    // Beyond the signed value end: a negative bar has a negative width and grows leftwards.
    const end = x + width;
    position =
      width < 0
        ? { x: end - LABEL_GAP, y: y + height / 2, anchor: "end" }
        : { x: end + LABEL_GAP, y: y + height / 2, anchor: "start" };
  } else {
    // Interior segment with no free end: label above it, or not at all (dataviz: never clip).
    if (textWidth > Math.abs(width)) return null;
    position = { x: x + width / 2, y: y - LABEL_GAP, anchor: "middle" };
  }

  return (
    <text
      x={position.x}
      y={position.y}
      textAnchor={position.anchor}
      dominantBaseline="middle"
      fill={CHART_THEME.axis}
      fontSize={12}
    >
      {text}
    </text>
  );
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
  hideHeader = false,
}: BarChartPresetProps) {
  const reduceMotion = usePrefersReducedMotion();

  const singleSeries = layout === "single" || diverging;
  const drawnInput = singleSeries ? inputSeries.slice(0, 1) : inputSeries;
  const folded = foldSeries(data, drawnInput, format);
  const series = folded.series;
  const rows = foldBars(
    folded.rows,
    categoryKey,
    series.map((s) => s.key),
    format,
    maxBars
  );
  // Additive formats fold into a summed "Other" (chart and table agree). Percent/points truncate,
  // so the table keeps every row and series the chart leaves off.
  const additive = isAdditive(format);
  const hiddenCount = additive ? 0 : data.length - rows.length;
  const hiddenSeries = additive ? 0 : drawnInput.length - series.length;
  const more = moreText(hiddenCount, hiddenSeries);
  // Ready vs empty from the full data: a truncation that leaves only empty bars must not hide
  // the table that still holds the values.
  const state = chartState({ loading, error, hasValue: hasFiniteValue(data, drawnInput) });

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
  // One series: compact value labels (the title names the series). 2–4: series-name labels on one
  // category per series (its last valued one; the first for horizontal stacks, whose labels sit
  // above the top bar).
  const valueLabels = series.length === 1;
  const nameLabels = series.length >= 2 && series.length <= MAX_DIRECT_LABELS;
  const placement: LabelPlacement = !horizontal ? "margin" : stacked ? "above" : "tip";
  const longestName = Math.max(0, ...series.map((s) => s.label.length));
  const rightMargin =
    nameLabels && placement !== "above"
      ? Math.min(160, longestName * LABEL_CHAR_PX + 2 * LABEL_GAP)
      : horizontal
        ? 56
        : 12;
  const radius: [number, number, number, number] = horizontal
    ? [0, CHART_THEME.bar.radius[0], CHART_THEME.bar.radius[1], 0]
    : CHART_THEME.bar.radius;
  const fmt = (value: number) => formatChartValue(value, format, currency);
  const tick = (value: number) => formatAxisTick(value, format, currency);
  const table = seriesTable({
    rows: additive ? rows : data,
    categoryKey,
    categoryLabel,
    series: additive ? series : drawnInput,
    format,
    currency,
  });

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
          ? summarizeBars({
              title,
              rows,
              categoryKey,
              series,
              format,
              currency,
              diverging,
              hiddenCount,
              hiddenSeries,
            })
          : undefined
      }
      footer={footer}
      className={className}
      data-testid={testId}
      hideHeader={hideHeader}
      toggle={<ChartTableToggle caption={title} columns={table.columns} rows={table.rows} />}
    >
      <ChartContainer config={config} className="aspect-auto w-full" style={{ height }}>
        <BarChart
          accessibilityLayer
          data={chartRows}
          layout={horizontal ? "vertical" : "horizontal"}
          margin={{ top: BAR_CHART_TOP_MARGIN_PX, right: rightMargin, bottom: 0, left: 0 }}
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
                height={BAR_CHART_VALUE_AXIS_PX}
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
                tickMargin={TICK_MARGIN}
                width={HORIZONTAL_CATEGORY_AXIS_PX}
                tick={renderCategoryTick}
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
              fill={diverging ? undefined : seriesColor(s, index)}
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
              {nameLabels ? (
                <LabelList
                  dataKey={s.key}
                  content={
                    <SeriesNameLabel
                      text={s.label}
                      targetIndex={labelTarget(rows, s.key, placement, stacked)}
                      placement={placement}
                      stacked={stacked}
                      rightMargin={rightMargin}
                    />
                  }
                />
              ) : null}
            </Bar>
          ))}
        </BarChart>
      </ChartContainer>
      {more ? <ChartMoreNote>{more}</ChartMoreNote> : null}
    </ChartCard>
  );
}
