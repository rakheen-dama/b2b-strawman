# AIVERIFY-012 — Monthly Budget input silently drops step-mismatched values

**Stage:** V9.4 / V9.5 (budget-exhaustion pre-LLM 403)
**Severity:** Medium
**Owner:** Product (spec) → Dev (implement)
**Type:** Frontend-only. No backend change, no migration.
**Related:** Same silent-drop UX class as AIVERIFY-010 (a user-entered value vanishing with no feedback), but a *different* field-level cause.

---

## Problem (QA evidence)

QA, during V9.4 verify on org `verifain-attorneys` / tenant `tenant_c6107524c9b4` (`qa_cycle/checkpoint-results/V9-final.md` §V9.4/9.5):

- The Monthly Budget `<Input type="number">` has `step={100}` (Rands), so the smallest non-zero settable budget is **R100 (10000c)**.
- Current month AI spend is **R22.68** — *below* R100. There is no step-aligned budget value that sits below spend, so the scenario's "set budget below spend" (to drive the pre-LLM 403) is **unreachable through the UI**.
- Worse — any step-mismatched value (R1 / R15 / R20 / R33) **fails to save with NO error surfaced**: no success message, no `FormMessage`, no `onInvalid` toast, and the DB `profile_version` / `updated_at` stay frozen. Step-*valid* values (4000 / 5000 / 6000) persist reliably. Reproduced deterministically by QA.

Two distinct harms:
1. **UX bug:** a user types a budget, clicks Save, sees no error, and the budget is silently unchanged.
2. **Test blocker:** V9.4/9.5 cannot drive a below-spend budget through the UI to observe the pre-LLM 403 + no-new-spend.

---

## Root Cause

Two confirmed mechanisms, both frontend.

### 1. Step granularity (`step={100}`) — the test blocker

`frontend/components/ai/ai-profile-form.tsx:543` — the budget input declares `step={100}`. With `min={0}`, the only valid values are multiples of R100. R100 minimum is arbitrary product granularity; it blocks any sub-R100 budget.

### 2. Step-mismatch → silent null → keep-old — the UX bug

The silent drop is a chain across three layers, all of which individually behave "reasonably" but compose into a swallow:

- **Browser:** for `<input type="number">`, when the typed value violates the `step` (or `min`) constraint, the DOM `value` getter returns an **empty string** (`""`), not the typed digits. So the React `onChange` at `ai-profile-form.tsx:554-566` sees `e.target.value.trim() === ""` and takes the cleared-field branch:
  ```
  if (raw === "") { field.onChange(undefined); return; }   // ai-profile-form.tsx:556-559
  ```
  → `monthlyBudgetCents` is set to `undefined`, i.e. "no cap". The user's typed R20 never reaches the field value.

- **Zod (no rejection):** `frontend/lib/schemas/ai-profile.ts:29-33` — the `monthlyBudgetCents` preprocess normalizes `undefined`/`null`/`""`/NaN → `undefined`, then `.optional()` accepts it. `undefined` is a *valid* optional value, so the resolver passes. Because there is **no zod rejection**, `handleSubmit`'s `onInvalid` (`ai-profile-form.tsx:162-168`, added for AIVERIFY-010) never fires — `onInvalid` only catches resolver *failures*, and a step-mismatched budget produces a resolver *success* (`undefined`). **This is why the AIVERIFY-010 `onInvalid` does not catch this defect.**

- **Server action (no rejection):** `frontend/app/(app)/org/[slug]/settings/ai/actions.ts:19-22` re-`safeParse`s the payload. Budget is optional, so `undefined`/absent passes. The PUT is sent with `monthlyBudgetCents` effectively `null`/absent.

- **Backend (keep-old, correct):** `backend/.../integration/ai/profile/AiFirmProfile.java:109-110`:
  ```java
  this.monthlyBudgetCents =
      monthlyBudgetCents != null ? monthlyBudgetCents : this.monthlyBudgetCents;
  ```
  This is **correct domain behaviour** — `null` means "field omitted, don't change". The bug is **not** here. The frontend must not send `null`/absent for a budget the user actually typed.

**Net:** user types R20 → browser reports `""` → field becomes `undefined` → zod passes (optional) → PUT omits budget → entity keeps old value → no feedback. Silent no-op.

### No backend granularity constraint to loosen

Confirmed there is **no** `@Min`/`multipleOf`/step-type constraint on the backend budget field:
- `backend/.../integration/ai/profile/UpdateAiFirmProfileRequest.java:6-15` — plain `Long monthlyBudgetCents`, no bean-validation annotations.
- `backend/.../integration/ai/profile/AiFirmProfile.java:52` — plain `private Long monthlyBudgetCents;`.
The R100 granularity lives **only** in the frontend `step={100}`. Loosening it is purely a frontend change.

---

## Fix (Frontend only)

**File:** `frontend/components/ai/ai-profile-form.tsx` (budget field, ~lines 540-567).

