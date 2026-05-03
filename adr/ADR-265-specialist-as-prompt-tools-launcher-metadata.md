# ADR-265: Specialist = System Prompt + Tool Subset + Launcher Metadata, Not an Entity

**Status**: Accepted

**Context**:

Phase 70 introduces three SA-specialised AI assistants — Billing, Intake, Inbox — on top of the Phase 52 chat infrastructure. Each specialist is identified by a stable id (`BILLING`, `INTAKE`, `INBOX`), has a system prompt with SA-specific domain knowledge, exposes a constrained subset of the existing `AssistantToolRegistry`, and can be launched from one or more inline UI surfaces (e.g. invoice draft toolbar, customer create dialog). The product team has signalled that more specialists will be added in later phases (Drafting, Compliance) and that retiring or renaming a specialist should be a low-risk activity.

The shape of "specialist" raises a structural question: should each specialist be a row in a tenant table — letting admins toggle, edit, or rename specialists — or should the specialist catalog be code-and-classpath-driven, with no per-tenant mutation? The answer affects migration cadence, prompt versioning, and the blast radius of a bad prompt change.

**Options Considered**:

1. **Per-tenant `Specialist` entity with editable prompt and tool list.** Firm admins create / edit / delete specialists; the registry reads from the database; prompts are tenant-mutable.
   - Pros:
     - Maximum tenant flexibility — a firm could, in theory, write its own specialist or alter the Billing prompt to match house style.
     - No code deploy needed to ship a new specialist.
   - Cons:
     - Tenant-mutable prompts are an attack surface and a support nightmare. A firm admin who edits the Billing prompt to "always invent line items" would silently degrade output quality, and the support team would have no diff against a known-good baseline.
     - Migration churn: every prompt revision becomes a data migration across all tenant schemas. Phase 70's three prompts (~3-5K tokens each) are already substantial; versioning them in the database forces every prompt edit through Flyway or a custom backfill.
     - Tool subsets in the database mean tool-id strings live in two places (Java registry + tenant rows). Renaming a tool becomes a coordinated migration.
     - The product team is not asking for tenant prompt editing in v1 (or v2). Building it would be speculative complexity.

2. **Code-only registry: `Specialist` as a Java record + `SpecialistRegistry` Spring bean, prompts as classpath markdown (CHOSEN).** No database table for specialists. Adding a specialist = writing one record + one markdown file + restarting. Prompts are versioned in the codebase like any other resource.
   - Pros:
     - Prompt versioning rides on git. Every change is a PR with a diff, a reviewer, and a deployable commit. Rollback = revert.
     - No migration cost to add or modify a specialist. A new specialist ships with code.
     - Tool-id consistency is guaranteed by the compiler — `List<String> toolIds` is validated against `AssistantToolRegistry` at startup; an unknown tool-id fails fast.
     - Reloadable in dev/local profile via a guarded endpoint, so prompt iteration during development doesn't require a full restart.
     - The `AiSpecialistInvocation` table (ADR-270) already records every invocation with `specialistId`, so audit / analytics never depend on a `Specialist` row existing.
   - Cons:
     - Tenants cannot customise prompts. If a firm wants "say 'attorney' instead of 'paralegal'" the answer is "use OrgSettings terminology lookup at runtime" or "open a feature request" — not a self-serve text edit.
     - Adding a specialist requires a deploy. Acceptable: specialist additions are product-strategy moments, not weekly tweaks.

3. **Hybrid: Java registry as the canonical catalog, tenant table for opt-in/opt-out + per-tenant overrides.** Code defines the specialist; tenants can disable it or override its tagline / launcher labels via a thin tenant table.
   - Pros:
     - Compromise — most flexibility lives in code, tenants get a small kill-switch surface.
     - A future "tenant prompt overlay" feature could slot into this shape.
   - Cons:
     - Two moving parts where one would do. Until a tenant actually asks for the kill-switch, the table is dead weight that adds a migration and a tenant-level cache.
     - The `AssistantToolRegistry` already filters tools by capability per acting member — a specialist that lists `INVOICE_EDIT`-only tools for a member without that capability already gracefully degrades. The kill-switch use case is therefore weak.
     - Carries the worst of both worlds: tenants can disable but cannot meaningfully reshape, so user expectation diverges from reality.

