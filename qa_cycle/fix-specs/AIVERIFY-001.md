# Fix Spec — AIVERIFY-001 (+ AIVERIFY-002 fold-in recommended)

**Stage:** V3 (matter-intake live-Claude) — blocker, almost certainly also affects V4–V7.
**Status:** SPEC_READY
**Owner:** Dev
**Scope:** Backend only. No migration.

---

## Problem

Live Claude responds correctly, but every AI skill calls `objectMapper.readValue(...)` on the **raw** LLM text. Claude wraps JSON in a markdown code fence (```` ```json … ``` ````), so Jackson throws `JacksonException` "Unexpected character ('`' code 96)". The skill's `createGates(...)` rethrows it as `InvalidStateException`, which propagates **out of the LLM try/catch but still inside the `@Transactional` method**, rolling back the entire execution. Result: `ai_executions`, `ai_llm_calls`, `ai_specialist_invocations`, `ai_execution_gates` all end with **0 rows** — real Anthropic spend incurred, never metered, failure not even recorded as FAILED, no gate created (V8 has nothing to approve).

Confirmed evidence (live, browser + DB) recorded in `qa_cycle/checkpoint-results/V3.md`.

---

## Root Cause (confirmed by file reads, not hypothesised)

1. **Raw parse, no fence strip — all 5 skills share the defect class.** Each skill does `objectMapper.readValue(outputContent, X.class)` on the verbatim LLM content, inside a `try { } catch (JacksonException e) { throw new InvalidStateException(...) }`:
   - `intake/MatterIntakeSkill.java:220` (catch→throw at 221–225)
   - `fica/FicaVerificationSkill.java:177` (catch→throw at 178–179)
   - `contractreview/ContractReviewSkill.java:152` (catch→throw at 153–154)
   - `drafting/DraftingSkill.java:246` (catch→throw at 247–248)
   - `complianceaudit/ComplianceAuditSkill.java:200` (catch→throw at 201–202)
   - **No fence-strip helper exists anywhere in `backend/src/main/java`** (grep for `stripFence`/`extractJson`/fenced-json = 0 hits). So this is a genuine shared defect class, not a one-off.

2. **Transaction boundary makes a parse failure catastrophic.** `AiSkillExecutionService.java`:
   - `@Transactional` on `executeSkill(SkillExecutionRequest)` — `:82–83`.
   - Provider LLM call wrapped in try/catch — `:111–150` (only the *provider* call is protected; on provider exception it correctly records FAILED and returns at `:140–149`).
   - Cost calc + COMPLETED save — `:153–155` (inside the transaction).
   - `request.skill().createGates(execution, response.content(), context)` — `:159`, **outside the try/catch, inside the transaction.** The parse `InvalidStateException` thrown here unwinds the whole `@Transactional` method → Spring rolls back → the COMPLETED execution saved at `:155` and the cost row are erased. This is the exact cause of "0 rows / cost never metered".

3. **Cost-metering location.** `costService.calculateCostCents(response)` runs at `AiSkillExecutionService.java:153`; persisted via `execution.markCompleted(response, costCents)` (`AiExecution.java:91–100`) at `:154–155`. Both are inside the rolled-back transaction → cost is lost on parse failure. Note `AiExecution.markFailed(...)` (`AiExecution.java:102–106`) does **not** carry cost/tokens, so the existing failure path also under-meters; relevant to the fix.

4. **`ai.specialist.failed` audit event is registered but never emitted.** Declared in `AuditEventTypeRegistry.java:130`; grep shows **zero** call sites emit it. A FAILED-with-evidence path must wire it in.

5. **Provider stack — not conflated, and PR #1443 is irrelevant to this path.** Skills resolve `integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class)` (`AiSkillExecutionService.java:230–232`) → `AnthropicAiProvider` (`integration/ai/anthropic/AnthropicAiProvider.java`, `@IntegrationAdapter(domain=AI, slug="anthropic")`). The chat/connection-test stack `assistant/provider/anthropic/AnthropicLlmProvider.java` is a **separate** provider and is not on the skill path. PR #1443 (`9dd5d2c1e`) only changed `AnthropicAiProvider.DEFAULT_MODEL` (used by the unrelated Phase-52 `generateText`/`summarize`/`suggestCategories` wrappers) — it did **not** touch `complete()`, `completeWithVision()`, or any parse code. Not a regression from #1443.

