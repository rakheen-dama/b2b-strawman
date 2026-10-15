# Phase 86 — Subtraction: Legal-Only Kazi, Archive the Horizontal Platform, Squash Migrations

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-10-15, founder returning after a 3-month gap). This phase exists because of a **product-focus decision, not a feature-roadmap one**: Kazi is a product for small-to-medium South African law firms, and the horizontal practice-management platform it grew out of (accounting-firm and consulting/agency verticals, a generic "fits every vertical" structure) is now cost without revenue. 83 phases shipped with zero production tenants. Every horizontal module still compiles, migrates, seeds and runs in the full `./mvnw verify`; every generic abstraction makes the legal UI worse. The founder's instruction: *"drop accounting and agency, push them out into a different fork so we don't lose it all, it's time to simplify."*
>
> **The one-time window this phase exploits:** there are **zero production tenants**. That makes every removal a true deletion (drop the entity, drop the table) and makes the 133 tenant + 25 global Flyway migrations squashable into a clean baseline. The day the first design-partner firm's trust ledger is in the database, this window closes permanently and every removal becomes a data migration. Sequencing decision (founder-confirmed): **Phase 86 (this) → Phase 87 (legal-native shell + API vocabulary) → Phase 84 (take-on, already specced)**. Phase 85 (`/legal` marketing page) is frontend-only and may run whenever convenient.
>
> **Scouting note (verified against current code, 2026-10-15 — do NOT re-derive):**
> - **Code weight:** backend `src/main/java` ≈ 176k lines / 1,628 files; frontend ≈ 245k lines TS; `portal/` ≈ 16k; `gateway/` ≈ 2k; `docs/` is a separate Nextra site. Tenant migrations run to `V135__add_cash_digest_reference_type.sql`; global to `V25__create_shard_config.sql`. Flyway is configured in `config/FlywayConfig.java` (global, `baselineOnMigrate(true)`) and `provisioning/TenantMigrationRunner.java` (per-schema).
> - **Removal candidates and their cross-package consumers (the seams to cut):**
>   - `capacity` (~2.5k lines, 9 tests) ← `report/ReportService` (utilization report). Also `budget` is imported by `capacity`.
>   - `retainer` (~2.5k, 11 tests) ← `billingrun/RetainerBillingService`, `customer/CustomerService`, `customerbackend/service/RetainerPortalSyncService`, `portal/notification/PortalEmailNotificationChannel`, `proposal/ProposalOrchestrationService`. Portal read-model `V20__portal_retainer_read_model.sql` + portal pages `/(authenticated)/retainer`. **Terminology maps `Retainer → Mandate` for legal-za** — the legal word survives but attaches to the engagement-letter/acceptance flow, not to an hour-bank entity.
>   - `schedule` (recurring schedules, ~1.9k, 4 tests) ← `projecttemplate/PeriodCalculator`, `projecttemplate/ProjectTemplateService`, `seeder/SchedulePackSeeder`, `notification/NotificationService`, `notification/NotificationEventHandler`. **`projecttemplate` STAYS** (matter templates; legal-za ships `project-template-packs/legal-za.json`) — only the recurring-schedule half is cut.
>   - `proposal` (~4.2k, 12 tests) ← `crm` (5 files), `audit/DatabaseAuditService`. Portal `V12__portal_proposals.sql` + `/(authenticated)/proposals`, frontend `lib/api/portal-proposals.ts`. **Terminology maps `Proposal → Engagement Letter`**: after removal an engagement letter is a `DocumentTemplate`-generated document sent through Phase 28 acceptance (`acceptance` pkg, which predates Phase 32 and works on any generated document). This replacement path is an acceptance test of this phase, not an assumption.
>   - `crm` (~2.9k, 12 tests) ← `seeder/DealPipelinePackSeeder`, `dashboard/DashboardService` (pipeline widget). Profile pack type `deal_pipeline` in `vertical-profiles/*.json`.
>   - `assistant` (~7.6k, 25 tests; subpackages `tool` 3.7k, `invocation` 1.8k, `specialist` 1.0k, `provider` 0.55k) ← `integration/IntegrationService` + `integration/AiSettingsController` (both only for `assistant.provider.LlmChatProviderRegistry` / `ModelInfo`), `automation/executor/InvokeAiSpecialistActionExecutor`. **`mcp` imports nothing from `assistant` or `automation`.** `integration/ai` (Phase 72/74: `AiProvider`, skills, gates, cost metering) imports only `assistant.provider.*`. Specialist artefacts: `automation-templates/ai-specialist-legal-za.json`, `V120__add_ai_specialist_invocations_and_llm_calls.sql`, payload classes under `assistant/invocation/payload`.
>   - `clause` ← `template` (6 files), `provisioning` (2), `integration` (2), `fielddefinition` (1); legal-za ships `clause: ["conveyancing-za-clauses"]`. **KEEP — it is part of the document pipeline, not agency baggage.** (Corrects the earlier ideation shortlist.)
>   - `billing` (tenant subscriptions, ~2.4k, 12 tests) ← `demo`, `security` (`SubscriptionGuardFilter`), `provisioning`, `member`, `assistant`. Contains both the **billing-method model** (`Subscription`, `BillingMethod` TRIAL/MANUAL/MANAGED, trial/grace/cancellation handlers, `AdminBillingController`, `PlatformBillingController`) — KEEP — and the **PayFast recurring path** (`payfast/`, `SubscriptionItnController`, `SubscriptionItnService`, `SubscriptionPayment`, `SubscriptionPaymentRepository`, `SubscribeResponse`, `PaymentResponse`, `BillingController` subscribe endpoints) — REMOVE. Global `V6`, `V17`, `V18` are subscription tables (squashed anyway).
>   - `integration/payment` (Stripe/PayFast/Mock invoice pay-now) ← `invoice/PaymentLinkService`, `invoice/PaymentReconciliationService`, `invoice/InvoiceTransitionService`, provisioning. **KEEP** (pay-now on a fee note is a legal use). `MockPaymentController` + `templates/portal/mock-payment.html` are the dev/e2e mock — keep, they are test infrastructure.
>   - `dev/DevPortalController` + `templates/portal/{dashboard,project-detail,generate-link}.html` (Phase 7 Thymeleaf harness) — dead since Phase 22/68; `SecurityConfig` and `portal/CustomerAuthFilter` carry `/portal/dev/**` permits.
>   - `frontend/app/portal/**` — stale copy of the portal inside the staff app (last touched by the mechanical `@b2mash/ui` extraction, PR #1421, 2026-06-11); the separate `portal/` app has a strict superset of its routes.
>   - `docs/` (Phase 59 Nextra site, built for `docs.heykazi.com`; brand is now `kazi.africa`) — referenced by `.github/workflows/deploy-vps.yml` ("Build & push docs", `./docs/Dockerfile`, `ghcr.io/.../kazi-docs`) and `compose/docker-compose.prod.yml` service `docs`.
>   - `demo` pkg (Phase 58 demo provisioning) — KEEP (sales demos) but its seeders touch removed domains; audit `demo/seed/*`.
> - **Module registry:** `verticals/VerticalModuleRegistry.java` is a hardcoded map. VERTICAL modules: `trust_accounting, court_calendar, conflict_check, regulatory_deadlines, lssa_tariff, …`; HORIZONTAL (admin-toggled in Settings → Features): `resource_planning, bulk_billing, automation_builder, information_requests, disbursements, …`. Profiles: `vertical-profiles/{legal-za,accounting-za,consulting-za}.json` — legal-za enables `court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing`; accounting-za `regulatory_deadlines, deadlines, information_requests, automation_builder`; consulting-za `resource_planning, automation_builder, information_requests`. Profile is chosen at provisioning (`ProvisioningController` `verticalProfile`) and by `platform-admin/demo`.
> - **Automation engine** (`automation`, ~5.7k): the only outside consumer is `packs`. legal-za automation templates use only `SEND_NOTIFICATION` (4) and `CREATE_TASK` (1) actions. The `automation_builder` module gates the rule-editor UI (`settings/automations/{new,[id],executions,ai-queue}`), not the engine.
> - **Deadlines** (`deadline` pkg, Phase 51 Epic 381–382) came out of the accounting phase but is in legal-za's enabled list — KEEP the module, drop the accounting-only `regulatory_deadlines` flavour and `FilingStatus` if nothing legal uses it (verify).
> - **Terminology:** `packages/shared/src/terminology-map.ts` (`TERMINOLOGY_BASE` keyed by profile + `PORTAL_TERMINOLOGY_OVERRIDES`), consumed via `frontend/lib/terminology.tsx` (`TerminologyProvider`, `useTerminology`, `t()`), `terminology-heading.tsx`, `terminology-text.tsx`. Phase 86 only deletes the `accounting-za` and `consulting-za` entries; Phase 87 deletes the mechanism.
> - **Navigation:** `frontend/lib/nav-items.ts` (`NAV_GROUPS`: work / projects / clients / finance / team / ai) rendered by `components/desktop-sidebar.tsx` → `nav-zone.tsx`. Items to drop here: Recurring Schedules, Pipeline, Proposals, Retainers, Resources, Utilization, and the assistant launcher(s).
> - **Frontend surface for removal:** routes `/resources`, `/resources/utilization`, `/retainers`, `/retainers/[id]`, `/schedules`, `/schedules/[id]`, `/proposals`, `/proposals/[id]`, `/pipeline`, `/pipeline/[id]`, `/settings/capacity`, `/settings/pipeline`, `/settings/automations/{new,[id],ai-queue}`; component dirs `capacity, retainers, schedules, proposals, pipeline, assistant` (+ horizontal widgets in `dashboard`); `lib/api/{capacity,retainers,schedules,crm,assistant-specialists,portal-proposals}.ts`; e2e dirs `consulting-lifecycle, pipeline, proposals, resources, retainers, schedules, specialists` + `playwright.consulting-lifecycle.config.ts`; QA scenarios `qa/testplan/demos/{accounting-za-90day-keycloak-v2,consulting-agency-90day-keycloak}.md`.
> - **Tests:** 796 backend test files; 405 frontend test files. Registry-test gotchas still apply: audit catalogue-count tests, `McpReadOnlyRegistryTest`, Phase81BoundaryTest — removals shift counts; only the full verify catches cross-package assertions (CLAUDE.md §5).
> - **External contract that must not move:** the Phase 78 MCP server tool names/schemas (`get_matter`, `list_correspondence`, `propose_task`, …) are consumed by the `kazi-legal-za` plugin in `../claude-for-legal-sa`. Nothing in this phase touches `mcp`.

## System Context

Kazi is a multi-tenant practice-management platform (Next.js 16 staff app + separate client `portal`, Spring Boot 4 / Java 25 backend, Spring Cloud Gateway BFF, Keycloak OIDC, schema-per-tenant via Hibernate + Flyway, S3/LocalStack, Mailpit). It was built as a horizontal foundation to be forked per vertical, with vertical *profiles* (legal-za, accounting-za, consulting-za) selecting modules, content packs and terminology at provisioning time. The legal vertical (`verticals/legal`: trust accounting ≈ 10k lines, court calendar, conflict check, tariff, disbursements, matter closure, statements) is coherent and is the product being taken to design-partner law firms (`.claude/ideas/gtm-design-partner-plan-2026-07-11.md`).

Everything horizontal that no law firm will use still ships: resource planning, retainer hour banks, recurring engagement schedules, a proposal pipeline, a CRM deal pipeline, an in-app chat assistant with its own tool registry parallel to the MCP server's, PayFast recurring tenant billing that contradicts the firm-level manual-pricing decision, two non-legal profiles, a stale in-app copy of the portal, a Thymeleaf dev harness, and a documentation site for a dead domain.

### Founder decisions that constrain this phase (2026-10-15 ideation)

- **Archive, don't fork.** Tag today's `main` (`horizontal-platform-final`), mirror the repository to an archived read-only repo (`kazi-horizontal-archive` or similar), and never maintain it. A maintained second codebase is explicitly rejected. If accounting or agency ever becomes real it restarts from the tag with hindsight.
- **Legal-only, single profile.** Delete `accounting-za` and `consulting-za` profiles and everything only they enable. Whether the vertical-profile *mechanism* survives as a one-entry registry or is collapsed is an `/architecture` call; the bias is "keep the JSON mechanism, one profile, no profile picker in the UI" because removal is churn with no user-visible benefit.
- **MCP is the only conversational AI surface.** Delete the in-app chat assistant (Phase 52) and specialist assistants (Phase 70). Keep the AI foundation (Phase 72), the AI intelligence skills (Phase 74: contract review, drafting, compliance audit, FICA gap, intake), execution gates, cost metering, BYOAK key settings, and the MCP server (78/81/82). `assistant/provider` (LLM provider registry + model list) is foundation and moves into `integration/ai`.
- **Keep, visible, as legal features:** Xero sync, online pay-now on invoices, document acceptance (e-sign), clauses, budgets ("fee estimates"), expenses (behind disbursements), deadlines, project templates (matter templates), information requests, tags/custom fields/saved views, bulk billing, collections, automation *engine*.
- **Remove PayFast recurring tenant billing**; keep the TRIAL / MANUAL / MANAGED billing-method model, subscription guard, expiry handlers and admin overrides (that *is* the pilot pricing model: firm-level, manual).
- **Squash migrations inside this phase** (founder-confirmed, not a separate phase): tenant series → fresh `V1` baseline, global series → fresh `V1` baseline, both generated from the post-removal schema.
- **Frozen, not removed:** Phase 75 scalability (job queue, shard registry, global `V24`/`V25`), the gateway BFF, Phase 13's dedicated-schema model. Don't extend, don't delete.

## Objective

Reduce Kazi to the product being sold: one vertical, one AI surface, one portal, one billing model, one clean migration baseline. Every package, route, pack, scenario, test, compose service and CI job that exists only for accounting firms, agencies, the generic platform story or a superseded implementation is deleted — not gated, not deprecated. The legal-za 90-day lifecycle (`qa/testplan/demos/legal-za-90day-keycloak.md` + `legal-za-full-lifecycle-keycloak.md`) must pass end-to-end on the squashed baseline, and `./mvnw verify` + frontend/portal `lint && build && test` (+ `format:check`) must be clean. Report the before/after line, file, migration and test counts in the phase closure.

## Constraints & Assumptions

- **Delete, don't disable.** Nothing in this phase is gated off behind a module flag; the point is less code. If a removal turns out to be load-bearing for a legal flow, the fix is to re-home the load-bearing part (as with `assistant/provider` → `integration/ai`), not to keep the package.
- **Cut at the seams listed in the scouting note.** Each consumer file named above gets a targeted edit; `/architecture` must enumerate every seam per removal so `/breakdown` can slice by package. Use IntelliJ call-hierarchy (`analyze_calls`) to confirm no undocumented consumers before deleting a package.
- **Replacement paths are acceptance tests.** (a) Engagement letter without `proposal`: generate from a `DocumentTemplate`, send via acceptance to a portal contact, contact accepts in the portal, certificate generated, audit written. (b) Billing runs without `retainer`: `BillingRunSelectionService` / `RetainerBillingService` removal leaves time-and-disbursement runs intact. (c) Notifications without `schedule`: `NotificationEventHandler` compiles and the legal notification catalogue is unchanged. (d) Dashboard without CRM/capacity widgets renders. (e) AI skills, gates, MCP tools, and Settings → AI (key + model list) work without `assistant`.
- **Migration squash rules.** Generate the new `V1__baseline.sql` (tenant) and `V1__baseline.sql` (global) from the *final* post-removal schema, preserving every CHECK constraint name (`chk_trust_txn_type` etc.), index, unique constraint, FK and default — Phase 84's spec references them by name. Verify with Hibernate schema validation in the test profile (the suite already provisions tenants per context; a baseline mismatch fails fast). Delete all prior `V*` files; the next feature migration (Phase 84's take-on tables) becomes `V2`. Record the squash commit and the archive tag in the baseline file header. All local/dev/e2e Postgres volumes are wiped (`dev-down.sh --clean`, `e2e-down.sh`); document this in the PR and in `CLAUDE.md`'s quick-start. The "Migration high-water" lines in `TASKS.md`/phase docs are updated.
- **Seeders and packs.** `SchedulePackSeeder`, `DealPipelinePackSeeder`, accounting/consulting field/template/automation/request packs, `MockPaymentIntegrationSeeder` if only used by removed flows (verify), and the `demo/seed/*` steps for removed domains are deleted; `PackReconciliationRunner` and `VerticalProfileReconciliationSeeder` are trimmed to the surviving pack types. Packs catalogue (Phase 65, `packs` pkg, Settings → Packs) stays as the install mechanism for legal content.
- **Automation engine stays headless-but-visible.** Keep the engine and the legal-za rule templates; keep a read-only *list* of installed rules with enable/disable (so an admin can stop a notification rule); delete the rule editor, `new`, `[id]` builder pages and the `ai-queue` page. Exact surviving page is an `/architecture` call; "no UI at all" is also acceptable if the legal templates are documented as fixed.
- **Terminology map:** remove only the `accounting-za` / `consulting-za` entries and `PORTAL_TERMINOLOGY_OVERRIDES` (which exists solely for accounting-za). The mechanism itself is Phase 87's to delete; do not pre-empt.
- **Docs & CI:** delete `docs/`, its Dockerfile, the `docs` service in `compose/docker-compose.prod.yml`, the "Build & push docs" job in `deploy-vps.yml`, and any Caddy route for it. Root `pnpm-workspace.yaml` / lockfile updated. `DEPLOY-VPS.md` updated.
- **Archive step is a precondition, done by the founder, verified by the agent**: the phase's first slice must confirm `git tag horizontal-platform-final` exists and is pushed before any deletion PR merges (the merge-gate hook may check for the tag).
- **Documentation sweep is in scope:** `TASKS.md` phase overview marks removed phases "Removed in Phase 86 — archived at `horizontal-platform-final`" (do not delete the rows; the history is the point of the tag); `architecture/ARCHITECTURE.md` section index annotated the same way; `CLAUDE.md` (root + `backend/` + `frontend/`) loses references to removed services/modules; `.claude/skills/*` and `.claude/agents/*` prompts that mention consulting/accounting profiles or the assistant are updated; memory of `qa/` scenario removal is noted in `qa/testplan/README` or equivalent.
- **Test strategy.** Every slice runs targeted tests for the deleted package's consumers AND the full `./mvnw verify` (`backend/scripts/verify.sh`) before merge — count assertions live in other packages. Frontend/portal: full vitest + `format:check`, not path-narrowed. Final capstone: both legal Playwright configs (`playwright.legal-lifecycle.config.ts`, `playwright.legal-depth-ii.config.ts`) green against the squashed baseline on the Keycloak dev stack; "PASS means observed".

