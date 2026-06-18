/**
 * Interpret a raw `<input type="number">` budget edit (Rands) into a domain decision.
 *
 * Three outcomes, deliberately distinct so a typed value can never silently vanish
 * (AIVERIFY-012):
 *  - `"no-cap"`   — the field is genuinely empty → budget cleared → no spending cap.
 *  - `"value"`    — a whole-Rand amount ≥0 → `cents` is the rand-to-cents conversion.
 *  - `"invalid"`  — the user typed something present-but-unusable (a non-integer like
 *                   `1.5`, a negative like `-3`, or a value the browser blanked on a
 *                   step/min violation, reported as `value` non-empty + `valueAsNumber`
 *                   NaN). The caller must surface a field error, NOT coerce to "no cap".
 *
 * `value` is the DOM string (blanked to `""` by the browser on a step/min mismatch);
 * `valueAsNumber` is the browser's parse (NaN when unparseable). Reading both lets us
 * tell a deliberately-cleared field (`value === ""`) from a typed-but-rejected one.
 * Whole Rands only — `Math.round(rands * 100)` keeps cents clean (no sub-Rand precision).
 *
 * Lives in `lib/` (not the Client Component) so it's unit-testable without dragging in
 * the form's `server-only` action import chain.
 */
export type BudgetInputDecision =
  | { kind: "no-cap" }
  | { kind: "value"; cents: number }
  | { kind: "invalid" };

export function interpretBudgetInput(value: string, valueAsNumber: number): BudgetInputDecision {
  if (value.trim() === "") {
    // Genuinely empty field → no cap. (A step/min-blanked field also reads as "" here;
    // with step={1} + whole Rands a normal typed value is no longer blanked, so this
    // branch fires only for a deliberately cleared field.)
    return { kind: "no-cap" };
  }
  const rands = Number.isNaN(valueAsNumber) ? Number(value) : valueAsNumber;
  if (!Number.isFinite(rands) || rands < 0 || !Number.isInteger(rands)) {
    // Present but unusable: NaN (blanked/garbage), negative, or sub-Rand precision.
    return { kind: "invalid" };
  }
  return { kind: "value", cents: Math.round(rands * 100) };
}

export const BUDGET_INVALID_MESSAGE = "Enter a whole Rand amount of zero or more.";
