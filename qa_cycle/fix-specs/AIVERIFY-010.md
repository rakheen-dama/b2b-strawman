# AIVERIFY-010 (REOPENED) — AI Monthly Budget save silently fails; no `PUT /api/ai/profile` reaches the backend

- **Stage**: V9.3 (cost metering & budget enforcement) — BLOCKER on V9.4 / V9.5
- **Severity**: BLOCKER
- **Owner**: Dev
- **Scope**: Frontend only (no backend change, no migration)
- **Status**: SPEC_READY (second attempt — PR #1446 / `ceb51c856` fixed the wrong layer)
- **Effort**: S (one-line root fix + schema hardening + tests)

---

## Problem (ground truth)

On the AI Profile page (`/org/[slug]/settings/ai`), editing the **Monthly Budget** field and clicking **Save Configuration** does not persist. The prior session's network capture showed **NO `PUT /api/ai/profile` reaches the backend** and (per the QA report) no surfaced FormMessage/toast.

PR #1446 (`ceb51c856`, "AIVERIFY-010") corrected the budget field display/onChange and the `monthlyBudgetCents` zod preprocess (unit-verified) — but the save was still broken afterwards, because **the real blocker is in a different field and a different layer**.

## Root Cause (CONFIRMED in code + reproduced, not hypothesised)

The submit is **not** aborted by React Hook Form. `form.handleSubmit(onSubmit)` fires `onSubmit` normally — the client-side resolver passes. The rejection happens **one layer deeper, inside the server action's own re-validation**, which then returns early **without ever calling the backend PUT**.

The chain:

1. **`frontend/components/ai/ai-profile-form.tsx:137-138`** — `onSubmit` assembles `submitData` and coerces the two optional notes fields to `null`:
   ```ts
   houseStyleNotes: data.houseStyleNotes || null,
   feeEstimationNotes: data.feeEstimationNotes || null,
   ```
   When the notes textareas are empty (the common case — many firms leave them blank), `"" || null` evaluates to **`null`**.

2. **`frontend/lib/schemas/ai-profile.ts:11,19`** — the schema for those fields is:
   ```ts
   houseStyleNotes: z.string().max(2000).optional().or(z.literal("")),
   feeEstimationNotes: z.string().max(2000).optional().or(z.literal("")),
   ```
   This union accepts `string`, `undefined`, or `""` — but **NOT `null`**.

3. **`frontend/app/(app)/org/[slug]/settings/ai/actions.ts:19-22`** — the server action re-validates `submitData` defensively before calling the backend:
   ```ts
   const parsed = aiProfileSchema.safeParse(data);
   if (!parsed.success) {
     return { success: false, error: parsed.error.issues[0]?.message ?? "Invalid input." };
   }
   ```
   With `houseStyleNotes: null` / `feeEstimationNotes: null`, `safeParse` **FAILS** → the action returns `{ success: false, error: "Invalid input" }` and **`updateAiProfile()` (the `PUT /api/ai/profile`) is never called**.

This exactly matches the network capture: no PUT, save appears to do nothing. (The form does set `result.error` into `message` at `ai-profile-form.tsx:148`, so the user may see a small "Invalid input" string — but the QA capture reported it as effectively silent.)

### Reproduction (run locally — this is the proof, not a guess)

A schema-only test reproduces it deterministically. Replicating the form's `onSubmit` assembly (empty notes → `null`) and feeding it to the same `aiProfileSchema.safeParse` the server action uses:

```
SERVER ACTION RESULT => FAIL :: houseStyleNotes=Invalid input | feeEstimationNotes=Invalid input
```

Conversely, a full rendered-form RHF submit with the **current merged code** does NOT abort client-side (onValid fires, onInvalid does not), and the schema validates the full payload cleanly **when notes are `""` rather than `null`** — confirming the abort is specifically the `|| null` transform meeting a schema that rejects `null`.

### Why #1446 missed it

`git show ceb51c856` touched only the budget field (`value`/`onChange`) and the `monthlyBudgetCents` preprocess. It never touched the `|| null` notes transform or the notes schema. The "budget edit triggers it" symptom is a red herring — editing budget just causes a submit, and the submit fails on the notes regardless of the budget value.

## Fix (root layer)

This is a Frontend-only fix. Two coordinated changes plus an instrumentation step.

### Step 0 — Reproduce-before-fix (MANDATORY, CLAUDE.md §4)

Before changing anything, the Dev must **observe** the failure. Add an `onInvalid` callback so RHF rejections (if any) and the server-action rejection are never swallowed:

```tsx
// in ai-profile-form.tsx
const onInvalid = (errors: FieldErrors<AiProfileFormData>) => {
  // Surface, don't swallow — every form must show why a submit failed.
  console.warn("AI profile form validation rejected:", errors);
  setIsError(true);
  setMessage("Please fix the highlighted fields and try again.");
};
// ...
<form onSubmit={form.handleSubmit(onSubmit, onInvalid)}>
```

Then run the form (live, see Verification) or the rendered-form vitest harness and confirm: client RHF `onInvalid` does **not** fire, but the **server action** returns `{ success:false, error:"Invalid input" }` and no PUT is sent. This pins the rejection to the server-action `safeParse` on `null` notes, matching the analysis above.

### Step 1 — Accept `null` for the optional notes fields (root fix)

The schema must accept `null` for the two nullable optional fields, since the form itself emits `null` for "cleared" and the backend column is nullable. Update **`frontend/lib/schemas/ai-profile.ts`**:

```ts
houseStyleNotes: z.string().max(2000).optional().nullable().or(z.literal("")),
feeEstimationNotes: z.string().max(2000).optional().nullable().or(z.literal("")),
```

(Equivalent acceptable form: `z.union([z.string().max(2000), z.literal(""), z.null()]).optional()`. Pick whichever keeps the `.max(2000)` guard intact — do not drop the length cap.)

Verify the resulting type still satisfies `UpdateAiProfileRequest` / `UpdateAiFirmProfileRequest` (the backend accepts nullable notes — confirm against `lib/api/ai.ts` `UpdateAiProfileRequest` and the backend `UpdateAiFirmProfileRequest` record; if the FE type is `string | null` it already matches).

### Step 2 — Never silently swallow form rejections (general hardening)

Keep the `onInvalid` callback from Step 0 in the shipped code (not just for debugging). Rationale (CLAUDE.md spirit): every form should surface validation failures. The current form has no `onInvalid`, so any future field that rejects client-side would abort with no feedback. Wiring `onInvalid` to set an error message closes that whole class.

### Do NOT

- Do **not** change the budget field again — it is correct post-#1446 (verified: schema preprocess + string display + NaN-guarded onChange all pass in isolation and in a rendered RHF submit).
- Do **not** weaken the `.max(2000)` length guard.
- Do **not** touch the backend. `GET/PUT /api/ai/profile` are correct (`AiFirmProfileController.java:27-40`, both `@RequiresCapability("AI_MANAGE")`); the backend `UpdateAiFirmProfileRequest` already accepts nullable notes. The bug is entirely in the FE schema rejecting the FE's own `null`.

## Scope (files)

- `frontend/lib/schemas/ai-profile.ts` — accept `null` on `houseStyleNotes` + `feeEstimationNotes` (root fix).
- `frontend/components/ai/ai-profile-form.tsx` — add `onInvalid` to `handleSubmit` so rejections surface (hardening + repro instrument).
- `frontend/lib/schemas/__tests__/ai-profile.test.ts` — add regression cases (below).

## Tests (stub-based, no live key — verifiable by `pnpm test`)

In `frontend/lib/schemas/__tests__/ai-profile.test.ts`, add:

1. `houseStyleNotes: null` → `safeParse` **succeeds** (the exact failing case).
2. `feeEstimationNotes: null` → succeeds.
3. The **full onSubmit-shaped payload** (notes coerced to `null` via `value || null`, budget edited) → succeeds. This is the end-to-end repro that proves the server action would now call the PUT.
4. Keep all existing budget cases green (regression guard for #1446).

Optionally add a `components/ai/ai-profile-form.test.tsx` rendered-form test asserting that editing budget + empty notes + clicking Save results in `updateAiProfileAction` being invoked (mock the action) — i.e. the submit is no longer short-circuited.

## Verification (LIVE — mandatory before merge, CLAUDE.md §3/§4)

Process miss owned in the 2026-06-15 log: #1446 merged on green markers + clean review WITHOUT a live pre-merge check. Do NOT repeat that. This fix must be live-verified BEFORE merge:

1. Start the cycle-branch stack (`svc.sh start backend gateway frontend`); log in as **Nomsa** (owner, `nomsa@verifain-test.local`, has `AI_MANAGE`).
2. Go to `/org/verifain-attorneys/settings/ai`. Leave House Style / Fee notes **empty** (the failing case). Edit **Monthly Budget** (e.g. set R7000).
3. Open the browser **Network** panel and click **Save Configuration**. Confirm a **`PUT /api/ai/profile`** request is sent with a body containing `monthlyBudgetCents: 700000` (and `houseStyleNotes: null` / `feeEstimationNotes: null`), returning **200**.
4. **Reload** the page → the budget field shows **R7000** (persisted, not reverted).
5. Confirm in DB (diagnostic read only):
   ```sql
   SELECT monthly_budget_cents, house_style_notes, fee_estimation_notes
   FROM tenant_c6107524c9b4.ai_firm_profiles;
   ```
   `monthly_budget_cents = 700000`.
6. Re-run **V9.3** (budget set + persists), then **V9.4 / V9.5** (budget enforcement — set a low budget, invoke a skill, observe pre-LLM 403 `ForbiddenException` with no spend; the backend path was already unit-verified in #1446 Part B and just needs a saveable budget to exercise).

Mark VERIFIED only after the live PUT + reload + DB confirmation.

## PR

One PR (CLAUDE.md §7). Frontend-only → merge bar is `pnpm lint && pnpm build && pnpm test` all green.