**Decision**: Option 2 — `Specialist` is a Java record and `SpecialistRegistry` is an in-code Spring-managed singleton. System prompts live as classpath markdown under `backend/src/main/resources/assistant/specialists/`. There is no `Specialist` entity, no tenant table for specialists, no migration in Phase 70 that touches a specialist catalog.

**Rationale**:

The blast radius of a bad prompt is real. A specialist's prompt encodes the firm's brand voice when polishing time entries, the validation rules for RSA ID numbers, and the refusal rules that keep the assistant inside its scope. Tenant-mutable prompts (Option 1) push that blast radius onto every tenant, with no review and no diff. Phase 52 already established that BYOAK-cost Anthropic calls go through a centrally-controlled provider adapter ([ADR-200](ADR-200-llm-chat-provider-interface.md)); centralising the prompts the same way is consistent.

The product team has not asked for per-tenant prompt editing, and building it speculatively conflicts with the YAGNI guidance in `backend/CLAUDE.md` ("Avoid premature abstractions — do not create provider/adapter patterns until there are two concrete implementations"). Three specialists in Phase 70, two more deferred to Phase 71, no third-party specialists planned: the registry stays small enough that a Spring `Map<String, Specialist>` is the right shape.

The decision composes cleanly with [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (a single `AiSpecialistInvocation` table records every run) and [ADR-269](ADR-269-sa-specialisation-in-prompts-not-fine-tuning.md) (SA context lives in prompts). Together they form a coherent surface: specialists are code + classpath, invocations are persisted per-tenant, and the LLM provider is unchanged from Phase 52.

**Consequences**:

- Positive:
  - Adding a specialist = one Java record + one markdown file + one PR. No migration. Reviewable diff. Reverts cleanly.
  - Prompt linter (a backend integration test that asserts each prompt contains required SA tokens like "ZAR", "SA English", RSA ID format references) protects against accidental deletions during prompt maintenance.
  - The `SpecialistRegistry` validates `toolIds` against `AssistantToolRegistry` at startup. Unknown tool-id = startup failure = caught in CI.
  - Dev/local profile gets a `POST /internal/assistant/specialists/reload` endpoint (gated by `@Profile({"local","dev"})` per the same pattern as `DevPortalController` / [ADR-033](ADR-033-local-only-thymeleaf-test-harness.md)). Production never sees this surface.

- Negative:
  - Tenants cannot customise specialist prompts in v1. A firm that wants a different tone has to live with the SA-default tone. If demand emerges, Phase 71+ can add an overlay table without breaking the registry contract.
  - A typo in a prompt is a deploy-required fix. Acceptable for the volume (low single digits of prompt edits per quarter expected).

- Neutral:
  - The launcher metadata (`route`, `surface`, `ctaLabel`) lives in the same record as the prompt resource — one record, one file. The frontend `<SpecialistLauncherButton>` reads `surface` strings and renders a button only when the launcher matches the current page.
  - Specialist display names + taglines + launcher labels go through the Phase 43 message catalogue for i18n. The record holds only the message keys / English defaults.

- Related: [ADR-200](ADR-200-llm-chat-provider-interface.md) (Phase 52 `LlmChatProvider` — unchanged), [ADR-266](ADR-266-inline-launchers-primary-chat-panel-secondary.md) (where specialists are launched from), [ADR-269](ADR-269-sa-specialisation-in-prompts-not-fine-tuning.md) (why SA context is in prompts), [ADR-270](ADR-270-ai-specialist-invocation-jsonb-output.md) (how invocations are recorded), [ADR-033](ADR-033-local-only-thymeleaf-test-harness.md) (profile-gated dev surfaces precedent).
