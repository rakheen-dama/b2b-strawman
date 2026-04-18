import { describe, it, expect } from "vitest";
import { formatCurrency, formatFileSize } from "@/lib/format";

describe("formatFileSize", () => {
  it("returns '0 B' for zero bytes", () => {
    expect(formatFileSize(0)).toBe("0 B");
  });

  it("formats bytes without decimal for values >= 10", () => {
    expect(formatFileSize(500)).toBe("500 B");
  });

  it("formats kilobytes with one decimal for small values", () => {
    expect(formatFileSize(1024)).toBe("1.0 KB");
  });

  it("formats megabytes", () => {
    expect(formatFileSize(1.5 * 1024 * 1024)).toBe("1.5 MB");
  });

  it("rounds large values in a unit", () => {
    expect(formatFileSize(100 * 1024 * 1024)).toBe("100 MB");
  });

  it("formats gigabytes", () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toBe("1.0 GB");
  });
});

describe("formatCurrency", () => {
  // Regression test for GAP-L-12: Node small-icu silently formats en-ZA as en-US
  // (e.g. "R 18,750.00") while browsers with full ICU render "R 18 750,00".
  // The intl-polyfill imported from format.ts must normalise both paths to the
  // browser output. Happy-dom (vitest env) uses host Node Intl, so this test
  // reproduces the SSR bug without the polyfill and passes with it.
  it("formats ZAR with narrow-no-break-space thousands and comma decimal (en-ZA)", () => {
    const out = formatCurrency(18750, "ZAR");
    expect(out).toMatch(/^R\u00A018\u00A0750,00$/);
  });

  it("formats USD with US-style separators", () => {
    const out = formatCurrency(18750, "USD");
    expect(out).toMatch(/^\$18,750\.00$/);
  });

  it("formats zero ZAR with correct en-ZA separators", () => {
    expect(formatCurrency(0, "ZAR")).toMatch(/^R\u00A00,00$/);
  });
});
