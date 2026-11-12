// Must stay the first import: `@/lib/format` loads the en-ZA Intl polyfill, and every
// Intl.NumberFormat below is created lazily (inside the functions), after it has run.
import { formatCurrency } from "@/lib/format";

/** What a chart value measures; picks the unit and precision of its label. */
export type ChartValueKind = "currency" | "hours" | "percent" | "points" | "count";

const PLACEHOLDER = "—";
/** U+2212 MINUS SIGN for signed points and negative abbreviated currency. */
const MINUS = "−";
/** Keeps a currency symbol and its abbreviation suffix on one line with the number. */
const NBSP = " ";

const numberFormats = new Map<string, Intl.NumberFormat>();

function numberFormat(minFractionDigits: number, maxFractionDigits: number): Intl.NumberFormat {
  const key = `${minFractionDigits}:${maxFractionDigits}`;
  let format = numberFormats.get(key);
  if (!format) {
    format = new Intl.NumberFormat("en-ZA", {
      minimumFractionDigits: minFractionDigits,
      maximumFractionDigits: maxFractionDigits,
    });
    numberFormats.set(key, format);
  }
  return format;
}

/** The currency symbol as `formatCurrency` renders it ("R", "US$", "€"), digits stripped. */
function currencySymbol(currency: string): string {
  return formatCurrency(0, currency)
    .replace(/[\d.,\s  -]/g, "")
    .trim();
}

function formatSignedPoints(value: number): string {
  const magnitude = numberFormat(1, 1).format(Math.abs(value));
  const isZero = numberFormat(1, 1).format(0) === magnitude;
  const sign = isZero ? "" : value > 0 ? "+" : MINUS;
  return `${sign}${magnitude} pp`;
}

const CURRENCY_SCALES: ReadonlyArray<{ divisor: number; suffix: string }> = [
  { divisor: 1e9, suffix: "bn" },
  { divisor: 1e6, suffix: "m" },
  { divisor: 1e3, suffix: "k" },
];

/** "R 1,2 bn" / "R 1,2 m" / "R 450 k" / "R 950" — one decimal at most once abbreviated. */
function formatCurrencyTick(value: number, currency: string): string {
  const symbol = currencySymbol(currency);
  const abs = Math.abs(value);
  const sign = value < 0 ? MINUS : "";

  // Largest scale the value reaches; a sub-1000 value that rounds to 1000 reads as "1 k".
  let index = CURRENCY_SCALES.findIndex(({ divisor }) => abs >= divisor);
  if (index === -1 && Math.round(abs) < 1000) {
    return `${sign}${symbol}${NBSP}${numberFormat(0, 0).format(abs)}`;
  }
  if (index === -1) index = CURRENCY_SCALES.length - 1;

  const round1 = (i: number) => Math.round((abs / CURRENCY_SCALES[i].divisor) * 10) / 10;
  let scaled = round1(index);
  // 999 950 would read "1 000 k"; the next scale up reads it as "1 m" instead.
  if (scaled >= 1000 && index > 0) {
    index -= 1;
    scaled = round1(index);
  }
  const body = `${numberFormat(0, 1).format(scaled)}${NBSP}${CURRENCY_SCALES[index].suffix}`;
  return `${sign}${symbol}${NBSP}${body}`;
}

function isMissing(value: number | null | undefined): value is null | undefined {
  return value === null || value === undefined || !Number.isFinite(value);
}

/**
 * Full-precision label for tooltips, sr summaries and "View as table". Currency delegates to
 * `formatCurrency` (en-ZA); hours one decimal + "h"; percent one decimal + "%"; points signed
 * one decimal + " pp"; count a grouped integer. Null or non-finite → "—".
 */
export function formatChartValue(
  value: number | null | undefined,
  kind: ChartValueKind,
  currency = "ZAR"
): string {
  if (isMissing(value)) return PLACEHOLDER;
  switch (kind) {
    case "currency":
      return formatCurrency(value, currency);
    case "hours":
      return `${numberFormat(1, 1).format(value)}h`;
    case "percent":
      return `${numberFormat(1, 1).format(value)}%`;
    case "points":
      return formatSignedPoints(value);
    case "count":
      return numberFormat(0, 0).format(value);
  }
}

/**
 * Compact axis tick. Currency abbreviates ("R 1,2 m", "R 450 k", "R 950"); hours at most one
 * decimal + "h"; percent an integer + "%"; points signed + " pp"; count a grouped integer.
 */
export function formatAxisTick(
  value: number | null | undefined,
  kind: ChartValueKind,
  currency = "ZAR"
): string {
  if (isMissing(value)) return PLACEHOLDER;
  switch (kind) {
    case "currency":
      return formatCurrencyTick(value, currency);
    case "hours":
      return `${numberFormat(0, 1).format(value)}h`;
    case "percent":
      return `${numberFormat(0, 0).format(value)}%`;
    case "points":
      return formatSignedPoints(value);
    case "count":
      return numberFormat(0, 0).format(value);
  }
}
