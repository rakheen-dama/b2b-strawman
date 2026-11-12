import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { DateCell } from "@/components/data-table/cells";
import { formatDate } from "@/lib/format";

// 641A F2: DateCell kind="instant" used the zone-less `formatDate`, so the UTC SSR render and a
// browser elsewhere could print different days (hydration mismatch) and CSV dates varied by
// viewer. Node re-reads process.env.TZ on assignment (Date and Intl follow), so stubbing TZ
// in-process exercises each zone inside the normal `pnpm test` run.
const LATE_INSTANT = "2026-05-01T23:30:00Z"; // 01:30 SAST on 2 May; still 1 May in UTC and New York
const YEAR_ROLL = "2026-12-31T22:15:00Z"; // 00:15 SAST on 1 Jan 2027; still 2026 in UTC
const ZONES = ["UTC", "America/New_York", "Africa/Johannesburg", "Pacific/Kiritimati"];

/** Moves the process zone, guarding against a vacuous stub. */
function inZone(zone: string) {
  vi.stubEnv("TZ", zone);
  expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe(zone);
}

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

const textOf = (node: ReactElement) => render(node).container.textContent;

describe("DateCell — instants on the South African calendar", () => {
  it("a zone-less format would differ by viewer (the stub is not vacuous)", () => {
    inZone("UTC");
    expect(formatDate(LATE_INSTANT)).toBe("1 May 2026");
    inZone("Pacific/Kiritimati");
    expect(formatDate(LATE_INSTANT)).toBe("2 May 2026");
  });

  it.each(ZONES)("kind=instant renders the SA date under TZ=%s", (zone) => {
    inZone(zone);
    expect(textOf(<DateCell value={LATE_INSTANT} />)).toBe("2 May 2026");
  });

  it("rolls the year on the SA calendar under a UTC runtime", () => {
    inZone("UTC");
    expect(textOf(<DateCell value={YEAR_ROLL} kind="instant" />)).toBe("1 Jan 2027");
  });

  it.each(ZONES)("kind=local renders the calendar date unchanged under TZ=%s", (zone) => {
    inZone("Africa/Johannesburg");
    const expected = textOf(<DateCell value="2026-05-01" kind="local" />);
    cleanup();
    inZone(zone);
    expect(textOf(<DateCell value="2026-05-01" kind="local" />)).toBe(expected);
    expect(expected).toMatch(/May 2026/);
  });

  it.each([null, undefined, "", "not-a-date"])("%o renders the — placeholder", (value) => {
    expect(textOf(<DateCell value={value} />)).toBe("—");
  });

  it("an unparseable kind=local value renders the — placeholder", () => {
    expect(textOf(<DateCell value="not-a-date" kind="local" />)).toBe("—");
  });
});
