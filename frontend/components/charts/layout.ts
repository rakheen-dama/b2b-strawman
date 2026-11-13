// Chart kit layout numbers — the one definition the kit presets draw with and callers size their
// boxes from (a skeleton or `minHeight` that equals the ready box, so nothing shifts). Plain
// values, no recharts: safe to import statically beside a `dynamic()` preset import.

/** BarChartPreset's top margin in px (room for the value labels above the bars). */
export const BAR_CHART_TOP_MARGIN_PX = 16;

/** BarChartPreset's horizontal value axis (`<XAxis type="number">`) height in px — recharts' default. */
export const BAR_CHART_VALUE_AXIS_PX = 30;

/** BarChartPreset's non-bar height of a horizontal chart: the top margin + the value axis. */
export const HORIZONTAL_BAR_AXIS_PX = BAR_CHART_TOP_MARGIN_PX + BAR_CHART_VALUE_AXIS_PX;

/**
 * One horizontal bar band in px: BarChartPreset keeps recharts' default 10% category gap on each
 * side, which leaves a 24px bar. Chart height = bars × band + `HORIZONTAL_BAR_AXIS_PX`.
 */
export const HORIZONTAL_BAR_BAND_PX = 30;

/** ChartTableToggle's root: the "View as table" row below every preset. */
export const TOGGLE_ROW_CLASS = "mt-3 flex flex-col gap-2";

/**
 * Height of ChartTableToggle's closed row in px: `mt-3` (12px, `TOGGLE_ROW_CLASS`) + the 32px
 * (`size="sm"`, `h-8`) "View as table" button.
 */
export const TOGGLE_ROW_PX = 44;

/**
 * A preset inside an existing card: no frame and no header of its own (no card in a card; the
 * figure keeps its title as its aria-label). Pass with `hideHeader`.
 */
export const FRAMELESS_CHART_CLASS =
  "gap-0 rounded-none border-0 bg-transparent py-0 shadow-none *:data-[slot=card-content]:px-0";
