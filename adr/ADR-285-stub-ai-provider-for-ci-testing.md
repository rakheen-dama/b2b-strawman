# ADR-285: Stub AI Provider for CI Testing

**Status**: Accepted

**Context**:

Phase 72 introduces AI skills that call the Anthropic API via the `AiProvider` port. The backend test suite runs in CI (embedded Postgres, no Docker, no external network calls) and must verify the full execution flow: skill invocation, execution creation, gate creation, gate approval/rejection, cost calculation, budget enforcement, audit event emission, and notification delivery.

Calling the real Anthropic API in CI is problematic: it costs money per call, is non-deterministic (different responses to the same prompt), requires a real API key in CI secrets, and is subject to rate limits and network failures. The question is how to test AI features without live API calls.

Phase 70 faced a similar problem for specialist testing. The `AnthropicLlmChatProvider` was mocked at the Spring bean level for integration tests, but there was no formalised stub pattern — each test class created its own mock. Phase 72 needs a more structured approach because skills produce structured JSON output that must be parsed and validated.

**Options Considered**:

1. **`StubAiProvider` with canned responses (CHOSEN)** — Create a `StubAiProvider implements AiProvider` that returns pre-defined JSON responses matching each skill's output schema. Register it as `@Primary` in a `@TestConfiguration` so it replaces the real provider in all integration tests. Canned responses are stored as test resources (`test/resources/ai/stubs/{skill-id}/response.json`).
   - Pros:
     - **Deterministic.** Tests always get the same response for the same skill. Assertions on execution records, gates, cost, and audit events are stable.
     - **No API cost.** Zero Anthropic API calls in CI. No API key needed in CI environment.
     - **Validates the full flow.** The stub returns a realistic response that exercises the output parser, gate creator, cost calculator, and audit emitter. Everything downstream of the API call is tested with real code.
     - **Canned responses as test resources.** `response.json` files are version-controlled alongside the skill code. When the output schema changes, the test resource is updated in the same PR. This is the same pattern as Phase 70's classpath specialist prompts.
     - **Fast.** No network latency, no HTTP overhead. The stub returns immediately.
     - **`StubAiProvider` doubles as the `NoOp` replacement in tests.** The existing `NoOpAiProvider` returns empty responses that cannot exercise downstream logic. The stub returns realistic responses that do.
   - Cons:
     - **Does not test prompt quality.** The stub does not validate whether the assembled prompt would produce the expected response from a real model. Prompt quality is a manual QA step, not a CI step.
     - **Canned responses may drift from real API output.** If Anthropic changes their response format (e.g., new fields, changed field names), the stub responses remain at the old format until manually updated. Mitigated: the stub response format is modelled on Kazi's `AiCompletionResponse` record (which wraps the Anthropic response), not on the raw Anthropic API format. Changes to the Anthropic API are absorbed by `AnthropicApiClient`, not by the stub.
     - **No negative-path testing from the API.** The stub cannot simulate Anthropic-specific errors (429 rate limit, 529 overloaded, malformed response). These are tested at the `AnthropicApiClient` unit test level with mocked HTTP responses, not at the integration test level.

2. **Recorded responses (VCR/WireMock pattern)** — Record real Anthropic API responses during a manual test session and replay them in CI. Use WireMock or a custom HTTP interceptor to intercept and replay HTTP traffic.
   - Pros:
     - Responses are real — they came from the actual API. Higher fidelity than hand-crafted stubs.
     - The recording captures the full HTTP exchange (headers, status codes, timing), enabling more thorough API client testing.
   - Cons:
     - **Requires an initial recording session with a real API key.** Someone must run the tests against the real API, record the responses, and commit them. This adds friction to the development workflow.
     - **Recordings contain sensitive data.** The recorded HTTP exchange includes the API key in the request headers and potentially client data in the prompt. These must be sanitised before committing, adding manual steps.
     - **Brittle.** Recorded responses are snapshots of a specific API version. When Anthropic updates their API (new fields, changed behaviour), recordings must be re-recorded. Unlike hand-crafted stubs (which only include fields Kazi cares about), recordings include the full response.
     - **WireMock adds a test dependency.** WireMock is a heavyweight test library (HTTP server, port management, JSON matching). The existing test stack (embedded Postgres, MockMvc, GreenMail) does not use WireMock. Adding it increases test infrastructure complexity.
     - **Non-deterministic prompt content.** Skill prompts include dynamic data (customer names, document lists, timestamps). The recorded response was generated for a specific prompt. If the test data changes, the recorded response may not match the new prompt, but the test still passes — a false positive.

