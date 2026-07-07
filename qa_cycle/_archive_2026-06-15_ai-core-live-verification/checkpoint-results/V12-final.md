# V12 — Audit trail (resume, 2026-06-15, QA)

**Cycle**: AI Core Live-Claude Verification (Keycloak)
**Actor**: Nomsa Verifain (owner; has TEAM_OVERSIGHT → can view the audit log)
**Driver**: Playwright MCP (audit UI) + SELECT-only DB cross-checks.
**Org**: `verifain-attorneys` / tenant `tenant_c6107524c9b4`.
**Route**: `/org/verifain-attorneys/settings/audit-log` (append-only feed; `audit_events` table has `prevent_audit_delete`/`prevent_audit_update` triggers — tamper-evident).

---

## V12.1 — events exist for the run → ✅ PASS (with a naming finding, see AIVERIFY-013)

DB `tenant_c6107524c9b4.audit_events`, AI event-type counts:

| event_type (as emitted) | count | corresponds to scenario's |
|---|---|---|
| `ai.skill.invoked` | 27 | `ai.specialist.invoked` (one per skill execution; = 27 `ai_executions`) |
| `ai.gate.approved` | 2 | `ai.specialist.approved` (V8.2 + AIVERIFY-008 publish gate) |
| `ai.gate.rejected` | 2 | `ai.specialist.rejected` (V8.4 + a duplicate-cleanup reject) |
| `ai.specialist.failed` | 1 | `ai.specialist.failed` (a real contract-review parse failure) |

Every action the cycle performed is represented:
- **27 invoked** events = the 27 metered `ai_executions` (1:1) across all 5 skills (matter-intake, fica-verification, contract-review, drafting, compliance-audit), both COMPLETED and FAILED.
- **2 approved** = MARK_KYC_COMPLETE (V8.2) + PUBLISH_COMPLIANCE_REPORT (AIVERIFY-008 publish gate).
- **2 rejected** = SELECT_MATTER_TEMPLATE (V8.4, notes "Wrong template") + CONFIRM_CONFLICT_SCREEN (notes "duplicate cleanup").
- **1 failed** = contract-review parse failure (the 131c FAILED-with-cost execution `0b8fa839…`), with full `errorMessage`.

> **Note:** V11.1 (bad-key) was DEFERRED this cycle, so the `ai.specialist.failed` event here is from a real skill parse failure rather than a bad-key failure. The failed-event **path is proven** regardless (event emitted, actor + cost + errorMessage captured).

### 🔶 FINDING — AIVERIFY-013 (Low, scenario-vs-product naming mismatch — NOT auto-disposed)
The scenario V12.1 names the events `ai.specialist.invoked / .approved / .rejected / .failed`. The product actually emits **`ai.skill.invoked`** (skill-execution path) and **`ai.gate.approved` / `ai.gate.rejected`** (gate-review path); only **`ai.specialist.failed`** matches the literal scenario string. The `ai.specialist.*` invoked/approved/rejected types ARE registered in `AuditEventTypeRegistry.java:114-148` but are emitted by the older automation/specialist-invocation path, not by the live skill+gate flow exercised in this cycle (skill exec emits `ai.skill.invoked` via `AiExecutionPersistenceService`; gate review emits `ai.gate.*`). **The substantive POPIA/Attorneys-Act defensibility property is satisfied** — invoked vs approved vs rejected vs failed are all distinctly recorded with actor attribution — so this is a naming/labelling discrepancy, not a missing-audit defect. Disposition is a product call: either (a) accept `ai.skill.*`/`ai.gate.*` as the canonical names and amend the scenario's literal strings (authorized scenario amendment), or (b) align the emitted names to `ai.specialist.*`. Logged for triage; **not** auto-disposed per CLAUDE.md §6.

---

## V12.2 — actor attribution + notes + skill/entity → ✅ PASS (Observed)

DB rows (gate + failed events) — actor, entity, details:

