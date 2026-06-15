import { describe, it, expect } from "vitest";
import { interpretBudgetInput } from "../ai-budget-input";

/**
 * AIVERIFY-012 — the Monthly Budget input used to silently drop typed-but-invalid
 * values: a sub-R100 entry under `step={100}` was blanked by the browser, read as an
 * empty field, and coerced to "no cap" with no feedback (DB unchanged). The fix splits
 * the onChange decision into three explicit outcomes via `interpretBudgetInput`, so a
 * typed value can never vanish silently — it either commits a real number or is flagged
 * invalid (the caller surfaces a FormMessage).
 */
describe("interpretBudgetInput (AIVERIFY-012)", () => {
  it("treats a genuinely empty field as 'no cap'", () => {
    expect(interpretBudgetInput("", NaN)).toEqual({ kind: "no-cap" });
    expect(interpretBudgetInput("   ", NaN)).toEqual({ kind: "no-cap" });
  });

  it("converts a whole-Rand amount to cents", () => {
    expect(interpretBudgetInput("5000", 5000)).toEqual({ kind: "value", cents: 500000 });
    expect(interpretBudgetInput("20", 20)).toEqual({ kind: "value", cents: 2000 });
  });

  it("allows a sub-R100 whole-Rand budget (the value the old step=100 blocked)", () => {
    // R20 < R100 — previously unreachable; needed to drive the V9.4/9.5 enforcement 403.
    expect(interpretBudgetInput("20", 20)).toEqual({ kind: "value", cents: 2000 });
  });

  it("treats zero as a valid (no-spend) cap, not empty", () => {
    expect(interpretBudgetInput("0", 0)).toEqual({ kind: "value", cents: 0 });
  });

  it("flags a non-integer (sub-Rand precision) as invalid rather than silently dropping it", () => {
    expect(interpretBudgetInput("1.5", 1.5)).toEqual({ kind: "invalid" });
  });

  it("flags a negative budget as invalid", () => {
    expect(interpretBudgetInput("-3", -3)).toEqual({ kind: "invalid" });
  });

  it("flags a present-but-unparseable value (browser reported NaN) as invalid, not 'no cap'", () => {
    // The core AIVERIFY-012 regression guard: non-empty + NaN must NOT become "no cap".
    expect(interpretBudgetInput("abc", NaN)).toEqual({ kind: "invalid" });
  });
});
