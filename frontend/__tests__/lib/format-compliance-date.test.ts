import { afterEach, describe, expect, it, vi } from "vitest";
import { formatComplianceDateWithTime } from "@/lib/format";

// C3 (Phase 89 631B, ADR-358): compliance/requests/[id] is server-rendered on a UTC Node runtime.
// A zone-less formatter printed the UTC wall clock there. The helper must print South African time
// whatever the process zone. Node re-reads process.env.TZ on assignment (Date and Intl both follow),
// so stubbing TZ in-process exercises each zone inside the normal `pnpm test` run — on a SAST
// laptop too. The file is also run under `TZ=UTC` and `TZ=Pacific/Kiritimati` (see PR).
const INSTANT = "2026-11-03T23:30:00Z"; // 01:30 SAST on 4 Nov; 23:30 on 3 Nov in UTC; 13:30 on 4 Nov in Kiritimati
const YEAR_ROLL = "2026-12-31T22:15:00Z"; // 00:15 SAST on 1 Jan 2027; still 2026 in UTC

/** Runs `fn` with the process zone moved to `zone`, guarding against a vacuous stub. */
function inZone<T>(zone: string, fn: () => T): T {
  vi.stubEnv("TZ", zone);
  expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(zone);
  return fn();
}

afterEach(() => {
  vi.unstubAllEnvs();
});

// The one exact-string pin (task 631.8). Date formatting uses the runtime's own ICU data (the
// @formatjs polyfill covers NumberFormat only), so the per-zone checks below compare against the
// SAST-zone render from the same runtime instead of re-pinning ICU punctuation.
it("renders the spec string on a UTC runtime", () => {
  expect(inZone("UTC", () => formatComplianceDateWithTime(INSTANT))).toBe("04 Nov 2026, 01:30");
});

describe.each(["UTC", "Pacific/Kiritimati", "America/New_York"])(
  "formatComplianceDateWithTime under TZ=%s",
  (zone) => {
    it("renders the South African wall clock", () => {
      const sast = inZone("Africa/Johannesburg", () => formatComplianceDateWithTime(INSTANT));
      const actual = inZone(zone, () => formatComplianceDateWithTime(INSTANT));
      expect(actual).toBe(sast);
      expect(actual).toContain("04 Nov 2026");
      expect(actual).toContain("01:30");
    });

    it("rolls the year on the SAST calendar", () => {
      const sast = inZone("Africa/Johannesburg", () => formatComplianceDateWithTime(YEAR_ROLL));
      const actual = inZone(zone, () => formatComplianceDateWithTime(YEAR_ROLL));
      expect(actual).toBe(sast);
      expect(actual).toContain("01 Jan 2027");
      expect(actual).toContain("00:15");
    });
  }
);

describe("formatComplianceDateWithTime (input handling)", () => {
  it("returns an empty string for invalid input", () => {
    expect(formatComplianceDateWithTime("")).toBe("");
    expect(formatComplianceDateWithTime("not-a-date")).toBe("");
  });
});