---

## Fix (elegant — fixes the defect CLASS, not one skill)

### (a) Central fence-stripping — one shared helper, parse in the base layer

The 5 skills each duplicate `readValue + catch(JacksonException)→InvalidStateException`. Collapse that into one place.

**Step a1 — Add a small JSON-extraction utility** (new file):
`integration/ai/skill/LlmJsonParser.java` (package `…integration.ai.skill`). A stateless helper that:
- strips a leading/trailing markdown code fence: tolerate ```` ``` ````, ```` ```json ````, ```` ```JSON ````, surrounding whitespace/newlines, and a trailing fence;
- if no fence is present, falls back to the substring from the first `{`/`[` to the matching last `}`/`]` (handles "Here is the JSON: { … }" preambles);
- exposes `<T> T parse(ObjectMapper mapper, String rawContent, Class<T> type)` that strips then `readValue`s, and on `JacksonException` throws `InvalidStateException("AI response parse failed", <message incl. type>)`.

Keep it a plain utility (static methods or a `@Component` injected into skills — prefer a `@Component` so it is mockable and consistent with constructor-injection conventions; no Lombok, Java record/util only).

**Step a2 — Route all 5 skills through it.** Replace the inline `try { objectMapper.readValue(...) } catch (JacksonException e) { throw … }` block in each `createGates` with one call:
```java
var output = llmJsonParser.parse(objectMapper, outputContent, MatterIntakeOutput.class);
```
Files: `MatterIntakeSkill.java:218–225`, `FicaVerificationSkill.java:175–180`, `ContractReviewSkill.java:150–155`, `DraftingSkill.java:244–249`, `ComplianceAuditSkill.java:198–203`. Inject `LlmJsonParser` via constructor in each (or use static call to avoid touching constructors — Dev's call; static keeps the diff minimal, but a `@Component` is more testable). The `objectMapper` field stays (still used for the actual bind).

> This is the minimal cohesive change: 1 new helper + 5 one-line call-site swaps. No skill keeps its own fence logic.

### (b) A parse failure must be recorded as FAILED **with cost metered** + emit `ai.specialist.failed` — never a silent full rollback

The structural bug is that `createGates` runs inside the transaction and outside the failure path. Fix in `AiSkillExecutionService.executeSkill`:

**Step b1 — Wrap gate creation in its own guard.** After the COMPLETED save (`:155`), wrap `createGates` (and only the parse/gate-building) so a parse failure does NOT unwind the whole method:
```java
List<AiExecutionGate> gates;
try {
  gates = request.skill().createGates(execution, response.content(), request.context());
} catch (InvalidStateException e) {
  // LLM answered (cost already metered at :153–155) but output was unparseable.
  // Record FAILED-with-cost; do NOT roll back the metered execution.
  execution.markFailedAfterCompletion(e.getMessage());   // see b2
  execution = executionRepository.save(execution);
  emitAuditEvent(execution);                              // ai.skill.invoked (existing)
  emitSpecialistFailedAudit(execution, e.getMessage());  // ai.specialist.failed (new, see b3)
  eventPublisher.publishEvent(toEvent(execution));
  return new SkillExecutionResult(execution, List.of());
}
```
Because the cost was already calculated and set on `execution` at `:153–155`, the metering survives — we transition the *same* row to FAILED while keeping `costCents`/tokens.

**Step b2 — Add a cost-preserving failure transition** on `AiExecution` (`AiExecution.java`): `markFailedAfterCompletion(String errorMessage)` that sets `status="FAILED"` + `errorMessage` but leaves `costCents`/`inputTokens`/`outputTokens`/`durationMs` intact (they were set by `markCompleted`). This distinguishes "LLM cost incurred, parse failed" from "LLM call itself failed" (existing `markFailed`, which has no usage).

**Step b3 — Emit the registered-but-unused `ai.specialist.failed` event** from `AiSkillExecutionService` (new private `emitSpecialistFailedAudit(...)`, mirroring `emitAuditEvent` at `:249–265`, with `eventType("ai.specialist.failed")` and details incl. `executionId`, `skillId`, `costCents`, `errorMessage`). Event type already exists in `AuditEventTypeRegistry.java:130`, so no registry change.

> Net effect: an unparseable LLM response now yields exactly one execution row, status FAILED, real `costCents` recorded, two audit events, zero gates — instead of total rollback. V8 can see the failure; cost-metering (V9) is honest.

### (c) Resolve the in-transaction LLM call (AIVERIFY-002)

Today the multi-second Anthropic HTTP round-trip happens inside `@Transactional`, holding a Hikari connection the whole time → `ProxyLeakTask` warning. Restructure so the network call is outside any DB transaction:

**Recommended pattern — split into pre-flight (read), LLM call (no tx), persist (short write tx):**
- Make `executeSkill(SkillExecutionRequest)` **non-`@Transactional`** at the top level. It orchestrates three phases:
  1. **Pre-flight + IN_PROGRESS persist** — in a short `@Transactional` helper (new package-private method, or a small collaborator): load profile, `checkBudget`, resolve provider, validate vision, save the IN_PROGRESS `AiExecution`. Returns the saved execution id + assembled prompts.
  2. **LLM call — NO transaction, NO DB connection held.** `provider.complete(...)` / `completeWithVision(...)`. On exception → call a short `@Transactional` `markExecutionFailed(id, msg)` and return.
  3. **Persist results — short `@Transactional` helper.** Re-load the execution by id, `markCompleted`, save, then `createGates` (guarded per (b)), `saveAll(gates)`, notifications, audit, event publish.
- Because Spring `@Transactional` is proxy-based, the phase helpers must be invoked through the bean (self-injection via `ObjectProvider<AiSkillExecutionService>` / `@Lazy` self-reference, or — cleaner — extract the two transactional phases into a tiny `AiExecutionPersistenceService` collaborator that this service calls). **Prefer the collaborator** (`AiExecutionPersistenceService` with `@Transactional` methods `startExecution(...)`, `completeExecution(...)`, `failExecution(...)`) — it keeps each transaction short, makes the boundary explicit, and avoids self-invocation proxy gotchas. This is the elegant version; the orchestrator should confirm this is the chosen shape.

> Splitting the transaction is the same root cause as (b): the fix for (b) (don't roll back metered work) and (c) (don't hold a connection across the LLM call) both flow from "the LLM call and the persistence must not share one long transaction." That is why I recommend folding them (see below).

---

## Scope

**Modify:**
- `integration/ai/skill/AiSkillExecutionService.java` — transaction restructure (b1, c) + new failed-audit emitter (b3).
- `integration/ai/execution/AiExecution.java` — `markFailedAfterCompletion(...)` (b2).
- 5 skills' `createGates` — route through `LlmJsonParser` (a2): `MatterIntakeSkill`, `FicaVerificationSkill`, `ContractReviewSkill`, `DraftingSkill`, `ComplianceAuditSkill`.

**Create:**
- `integration/ai/skill/LlmJsonParser.java` (a1).
- `integration/ai/skill/AiExecutionPersistenceService.java` (c — short-transaction collaborator) **[recommended shape; orchestrator confirms]**.

**Migration:** None — no schema change (status is already a free-form String column; `ai.specialist.failed` already registered).

---

## One fix or two? — Recommendation: **ONE PR.**

AIVERIFY-001 and AIVERIFY-002 share a single root cause: **the LLM call and the persistence live in one long `@Transactional` method.** That single fact produces both symptoms — (001) a post-LLM parse failure rolls back the metered execution, and (002) a JDBC connection is pinned across the multi-second HTTP call. You cannot cleanly fix 001 (record FAILED-with-cost without rolling back) without touching the same transaction boundary you must restructure for 002. Splitting them would mean two PRs editing the same method back-to-back, with the first leaving a known connection leak in place. Per CLAUDE.md §7, "same-bug-class cluster" — they are the same boundary defect — so one cohesive PR is the correct call. **Recommendation: fold 002 into the 001 PR**, with the fence helper (a) included since it is the trigger and is trivially co-located. The orchestrator authorizes.

(If the orchestrator prefers strict one-fix-per-PR, the only clean split is: PR1 = (a) fence helper + (b) guarded-gate-with-FAILED-metering keeping the existing single transaction; PR2 = (c) transaction split. But PR1 alone still holds the connection across the LLM call, so 002 stays open one cycle. I do not recommend this.)

---

## AIVERIFY-003 — assessment: **independent root cause. Defer (do not fold in).**

The endpoint exists: `AiSpecialistInvocationController.java:37–55` maps `GET /api/assistant/invocations`, accepts `status` (`InvocationStatus`, which includes `PENDING_APPROVAL` — `InvocationStatus.java:6`), `contextEntityType`, `contextEntityId`. So the 404 is **not** a missing route. It is guarded by `@RequiresCapability("AI_ASSISTANT_USE")` (`:38`); a capability denial surfaces as 404 by the codebase's security-by-obscurity convention (`ResourceNotFoundException` = 404 for access-denied per `backend/CLAUDE.md`). Likely cause is either (i) the customer-detail page calls it without the `AI_ASSISTANT_USE` capability in context, or (ii) a frontend/gateway path mismatch. Unrelated to the parse/transaction defect, non-cascading, cosmetic console error. **Recommend a separate small investigation later in the cycle** (after V3 re-verified), not bundled here.

---

## Test Plan (stub-based, verifiable by `./mvnw verify` with no live key)

Existing coverage (all use `StubAiProvider`, which loads canned `ai/stubs/{skill-id}/response.json` — `testutil/StubAiProvider.java:67,91`):
- `integration/ai/skill/AiSkillExecutionServiceTest` — orchestration.
- `integration/ai/skill/AiSkillEndToEndTest` — invoke→gate→approve via MockMvc + StubAiProvider.
- `integration/ai/skill/AiSkillControllerTest` — controller wiring.
- `integration/ai/skill/intake/MatterIntakeSkillTest` — `createGates` directly (it already parses canned JSON via `parseOutput` at `:256`).
- `integration/ai/gate/AiExecutionGateControllerTest`, `AiExecutionGateServiceTest`, `AiExecutionGateTest`.
- `integration/ai/cost/AiCostServiceTest`.
- `integration/ai/anthropic/AnthropicAiProviderTest`.

**New tests to add:**
1. **`LlmJsonParserTest`** (unit, no Spring) — asserts fence tolerance: bare JSON, ```` ```json … ``` ````, ```` ``` … ``` ````, leading prose + JSON, trailing whitespace, and a genuinely malformed body → `InvalidStateException`. This is the cheapest place to prove fence stripping.
2. **`AiSkillExecutionServiceTest` — new case "unparseable LLM output → FAILED with cost metered, no rollback, no gates."** Point `StubAiProvider` at a canned response that is non-JSON garbage (or wrap valid JSON the parser still can't bind), invoke a skill, then assert: exactly **one** `ai_executions` row, `status == "FAILED"`, `costCents > 0`, `inputTokens/outputTokens > 0`, **zero** gate rows, and an `ai.specialist.failed` audit event present. This is the regression that reproduces AIVERIFY-001 (red before fix, green after).
3. **Fence-tolerant happy path** — add a fenced variant stub (e.g. wrap the existing matter-intake stub in ```` ```json ````) and assert the skill still parses and creates gates. Easiest as a parameterized `MatterIntakeSkillTest.createGates` case feeding fenced content.

