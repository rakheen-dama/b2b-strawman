# QA Verification: AI Core — Live Claude End-to-End (Keycloak Mode)

**Vertical profile**: `legal-za`
**Story**: "Verifain Attorneys" — a Cape Town firm switching on AI for the first time
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/ai-core-verification-keycloak.md`

## Purpose

Phases 72 & 74 shipped the AI core **code-complete but it has never been exercised against a live Claude API.** This scenario verifies the entire AI substrate end-to-end against **real Claude** (not the `NoOpAiProvider` stub): BYOAK key configuration, firm profile cold-start, all 5 skills, execution-gate approval, cost metering + budget enforcement, capability/RBAC gating, audit trail, and error/degradation paths. It is the **blocker to clear before any MCP-server work** (see `.claude/ideas/mcp-plugin-strategy-2026-06-14.md`).

> **Not a lifecycle demo.** Organised as verification stages (V0–V12), not 90 days. Each stage states an **Observed PASS criterion** — PASS means the behaviour was *observed* (live Claude round-trip → backend log → gate row → audit event → cost increment), never inferred from "the code looks right."

---

## 🔴 Critical prerequisite — a funded Anthropic API key (BYOAK)

This scenario **cannot pass without a real, funded Anthropic API key.** The whole point is to confirm the system calls live Claude, not the stub.

- The tester (human) must supply a working `sk-ant-…` key. It is entered **once, through the Integrations UI** in stage V1 (`SetApiKeyDialog`) — never committed to the repo, never pasted into this file, never inserted via SQL.
- Estimated spend for a full clean run: **< R30** (5 skills × Sonnet, plus retries). Confirm the key has balance.
- **Stub-vs-live proof obligation:** the `AnthropicAiProvider` is only selected when `OrgIntegration(domain=AI).providerSlug == "anthropic"` AND a key is stored in `SecretStore`. Otherwise `NoOpAiProvider` returns empty output. Therefore **every skill PASS must prove it hit live Claude**: non-empty structured `output`, `costCents > 0`, and non-zero token counts in the backend log / cost summary. A "successful" call with `costCents == 0` or empty output means the stub answered → **FAIL**.

---

## Actors

| Role | Name | Keycloak email | Password | AI capabilities |
|---|---|---|---|---|
| Owner / Principal | Nomsa Verifain | `nomsa@verifain-test.local` | `SecureP@ss1` | AI_MANAGE, AI_EXECUTE, AI_REVIEW |
| Member / Paralegal | Pieter Botha | `pieter@verifain-test.local` | `SecureP@ss2` | *(none — used for RBAC negative test)* |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` | — |

*Pieter must be invited with the plain **member** role and must NOT be granted a custom role carrying AI_EXECUTE/AI_REVIEW (used in V10 to prove gating).*

## Verification highlights (capture 📸 on clean pass)

1. **V1** — AI Assistant integration card with live key set (masked suffix) + "Connection test passed"
2. **V3** — Matter-intake panel showing a *real* Claude template recommendation + conflict-screen result
3. **V8** — Attorney approving a PENDING `MARK_KYC_COMPLETE` gate and the checklist items actually flipping to complete
4. **V9** — Cost summary incrementing in ZAR across invocations, then a `403 budget exhausted` when the cap is set low
5. **V12** — Audit feed showing `ai.specialist.invoked / approved / rejected` with the correct actor

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" (M.1–M.9). In addition:

- [ ] **0.A** Confirm no tenant schema named `tenant_verifain*` exists (drop if present)
- [ ] **0.B** Delete any Keycloak users with `@verifain-test.local` emails from the `docteams` realm
- [ ] **0.C** Confirm backend started with `integration.encryption-key` set (required for `SecretStore`; without it the key cannot be stored). Check `svc.sh logs backend` for a startup failure on the encryption key.
- [ ] **0.D** Tester confirms a funded Anthropic key is on hand for V1. Note the **last 4 chars** so V1 can verify the stored masked suffix matches.
- [ ] **0.E** Tail backend logs in a side terminal for the whole run: `bash compose/scripts/svc.sh logs backend` — used to prove live-Claude calls (token counts, model id) and to catch parse/serialisation errors.

---

## V0 — Onboard the firm

**Actor**: Nomsa (then Platform Admin)

