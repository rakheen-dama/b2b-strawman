import { describe, it, expect } from "vitest";
import { aiProfileSchema } from "../ai-profile";

const base = {
  practiceAreas: ["Litigation"],
  jurisdiction: "ZA-WC",
  riskCalibration: "CONSERVATIVE" as const,
  preferredModel: "claude-sonnet-4-6" as const,
};

function parseBudget(monthlyBudgetCents: unknown) {
  return aiProfileSchema.safeParse({ ...base, monthlyBudgetCents });
}

describe("aiProfileSchema.monthlyBudgetCents (AIVERIFY-010)", () => {
  it("accepts a valid budget in cents", () => {
    const result = parseBudget(500_000);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBe(500_000);
  });

  it("accepts zero as a deliberate cap", () => {
    const result = parseBudget(0);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBe(0);
  });

  it("treats a cleared field (undefined) as no cap", () => {
    const result = parseBudget(undefined);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBeUndefined();
  });

  // Regression: a cleared number input could yield NaN/null/"" — none of these must
  // fail validation and silently abort the whole form submit. They all mean "no cap".
  it.each([NaN, null, ""])("normalises %p to undefined instead of failing", (value) => {
    const result = parseBudget(value);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBeUndefined();
  });

  it("rejects a negative budget with a visible message", () => {
    const result = parseBudget(-100);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0]?.message).toBe("Budget must be zero or positive");
    }
  });

  it("coerces a numeric string to a number", () => {
    const result = parseBudget("500000");
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBe(500_000);
  });
});