(Optional) AIVERIFY-002 has no clean unit assertion for "connection not held across LLM call"; the architectural split is the evidence. Confirm no `ProxyLeakTask` warning in the V3 live re-run logs.

---

## Estimated effort: **M**

New helper + 5 trivial call-site swaps (S) + transaction restructure into a persistence collaborator + cost-preserving failure path + new audit emitter + 3 tests (M). Single cohesive PR.

---

## Verification

Re-run **stage V3** (matter-intake, live Claude) on `main` after merge. Live-Claude PASS criteria:
- Matter-intake invocation completes; UI shows the intake recommendation / gate (no "AI response could not be parsed" error).
- DB: **≥1** `ai_executions` row for the run with `status COMPLETED`, **non-empty** `output_content`, **`costCents > 0`**, non-zero token usage; corresponding `ai_execution_gates` row(s) created (SELECT_MATTER_TEMPLATE and/or CONFIRM_CONFLICT_SCREEN).
- Backend log: no `ProxyLeakTask: Apparent connection leak` originating from `AiSkillExecutionService` during the call (AIVERIFY-002).
- Negative proof retained: an artificially unparseable response (not part of live happy path, covered by test 2) yields FAILED-with-cost, not a rollback.
- Gate id(s) recorded for V8.
Then unblock and proceed V4–V7 (same skills, same defect class — expected to pass once the shared parser + boundary are fixed).
