import { describe, it, expect } from "vitest";
import {
  formatHours,
  previousPeriodBounds,
} from "@/lib/api/retainer";

describe("formatHours", () => {
  it("formats decimal hours with two decimals and 'h' suffix", () => {
    expect(formatHours(0)).toBe("0.00h");
    expect(formatHours(1.5)).toBe("1.50h");
    expect(formatHours(12.345)).toBe("12.35h");
  });

  it("returns '0.00h' for non-finite values", () => {
    expect(formatHours(Number.NaN)).toBe("0.00h");
    expect(formatHours(Number.POSITIVE_INFINITY)).toBe("0.00h");
    expect(formatHours(Number.NEGATIVE_INFINITY)).toBe("0.00h");
  });
});

describe("previousPeriodBounds", () => {
  // Clamping/rollover regression cases: these cover the behaviour of the
  // day-clamping + year-rollover logic. Kept together so breakages point
  // straight at the responsible branch.
  it.each([
    // Mar 31 → Feb 28 (naive setUTCMonth would overflow into March)
    [
      "2026-03-31",
      "MONTHLY" as const,
      { from: "2026-02-28", to: "2026-03-30" },
    ],
    // May 31 − 3 months → Feb 28 (non-leap year clamp)
    [
      "2026-05-31",
      "QUARTERLY" as const,
      { from: "2026-02-28", to: "2026-05-30" },
    ],
    // Feb 29 leap-day − 12 months → Feb 28 prior (non-leap) year
    [
      "2024-02-29",
      "ANNUAL" as const,
      { from: "2023-02-28", to: "2024-02-28" },
    ],
    // Jan 15 − 1 month → Dec 15 of prior year
    [
      "2026-01-15",
      "MONTHLY" as const,
      { from: "2025-12-15", to: "2026-01-14" },
    ],
  ])(
    "clamps/rolls-over correctly for %s (%s)",
    (start, period, expected) => {
      expect(previousPeriodBounds(start, period)).toEqual(expected);
    },
  );

  // Typical (no clamping required) cases across each period type.
  it.each([
    [
      "2026-03-15",
      "MONTHLY" as const,
      { from: "2026-02-15", to: "2026-03-14" },
    ],
    [
      "2026-04-01",
      "QUARTERLY" as const,
      { from: "2026-01-01", to: "2026-03-31" },
    ],
    [
      "2026-06-01",
      "ANNUAL" as const,
      { from: "2025-06-01", to: "2026-05-31" },
    ],
  ])(
    "computes previous bounds for typical case %s (%s)",
    (start, period, expected) => {
      expect(previousPeriodBounds(start, period)).toEqual(expected);
    },
  );
});
