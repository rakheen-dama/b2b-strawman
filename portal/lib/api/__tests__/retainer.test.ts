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
  it("subtracts one month for MONTHLY (typical mid-month case)", () => {
    // Period starts mid-month → previous period is the full prior month shifted
    // by the same day-offset: Feb 15 → Mar 14.
    expect(previousPeriodBounds("2026-03-15", "MONTHLY")).toEqual({
      from: "2026-02-15",
      to: "2026-03-14",
    });
  });

  it("clamps day for MONTHLY when prior month is shorter (Mar 31 → Feb 28)", () => {
    // Regression test: naive `Date.setUTCMonth(m-1)` on 2026-03-31 rolls over
    // into March (Feb 28 + 3 overflow days = Mar 3). The helper must clamp the
    // day to Feb's last valid day instead.
    expect(previousPeriodBounds("2026-03-31", "MONTHLY")).toEqual({
      from: "2026-02-28",
      to: "2026-03-30",
    });
  });

  it("clamps day for QUARTERLY when target month is shorter (May 31 → Feb 28)", () => {
    // 2026-05-31 − 3 months should land in February (28 days in 2026, a
    // non-leap year), not overflow into March.
    expect(previousPeriodBounds("2026-05-31", "QUARTERLY")).toEqual({
      from: "2026-02-28",
      to: "2026-05-30",
    });
  });

  it("clamps day for ANNUAL on leap-day (Feb 29 → Feb 28 in prior year)", () => {
    // 2024-02-29 (leap) minus 12 months → 2023-02-28 (non-leap clamp).
    expect(previousPeriodBounds("2024-02-29", "ANNUAL")).toEqual({
      from: "2023-02-28",
      to: "2024-02-28",
    });
  });

  it("handles year rollover for MONTHLY (Jan → Dec of prior year)", () => {
    // 2026-01-15 − 1 month → 2025-12-15. prevEnd = 2026-01-14 (day before
    // the current-period start).
    expect(previousPeriodBounds("2026-01-15", "MONTHLY")).toEqual({
      from: "2025-12-15",
      to: "2026-01-14",
    });
  });

  it("handles QUARTERLY typical case", () => {
    expect(previousPeriodBounds("2026-04-01", "QUARTERLY")).toEqual({
      from: "2026-01-01",
      to: "2026-03-31",
    });
  });

  it("handles ANNUAL typical case", () => {
    expect(previousPeriodBounds("2026-06-01", "ANNUAL")).toEqual({
      from: "2025-06-01",
      to: "2026-05-31",
    });
  });
});