---

## Section 1 — Archive & Pre-flight

- Founder: `git tag -a horizontal-platform-final -m "Last commit of the horizontal multi-vertical platform before Phase 86 subtraction"` on `main`, push the tag, mirror to an archived repo, set the archive repo read-only. Record both URLs in `.claude/ideas/phase86-87-ideation-2026-10-15.md` and in `TASKS.md`.
- Agent: baseline metrics snapshot (backend lines/files/tests, frontend lines/tests, portal lines, migration counts, full-verify wall time) into `tasks/phase86-subtraction.md` for the closure delta.
- Agent: dependency confirmation per removal using IntelliJ call hierarchy; any consumer not in the scouting note is added to the slice plan before deletion starts.

## Section 2 — Backend Package Removals (one slice per package, dependency order)

Order so each slice compiles: `crm` → `proposal` → `retainer` → `capacity` → `schedule` → `assistant` (with `provider` re-homed first) → PayFast subscription path → `dev` harness → accounting/consulting profiles + packs + seeders → `regulatory_deadlines` flavour (if unused by legal). Each slice: delete package + tests, edit the named consumer seams, delete the package's audit event names from the catalogue (and fix the count tests), delete its notification types/templates, delete its portal read-model projections and event handlers, delete its capability constants if unused elsewhere, and its `VerticalModuleRegistry` entry. No migration edits per slice — the squash (Section 6) deletes the tables.

