import { describe, expect, it } from "vitest";

import { formatAxisTick, formatChartValue } from "@/lib/chart-format";
import { formatCurrency } from "@/lib/format";

describe("formatChartValue", () => {
  it("currency equals formatCurrency exactly (en-ZA, polyfilled)", () => {
    expect(formatChartValue(184500, "currency")).toBe(formatCurrency(184500));
    expect(formatChartValue(184500, "currency")).toMatch(/^R\s184\s500,00$/);
    expect(formatChartValue(12.5, "currency", "USD")).toBe(formatCurrency(12.5, "USD"));
  });

  it("hours: one decimal (en-ZA comma) + h", () => {
    expect(formatChartValue(12.34, "hours")).toBe("12,3h");
    expect(formatChartValue(8, "hours")).toBe("8,0h");
  });

  it("percent: one decimal + %", () => {
    expect(formatChartValue(45.46, "percent")).toBe("45,5%");
  });

  it("points: signed one decimal + pp, zero unsigned", () => {
    expect(formatChartValue(3.24, "points")).toBe("+3,2 pp");
    expect(formatChartValue(-1, "points")).toBe("−1,0 pp");
    expect(formatChartValue(0, "points")).toBe("0,0 pp");
    expect(formatChartValue(-0.01, "points")).toBe("0,0 pp");
  });

  it("count: grouped integer", () => {
    expect(formatChartValue(12345, "count")).toMatch(/^12\s345$/);
    expect(formatChartValue(7, "count")).toBe("7");
  });

  it("null, undefined and non-finite render a dash", () => {
    expect(formatChartValue(null, "currency")).toBe("—");
    expect(formatChartValue(undefined, "hours")).toBe("—");
    expect(formatChartValue(Number.NaN, "percent")).toBe("—");
    expect(formatAxisTick(Infinity, "count")).toBe("—");
  });
});

describe("formatAxisTick", () => {
  it("abbreviates currency to bn / m / k with at most one decimal", () => {
    expect(formatAxisTick(1_200_000, "currency")).toMatch(/^R\s1,2\sm$/);
    expect(formatAxisTick(450_000, "currency")).toMatch(/^R\s450\sk$/);
    expect(formatAxisTick(1_500, "currency")).toMatch(/^R\s1,5\sk$/);
    expect(formatAxisTick(950, "currency")).toMatch(/^R\s950$/);
    expect(formatAxisTick(0, "currency")).toMatch(/^R\s0$/);
    expect(formatAxisTick(2_300_000_000, "currency")).toMatch(/^R\s2,3\sbn$/);
  });

  it("rounds up into the next scale instead of '1 000 k'", () => {
    expect(formatAxisTick(999_960, "currency")).toMatch(/^R\s1\sm$/);
    expect(formatAxisTick(999.7, "currency")).toMatch(/^R\s1\sk$/);
  });

  it("signs negative currency and keeps a non-ZAR symbol truthful", () => {
    expect(formatAxisTick(-450_000, "currency")).toMatch(/^−R\s450\sk$/);
    const usd = formatAxisTick(1_200_000, "currency", "USD");
    expect(usd).not.toMatch(/^R\s/);
    expect(usd).toMatch(/\s1,2\sm$/);
  });

  it("hours, percent, points and count ticks", () => {
    expect(formatAxisTick(12, "hours")).toBe("12h");
    expect(formatAxisTick(12.5, "hours")).toBe("12,5h");
    expect(formatAxisTick(45.4, "percent")).toBe("45%");
    expect(formatAxisTick(-2.5, "points")).toBe("−2,5 pp");
    expect(formatAxisTick(1234567, "count")).toMatch(/^1\s234\s567$/);
  });
});