3. **Live API calls in a staging environment** — Run integration tests against the real Anthropic API in a staging CI pipeline (not the main CI). Use a dedicated test API key with a low-spend budget.
   - Pros:
     - Tests the full end-to-end flow including real prompt quality, real token counts, and real response format.
     - Catches API compatibility issues (Anthropic API changes, model deprecations) early.
   - Cons:
     - **Costs money.** Each CI run invokes the API multiple times. At ~$0.003-0.015 per invocation (depending on model and token count), 10 test invocations per CI run, 20 CI runs per day = $0.60-3.00/day. Sustainable but non-trivial over time.
     - **Non-deterministic.** The same prompt can produce different JSON structures, different confidence scores, different reasoning text. Tests cannot assert on exact values — only on structural validity. This limits the depth of assertions.
     - **Slow.** Each API call takes 5-30 seconds. A test suite with 10 invocations adds 50-300 seconds of wall-clock time. Unacceptable for the main CI pipeline.
     - **API key in CI secrets.** The Anthropic API key must be stored in CI secrets and rotated periodically. Security surface increases.
     - **Rate limit risk.** Aggressive CI (many branches, many runs) could hit Anthropic's rate limits, causing flaky test failures.
     - **Breaks the "no external network calls in CI" convention.** The backend test suite runs against embedded Postgres with no Docker and no network dependencies. Adding an external API dependency breaks this invariant.

**Decision**: Option 1 — `StubAiProvider` with canned responses stored as test resources. The stub is registered as `@Primary` via `@TestConfiguration` and replaces the real provider in all integration tests.

**Rationale**:

The purpose of CI tests is to verify the application's internal logic: execution creation, gate creation/approval/rejection/expiry, cost calculation, budget enforcement, capability gating, audit event emission, and notification delivery. All of this logic is downstream of the `AiProvider.complete()` call. A `StubAiProvider` that returns realistic, deterministic JSON exercises 100% of this downstream logic without the cost, latency, non-determinism, and infrastructure complexity of live API calls.

Prompt quality — whether the assembled prompt produces useful AI output — is a fundamentally different concern. It depends on the model's behaviour, the prompt's phrasing, and the quality of the input data. This is tested via manual QA with a real API key, using representative SA legal documents (redacted). Manual QA is the appropriate tool for evaluating prompt quality because it requires human judgment (is the FICA verification result correct? does the fee estimate seem reasonable?) that CI cannot provide.

The `StubAiProvider` pattern is simpler and more reliable than recorded responses (Option 2) because it avoids HTTP-level recording, sanitisation, and replay. The stub operates at the Java interface level — it implements `AiProvider` and returns `AiCompletionResponse` records directly. This is the same abstraction level as the rest of the application code.

Live API calls in CI (Option 3) violate the "no external network calls" convention, add cost and latency, and produce non-deterministic results. The marginal value of testing against the real API does not justify the operational complexity.

**Consequences**:

- Positive:
  - Deterministic, fast CI tests. No API cost, no API key in CI, no network dependency.
  - Full downstream flow tested: parsing, gates, cost, audit, notifications.
  - Canned responses version-controlled alongside skill code. Schema changes are caught in PR review.
  - Pattern reusable for Phase 73+ skills — each skill adds a `response.json` test resource.

- Negative:
  - No prompt quality testing in CI. Manual QA required for prompt evaluation. This is a known gap, documented in the QA plan.
  - Canned responses must be manually kept in sync with the output schema. A schema change without a corresponding response update will fail the test (which is correct — it catches the drift).

- Neutral:
  - `StubAiProvider` lives in `test/.../testutil/StubAiProvider.java` alongside existing test utilities (`TestMemberHelper`, `TestJwtFactory`, etc.).
  - The stub is registered via `@TestConfiguration` in `TestAiConfiguration.java`, imported by `TestcontainersConfiguration` (which all integration tests already import).
  - Canned responses live in `test/resources/ai/stubs/{skill-id}/response.json`. Each file contains a valid JSON response matching the skill's output schema.
  - Token counts in canned responses are realistic estimates (e.g., 2000 input tokens, 800 output tokens for FICA verification). Cost tests use these to verify the `AiCostService` calculation logic.
  - Manual QA for prompt quality uses a separate test profile (`test-with-api`) that activates `AnthropicAiProvider` instead of `StubAiProvider`. This profile is never used in CI — only in local manual testing with a real API key configured in `application-test-with-api.yml`.

- Related: [ADR-268](ADR-268-ocr-via-claude-vision-byoak-no-separate-vendor.md) (BYOAK — no separate vendor for AI operations), [ADR-280](ADR-280-evolve-ai-provider-port-for-skills.md) (AiProvider interface — what the stub implements), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md) (cost metering — stub responses include realistic token counts for cost testing)