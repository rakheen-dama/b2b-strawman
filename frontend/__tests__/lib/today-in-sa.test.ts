import { describe, expect, it } from "vitest";
import { isoDateInSA, todayInSA, KAZI_TIME_ZONE } from "@b2mash/shared/date";
import { isOverdue } from "@b2mash/shared/format";
import { resolveMyWorkDateRange } from "@/app/(app)/org/[slug]/my-work/my-work-date-range";

// Every case passes the instant explicitly, so these hold in any process zone (G88-F49).
describe("todayInSA", () => {
  it("is the business zone", () => {
    expect(KAZI_TIME_ZONE).toBe("Africa/Johannesburg");
  });

  it("dates 22:30 UTC as the next SAST day", () => {
    expect(todayInSA(new Date("2026-10-29T22:30:00Z"))).toBe("2026-10-30");
  });

  it("keeps the same day just before SAST midnight", () => {
    expect(todayInSA(new Date("2026-10-29T21:59:59Z"))).toBe("2026-10-29");
  });

  it("rolls the year at SAST midnight on 31 December", () => {
    expect(todayInSA(new Date("2026-12-31T22:00:00Z"))).toBe("2027-01-01");
  });

  it("converts any stored instant to its SAST calendar date", () => {
    expect(isoDateInSA(new Date("2026-10-31T23:30:00Z"))).toBe("2026-11-01");
  });
});

describe("isOverdue (shared)", () => {
  it("compares against the SA calendar date", () => {
    const today = todayInSA();
    expect(isOverdue(today)).toBe(false);
    expect(isOverdue("2000-01-01")).toBe(true);
    expect(isOverdue("not-a-date")).toBe(false);
  });
});

describe("resolveMyWorkDateRange (G88-F49)", () => {
  it("uses the SAST week: Monday 2 Nov 00:30 SAST is still Sunday 1 Nov in UTC", () => {
    const mondayEarlySast = new Date("2026-11-01T22:30:00Z");
    expect(resolveMyWorkDateRange({}, mondayEarlySast)).toEqual({
      from: "2026-11-02",
      to: "2026-11-08",
    });
  });

  it("keeps an explicit range", () => {
    expect(resolveMyWorkDateRange({ from: "2026-01-05", to: "2026-01-11" })).toEqual({
      from: "2026-01-05",
      to: "2026-01-11",
    });
  });
});
