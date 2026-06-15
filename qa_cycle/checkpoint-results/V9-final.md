# V9 — Cost metering & budget enforcement (resume verify, 2026-06-15)

**Cycle**: AI Core Live-Claude Verification (Keycloak)
**Actor**: Nomsa (owner, AI_MANAGE)
**Stack**: Keycloak dev (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Driver**: Playwright MCP
**Org**: `verifain-attorneys` / tenant `tenant_c6107524c9b4`

## Env-health gate — ✅ PASS (live Claude healthy again)

Ran FICA verification on Sipho Dlamini as Nomsa via the customer-page "Verify with AI" button.
- UI rendered full FICA result: Assessment INCOMPLETE / Risk HIGH / checklist + recommended actions, **"Cost: R 1.14"**, "Completed in 53941ms", PENDING "Mark Kyc Complete" gate (execution `b1d92662…`).
- DB (`ai_executions`, latest): `fica-verification | COMPLETED | cost_cents=114 | input_tokens=544 | output_tokens=3570 | 2026-06-15 10:11:58`.
- No `"Request cancelled"` / I/O error in backend log. **Env degradation cleared.**
- Evidence: `v9-health-gate-fica-complete.png`.

## V9.3 — Budget save (AIVERIFY-010, 3rd attempt) — ✅ PASS / VERIFIED

Repro of the exact prior-failure condition: cleared BOTH house-style and fee notes to empty (the `"" || null` coercion that broke the save twice before), then changed **Monthly Budget 7000 → 6000** and clicked Save.

- DB after save: `monthly_budget_cents = 600000`, `profile_version = 8`, `updated_at = 2026-06-15 10:14:10` (was 700000). **The PUT fired and the budget persisted** — exactly what was missing in the two prior REOPENED attempts.
- Server-action POST `/org/verifain-attorneys/settings/ai` → **200 OK**.
- Reload → field shows **6000** (persisted in UI), notes repopulated from DB.
- Backend `AiFirmProfileService.updateProfile` confirmed to have **no budget-vs-spend rejection** — it saves whatever it receives; the entity `updateProfile` keeps old budget only when incoming value is null (`monthly_budget_cents = x != null ? x : this`).
- Further confirmation across the turn: 6000→4000 (`profile_version 9`) and 4000→5000 (`profile_version 10`) both persisted reliably.
- Evidence: `v9-3-budget-saved-6000-persists.png`, `v9-budget-restored-5000.png`.

**AIVERIFY-010 → VERIFIED.** Budget save lives; the PUT reaches the backend and persists with empty notes.

## V9.4 / V9.5 — Budget enforcement 403 — ⚠️ PARTIAL / BLOCKED (new defect AIVERIFY-012)

**Could not set Monthly Budget below current spend through the UI**, so the pre-LLM 403 path could not be exercised this turn.

**New defect found — AIVERIFY-012 (Medium): the Monthly Budget input silently drops step-mismatched values.**
- The budget `<Input type="number">` has `step={100}` (Rands) → smallest non-zero settable budget is **R100 (10000c)**.
- Current month spend is **R22.68**, which is **below R100**, so there is no step-aligned budget value that sits below spend. The scenario's "set budget to R1" is unreachable.
- Worse: any step-mismatched value (R1, R15, R20, R33) **fails to save with NO error surfaced**. Reproduced deterministically:
  - Step-VALID values (6000, 4000, 5000) → persist (DB `profile_version` increments, `updated_at` advances).
  - Step-MISMATCHED values (33 — above spend; 20, 15, 1 — below spend) → **DB unchanged** (`profile_version`/`updated_at` frozen), no success message, no `onInvalid` validation warning, no `FormMessage`. Tested via Playwright `fill()` AND via native-setter+input/change dispatch (React `field.value` correctly held the value, e.g. `reactPropsValue="1"`), yet the save no-ops.
  - Root mechanism (high confidence): for a step-mismatched number input the submit path resolves `monthlyBudgetCents` to `undefined`/`null`; the server action's `safeParse` passes (budget optional), the PUT sends `monthlyBudgetCents: null`, and `AiFirmProfile.updateProfile` keeps the old value (`x != null ? x : this`). Net: the user changes the budget, clicks Save, sees no error, and the budget is unchanged. Same silent-drop UX class as AIVERIFY-010 but a different field-level cause (step granularity, not null-notes).
- Impact: (a) blocks the V9.4/9.5 below-spend enforcement test in the UI; (b) a real product UX bug — users cannot set fine-grained budgets and get no feedback when a value is rejected.

**Backend 403 enforcement itself was NOT contradicted** — prior cycle unit-verified `AiCostService.checkBudget → ForbiddenException` pre-LLM; this turn simply could not drive budget < spend via the UI to observe it live. No SQL shortcut taken (per QA rules, budget changes must go through the UI under test).

**Recommendation:** fix AIVERIFY-012 (drop/loosen `step={100}` to allow any positive integer Rand value, OR surface a clear validation message on step-mismatch + ensure step-mismatched values still submit). Then re-run V9.4/9.5 with budget = R1 to observe the pre-LLM 403 + no-new-spend.

## Budget hygiene
Monthly Budget RESTORED to **R5000 (500000c, profile_version 10)** and confirmed persisted in DB + UI, so member-execute (Step 3) and later V11/V12 are not budget-blocked.

## Spend ledger (this turn)
- Start: R21.12 (14 COMPLETED). Added: FICA R1.14 (Nomsa) + matter-intake R1.56 (Pieter) = R2.70.
- End: **R22.68 (15 COMPLETED)**, budget R5000.
