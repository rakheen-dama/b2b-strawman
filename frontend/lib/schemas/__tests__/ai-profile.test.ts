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

// AIVERIFY-010 (reopened): the form's onSubmit coerces empty notes textareas via
// `value || null`, producing `null`. The server action re-runs aiProfileSchema.safeParse
// on that payload BEFORE calling the backend PUT. The schema must therefore accept `null`
// for the two optional notes fields, or the entire save is silently dropped (no PUT sent).
describe("aiProfileSchema notes fields accept null (AIVERIFY-010)", () => {
  it("accepts houseStyleNotes: null (the exact failing case)", () => {
    const result = aiProfileSchema.safeParse({ ...base, houseStyleNotes: null });
    expect(result.success).toBe(true);
  });

  it("accepts feeEstimationNotes: null (the exact failing case)", () => {
    const result = aiProfileSchema.safeParse({ ...base, feeEstimationNotes: null });
    expect(result.success).toBe(true);
  });

  it("still accepts empty string and undefined for notes", () => {
    expect(
      aiProfileSchema.safeParse({ ...base, houseStyleNotes: "", feeEstimationNotes: undefined })
        .success
    ).toBe(true);
    expect(aiProfileSchema.safeParse({ ...base }).success).toBe(true);
  });

  it("still accepts a populated notes string", () => {
    const result = aiProfileSchema.safeParse({
      ...base,
      houseStyleNotes: "Use formal tone.",
      feeEstimationNotes: "R2,500/hour standard rate.",
    });
    expect(result.success).toBe(true);
  });

  it("still enforces the 2000-char cap on notes", () => {
    const tooLong = "x".repeat(2001);
    expect(aiProfileSchema.safeParse({ ...base, houseStyleNotes: tooLong }).success).toBe(false);
    expect(aiProfileSchema.safeParse({ ...base, feeEstimationNotes: tooLong }).success).toBe(false);
  });

  // End-to-end repro: the full onSubmit-shaped payload (empty notes coerced to null via
  // `value || null`, budget edited) must validate, proving the server action would now
  // reach the backend PUT instead of returning { success:false, error:"Invalid input" }.
  it("accepts the full onSubmit-shaped payload with empty notes coerced to null", () => {
    const formData = {
      practiceAreas: ["Litigation"],
      jurisdiction: "ZA-WC",
      riskCalibration: "CONSERVATIVE" as const,
      preferredModel: "claude-sonnet-4-6" as const,
      ficaRequirements: {
        enhancedDueDiligence: false,
        pepScreening: false,
        sourceOfFundsRequired: false,
      },
      monthlyBudgetCents: 700_000,
      coldStartCompleted: true,
    };
    const emptyNotes = "";
    const submitData = {
      ...formData,
      houseStyleNotes: emptyNotes || null,
      feeEstimationNotes: emptyNotes || null,
    };
    const result = aiProfileSchema.safeParse(submitData);
    expect(result.success).toBe(true);
    if (result.success) expect(result.data.monthlyBudgetCents).toBe(700_000);
  });
});