| event_type | entity_type | actor (details.actor_name) | actor_id | key details |
|---|---|---|---|---|
| ai.specialist.failed | DOCUMENT | **Nomsa Verifain** | bbfdd8ac… | skillId=contract-review, costCents=131, model, full errorMessage (parse failure) |
| ai.gate.approved | ai_execution_gate | **Nomsa Verifain** | bbfdd8ac… | gateType=MARK_KYC_COMPLETE, executionId |
| ai.gate.rejected | ai_execution_gate | **Nomsa Verifain** | bbfdd8ac… | gateType=SELECT_MATTER_TEMPLATE, **notes="Wrong template"** |
| ai.gate.rejected | ai_execution_gate | **Nomsa Verifain** | bbfdd8ac… | gateType=CONFIRM_CONFLICT_SCREEN, notes="duplicate cleanup" |
| ai.gate.approved | ai_execution_gate | **Nomsa Verifain** | bbfdd8ac… | gateType=PUBLISH_COMPLIANCE_REPORT, executionId |

- **Approve/reject carry the correct actor (Nomsa) + reviewer notes** — V8.4's "Wrong template" rejection note is captured verbatim. ✅
- **Invoked events carry skill + entity + actor.** Attribution distinguishes actors correctly:
  - **Nomsa Verifain** (`bbfdd8ac…`): 17 `ai.skill.invoked`.
  - **Pieter Botha** (`fffaa27f…`): 10 `ai.skill.invoked` — including the **V10.1 matter-intake at 2026-06-15 10:29 COMPLETED on CUSTOMER:Sipho, attributed to Pieter** (exactly the scenario's actor-attribution requirement: the member's invocation is recorded as the member's, not the owner's). Each row carries `skillId`, `entity_type` (CUSTOMER/FIRM/DOCUMENT/PROJECT), `status`, `actor_name`. ✅

`actor_type=USER`, `source=API` on all — correct (no SYSTEM mis-attribution for human-driven calls).

---

## V12.3 — filterable by actor and entity → ✅ PASS (Observed, live UI)

Exercised the audit-log UI filters as Nomsa (browser):

- **Baseline:** 76 total events.
- **Event-type filter** `ai.gate.approved` → URL `?eventType=ai.gate.approved` → narrows to **2 total** (only the 2 approvals; no other types leak). Matches DB. Screenshot: `v12-audit-filter-gate-approved.png`.
- **Actor + event-type filter** `actorId=fffaa27f…(Pieter) & eventType=ai.skill.invoked` → **10 total**, all 10 rows = Pieter Botha's invoked events, 0 other actors/types. Matches DB (Pieter = 10 invoked). Screenshot: `v12-audit-filter-actor-pieter.png`.
- **Entity-type filter** `entityType=ai_execution_gate` → **4 total** (2 approved + 2 rejected, 0 skill-invoked). Matches DB.

The UI exposes filters for date range (From/To), Actor ID, Event type, Entity type, Entity ID, and Severity (INFO/NOTICE/WARNING/CRITICAL), plus Export. Filters compose correctly and the counts reconcile exactly with the DB. ✅

---

## V12 verdict
**V12 = ✅ PASS.** The audit trail records every AI action (invoked / approved / rejected / failed) with correct actor attribution (Nomsa vs Pieter distinguished, including the member's V10.1 invocation), reviewer notes on approve/reject, skill+entity on invocations, and full error detail on failures. The feed is append-only (tamper-evident triggers) and filterable by event type, actor, and entity — all verified live with counts reconciled to the DB. **The POPIA/Attorneys-Act defensibility property the MCP plan depends on is met.** One Low finding (AIVERIFY-013): the emitted event-type names are `ai.skill.invoked` / `ai.gate.approved` / `ai.gate.rejected` rather than the scenario's literal `ai.specialist.*` strings — a naming discrepancy, not a missing-audit defect; disposition is a product call.

Evidence: this file; screenshots `v12-audit-filter-gate-approved.png`, `v12-audit-filter-actor-pieter.png`; DB `audit_events` rows above; UI route `/settings/audit-log`.