## Section 3 — AI Surface Consolidation

- Move `assistant/provider` (`LlmChatProviderRegistry`, `ModelInfo`, provider impls) into `integration/ai`; update `IntegrationService`, `AiSettingsController`.
- Delete `assistant/{tool,invocation,specialist}`, `automation/executor/InvokeAiSpecialistActionExecutor`, `automation-templates/ai-specialist-legal-za.json`, `assistant/specialists/*.md` resources, frontend `components/assistant`, `lib/api/assistant-specialists.ts`, the chat panel / launcher in the shell, `settings/automations/ai-queue`, e2e `specialists`.
- Keep and re-verify: `settings/ai` (key, model, consent), `settings/ai/history`, `ai/reviews` (Phase 74), `settings/integrations/mcp`, all MCP tools, `McpReadOnlyRegistryTest`, the AI cost metering. QA scenario `qa/testplan/demos/ai-core-verification-keycloak.md` is trimmed of assistant steps and re-run.

## Section 4 — Frontend, Portal & QA Surface Removals

- Delete the routes, component dirs, `lib/api` modules, nav items, dashboard widgets, command-palette entries, setup-status/contextual-action entries, help copy (`lib/messages/en/help.json`), and tests listed in the scouting note.
- Delete `frontend/app/portal/**` and any `proxy.ts` rewrite that targets it (verify the separate `portal/` app owns `/portal` in `compose` and Caddy).
- Portal app: delete `retainer`, `proposals` pages, their API clients, nav entries, read-model types.
- Delete e2e dirs and the consulting Playwright config; delete the two non-legal QA demo scenarios; update `qa/acceptance/INDEX.md` if it indexes removed features.