### A. Loosen granularity — `step={1}` (orchestrator-authorized architectural decision)

Change `step={100}` → `step={1}` on the budget `<Input>` (`ai-profile-form.tsx:543`). Any positive integer Rand value becomes settable, which both removes the arbitrary R100 floor and is required to exercise V9.4/9.5 enforcement below the R22.68 spend.

`step={1}` keeps the field integer-Rand (the value↔cents conversion at `ai-profile-form.tsx:549-553` and `554-566` multiplies/divides by 100, so whole Rands map cleanly to cents). Do **not** allow sub-Rand precision — keep the `Math.round(rands * 100)` so cents stay clean.

### B. Don't silently coerce a typed value to "no cap"; surface an error if truly unparseable

`step={1}` alone fixes the *blocker* and the *common* case (whole-Rand values now satisfy `step`, so the browser stops emptying the value). But to close the **defect class** robustly — so a non-empty input never again silently vanishes — also harden the onChange path so it distinguishes "user cleared the field" from "user typed something the browser/parse rejected":

- Read the user's keystrokes from a source that is **not** blanked by constraint violation. Because the DOM `value` getter empties on step/min mismatch, rely on a robust parse of what is actually present and treat a genuinely-empty field as "no cap" only when the field is truly empty. With `step={1}` + integer Rands, a normal typed value will not be blanked; the residual concern is values like `"1.5"` or `"-3"`. For those:
  - If the field is non-empty but does **not** resolve to a finite, ≥0 number, set a field-level error via `form.setError("monthlyBudgetCents", { message: "Enter a whole Rand amount of zero or more." })` (and clear it with `form.clearErrors("monthlyBudgetCents")` on the next valid keystroke) so `<FormMessage />` (`ai-profile-form.tsx:573`) renders it. The principle: **a non-empty budget the user types either saves as a real number, or shows an error — it never silently becomes "no cap".**
- Keep the existing empty-string → `undefined` ("no cap") branch for a *deliberately cleared* field — that is correct and must stay.

Implementation note for Dev: prefer reading `e.target.valueAsNumber` / `e.target.value` together — if `value` is non-empty but `valueAsNumber` is `NaN` (or `value` non-empty but the constraint blanked it), that's the "typed but invalid" case → `setError`, not `onChange(undefined)`. Verify the exact browser behaviour for `step={1}` step-mismatch in the live repro before finalizing the branch logic (don't ship on assumption — reproduce the R20 case post-change and confirm it now saves).

### What NOT to change
- **No backend change.** The entity keep-old-on-null at `AiFirmProfile.java:109-110` is correct.
- **No schema change** to `ai-profile.ts` — the preprocess (`:29-33`) already normalizes; the fix is making the *input* deliver a real number for typed values rather than relying on the schema to reject. (If Dev finds it cleaner to also tighten the schema, that's acceptable, but the load-bearing fix is the onChange + `step={1}`.)
- Keep the rand↔cents conversion (`Math.round(rands * 100)` on write, `Math.round(cents / 100)` on display) intact.

---

## Scope

| Area | File | Change |
|------|------|--------|
| Frontend | `frontend/components/ai/ai-profile-form.tsx` | `step={100}` → `step={1}`; harden budget `onChange` so a non-empty-but-invalid value sets a `FormMessage` error instead of coercing to `undefined`/"no cap" |

One PR. Frontend-only. No migration, no backend.

---

## Verification (LIVE — observed, not inferred)

Run against the live stack as an AI-manage user (owner/admin) on a firm with existing AI spend (e.g. `verifain-attorneys`, current spend ~R22.68).

1. **Sub-spend budget now settable + persists:**
   - Settings → AI → set Monthly Budget to **R20** (below the R22.68 spend). Save.
   - Expect the success message ("Configuration saved successfully.").
   - Confirm in DB: `monthly_budget_cents = 2000` AND `profile_version` incremented (i.e. the PUT actually fired and the entity updated — not frozen).
2. **Enforcement 403 (V9.4/9.5):**
   - With budget = R20 and spend already R22.68 (> budget), run any AI skill.
   - Expect a **pre-LLM 403** (budget-exhausted) and **NO new spend** — confirm spend in the cost panel / DB is unchanged after the attempt and no new `ai_llm_calls` row was created.
3. **Bad input now shows an error (not a silent drop):**
   - Type a deliberately-invalid budget (e.g. `1.5` or `-3`). Save (or blur).
   - Expect a visible `FormMessage` error under the budget field, and the budget value **unchanged** in DB. No silent no-op.
4. **Restore:** set budget back to **R5000** and Save; confirm it persists (`monthly_budget_cents = 500000`, `profile_version` incremented).

All four must be **observed** (browser + DB), per the PASS-means-observed gate. Mark `DEFERRED` if the live run can't complete — do not infer.

---

## Effort

Small. Single-file frontend change (one attribute + ~10 lines of onChange hardening). The work is in the **live verification** (driving the 403 + confirming no silent drop), not the code.
