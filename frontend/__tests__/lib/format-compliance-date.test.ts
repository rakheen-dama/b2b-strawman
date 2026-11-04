import { afterEach, describe, expect, it, vi } from "vitest";
import { formatComplianceDateWithTime } from "@/lib/format";

// C3 (Phase 89 631B, ADR-358): compliance/requests/[id] is server-rendered on a UTC Node runtime.
// A zone-less formatter printed the UTC wall clock there. The helper must print South African time
// whatever the process zone. Node re-reads process.env.TZ on assignment (Date and Intl both follow),
// so stubbing TZ in-process exercises each zone inside the normal `pnpm test` run — on a SAST
// laptop too. The file is also run under `TZ=UTC` and `TZ=Pacific/Kiritimati` (see PR).
const INSTANT = "2026-11-03T23:30:00Z"; // 01:30 SAST on 4 Nov; 23:30 on 3 Nov in UTC; 13:30 on 4 Nov in Kiritimati

afterEach(() => {
  vi.unstubAllEnvs();
});

describe.each(["UTC", "Pacific/Kiritimati", "America/New_York", "Africa/Johannesburg"])(
  "formatComplianceDateWithTime under TZ=%s",
  (zone) => {
    it("renders the South African wall clock", () => {
      vi.stubEnv("TZ", zone);
      // Guard against a vacuous pass: the stub must really have moved the process zone.
      expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(zone);

      expect(formatComplianceDateWithTime(INSTANT)).toBe("04 Nov 2026, 01:30");
    });

    it("rolls the year on the SAST calendar", () => {
      vi.stubEnv("TZ", zone);
      expect(formatComplianceDateWithTime("2026-12-31T22:15:00Z")).toBe("01 Jan 2027, 00:15");
    });
  }
);

describe("formatComplianceDateWithTime (input handling)", () => {
  it("returns an empty string for invalid input", () => {
    expect(formatComplianceDateWithTime("")).toBe("");
    expect(formatComplianceDateWithTime("not-a-date")).toBe("");
  });
});
