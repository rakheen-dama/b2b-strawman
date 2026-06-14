# QA Cycle Status — AI Core Live-Claude Verification (Keycloak) — Cycle 2026-06-14

- **Branch**: `bugfix_cycle_2026-06-14`
- **Scenario**: `qa/testplan/demos/ai-core-verification-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
- **Started**: 2026-06-14
- **Mode**: Clean slate. Session 0 = dev-up + keycloak-bootstrap + svc start all, then drop any `tenant_verifain*` schema and delete `@verifain-test.local` KC users. Orgs/users created through real onboarding flow.
- **Context**: Phases 72 & 74 shipped the AI core code-complete but never exercised against live Claude (only the `NoOpAiProvider` stub + stubbed backend tests). This cycle verifies the whole AI substrate end-to-end against **real Claude**. It is the blocker to clear before any MCP-server work (`.claude/ideas/mcp-plugin-strategy-2026-06-14.md`).

## 🔴 Human-in-the-loop dependency (RESOLVED for this run)

- A funded `sk-ant-…` Anthropic key is required. **Decision**: the human enters it themselves.
- **Flow**: QA runs V0 onboarding → orchestrator PAUSES → human logs in as Nomsa (`nomsa@verifain-test.local` / `SecureP@ss1`) in their OWN browser at http://localhost:3000 → Integrations UI → enters funded key (V1 steps 1.1–1.7), enables, runs connection test green → tells orchestrator "done" → QA resumes verifying V1 then V2–V12.
- Estimated live spend for a clean run: **< R30**. Stub-vs-live proof obligation: every skill PASS needs non-empty `output` + `costCents > 0` + non-zero token usage in backend log. `costCents == 0` / empty output = stub answered = **FAIL**.

## Per-Stage Workflow (NON-NEGOTIABLE)

For each verification stage V0–V12:

1. **QA agent walks the stage end-to-end** via Playwright MCP. Records the **Observed PASS** criterion result with evidence (browser state + backend log excerpt + gate id / audit id / costCents).
2. **Triage every gap.** Product agent decides: SPEC_READY, WONT_FIX-EXEMPT, or scenario-amendment (requires authorization).
3. **Fix every spec.** Dev agent: reproduce → full `./mvnw verify` → marker → commit → PR → review → merge.
4. **PR each bugfix into `main`** (its own PR). Pre-merge gate hook blocks unless verify markers green.
5. **Retest each fix on main** with QA agent. Mark VERIFIED only after observed end-to-end PASS.
6. **Only then advance.** Next stage starts when the current stage's blocking gaps are VERIFIED.

## Mandate
- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired (per CLAUDE.md §6 WONT_FIX exemptions). Everything else is own/fix.
- No production data; all data disposable. Backward compatibility not a priority.
- No SQL shortcuts — all operations via REST API or browser UI. Only legitimate REST use is the Mailpit email API.

## QA Position

- **Stage**: **V3 — BLOCKED (HELD).** V1 (BYOAK, live Claude proven) and V2 (firm AI profile cold-start) both PASS. **V3 matter-intake FAILED** — live Claude responds but the backend cannot parse Claude's markdown-fenced JSON; the parse error rolls back the whole `@Transactional` execution, so **no gate / no cost / no execution row is recorded**. This is a blocker (AIVERIFY-001) and almost certainly affects V4–V7 (identical raw-`readValue` pattern in all 5 skills). QA held at V3 per the stop-on-blocker rule. **Do NOT advance to V4** until AIVERIFY-001 is fixed and V3 re-verified.
- **Gate ids for V8**: **NONE created** — the V3 parse failure produced 0 gate rows. V8 has nothing to approve until V3 is fixed.
- **Resolved org slug**: `verifain-attorneys` (tenant `tenant_c6107524c9b4`, org KC id `221d86fb-f1b6-440d-b2df-873239f6d784`, profile `legal-za`). V0 evidence + full detail: `qa_cycle/checkpoint-results/V0.md`. V1/V2/V3 detail: `qa_cycle/checkpoint-results/V1.md`, `V2.md`, `V3.md`.
- **Test client**: Sipho Dlamini (INDIVIDUAL), id `809563f8-1feb-4043-bbd7-c9aeaf356900`.

## Dev Stack

- **Status**: Running (clean slate, 2026-06-14). All services healthy: backend :8080 UP, gateway :8443 UP, frontend :3000 (200), portal :3002 healthy; KC realm `docteams` 200. Docker infra (b2b-postgres, b2b-keycloak, b2b-mailpit, b2b-localstack) all healthy. Encryption-key present (`integration.encryption-key` set in `application-local.yml`, backend runs `local,keycloak` profile) — no SecretStore/encryption error at startup → V1 BYOAK unblocked.

## Flags

- (none)

## Tracker

| Gap ID | Stage | Summary | Severity | Owner | Status |
|--------|-------|---------|----------|-------|--------|
| AIVERIFY-001 | V3 | **Matter-intake skill fails to parse live Claude output.** Claude returns JSON wrapped in a ```` ```json ```` markdown fence; backend calls `objectMapper.readValue` on the raw fenced text (`MatterIntakeSkill.java:220`) → `JacksonException` "Unexpected character ('`' code 96)". Because `createGates` throws inside the `@Transactional executeSkill` (l.82) AFTER the COMPLETED save (l.155) and OUTSIDE the LLM try/catch (l.137), the parse exception **rolls back the entire execution** — `ai_executions`/`ai_llm_calls`/`ai_specialist_invocations`/`ai_execution_gates` all 0 rows. Real Anthropic cost incurred but never metered; failure not even recorded as FAILED. Same raw-`readValue` pattern in all 5 skills (fica:181, contractreview:156, drafting:250, complianceaudit:204) → V4–V7 almost certainly affected. | **BLOCKER** | Product/Dev | OPEN |
| AIVERIFY-002 | V3 | **HikariCP connection leak during skill execution.** `@Transactional executeMatterIntake` holds a DB connection open for the entire multi-second Anthropic HTTP round-trip → `ProxyLeakTask: Apparent connection leak detected` (origin `AiSkillExecutionService$$SpringCGLIB$$0.executeMatterIntake`). Non-cascading but should be fixed (don't hold a JDBC connection across an external LLM call). | Medium | Dev | OPEN |
| AIVERIFY-003 | V3 | **404 on customer-page assistant widget.** `GET /api/assistant/invocations?contextEntityType=customer&contextEntityId=...&status=PENDING_APPROVAL&size=10` → 404 from the customer-detail page. Non-cascading; cosmetic console error. | Low | Dev | OPEN |

## Stage Checklist

| Stage | Description | Result |
|-------|-------------|--------|
| V0 | Onboard firm (Nomsa owner, Pieter member) | ✅ PASS |
| V1 | BYOAK key configuration (human enters key) | ✅ PASS (live Claude proven) |
| V2 | Firm AI profile cold-start | ✅ PASS |
| V3 | Matter-intake skill (live Claude) | ❌ FAIL — BLOCKER (AIVERIFY-001) |
| V4 | FICA verification skill (live Claude) | PENDING |
| V5 | Contract review skill (live Claude) | PENDING |
| V6 | Drafting skill (live Claude) | PENDING |
| V7 | Compliance audit skill (live Claude) | PENDING |
| V8 | Execution-gate approval flow | PENDING |
| V9 | Cost metering & budget enforcement | PENDING |
| V10 | Capability / RBAC gating (Pieter) | PENDING |
| V11 | Error & graceful-degradation paths | PENDING |
| V12 | Audit trail | PENDING |

## Log

- **2026-06-14** — Cycle initialized. Archived completed Legal-ZA cycle (ALL_DAYS_COMPLETE, 0 open gaps) to `qa_cycle/_archive_2026-06-13_legal-full-lifecycle-kc/`. Branch `bugfix_cycle_2026-06-14` created. Fresh tracker seeded. Human-in-the-loop key decision: human enters key at V1. Stack decision: clean-slate startup. Next action: Infra agent (Session 0 clean-slate startup).
- **2026-06-14 (QA, V0)** — Onboarding walked end-to-end via Playwright MCP. **V0 PASS (all sub-steps 0.1–0.4).** Org slug resolved = `verifain-attorneys` (tenant `tenant_c6107524c9b4`). Vertical profile `legal-za` auto-assigned — confirmed via backend provisioning log (legal-za field/template/compliance/AI-specialist packs installed). Nomsa Verifain registered + logged in as **Owner** (Team page row). Pieter Botha invited as plain **member** (no 2-member plan gate hit), registered + logged in; KC shows only `default-roles-docteams` (no AI roles) and his sidebar lacks the "AI" nav item — clean RBAC negative-test subject for V10. 0 console errors throughout. Evidence: `qa_cycle/checkpoint-results/V0.md` + `v0-*.png`. No defects filed. **STOPPED before V1** for human key entry (BYOAK gate). Noted testing-only session-handoff caveat (KC SSO "already authenticated" when switching actors on invite links — resolved via admin-API session logout + browser-context reset; not a product defect).
- **2026-06-14 (QA, V1–V3)** — Walked V1→V3 via Playwright MCP as Nomsa (had to sign out a stale Pieter session + re-auth via Keycloak). **V1 PASS (live Claude proven):** AI card = anthropic / ••••nWbAAA / Sonnet 4.6 / Enabled / Active; connection test is a real Anthropic round-trip — proof = `testConnectionAction` latency 1427–1824 ms (vs ~25 ms for stubbed actions) + `integration.connection_tested {"success":true,"providerSlug":"anthropic"}` audit events + slug routing to `AnthropicLlmProvider.validateKey` (real `POST /v1/messages`). Stub ruled out. Negative bogus-key check deferred to V11. **V2 PASS:** firm AI profile cold-start persisted (`ai_firm_profiles`: cold_start_completed=t, ZA-WC, CONSERVATIVE, budget 500000c, claude-sonnet-4-6, Litigation+Conveyancing, FICA EDD+PEP, house-style + fee notes); page flipped "Set Up AI"→"AI Configuration"/"Save Configuration" after reload; usage panel renders R0/0. **V3 FAIL — BLOCKER (AIVERIFY-001):** matter-intake — live Claude responds but backend `objectMapper.readValue` chokes on Claude's markdown-fenced JSON (`MatterIntakeSkill.java:220`); the parse exception propagates out of the `@Transactional executeSkill` (outside the LLM try/catch) and **rolls back everything** → 0 rows in ai_executions/ai_llm_calls/ai_specialist_invocations/ai_execution_gates, real cost never metered, **no gate created** (V8 has nothing to approve). Same raw-readValue pattern in all 5 skills → V4–V7 almost certainly affected. Also filed AIVERIFY-002 (Hikari connection leak: JDBC connection held across the LLM HTTP call) and AIVERIFY-003 (404 on customer-page assistant-invocations widget). **QA HELD at V3** per stop-on-blocker rule — do not advance to V4 until AIVERIFY-001 fixed + V3 re-verified on main. Test client Sipho Dlamini = `809563f8-1feb-4043-bbd7-c9aeaf356900`. Evidence: `qa_cycle/checkpoint-results/V1.md`, `V2.md`, `V3.md` + `v1-*`/`v2-*`/`v3-*.png`.
- **2026-06-14 (Infra, Session 0)** — Clean-slate stack brought up. Docker infra verified healthy (postgres/keycloak/mailpit/localstack). `keycloak-bootstrap.sh` run idempotently — padmin@docteams.local confirmed. Clean-slate teardown (0.A/0.B): **0 `tenant_verifain*` schemas** found, **0 `@verifain-test.local` KC users** found, **0 verifain `access_requests`** rows in public schema — environment was already clean, nothing to drop/delete. Encryption-key check (0.C): `integration.encryption-key` present in `application-local.yml:86` (valid 32-byte base64 dev key), backend launches with `SPRING_PROFILES_ACTIVE=local,keycloak` — **no encryption/SecretStore startup error**; V1 BYOAK unblocked. `svc.sh start all` → all 4 services healthy on first attempt. Sanity probes: backend/gateway `/actuator/health` UP, frontend 200, KC realm 200. QA Position remains V0.
