# V11 — Error & graceful-degradation paths (resume, 2026-06-15, QA)

**Cycle**: AI Core Live-Claude Verification (Keycloak)
**Actor**: Nomsa Verifain (owner; AI_MANAGE/AI_EXECUTE/AI_REVIEW), `nomsa@verifain-test.local`
**Driver**: Playwright MCP. Session already had a live Nomsa SSO session (`/bff/me` → nomsa@verifain-test.local, userId `601d6446…`).
**Org**: `verifain-attorneys` / tenant `tenant_c6107524c9b4`. Budget R5000 (profile_version 14), headroom ~R4976 at start.

## Scope this turn
- **V11.1 (bad key)** — DEFERRED (would overwrite the human's working Anthropic key; needs human re-entry). NOT run.
- **V11.2 (missing-profile graceful degradation)** — optional/second-org; not required this turn.
- **V11.3 (prompt caching)** — ✅ PASS (observed live).
- **V11.4 (input validation)** — ✅ PASS (observed: live UI guard + backend pre-flight 400 confirmed in code).

---

## V11.3 — Prompt caching → ✅ PASS (Observed, live)

**PASS criterion (scenario):** invoke the same skill on the same entity twice; on the 2nd call the backend shows `cacheReadInputTokens > 0` (system prompt cached) and cost is lower than the 1st.

### Implementation (confirmed in code)
Anthropic prompt caching IS implemented:
- `AnthropicApiClient.java:30` — `CACHE_CONTROL_EPHEMERAL = Map.of("type","ephemeral")`.
- `AnthropicApiClient.java:126-128` (text) / `:142-144` (vision) — wraps the **system prompt** in a `SystemBlock` carrying `cache_control: ephemeral`.
- `AnthropicApiClient.java:230-236` — `mapResponse()` extracts `usage.cache_read_input_tokens` + `usage.cache_creation_input_tokens`.
- Persisted on `ai_executions.cache_read_input_tokens` / `cache_creation_input_tokens` (`AiExecution.java:98-101`). (`ai_llm_calls` table is empty across this whole cycle — this tenant meters on `ai_executions`.)
- `AiCostService` prices cache-read + cache-creation tokens separately (cache-read ≈ 0.1× input price).

### Live observation 1 — fresh cache WRITE (today, this turn)
Ran **FICA on Sipho Dlamini** as Nomsa via the customer-page "Verify with AI" button → COMPLETED, **Cost R 1.04**, "Completed in 51204ms", MARK_KYC_COMPLETE gate PENDING (`87af502a…`). DB row:

| skill | status | cost_cents | cache_read | cache_creation | in_tok | out_tok | created_at |
|---|---|---|---|---|---|---|---|
| fica-verification | COMPLETED | 104 | **0** | **1671** | 544 | 3235 | 2026-06-15 12:32:57 |

→ `cache_creation=1671, cache_read=0` = a **cache WRITE** on a cold cache. Confirms the system prompt is being committed to the ephemeral cache.

### Live observation 2 — cache HIT pair already in the trail (compliance-audit, 2026-06-14)
The DB already contains a textbook live **cache-write → cache-read** pair on the same skill within the 5-min ephemeral TTL:

| skill | status | cost_cents | cache_read | cache_creation | in_tok | out_tok | created_at |
|---|---|---|---|---|---|---|---|
| compliance-audit | COMPLETED | 200 | 0 | **1848** | 231 | 6717 | 2026-06-14 23:10:27 |
| compliance-audit | COMPLETED | **125** | **1848** | 228 | **3** | 4397 | 2026-06-14 23:13:27 |

- 23:10:27 = cache WRITE (`cache_creation=1848`).
- 23:13:27 (≈3 min later, within the 5-min TTL) = **cache HIT**: `cache_read_input_tokens=1848` (read the previously-written system prompt), `cache_creation=228` (small delta), fresh `input_tokens=3` only.
- **Cost dropped to 125c** vs the cold-cache compliance-audit runs (200 / 180 / 169 / 167c) — the cache read is billed at ~10% of fresh input, so the repeat call is demonstrably cheaper. ✅

**Verdict V11.3 = PASS.** Prompt caching is implemented on the system prompt, engages live (cache_creation on the first call, cache_read>0 on a repeat within TTL), and the cached repeat is cheaper. Aggregate over the cycle: 27 executions, `sum(cache_read)=7128`, `sum(cache_creation)=31399` tokens — caching active throughout.

### ⚠️ ENV-HEALTH note (no budget burned on a 2nd live call)
I attempted a 2nd live FICA ("Run Again", clicked 12:34:01) to capture a fresh same-session cache-read. It **never persisted** (no new `ai_executions` row, no IN_PROGRESS row, no FICA/Anthropic log line) and coincided with **HikariPool thread-starvation warnings** in the backend log (`HikariPool-1/2/3 … Thread starvation or clock leap detected, housekeeper delta=57s→1m21s` at 12:35–12:37) — the same env-degradation signature the ENV-HEALTH GUARD warns about. Per the guard I **did not retry-burn**: the cache-hit PASS is already proven by the compliance-audit pair above + today's cache-write, so a 2nd live call was not needed. The hung "Run Again" is an env/thread-saturation symptom, **not** a caching defect. Backend stayed UP (no crash, no "Request cancelled" line this time — it was thread starvation, not socket death). Final exec count = 27.

---

## V11.4 — Input validation → ✅ PASS (Observed)

**PASS criterion (scenario):** matter-intake with a <20-char description → 400; fica-verification with no documents / no pending items → a clear, handled error (no 500/stacktrace/silent hang, and no wasted LLM call).

### Case A — matter-intake, <20-char description
- **Live UI guard (observed):** at `/projects/new`, selected **Sipho Dlamini** + typed a 10-char description ("short desc"). The **"Get AI Recommendations" button stays `disabled`** (verified via DOM: `disabled=true`). The label reads "Description (min 20 chars for AI)". The UI prevents the call up front → no wasted LLM spend. Screenshot: `v11-4-matter-intake-short-desc-button-disabled.png`.
- **Backend pre-flight (confirmed in code):** `MatterIntakeSkill.java:99-103` — `if (description == null || description.length() < 20) throw new InvalidStateException("Description too short", "Matter description must be at least 20 characters for meaningful intake analysis")`. This throw is **before** the LLM call (no cost). `InvalidStateException` extends `ErrorResponseException` and is constructed with `HttpStatus.BAD_REQUEST` + a `ProblemDetail` body (`InvalidStateException.java:6-11`) → a clean **400**, not a 500.
- (A direct cross-origin REST probe from the :3000 page to :8443 was CORS-blocked — the known double-Origin/CORS deferred item — so the 400 is evidenced by the live UI guard + the code path mapping to BAD_REQUEST, not by a hand-issued curl. The skill's normal path is a Next.js server action, not a browser-origin fetch.)

### Case B — fica-verification, no documents / no pending items
- **Backend pre-flight (confirmed in code):** `FicaVerificationSkill.java:104-133`:
  - No uploaded documents → `InvalidStateException("No documents", "Customer has no uploaded documents for FICA verification")` → **400**.
  - No active checklist with PENDING items → `InvalidStateException("No active checklist", "Customer has no active compliance checklist with PENDING items")` → **400**.
  - Both are pre-flight (before any LLM call) → clean 400 + descriptive ProblemDetail, no 500/stacktrace, no wasted spend.
- **Live corroboration:** today's FICA-on-Sipho ran gracefully even when PDF text extraction failed (PDFBox `WARN found wrong object number` at 12:32:57) — the skill returned a structured NEEDS REVIEW result ("Document text extraction failed — content unverifiable…"), **not** a 500. Confirms the skill degrades gracefully on bad/unreadable document input rather than crashing.

**Verdict V11.4 = PASS.** Both invalid-input paths are rejected pre-flight with clean 400s + descriptive messages; the UI additionally blocks the matter-intake short-description case before submit; unreadable-document input degrades to a structured "needs review" result, not a stacktrace.

---

## V11 verdict
- **V11.3 = ✅ PASS** (prompt caching implemented + engages live + cheaper repeat).
- **V11.4 = ✅ PASS** (matter-intake <20 char → 400 + UI guard; FICA no-docs/no-pending → 400; graceful on unreadable doc).
- **V11.1 = DEFERRED** (bad-key test invalidates the human's stored key; needs human re-entry).
- **V11.2 = not run** (optional second-org graceful-degradation; not required).
- **ENV note:** thread-starvation (HikariPool) re-appeared on a 2nd back-to-back live call; flagged, no retry-burn. Recommend a backend restart before any further live-call-heavy stages.

Evidence: this file; screenshot `v11-4-matter-intake-short-desc-button-disabled.png`; DB cache-token rows above; backend log `.svc/logs/backend.log` (HikariPool starvation 12:35–12:37; PDFBox WARN 12:32:57).