## Section 5 — Billing Model Trim

- Remove the PayFast recurring subscription path (files enumerated in the scouting note) and the `platform-admin/billing` / `settings/billing` UI for subscribing/ITN; keep billing-method display and admin override. `SubscriptionGuardFilter` semantics unchanged for TRIAL/MANUAL/MANAGED.
- `BillingProperties` loses PayFast keys; `application-*.yml` and compose env cleaned; `DEPLOY-VPS.md` updated.

## Section 6 — Migration Squash

- After Sections 2–5 are merged and verify is clean: generate the tenant and global baselines from the live post-removal schema (dump from a freshly provisioned tenant on the dev stack, hand-cleaned into deterministic DDL — no `pg_dump` noise, no `OWNER TO`), replace `db/migration/tenant/V*` and `db/migration/global/V*` with `V1__baseline.sql` each, keep the `chk_*` names, re-run the entire suite, wipe all volumes, re-provision, and run both legal Playwright configs. Update `FlywayConfig` / `TenantMigrationRunner` only if the baseline requires it (it should not).
- Deliver a short `backend/db/BASELINE.md` explaining the squash, the tag it was cut from, and the rule going forward ("after the first production tenant, never squash again").

## Section 7 — Documentation Sweep & Closure

- `TASKS.md`, `ARCHITECTURE.md`, root/backend/frontend `CLAUDE.md`, `.claude/skills`, `.claude/agents`, `DEPLOY-VPS.md`, `compose/README`, `qa/` indexes.
- Closure report in `tasks/phase86-subtraction.md`: before/after metrics, list of archived phases, link to tag and archive repo, full-verify log path, Playwright run artefacts.

