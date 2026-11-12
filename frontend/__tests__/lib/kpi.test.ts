import { describe, expect, it } from "vitest";

import { computeDelta, isoWeekKey, zeroFillTrend } from "@/lib/kpi";

describe("computeDelta", () => {
  it("percent: % change against previous", () => {
    expect(computeDelta(112.5, 100, "percent")).toBeCloseTo(12.5);
    expect(computeDelta(75, 100, "percent")).toBeCloseTo(-25);
  });

  it("percent: null when previous is 0 (no ∞%) or either side is null", () => {
    expect(computeDelta(10, 0, "percent")).toBeNull();
    expect(computeDelta(10, null, "percent")).toBeNull();
    expect(computeDelta(null, 10, "percent")).toBeNull();
    expect(computeDelta(undefined, 10, "percent")).toBeNull();
  });

  it("percent: a negative previous measures change against its magnitude", () => {
    expect(computeDelta(-50, -100, "percent")).toBeCloseTo(50);
  });

  it("points: current − previous when both are present", () => {
    expect(computeDelta(62.5, 58, "points")).toBeCloseTo(4.5);
    expect(computeDelta(40, 45, "points")).toBeCloseTo(-5);
    expect(computeDelta(30, 0, "points")).toBe(30);
  });

  it("points: null when either side is null; non-finite → null", () => {
    expect(computeDelta(62.5, null, "points")).toBeNull();
    expect(computeDelta(null, 58, "points")).toBeNull();
    expect(computeDelta(Number.NaN, 58, "points")).toBeNull();
    expect(computeDelta(Infinity, 58, "percent")).toBeNull();
  });
});

describe("isoWeekKey", () => {
  it.each([
    ["2026-11-12", "2026-W46"],
    ["2026-12-28", "2026-W53"],
    ["2027-01-02", "2026-W53"],
    ["2027-01-03", "2026-W53"],
    ["2027-01-04", "2027-W01"],
    ["2026-01-01", "2026-W01"],
    ["2025-12-29", "2026-W01"],
    ["2024-12-30", "2025-W01"],
    ["2021-01-03", "2020-W53"],
  ])('%s → %s (Postgres IYYY-"W"IW, Monday start)', (date, key) => {
    expect(isoWeekKey(date)).toBe(key);
  });

  it("maps a Date instant through its South African calendar date", () => {
    // Sunday 2027-01-03 22:30 UTC is Monday 2027-01-04 00:30 SAST → the next ISO week.
    expect(isoWeekKey(new Date("2027-01-03T22:30:00Z"))).toBe("2027-W01");
    expect(isoWeekKey(new Date("2027-01-03T12:00:00Z"))).toBe("2026-W53");
  });
});

describe("zeroFillTrend", () => {
  it("fills missing weeks with 0 and returns exactly 6 points oldest first", () => {
    const trend = [
      { period: "2026-W42", value: 12 },
      { period: "2026-W45", value: 7.5 },
    ];
    expect(zeroFillTrend(trend, "2026-11-12")).toEqual([
      { period: "2026-W41", value: 0 },
      { period: "2026-W42", value: 12 },
      { period: "2026-W43", value: 0 },
      { period: "2026-W44", value: 0 },
      { period: "2026-W45", value: 7.5 },
      { period: "2026-W46", value: 0 },
    ]);
  });

  it("drops a 7th, older week returned by the backend's to − 6 weeks window", () => {
    const trend = ["W40", "W41", "W42", "W43", "W44", "W45", "W46"].map((w, i) => ({
      period: `2026-${w}`,
      value: i + 1,
    }));
    const filled = zeroFillTrend(trend, "2026-11-12");
    expect(filled).toHaveLength(6);
    expect(filled[0]).toEqual({ period: "2026-W41", value: 2 });
    expect(filled[5]).toEqual({ period: "2026-W46", value: 7 });
  });

  it("spans the 53-week ISO year 2026 into January 2027", () => {
    expect(zeroFillTrend([], "2027-01-02").map((p) => p.period)).toEqual([
      "2026-W48",
      "2026-W49",
      "2026-W50",
      "2026-W51",
      "2026-W52",
      "2026-W53",
    ]);
    const crossing = zeroFillTrend([{ period: "2026-W53", value: 3 }], "2027-01-05");
    expect(crossing.map((p) => p.period)).toEqual([
      "2026-W49",
      "2026-W50",
      "2026-W51",
      "2026-W52",
      "2026-W53",
      "2027-W01",
    ]);
    expect(crossing[4].value).toBe(3);
  });

  it("returns six zeros for an empty trend and honours n", () => {
    const empty = zeroFillTrend([], "2026-11-12");
    expect(empty).toHaveLength(6);
    expect(empty.every((p) => p.value === 0)).toBe(true);
    expect(zeroFillTrend([], "2026-11-12", 3).map((p) => p.period)).toEqual([
      "2026-W44",
      "2026-W45",
      "2026-W46",
    ]);
  });
});
