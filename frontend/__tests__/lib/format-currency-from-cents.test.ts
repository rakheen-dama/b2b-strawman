import { describe, it, expect } from "vitest";
import { formatCurrency, formatCurrencyFromCents } from "@/lib/format";

// Phase 88 626F (F-4): one helper for integer-cents amounts (AI cost, fee estimates) instead of
// per-component `(cents) => formatCurrency(cents / 100)` wrappers.
describe("formatCurrencyFromCents", () => {
  it("formats cents as en-ZA rands", () => {
    expect(formatCurrencyFromCents(12345)).toMatch(/^R\s123,45$/);
  });

  it("formats zero", () => {
    expect(formatCurrencyFromCents(0)).toMatch(/^R\s0,00$/);
  });

  it("formats a negative amount the same way formatCurrency does", () => {
    const out = formatCurrencyFromCents(-12345);
    expect(out).toBe(formatCurrency(-123.45));
    expect(out).toMatch(/123,45/);
    expect(out).toContain("-");
  });

  it("passes the currency through", () => {
    expect(formatCurrencyFromCents(12345, "USD")).toBe(formatCurrency(123.45, "USD"));
  });
});
