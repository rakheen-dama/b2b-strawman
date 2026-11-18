import { describe, it, expect } from "vitest";
import {
  FUND_SHARE_TOLERANCE_CENTS,
  fundShareCents,
  fundShareToleranceMessage,
  fundShareWithinTolerance,
  interestSplit,
  toCents,
} from "@/lib/trust-interest";

// The 5% and the R0,01 tolerance mirror the backend's
// TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT / STATUTORY_LPFF_SHARE_TOLERANCE (648B);
// the server is the authority — these pin the preview the firm sees before it records.

describe("fundShareCents — 5% of the gross, half-up to the cent", () => {
  it.each([
    [0.09, 0], // 0.0045 → 0.00
    [0.1, 1], // 0.005 → 0.01
    [0.29, 1], // 0.0145 → 0.01
    [0.3, 2], // 0.015 → 0.02 (half-up)
    [100, 500],
    [400, 2000],
    [12.34, 62], // 0.617 → 0.62
  ])("gross R%s → %s cents", (gross, cents) => {
    expect(fundShareCents(toCents(gross))).toBe(cents);
  });

  it("toCents absorbs float error", () => {
    expect(toCents(0.1 + 0.2)).toBe(30);
  });
});

describe("interestSplit", () => {
  it("splits a gross into the Fund's 5% and the client's 95%", () => {
    expect(interestSplit(400)).toEqual({ fundShare: 20, clientShare: 380 });
    expect(interestSplit(100)).toEqual({ fundShare: 5, clientShare: 95 });
    expect(interestSplit(0.3)).toEqual({ fundShare: 0.02, clientShare: 0.28 });
  });

  it("uses a typed Fund share when given", () => {
    expect(interestSplit(400, 20.01)).toEqual({ fundShare: 20.01, clientShare: 379.99 });
    expect(interestSplit(400, Number.NaN)).toEqual({ fundShare: 20, clientShare: 380 });
  });

  it("is null for a gross that is not a positive amount", () => {
    expect(interestSplit(undefined)).toBeNull();
    expect(interestSplit(0)).toBeNull();
    expect(interestSplit(-5)).toBeNull();
    expect(interestSplit(Number.NaN)).toBeNull();
  });
});

describe("fundShareWithinTolerance — within R0,01 of 5%", () => {
  it("is one cent", () => {
    expect(FUND_SHARE_TOLERANCE_CENTS).toBe(1);
  });

  it("accepts the computed share and one cent either side", () => {
    expect(fundShareWithinTolerance(400, 20)).toBe(true);
    expect(fundShareWithinTolerance(400, 19.99)).toBe(true);
    expect(fundShareWithinTolerance(400, 20.01)).toBe(true);
  });

  it("refuses two cents or more away", () => {
    expect(fundShareWithinTolerance(400, 19.98)).toBe(false);
    expect(fundShareWithinTolerance(400, 20.02)).toBe(false);
    expect(fundShareWithinTolerance(400, 21.5)).toBe(false);
  });

  it("states the computed share in the server's sentence", () => {
    expect(fundShareToleranceMessage(400)).toBe(
      "The Fund's share must be within R0,01 of 5% of the gross interest (R20,00)."
    );
  });
});
