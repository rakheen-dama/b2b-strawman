# ADR-334: Automation Engine Without a Builder — Headless Pack Rules, Always-On Admin List with Enable/Disable

**Status**: Accepted

**Context**:

Phase 37 shipped a structured rules engine (trigger → conditions → actions; ADR-145 chose this over a visual workflow builder), with JSONB rule config (ADR-148), cycle detection (ADR-146), delayed actions (ADR-147) and execution logging (ADR-149). Rules reach tenants two ways: pack-installed templates (`automation-templates/{common,legal-za}.json` via `AutomationPackInstaller`, Phase 65 packs catalogue, ADR-240) and the rule editor under `settings/automations/{new,[id]}` gated by the `automation_builder` HORIZONTAL module (ADR-239). `settings/automations/executions` shows runs; `settings/automations/ai-queue` is the Phase 70 specialist review queue.

Scouting (`architecture/phase86-subtraction-legal-only.md` §11.3.9, §11.3.10(i)) found that `legal-za.json` does **not** enable `automation_builder`, so a law firm today cannot see, let alone disable, any installed rule — including the four `SEND_NOTIFICATION` rules and one `CREATE_TASK` rule its pack installs. The engine's only outside consumer is `packs`. The founder decision (`requirements/claude-code-prompt-phase86.md`, "Automation engine stays headless-but-visible"): keep the engine and the legal-za templates; keep a read-only *list* of installed rules with enable/disable; delete the rule editor, `new`, `[id]` and `ai-queue`; "no UI at all" is also acceptable if the templates are documented as fixed. The exact surviving page is this ADR's call.

Two legal-za rules trigger on `PROPOSAL_SENT` ("Engagement Letter Follow-up" in `legal-za.json`, one more in `common.json`). `proposal` is deleted in slice 3 (ADR-336) and no acceptance-request-sent trigger exists; adding one is a new feature, which the phase forbids.

**Options Considered**:

### A. Engine and UI

1. **Keep the engine and pack rules; delete the editor, `ai-queue` and the `automation_builder` module; Settings → Automations becomes an always-on admin page listing installed rules with enable/disable, plus `executions` (CHOSEN)** — backend `AutomationRuleController` loses `POST /api/automation-rules` (:40), `PUT /{id}` (:55), `DELETE /{id}` (:62), `POST /{id}/duplicate` (:75), `POST /{id}/test` (:83) and the whole `AutomationActionController` (`/api/automation-rules/{ruleId}/actions`, :29-55), and keeps `GET /api/automation-rules`, `GET /{id}`, `POST /{id}/toggle` and `AutomationExecutionController`; `requiredModule: "automation_builder"` is removed from the nav item; rule-editor components under `components/automations/` are deleted.
   - Pros: an admin can stop a notification rule that annoys the firm — the one operation a headless engine still needs a UI for; the engine, `AutomationPackInstaller`, cycle detection and execution logging are untouched; legal firms gain visibility they never had (the module was never on for them); the page needs no module gate because every surviving tenant is legal-za and every rule comes from a pack.
   - Cons: the engine keeps editor-era generality (conditions JSONB, arbitrary action lists, delay scheduling) that no one can author in-product; the rules list is effectively a pack manifest viewer with a toggle.
2. **Delete the engine entirely; re-implement the five legal rules as hard-coded event listeners.**
   - Pros: ~5.7k lines and the automation tests leave; no JSONB config, no cycle detection to maintain.
   - Cons: forbidden by the keep list ("automation *engine*"); the `DOCUMENT_ACCEPTED`, `FieldDateApproaching` and scheduled-trigger paths (`AutomationPollTriggersHandler` on the Phase 75 queue) are live legal behaviour; hard-coding removes the per-firm off switch; re-implementing working behaviour is a new feature in a subtraction phase.
3. **Keep the engine and the builder; drop only `ai-queue`.**
   - Pros: firms could author their own rules.
   - Cons: legal-za never enabled the builder, so this *adds* a surface to the legal product; the editor UI is the largest part of `components/automations/` and carries its own tests and e2e; `automation_builder` would be the only HORIZONTAL module whose purpose is a page no pilot asked for.
4. **Engine headless with no UI at all; templates documented as fixed.**
   - Pros: smallest frontend; explicitly permitted by the requirements.
   - Cons: no way for a firm to silence a rule without a platform-admin SQL change — which QA rules forbid as a workaround; `executions` is the only diagnostic view of why a notification or task appeared and would also go.

### B. The two `PROPOSAL_SENT` rules

1. **Delete both rules; defer an `ACCEPTANCE_REQUEST_SENT` trigger to Phase 87+ (CHOSEN)** — `TriggerType.PROPOSAL_SENT`, `TriggerTypeMapping`, the `AutomationContext` branch and `AutomationEventListener` entry go with `ProposalSentEvent`; `LegalZaAutomationPackTest.EXPECTED_SLUGS` loses the slug and its `hasSize` is recounted.
   - Pros: no new trigger, event or template in a subtraction phase; the engagement-letter path (ADR-336) still fires `DOCUMENT_ACCEPTED` on acceptance, so the engine's involvement in the flow is preserved at the step that matters legally.
   - Cons: the "follow up on an unsigned engagement letter after N days" task is lost until the trigger exists.