Run the standard Keycloak onboarding (mirror Day 0 of `legal-za-90day-keycloak.md`):
- [ ] **0.1** Access request at `/request-access` (Org: **Verifain Attorneys**, Country: South Africa, Industry: Legal Services) → OTP via Mailpit → submit
- [ ] **0.2** Platform admin approves at `/platform-admin/access-requests`; confirm vertical profile auto-assigned `legal-za`
- [ ] **0.3** Nomsa registers via the Keycloak invite link → logs in → lands on dashboard as **owner**
- [ ] **0.4** Owner invites **Pieter Botha** as a plain **member** (upgrade plan first if the 2-member gate blocks). Pieter accepts via invite + registers.

**Observed PASS**: both users authenticate; Nomsa is owner, Pieter is member.

---

## V1 — BYOAK key configuration (UI)

**Actor**: Nomsa · Route: `/org/verifain-attorneys/settings/integrations`

- [ ] **1.1** Open Integrations → the **"AI Assistant"** card is visible (PRO tier; upgrade plan if gated)
- [ ] **1.2** Select provider **anthropic** in the provider dropdown
- [ ] **1.3** Click **Set API Key** → `SetApiKeyDialog` → paste the funded `sk-ant-…` key → save
- [ ] **1.4** Card now shows masked suffix `••••<last4>` — **confirm it matches the last 4 noted in 0.D**
- [ ] **1.5** Toggle **Enable** on
- [ ] **1.6** Click **Connection test** → expect a success state ("Connection test passed" or equivalent)
- [ ] **1.7** Model dropdown appears; leave at `claude-sonnet-4-6`

**Observed PASS**: key stored (masked suffix matches), integration enabled, connection test green. Backend log shows a real Anthropic round-trip for the test (HTTP 200, a model id). **If the connection test passes with no outbound call logged → suspect stub → FAIL and investigate.**
**Negative check**: temporarily set a bogus key → connection test must surface a clear auth error (not a generic 500), then restore the real key.

---

## V2 — Firm AI profile cold-start (UI)

**Actor**: Nomsa · Route: `/org/verifain-attorneys/settings/ai`

- [ ] **2.1** Page renders all 8 sections; banner indicates cold-start not yet completed
- [ ] **2.2** Practice Areas: add **Litigation**, **Conveyancing**
- [ ] **2.3** Jurisdiction: **ZA-WC** (Western Cape)
- [ ] **2.4** Risk Calibration: **CONSERVATIVE**
- [ ] **2.5** House Style Notes: "Formal SA legal register; cite the Attorneys Act where relevant; no contractions."
- [ ] **2.6** FICA Requirements: tick **Enhanced Due Diligence** + **PEP Screening**
- [ ] **2.7** Fee Estimation Notes: "LSSA tariff baseline + 10%."
- [ ] **2.8** Model preference: **Claude Sonnet 4.6**
- [ ] **2.9** Monthly Budget: **R5,000** (`monthlyBudgetCents = 500000`)
- [ ] **2.10** Click **Complete Setup**

**Observed PASS**: `PUT /api/ai/profile` returns 200; reload shows persisted values and `coldStartCompleted = true`. The cost-summary side panel renders (spend R0.00, 0 invocations).

---

## V3 — Matter-intake skill (live Claude)

**Actor**: Nomsa · prerequisite: a customer exists. Create client **"Sipho Dlamini"** (INDIVIDUAL) at `/customers` if needed.

- [ ] **3.1** Go to `/org/verifain-attorneys/projects/new` → select customer **Sipho Dlamini**
- [ ] **3.2** Enter a matter description ≥ 20 chars: "Civil claim against a former business partner for breach of a shareholders' agreement; seeking damages and an interdict."
- [ ] **3.3** Click **Get AI Recommendations** → spinner "Analysing matter…"
- [ ] **3.4** Result renders: a **template recommendation** (with reasoning) and a **conflict-screening** status (CLEAR / POTENTIAL_CONFLICT)
- [ ] **3.5** A PENDING gate of type `SELECT_MATTER_TEMPLATE` (and/or `CONFIRM_CONFLICT_SCREEN`) is created

**Observed PASS** (live, not stub): result text is substantive and matter-specific (not empty/placeholder); `costCents > 0`; backend log shows Sonnet token usage; ≥1 PENDING gate row created. `POST /api/ai/skills/matter-intake` returns `status=COMPLETED`.

---

## V4 — FICA verification skill (live Claude)