## Out of Scope

- The legal-native shell, navigation IA, settings consolidation, and the API/TypeScript rename Project→Matter / Customer→Client — **Phase 87**.
- Renaming JPA entities or tables (deferred indefinitely; Phase 87 renames the wire, not the schema).
- Removing the gateway BFF, Keycloak, Phase 75 job queue / shard registry, Phase 13 dedicated-schema model.
- Removing Xero, online payments, acceptance, clauses, budgets, expenses, packs catalogue, custom fields, tags, saved views, information requests, bulk billing, collections, deadlines, project templates, the automation engine, the demo provisioning package.
- Any new feature. Any fix not required to keep a surviving legal flow green.
- Take-on (Phase 84) and the `/legal` landing page (Phase 85) — unchanged, sequenced after/alongside.

## ADR Topics to Address

1. Archive tag + mirrored repo instead of a maintained fork (why, what "never maintained" means, how to resurrect).
2. Migration squash policy at zero tenants; the irreversibility rule after first production tenant; baseline generation method.
3. Single-vertical stance: keep the profile mechanism with one profile vs collapse it — and the consequences for `VerticalModuleRegistry` (which modules remain toggleable, if any).
4. MCP as the sole conversational AI surface; `assistant/provider` re-homed into the AI foundation; what the in-app product keeps (skills, reviews, gates) vs drops (chat, specialists).
5. Automation engine without a builder: headless rules from packs, enable/disable only.
6. Billing: manual firm-level billing method as the only model; PSP recurring removed; what re-adding it would take.
7. Engagement letter = generated document + acceptance (replaces the proposal entity); retainer "mandate" wording migrates to this flow.

## Style & Boundaries

- One package (or one coherent surface) per PR; CLAUDE.md quality gates apply unchanged — full verify before merge, review pass, PASS means observed.
- Scenario amendments to `qa/testplan/demos/legal-za-*.md` are authorised **only** where they remove steps for deleted features; any other amendment is flagged.
- No "while I'm here" refactors inside surviving packages. If a surviving package has dead code exposed by a removal, note it in the closure report for Phase 87 or later.
- Tenant-isolation tests for surviving packages must still pass; do not delete an isolation test because its neighbour was removed.
- Do not touch `mcp` or the `kazi-legal-za` plugin contract.

## Next step

Founder: create and push the `horizontal-platform-final` tag and the archive mirror.
Then run `/architecture requirements/claude-code-prompt-phase86.md` to generate the architecture section and ADRs, followed by `/breakdown 86`.