2. **Add `ACCEPTANCE_REQUEST_SENT` now and re-point the rules.**
   - Pros: feature parity for the follow-up task.
   - Cons: a new `DomainEvent`, trigger type, context branch, mapping, template edit and tests — a new feature the requirements and §11.10 ("no slice may add a trigger") forbid.
3. **Re-point the rules to the existing `DOCUMENT_ACCEPTED` trigger.**
   - Pros: no new trigger.
   - Cons: semantically wrong — a follow-up on *acceptance* fires after the client has already signed; it would create a task nobody needs and misrepresent the pack.

**Decision**: Keep the automation engine and the legal-za pack rules; delete the rule editor (`new`, `[id]`), the `ai-queue` page (with ADR-333), the `INVOKE_AI_SPECIALIST` action and the `automation_builder` module; Settings → Automations becomes an always-on admin page listing installed rules with enable/disable and the `executions` view (A1). Both `PROPOSAL_SENT` rules are deleted and an `ACCEPTANCE_REQUEST_SENT` trigger is noted for Phase 87+ (B1).

**Rationale**:

1. **Headless-but-visible is the founder's floor, and the toggle is the minimum that clears it.** Without enable/disable a firm's only recourse against an unwanted notification is a platform-admin database change, which the QA rules and the pilot's support model both reject.
2. **The engine is load-bearing for legal flows.** `DOCUMENT_ACCEPTED` (engagement letters), `FieldDateApproaching` (custom-field dates feeding the portal deadlines module), and scheduled triggers on the Phase 75 job queue are all engine paths that the legal lifecycle exercises. Deleting the engine would be re-implementation, not subtraction.
3. **The builder never had a legal user.** `legal-za.json` does not enable `automation_builder`; the editor exists for accounting/consulting pilots that no longer exist. Removing the module and the gate makes a page that legal firms could never see into one they always can — strictly more useful for less code.
4. **Packs are the install path, by design.** ADR-240/241 made add-only pack install the way content reaches tenants; a rules list over pack-installed rules is consistent with that, and `AutomationPackInstaller` stays.
5. **No new triggers in a subtraction phase.** The follow-up task is a real loss but a small one; adding a trigger here would widen slice 3 and set a precedent against "any new feature" being out of scope.

**Consequences**:

- Positive: Settings → Automations is always-on for admins, lists pack-installed rules with an enable/disable toggle, and keeps `executions`; `automation_builder` leaves `VerticalModuleRegistry` (ADR-332: 8 modules remain); `components/automations/rule-list.tsx` loses its `docsLink` with the docs site (§11.3.8).
- Positive: `AutomationRule.last_run_at` (V120) stays — verified at build time to belong to the scheduled-trigger engine, not the specialist executor; `AutomationPollTriggersHandler` and `AutomationSchedulerScheduledTriggerIntegrationTest` survive, re-fixtured on `SEND_NOTIFICATION`.
- Negative: firms cannot author rules in-product; every rule comes from `common.json` / `legal-za.json` and changing one is a pack edit plus reconciliation, not a UI action.
- Negative: the engine carries editor-era generality (conditions JSONB, action lists, delays, cycle detection) with no in-product author — dead-looking code that must not be pruned in this phase and is reported for Phase 87.
- Negative: the engagement-letter follow-up task is gone; "Engagement Letter Follow-up" disappears from the legal-za pack and `LegalZaAutomationPackTest` until an `ACCEPTANCE_REQUEST_SENT` trigger exists.
- Negative: `AutomationRuleController`'s write endpoints are deleted, so a future rule editor is a rebuild, not an un-hide; the tag (ADR-330) is the reference.
- Related: [ADR-145](ADR-145-rule-engine-vs-visual-workflow.md) (Phase 37 engine shape, retained), [ADR-148](ADR-148-jsonb-config-vs-normalized-tables.md) / [ADR-149](ADR-149-execution-logging-granularity.md) (config and executions, retained), [ADR-239](ADR-239-horizontal-vs-vertical-module-gating.md) (module gate removed for this page), [ADR-240](ADR-240-unified-pack-catalog-install-pipeline.md) / [ADR-241](ADR-241-add-only-pack-semantics.md) / [ADR-244](ADR-244-pack-only-vertical-profiles.md) (Phase 65 packs — the install path), [ADR-333](ADR-333-mcp-sole-conversational-ai-surface.md) (`ai-queue` and `INVOKE_AI_SPECIALIST`), [ADR-336](ADR-336-engagement-letter-document-plus-acceptance.md) (why `PROPOSAL_SENT` has no event to fire on), [ADR-332](ADR-332-single-vertical-profile-mechanism-retained.md).