**Actor**: Nomsa · prerequisites built via UI first:
- [ ] **4.0a** On **Sipho Dlamini** ensure a FICA/KYC checklist is instantiated with ≥1 PENDING item (legal-za compliance pack)
- [ ] **4.0b** Upload ≥1 identity document to the customer (e.g. a sample ID PDF) so the skill has something to read from S3

- [ ] **4.1** On `/org/verifain-attorneys/customers/{id}` → checklist tab → click **Verify with AI** (Sparkles)
- [ ] **4.2** Spinner "Verifying…"; result lists `recommendedActions` (MARK_ITEMS_COMPLETE / REQUEST_ADDITIONAL_DOCUMENT)
- [ ] **4.3** A PENDING `MARK_KYC_COMPLETE` gate is created carrying `checklist_item_ids` + reasoning

**Observed PASS**: substantive document-grounded output; `costCents > 0`; gate created with a non-empty `proposedAction`. (If vision is used, confirm the doc bytes were actually sent — log shows image/input tokens.)

---

## V5 — Contract review skill (live Claude)

**Actor**: Nomsa · prerequisites: a project + an uploaded PDF/DOCX contract document.
- [ ] **5.0** Create project "Shareholder dispute — Dlamini" for Sipho; upload a sample contract PDF to it
- [ ] **5.1** On `/org/verifain-attorneys/projects/{id}` → document row → click **Review with AI**
- [ ] **5.2** Spinner "Reviewing contract…"; a structured review report renders (risk findings / clauses)

**Observed PASS**: non-empty review grounded in the uploaded document; `costCents > 0`; execution recorded; any gates created are PENDING. Confirm large-doc handling did not silently truncate (check log for input token count vs document size; note if truncated).

---

## V6 — Drafting skill (live Claude)

**Actor**: Nomsa · prerequisites: an active document template + the project from V5.
- [ ] **6.0** Confirm ≥1 document template exists (create a simple one via `/settings` templates if none)
- [ ] **6.1** On the project → actions → **Draft with AI** → `DraftingDialog` → select the template → **Generate Draft**
- [ ] **6.2** A generated draft renders, reflecting the firm house-style note from V2 (formal register)

**Observed PASS**: draft is non-empty, on-template, and reflects profile house-style; `costCents > 0`; execution recorded. Any write-back is gated, not auto-applied.

---

## V7 — Compliance audit skill (live Claude)

**Actor**: Nomsa · Route: `/org/verifain-attorneys/compliance`
- [ ] **7.1** Click **Run AI Audit** → spinner "Running compliance audit…"
- [ ] **7.2** `ComplianceAuditOutput` renders findings/recommendations across the org

**Observed PASS**: substantive org-scoped findings; `costCents > 0`; `POST /api/ai/skills/compliance-audit` returns COMPLETED. Empty body is accepted.

---

## V8 — Execution-gate approval flow (the liability gate)

**Actor**: Nomsa (has AI_REVIEW) · Route: `/org/verifain-attorneys/ai/reviews`

- [ ] **8.1** Pending tab lists the gates created in V3–V6; each expands to show full `proposedAction` + `aiReasoning` + expiry
- [ ] **8.2** **Approve** the V4 `MARK_KYC_COMPLETE` gate (add notes "Verified ID against checklist") → status → APPROVED
- [ ] **8.3** **Confirm the approval actually applied the action**: return to Sipho's FICA checklist → the items named in the gate's `checklist_item_ids` are now **COMPLETE**
- [ ] **8.4** **Reject** the V3 `SELECT_MATTER_TEMPLATE` gate (notes "Wrong template") → status → REJECTED; confirm **no** template was applied
- [ ] **8.5** Re-approving an already-resolved gate is refused (state guard: not PENDING)

**Observed PASS**: gate state transitions persist with `reviewedBy`/`reviewedAt`/`reviewNotes`; **approval mutates real domain state** (checklist flips); rejection is a no-op on domain state. This is the core Attorneys-Act safety property.

---

## V9 — Cost metering & budget enforcement

**Actor**: Nomsa

- [ ] **9.1** `/settings/ai` cost panel (or `GET /api/ai/cost-summary`): spend in ZAR > R0, `invocationCount` equals the number of live skill calls so far
- [ ] **9.2** Cross-check: sum of `costCents` reported by V3–V7 ≈ the cost-summary `currentMonthSpentCents`
- [ ] **9.3** Set **Monthly Budget** to a value below current spend (e.g. R1) and save
- [ ] **9.4** Invoke any skill again → expect **403** "AI budget exhausted or skill not permitted"; UI shows the tooltip, not a 500
- [ ] **9.5** Restore budget to R5,000 → skills work again

