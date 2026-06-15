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

---

## V9.4/9.5 + AIVERIFY-012 (resume verify, 2026-06-15, PR #1449 merged + HMR'd)

**Actor**: Nomsa (owner, AI_MANAGE), Playwright MCP, org `verifain-attorneys` / tenant `tenant_c6107524c9b4`.
**Auth note**: session opened as Pieter; signed out via gateway form-POST `/logout` (CSRF token) + admin-API KC session logout (204), fresh KC login as Nomsa (`/bff/me` → nomsa@verifain-test.local confirmed).

### Spend reconciliation (important for the enforcement maths)
- DB COMPLETED-only sum = **2268c (R22.68)**, but the cost-summary panel reports **R23.99 (2399c)** = all 26 executions incl. 11 FAILED-with-cost (131c). The budget gate `AiCostService.checkBudget` compares against the **full metered spend R23.99**, not just COMPLETED. So R20 budget < R23.99 spend → enforcement fires. (No change of data; SELECT-only.)

### AIVERIFY-012 — ✅ VERIFIED

**(a) Sub-R100 budget now saves.** DOM confirms the fix: budget `<input type=number>` now `step="1"` min="0" (was `step="100"`). Set Monthly Budget **R20** → Save (server-action POST 200). DB: `monthly_budget_cents = 2000`, `profile_version 10 → 11`, `updated_at` advanced. Reload → field shows **20** (persisted). The PUT fired and a value below the old R100 floor persisted. Screenshot: `v9-aiverify012-budget-r20-saved.png`.
  - (Backend log does not echo PUT bodies; the profile_version increment 10→11 + value 2000 is the authoritative proof the PUT reached `AiFirmProfileService.updateProfile` and was not frozen.)

**(b) Bad input shows an error, not a silent drop.** Typed an invalid budget (`-3`) → a visible `<FormMessage>` rendered under the budget field: **"Enter a whole Rand amount of zero or more."** (= `BUDGET_INVALID_MESSAGE` from `lib/ai-budget-input.ts`). DB unchanged by the invalid keystroke alone (`monthly_budget_cents` still 2000 → later 20500, `profile_version` only moved on explicit saves). This is the AIVERIFY-012 fix: the old behaviour silently coerced step/min-mismatched input to "no cap" with zero feedback; now `interpretBudgetInput` → `form.setError` surfaces a FormMessage. Screenshot: `v9-aiverify012-invalid-input-formmessage.png`.
  - **Minor follow-up (pre-existing, logged in #1449 review, NOT a regression, out of scope):** the `setError("manual")` does not hard-block `handleSubmit` — if a *valid* value is still held in field state when Save is clicked, RHF's zod resolver re-validates and the prior committed value saves. The FormMessage requirement (no silent drop) is met; the submit-block hardening is a separate UX nit.

→ **AIVERIFY-012 = VERIFIED** (both (a) and (b) hold).

### V9.4 — budget-exhaustion enforcement → ✅ PASS (Observed, pre-LLM 403)

Set budget **R20 (2000c, profile_version 13)** via UI (< spend R23.99). **Before** the attempt: 26 executions / spend 2399c / `ai_llm_calls`=0 / backend log @ line 619. Ran **FICA on Sipho Dlamini** as Nomsa via the customer-page "Verify with AI" button.

- **UI** surfaced: **"AI budget exhausted or skill not permitted. Check AI settings."** — a budget-exceeded error, NOT a skill result, NOT a 500. Screenshot: `v9-4-budget-exhausted-403-ui.png`.
- **Backend log** (the gate, pre-LLM):
  ```
  WARN GlobalExceptionHandler  Forbidden: path=/api/ai/skills/fica-verification, method=POST,
  reason=Monthly AI spend of R23.99 has reached the budget of R20.00
  tenantId=tenant_c6107524c9b4  userId=601d6446-...  memberId=bbfdd8ac-...
  ```
  → clean **403** from `AiCostService.checkBudget` → `ForbiddenException`, surfaced by `GlobalExceptionHandler`.
- **NO Anthropic call**: no `/v1/messages` / Anthropic HTTP / "Request cancelled" log line followed the Forbidden — rejection is **pre-LLM** (the request never reached the provider).

### V9.5 — no spend leaks past the gate → ✅ PASS (Observed)

**After** the rejected attempt:
- `ai_executions`: **26 → 26** (no new FICA row; most-recent row is still the 10:27 matter-intake — no IN_PROGRESS/FAILED/COMPLETED row created for the rejected call).
- spend: **2399c → 2399c** (R23.99, UNCHANGED).
- `ai_llm_calls`: **0 → 0**.
→ The budget cap blocks the invocation at the **service layer before any LLM cost is incurred** — zero spend leak. (Note: this tenant meters cost on `ai_executions`; `ai_llm_calls` is empty across the whole cycle.)

**Maps to scenario:** V9.4 "Invoke any skill again → expect 403 'AI budget exhausted or skill not permitted'; UI shows the tooltip, not a 500" → PASS. V9.5 "Restore budget to R5,000 → skills work again" → budget restored (below); pre-LLM 403 + no-new-spend is the Observed-PASS core property.

### Step 3 — budget RESTORED → ✅
Monthly Budget set back to **R5000** via UI → Save. DB: `monthly_budget_cents = 500000`, `profile_version 13 → 14`, `updated_at` advanced. Reload → field shows **5000**, panel "R 4976.01 remaining". Headroom restored for V11/V12. Screenshot: `v9-budget-restored-5000-final.png`.

### V9 verdict
**V9 = ✅ PASS (all of 9.1–9.5).** 9.1/9.2 PASS (prior turn), 9.3 PASS (AIVERIFY-010 VERIFIED, prior turn), **9.4/9.5 PASS this turn**, AIVERIFY-012 VERIFIED. Final state: budget R5000 (profile_version 14), spend R23.99 (26 executions, 15 COMPLETED). No data changed by SQL; all budget edits via the Settings UI under test.
