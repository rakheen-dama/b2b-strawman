import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDate, formatDateShort } from "@/lib/format";
import { inZone } from "@/__tests__/helpers/time-zone";

// A yyyy-MM-dd value (a court date, a fee-note due date, a rate's effective date) is a calendar
// date, not an instant. `new Date("2026-11-27")` is UTC midnight, so a zone-less formatter printed
// the day before in every browser west of UTC ("26 Nov 2026" in New York for a 27 Nov hearing).
// The helpers must print the same calendar date in every zone. Node re-reads process.env.TZ on
// assignment, so stubbing TZ exercises each zone inside the normal `pnpm test` run.

afterEach(() => {
  vi.unstubAllEnvs();
});

const ZONES = [
  "UTC",
  "Africa/Johannesburg",
  "America/New_York",
  "Asia/Dubai",
  "Pacific/Pago_Pago", // UTC-11: the westernmost populated offset
  "Pacific/Kiritimati", // UTC+14: the easternmost
];

describe.each(ZONES)("date-only values under TZ=%s", (zone) => {
  it("formatDate prints the stored calendar date", () => {
    inZone(zone);
    expect(formatDate("2026-11-27")).toBe("27 Nov 2026");
    expect(formatDate("2026-11-23")).toBe("23 Nov 2026");
  });

  it("formatDate rolls neither the month nor the year", () => {
    inZone(zone);
    expect(formatDate("2027-01-01")).toBe("1 Jan 2027");
    expect(formatDate("2026-03-01")).toBe("1 Mar 2026");
  });

  it("formatDateShort prints the stored calendar date", () => {
    inZone(zone);
    expect(formatDateShort("2026-11-27")).toBe("27 Nov");
    expect(formatDateShort("2027-01-01")).toBe("1 Jan");
  });
});

describe("date-only values across a US clock change (New York, 8 Mar / 1 Nov 2026)", () => {
  it("prints the stored calendar date on both sides of each change", () => {
    inZone("America/New_York");
    expect(formatDate("2026-03-08")).toBe("8 Mar 2026");
    expect(formatDate("2026-03-09")).toBe("9 Mar 2026");
    expect(formatDate("2026-11-01")).toBe("1 Nov 2026");
    expect(formatDate("2026-11-02")).toBe("2 Nov 2026");
  });
});

describe("instants keep the runtime zone (unchanged behaviour)", () => {
  // 2026-11-28T03:00Z is 27 Nov 22:00 in New York and 28 Nov 05:00 SAST.
  const INSTANT = "2026-11-28T03:00:00Z";

  it("formats an ISO instant on the runtime calendar", () => {
    inZone("America/New_York");
    expect(formatDate(INSTANT)).toBe("27 Nov 2026");
    expect(formatDateShort(INSTANT)).toBe("27 Nov");
    inZone("Africa/Johannesburg");
    expect(formatDate(INSTANT)).toBe("28 Nov 2026");
  });

  it("formats a Date on the runtime calendar", () => {
    inZone("America/New_York");
    expect(formatDate(new Date(INSTANT))).toBe("27 Nov 2026");
  });
});

describe("input handling", () => {
  it("returns an empty string for empty or invalid input", () => {
    expect(formatDate("")).toBe("");
    expect(formatDate("not-a-date")).toBe("");
    expect(formatDate("2026-13-45")).toBe("");
    expect(formatDateShort("")).toBe("");
    expect(formatDateShort("2026-02-30x")).toBe("");
  });
});