**Observed PASS**: metering increments accurately per invocation; budget cap blocks invocation at the service layer with a clean 403 surfaced in the UI.

---

## V10 — Capability / RBAC gating

**Actor**: Pieter (plain member, no AI capabilities)

- [ ] **10.1** Pieter logs in; AI skill buttons are hidden/disabled OR invocation `POST /api/ai/skills/*` returns **403** (lacks AI_EXECUTE)
- [ ] **10.2** Pieter cannot reach `/ai/reviews` approval actions (lacks AI_REVIEW) → 403 on approve/reject
- [ ] **10.3** Pieter cannot edit the AI profile (`PUT /api/ai/profile` → 403, lacks AI_MANAGE)

**Observed PASS**: every AI endpoint enforces its `@RequiresCapability` for a member without the capability. (Tenant isolation is implicit — Pieter is same-tenant; this verifies capability, not cross-tenant.)

---

## V11 — Error & graceful-degradation paths

**Actor**: Nomsa

- [ ] **11.1** **Bad key**: set an invalid Anthropic key → invoke a skill → expect a clean error surface ("Try Again" amber box), execution recorded as FAILED, audit `ai.specialist.failed`, `costCents = 0`. Restore the real key.
- [ ] **11.2** **Missing profile fields / cold-start incomplete**: (optional) on a second throwaway org with key but no profile, confirm a skill still runs and flags profile gaps rather than 500-ing (graceful degradation).
- [ ] **11.3** **Prompt caching**: invoke the same skill on the same entity twice; on the 2nd call the backend log shows `cacheReadInputTokens > 0` (system prompt / firm profile cached), and cost is lower than the 1st call.
- [ ] **11.4** **Input validation**: matter-intake with a <20-char description → 400; fica-verification with no documents/pending items → a clear, handled error.

**Observed PASS**: failures are handled (no 500s leaking stack traces), FAILED executions are recorded + audited, prompt caching demonstrably engages on repeat calls.

---

## V12 — Audit trail

**Actor**: Nomsa · Route: audit/activity feed (and/or `GET` audit query API)

- [ ] **12.1** Confirm events exist for the run: `ai.specialist.invoked` (each skill), `ai.specialist.approved` (V8.2), `ai.specialist.rejected` (V8.4), `ai.specialist.failed` (V11.1)
- [ ] **12.2** Approved/rejected events carry the correct **actor** (Nomsa) + notes; invoked events carry the skill/entity
- [ ] **12.3** Events are filterable by actor and entity

**Observed PASS**: the audit trail distinguishes AI-suggested vs human-approved with actor attribution — the POPIA/Attorneys-Act defensibility property the MCP plan depends on.

---

## Exit criteria (all must hold for a GREEN AI-core verification)

1. Every skill (V3–V7) demonstrably hit **live Claude** (non-empty output + `costCents > 0` + token usage logged) — none answered by the stub.
2. Structured output parsed for all 5 skills without serialisation errors in the backend log.
3. Gate approval **applies** the action; rejection is a no-op; state guards hold (V8).
4. Cost metering is accurate and budget enforcement returns a clean 403 (V9).
5. All AI endpoints enforce their capability (V10).
6. Failures are handled gracefully + audited; prompt caching engages (V11).
7. Audit trail attributes invoked/approved/rejected correctly (V12).

Anything short of this is logged in `qa_cycle/status.md` with evidence (log excerpt, gate id, audit event id) and triaged — own / fix / WONT_FIX-with-reason. Per the project quality gates, a fix that changes backend behaviour requires a clean full `./mvnw verify` before it counts as green, and "PASS" means observed, not inferred.

---

## How to launch

```
/qa-cycle-kc qa/testplan/demos/ai-core-verification-keycloak.md
```

The cycle's Infra agent will bring up the Keycloak dev stack; the QA agent drives stages V0–V12 via Playwright. **A human must enter the live Anthropic key at stage V1** (it cannot be scripted or seeded). Note: existing backend tests already cover the AI surface with a stub (`AiSkillControllerTest`, `AiSkillEndToEndTest`, `AiExecutionGateControllerTest`, `AiCostServiceTest`, `AnthropicAiProviderTest`); this scenario is the **live-Claude** layer those cannot exercise.
