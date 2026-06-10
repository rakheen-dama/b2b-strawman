# 80 — Technical Debt, Gaps & Improvement Areas

**Last reviewed:** 2026-05-10
**Source:** synthesis of open-questions / fragility / drift findings surfaced across the discovery sweep (Phase A), module pages (Phase C), cross-cutting pages (Phase D), and verification pass (Phase E). This file is the consolidated risk register.

This file is **not a roadmap.** It is an inventory of debt, gaps, and risks visible from current code + docs. Decisions about what to address, when, and how are out of scope here — those belong in ADRs and phase docs.

## How to read this file

- **Severity** uses four levels:
  - **Critical** — known active defect or load-bearing risk; remediating it is on the path of any work that touches the area.
  - **High** — fragility that will bite within 1–2 phases of normal evolution.
  - **Med** — friction or inconsistency that compounds over time but is currently working.
  - **Low** — hygiene item; cosmetic, naming, or dead-code issue.
- **Effort** uses three levels: **S** (≤ 1 PR), **M** (multi-PR but scoped), **L** (architectural change requiring an ADR).
- Every entry references the originating page in `kazi-architecture/` (open question or fragility section) or the discovery report so the evidence is traceable.

## Top 5 risks (executive summary)

1. **R-01 Profile-switch orphans** — Critical, L. Reconciler is add-only; switching a tenant from legal→consulting leaves orphaned trust tables, FICA fields, legal templates. The single most important architectural risk in the codebase. Source: `20-cross-cutting/multi-vertical.md` §5; `30-modules/vertical-profiles.md` §10; `50-flows/pack-install-and-vertical-onboarding.md`.
2. **R-02 Observability is missing** — High, L. Only `/actuator/health` exposed. No Prometheus, no per-tenant metrics, no scheduled-job success/failure metric, no integration-adapter latency. First sign of production trouble is a user report. Source: `20-cross-cutting/observability.md`.
3. **R-03 Plan-tier graveyard inside Subscription** — Critical, M. `Subscription` state machine still has `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED` plus 5 stale ADRs (010/013/014/219/222) despite the "no plan-tier subscriptions" product decision. Source: `30-modules/platform-administration.md`; `_discovery/A4-adr-triage.md`; user memory `project_no_plan_subscriptions.md`.
4. **R-04 Audit non-bus is implicit convention, not enforced** — High, M. Every other secondary effect uses `@TransactionalEventListener(AFTER_COMMIT)`; audit alone is in-transaction by deliberate design. There is no ArchUnit test or PR check for "audit emission must be in-transaction." A well-meaning refactor that "modernises" audit to use the bus would silently break atomicity guarantees. Source: `30-modules/audit.md`; `20-cross-cutting/audit-and-compliance.md` §2.
5. **R-05 Filter-chain ordering is a dependency-resolution ladder with no enforcement** — High, M. `Bearer → Tenant → Member → SubscriptionGuard → PlatformAdmin → @RequiresCapability` — each filter's input scope is bound by the prior filter's output. No test guards the order. A refactor that moves SubscriptionGuard earlier would silently break owner-bypass. Source: `30-modules/identity-access.md`; `20-cross-cutting/auth-and-rbac.md` §3.

---

## A — Cross-cutting load-bearing risks

| ID | Risk | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| A-01 | Profile-switch orphans | Critical | L | `VerticalProfileReconciliationSeeder` is add-only by design (idempotency invariant). legal→consulting switch leaves trust tables, FICA fields, legal templates, ChecklistInstances. UI gates hide them; storage and re-flip surface them. | `multi-vertical.md` §5; `vertical-profiles.md` §10 |
| A-02 | Observability is mostly absent | High | L | Only `/actuator/health` (and `info`, prod adds `metrics`). No Prometheus, Micrometer registry, OpenTelemetry, no `@Timed` annotations, no per-tenant metric, no scheduler success/failure metric. | `observability.md` |
| A-03 | No audit / retention purge | High | M | `audit_events` accumulates indefinitely. `customer-lifecycle` retention clocks anonymise PII but don't compact audit history. Long-running tenants will see unbounded table growth. | `audit.md` §10; `audit-and-compliance.md` §10 |
| A-04 | No webhook tenant-identification design | High | M | ADR-096 / ADR-099 exist as decisions but no implementation in gateway or backend. Inbound webhooks (PSP, AI, KYC) lack a documented tenant-resolution path. | `integration-ports.md` §9; `20-cross-cutting/integration-ports.md` |
| A-05 | No gateway rate limiting | Med | M | ADR-097 commits to a strategy; no implementation visible. Gateway has zero custom filters today. | `_discovery/A3-portal-gateway-map.md` §10; `auth-and-rbac.md` |
| A-06 | No integration-key rotation enforcement | Med | M | `EncryptedDatabaseSecretStore` (ADR-090) supports update; no rotation cadence enforced. `OrgIntegration.keySuffix` is the rotation hook but unused. | `integration-ports.md` §9 |
| A-07 | TRUNCATE bypass on audit immutability | Med | S | `@Immutable` JPA + Postgres trigger block UPDATE+DELETE; verify trigger covers TRUNCATE (likely doesn't — Postgres triggers don't fire on TRUNCATE by default). | `audit.md` §10; `audit-and-compliance.md` §10 |
| A-08 | DSAR PAIA scope is South Africa only | Med | L | `compliance/dataprotection/` PAIA generation is hard-coded to ZA. Expansion to GDPR / other regimes untested; no jurisdiction-aware abstraction. | `customer-lifecycle.md`; `data-protection.md` §8 |

## B — Code / decision drift (doc says one thing; code shows another)

| ID | Drift | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| B-01 | Plan-tier states still in `Subscription` enum | High | M | `Subscription.SubscriptionStatus` includes `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED` from the abandoned plan-tier model. The "no plan-tier subscriptions" decision was made (per memory `project_no_plan_subscriptions.md`) but the enum was never cleaned up. | `platform-administration.md` |
| B-02 | `Member.clerkUserId` field name lies | Med | S | Clerk auth was retired and replaced by Keycloak. The field still holds the IDP `sub` claim but the name advertises a vendor that's no longer in use. | `identity-access.md` §10; `auth-and-rbac.md` §9 |
| B-03 | Reporting uses Thymeleaf, ADR-263 says Tiptap | Med | M | ADR-263 commits to "audit pdf via Tiptap pipeline" as the doc-render path; reporting templates use Thymeleaf `template_body` (ADR-082). Decision divergence — both ADRs are Active. | `reporting.md` |
| B-04 | `accounting-za.json` slug `"deadlines"` vs registry `"regulatory_deadlines"` | High | S | The accounting profile declares `"deadlines"` in `enabledModules`; `VerticalModuleRegistry` has the slug as `regulatory_deadlines`. Likely active defect — accounting tenants don't get the deadline module. | `60-verticals/accounting-za.md` |
| B-05 | `automation-legal-za` pack not referenced in `legal-za.json` | High | S | The pack JSON ships with `verticalProfile: legal-za` but `legal-za.json` has no `packs.automation` key, so legal-za tenants don't auto-install it at provisioning. | `60-verticals/seeds-and-packs.md` |
| B-06 | `consulting-za.json` ≠ phase66 doc | Low | S | Phase 66 architecture doc shows empty `enabledModules`; the shipped JSON has three. Doc was not updated when the JSON was finalised. | `60-verticals/consulting-za.md` |
| B-07 | "base" profile is `consulting-generic.json` | Low | S | Documentation refers to a `base` vertical; no `base.json` exists in `vertical-profiles/`. The de-facto base is `consulting-generic.json` with empty modules. Naming gap. | `60-verticals/base.md` |
| B-08 | ADR-139 / ADR-156 retired without formal `Superseded` marker | Low | S | A4 triage marked them Stale based on context; the ADR files themselves carry no `Status: Superseded by ADR-178` line. | `_discovery/A4-adr-triage.md`; `90-adr-index.md` |
| B-10 | Retainers backend missing module-gate call | Med | S | Backend services don't call `verticalModuleGuard.requireModule("retainer_agreements")`. Defense-in-depth gap; UI nav-absence and portal redirect are the only enforcement. | `retainers.md` §6 |

## C — Vocabulary / naming drift

| ID | Issue | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| C-01 | `PortalContact` ↔ "Customer Contact" UI naming | Low | M | Backend entity is `PortalContact`; frontend UI sometimes labels it "Customer Contact". Two names for the same thing. | `glossary.md` divergence #1 |
| C-02 | Three `BillingStatus` enums | Med | M | `TimeEntry.BillingStatus`, `Expense.BillingStatus`, `Disbursement.BillingStatus` are three distinct enums with different value sets. Code reading the wrong one fails silently or mistypes. | `glossary.md` divergence #3; `time-entry.md` §10; `expenses.md` §10 |
| C-03 | Three `PaymentStatus` enums | Med | M | Integration-adapter `PaymentStatus`, `SubscriptionPayment` inner enum, ledger payment-event status. Always require qualification; refactor candidate. | `glossary.md` divergence #3 |
| C-04 | `Project.status=CLOSED` is legal-vertical-only and non-terminal | Med | S | Counterintuitive: most readers assume CLOSED is terminal. Legal-vertical reopens supported; non-legal projects skip CLOSED. Easy to misread the state machine. | `glossary.md` divergence #5; `projects.md` §10 |
| C-05 | "Tariff Schedule" is overloaded | Low | M | Both a legal-vertical entity (`TariffSchedule` with TariffItems) AND the legal-za UI relabel of the generic Billing Rate. Two related-but-distinct meanings. | `glossary.md` divergence #6; `legal-za.md` |
| C-06 | `consulting-generic` vs `consulting-za` distinction unclear | Low | S | `useProfile()` returns `consulting-generic` as a possible ProfileId; relationship to `consulting-za` and "base" not documented. | `consulting-za.md` §10 |
| C-07 | "Workflow" is not a code term | Low | S | Product talk uses "workflow"; code term is "Automation Rule". Easy to misuse. | `glossary.md` watch-words |

## D — Architectural inconsistencies (load-bearing patterns at risk)

| ID | Pattern | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| D-01 | Two `PackInstaller` paths (transition incomplete) | Med | L | 2 modern SPI implementations (`TemplatePackInstaller`, `AutomationPackInstaller`) coexist with 9 legacy `AbstractPackSeeder` subclasses. Migration started, never finished. Each new pack type has to choose a path. | `packs.md`; `60-verticals/seeds-and-packs.md` |
| D-02 | Frontend and portal duplicate Shadcn + terminology + utilities | Med | L | No shared package between `frontend/` and `portal/`. Each maintains its own `components/ui/`, its own `terminology-map.ts`, its own format helpers. Drift is observable; refactor candidate. | `_discovery/A6-cross-cutting.md`; `70-repos/portal.md` |
| D-03 | Audit non-bus convention has no enforcement | High | M | The "audit must be in-transaction" rule is documented (`audit-and-compliance.md` §2) but no ArchUnit test, no PR check, no annotation. Refactor risk. | `audit.md`; `audit-and-compliance.md` |
| D-04 | Filter-chain ordering has no enforcement | High | M | The Bearer→Tenant→Member→SubscriptionGuard→PlatformAdmin order is correctness-load-bearing. No test pins the order. | `identity-access.md`; `auth-and-rbac.md` §3 |
| D-05 | Universal `AutomationEventListener` subscription | Med | L | Every domain event hits the trigger-matching engine; no opt-out. Per-tenant rule cardinality * 41 event types = potentially expensive at scale. | `automation.md` §10; `domain-events.md` §10 |
| D-06 | Dual project-detail vs portal-detail paths | Low | M | Staff frontend renders projects via `projects/[id]/page.tsx`; portal renders the same project via 5 parallel `/portal/projects/{id}/...` calls. Read-model split is intentional (per ADR-031 / ADR-078) but the data shapes have drifted. | `30-modules/customer-portal.md` |
| D-07 | OrgSettings is a god-object | Med | L | ~40 columns, referenced by virtually every module. Pack-status JSONB ledgers + branding + terminology + feature flags + retention thresholds + email rate-limits + time-reminder config. Splitting it would touch every module. | `settings-navigation.md` §10 |
| D-08 | `ProjectMember` ownership ambiguity | Low | S | Lives in `member/` package but is consumed exclusively by `projects/`. Either move the file or document the ownership choice. | `projects.md` §10 |

## E — Lifecycle / state-machine debt

| ID | Issue | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| E-01 | Two non-cascading retention clocks | Med | M | `Customer.offboardedAt` and `Project.retentionClockStartedAt` operate independently. Customer offboarding does NOT auto-trigger retention on the customer's projects. Compliance gap if relied upon. | `customer-lifecycle.md`; `data-protection.md` §3 |
| E-02 | 7-state `LifecycleStatus` has redundant transitions | Low | M | `OFFBOARDING` and `OFFBOARDED` are functionally adjacent — no observed product use of the intermediate state. Consolidation candidate. | `customer-lifecycle.md`; `glossary.md` |
| E-03 | `RetainerPeriod.PeriodStatus.INVOICED` is dead | Low | S | Defined in the enum; `RetainerPeriod.close(...)` never sets it. Likely an unwired transition or a removed feature. | `retainers.md` §10 |
| E-04 | `ProjectStatus.VALID_TRANSITIONS` vs `ALLOWED_TRANSITIONS` | Low | S | Naming inconsistency between the constant in code and the doc references; trivial rename. | `_discovery/E1-batch1-verify.md` |
| E-05 | Acceptance certificate chain-of-custody | Med | M | ADR-108 commits to certificate integrity; no test or code-review checklist verifies the end-to-end trail (acceptance event → audit row → PDF generation → S3 immutability). | `proposals-acceptance.md` §10 |
| E-06 | ChecklistTemplate snapshot vs live update | Low | M | When a pack-seeded template is updated, existing `ChecklistInstance` rows do NOT pick up the change. Snapshot-on-instantiation is implicit; not documented as a contract. | `checklists.md` §10 |

## F — Domain-event bus debt

| ID | Issue | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| F-01 | No event payload versioning | Med | L | 41 sealed `DomainEvent` permits with no version field. Schema migration of an event payload risks breaking all consumers atomically. | `domain-events.md` §10 |
| F-02 | `MemberOverAllocatedEvent` has no listeners | Low | S | Published by `capacity` service; no `@EventListener` consumes it. Either dead emission or a missing consumer. | `capacity-planning.md` §10 |
| F-03 | No DLQ for failed event listeners | Med | M | A failing `@TransactionalEventListener` has no fallback or retry queue. Notifications, portal-readmodel sync, integration push all fail silently after exception logging. | `domain-events.md` §10 |
| F-04 | Sealed interface adds-only | Low | S | Adding a new event type requires editing the sealed declaration. Slight friction; not a real architectural problem unless events proliferate fast. | `domain-events.md` §10 |
| F-05 | Cross-module event coupling | Med | L | Downstream consumers depend on upstream event payloads. Refactoring an emitter is a cross-module concern; no contract-testing in place. | `domain-events.md` §10 |

## G — Multi-vertical implementation gaps

| ID | Gap | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| G-01 | Profile JSON loader silently skips bad files | Med | S | `VerticalProfileRegistry` continues on JSON parse error. A typo in a profile JSON disappears a vertical without an alert. Add fail-fast or boot-time warning. | `vertical-profiles.md` §6 |
| G-02 | Pack uninstall not implemented | High | L | The reconciler is add-only. Removal of a pack from a profile JSON does not remove its content from existing tenants. The orphaned-data fragility (A-01) is the headline manifestation. | `packs.md`; `vertical-profiles.md` |
| G-03 | Pack content versioning not modelled | Med | L | When a pack's seed content evolves (a new field, a renamed template), there is no forward-migration story for tenants that already installed the pack. `PackInstall.packVersion` is recorded but not used for migration. | `packs.md` §10 |
| G-04 | Slug-conflict resolution undefined | Low | S | Two packs declaring the same field slug, template slug, or rate-card slug have undefined behaviour. No registration-time check at boot; no run-time precedence rule. | `packs.md`; `custom-fields-tags-views.md` §10 |
| G-05 | Profile evolution: do existing tenants backfill? | Med | L | When base adds a new module, existing tenants on real verticals don't backfill via reconciliation (their profile JSON doesn't include it). Either auto-include base modules or document the opt-in pattern. | `60-verticals/base.md` §10 |
| G-06 | Vertical-pack admin authoring workflow undefined | Low | L | Packs ship as classpath JSON resources today. No admin UI, no DB-backed pack authoring, no dev-vs-prod flow. Adding a new vertical means a code release. | `packs.md` §10 |

## H — Active defects discovered during the architecture sweep

These are concrete bugs surfaced while writing the module pages. None blocked Phase E completion; all are S-effort fixes.

| ID | Defect | Sev | Effort | Where | Status |
|---|---|---|---|---|---|
| H-01 | `s3/` package paths in module page | — | S | `documents-templates.md` | **Fixed in Phase E.** Package was relocated to `integration/storage/`. |
| H-02 | Information-request event-to-line mapping | — | S | `information-requests.md` | **Fixed in Phase E.** 4 events anchored to wrong lines; 2 published from sibling services (project-templates / portal-backend). |
| H-03 | `accounting-za.json` slug `"deadlines"` ≠ registry `"regulatory_deadlines"` | High | S | `verticals/accounting-za.json` | Open. Likely active defect — accounting tenants miss the deadline module. |
| H-04 | `automation-legal-za` pack not referenced in `legal-za.json` | High | S | `verticals/legal-za.json` | Open. Pack ships standalone but doesn't auto-install. |
| H-05 | `Subscription` enum still contains plan-tier states | High | M | `billing/Subscription.java` | Open. Per `project_no_plan_subscriptions.md` decision; not yet cleaned up. |
| H-06 | Reports controller missing `@RequiresCapability` | High | S | `reporting/ReportingController.java` | **Fixed.** `@RequiresCapability("FINANCIAL_VISIBILITY")` added to all 6 endpoints; member-role denial covered by `ReportingControllerTest`. |
| H-07 | `ViewFilterHelper` `tableName` SQL surface | Med | S | `view/ViewFilterHelper.java` | Open IF tableName ever becomes user-controlled. Currently safe (caller-supplied from controlled enum). |
| H-08 | Audit triggers may not cover `TRUNCATE` | Med | S | `V12_*.sql` migration | Open — verify the trigger function and consider blocking TRUNCATE. |
| H-09 | `RetainerPeriod.PeriodStatus.INVOICED` unwired | Low | S | `retainer/PeriodStatus.java` | Open. Dead enum value or unwired transition. |
| H-10 | `RequestReminderScheduler` cadence vs A1 | Low | S | `informationrequest/RequestReminderScheduler.java` | A1 said "daily"; actual is `fixedRate` 6h. Doc was correct (`information-requests.md` §6); A1 was the drift. |

## I — Operational / production readiness gaps

| ID | Gap | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| I-01 | No Prometheus / Micrometer registry | High | M | See A-02. | `observability.md` |
| I-02 | No per-tenant metric tagging | High | M | Metrics in MDC (`tenantId`) but not in metric registry. Multi-tenant noisy-neighbour invisible. | `observability.md` |
| I-03 | No scheduled-job lateness metric | Med | M | If `AutomationScheduler.pollScheduledTriggers` (60s) skips a beat, no alert. 15+ schedulers all unobserved. | `observability.md`; `automation.md` |
| I-04 | No integration-adapter latency / error metric | Med | M | PSP, email, AI, accounting calls have no Micrometer wrapper. Per-tenant adapter health invisible. | `integration-ports.md` §10 |
| I-05 | No AI specialist token-cost tracking | Med | M | BYOAK keys mean tenants pay for their own LLM tokens; no in-app accounting of usage per tenant. | `ai-assistant.md` §10; `automation.md` §10 |
| I-06 | Email rate-limit observability missing | Med | M | `notifications.md` flags this; settings UI exists for rate limits, no dashboard for actual use. | `notifications.md` §10 |
| I-07 | Health checks don't validate per-tenant schema reachability | Low | M | `/actuator/health` only reports app-level health, not whether the tenant `search_path` is set or Flyway-baselined. | `observability.md`; `multitenancy.md` §7 |
| I-08 | Gateway has no observability beyond health | Med | M | Single thin route, no per-route timing or error metrics. No correlation-id propagation. | `observability.md` |

## J — Frontend / portal debt

| ID | Issue | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| J-01 | AI assistant calls `/api/assistant/chat` directly on backend | Low | S | The frontend convention is "every fetch through `lib/api/client.ts`"; the AI assistant hook breaks this for SSE streaming (browser → backend directly). Documented; no Next.js Route Handler proxy. | `ai-assistant.md`; `_discovery/A2-frontend-map.md` |
| J-02 | Portal JWT in localStorage (XSS exposure) | Med | M | ADR-077 committed; tradeoff documented. Cookie-based alternative not chosen. Worth a re-look as portal scope grows. | `customer-portal.md`; `auth-and-rbac.md` §9 |
| J-03 | Portal JWT not revocable on customer offboard | High | M | When a `PortalContact` is suspended, in-flight JWTs continue to work until expiry. No revocation list. | `customer-portal.md` §10 |
| J-04 | No OpenAPI codegen | Med | L | All TypeScript types in `frontend/lib/types/*` are hand-written, parallel to Java DTOs. Backend changes can drift silently from frontend types until a runtime error. | `_discovery/A2-frontend-map.md` |
| J-05 | Portal duplicates Shadcn install | Low | M | See D-02. Each app has its own `components/ui/`, `components.json`. | `70-repos/portal.md` |

## K — Test / merge-bar gaps

| ID | Gap | Sev | Effort | Detail | Source |
|---|---|---|---|---|---|
| K-01 | No CI check for `kazi-architecture/` updates vs code changes | Low | M | Documented as a future improvement in `99-conventions.md`. The folder rots without enforcement. | `99-conventions.md` |
| K-02 | No ArchUnit test pinning filter-chain order | High | S | See D-04. | `auth-and-rbac.md` |
| K-03 | No ArchUnit test for "audit emission must be in-transaction" | High | S | See D-03. | `audit-and-compliance.md` |
| K-04 | No contract test between `DomainEvent` payloads and consumers | Med | M | Refactoring an emitter risks breaking consumers silently. | `domain-events.md` |
| K-05 | No test for vertical-profile JSON validity | Med | S | `VerticalProfileRegistry` skips bad files at boot. Add a parse-test in CI. | `vertical-profiles.md` §6 |

## L — Known fragility per-module (one-line each)

Cross-references for areas where the work is mostly done but specific edges are weak.

| Module | Fragility | Pointer |
|---|---|---|
| `customer-lifecycle` | 7-state enum with redundant OFFBOARDING; FK cascade gap on retention clocks. | `customer-lifecycle.md` §10 |
| `projects` | Computed vs persisted status (ADR-060 vs ADR-066) ambiguity in some queries. | `projects.md` §10 |
| `tasks` | Recurrence parent-chain has theoretical unbounded depth (root pointer keeps it at 1 in practice). | `tasks.md` §10 |
| `time-entry` | Bulk weekly endpoint not load-tested at scale. | `time-entry.md` §10 |
| `expenses` | Capability gate inconsistent on edit (some PATCHes ungated). | `expenses.md` §10 |
| `documents-templates` | PDF engine binding post-Tiptap not formalised; Phase 42 DOCX edge cases. | `documents-templates.md` §10 |
| `invoicing` | 7 open questions including invoice-numbering for restored deletes. | `invoicing.md` §10 |
| `retainers` | Three documented domain events were not in the bounded-contexts page (corrected during Phase C). | `retainers.md` |
| `trust-accounting` | Reconciliation cadence + threshold precedence not codified. | `trust-accounting.md` §10 |
| `proposals-acceptance` | Proposal → Project conversion semantics undefined. | `proposals-acceptance.md` §10 |
| `automation` | Cycle detection is a placeholder. | `automation.md` §10 |
| `ai-assistant` | BYOAK key rotation gap; tool-capability ArchUnit absent. | `ai-assistant.md` §10 |
| `audit` | Severity-derivation logic ~100-entry ceiling. | `audit.md` §10 |
| `notifications` | Free-form notification type strings (no enum); no retry on email failure. | `notifications.md` §10 |
| `reporting` | Backend `FINANCIAL_VISIBILITY` gate added (H-06, fixed). | `reporting.md` §10 |
| `capacity-planning` | `MemberOverAllocatedEvent` has no listeners (F-02). | `capacity-planning.md` §10 |
| `customer-portal` | JWT-on-suspend revocation gap (J-03); read-model schema vs extension drift. | `customer-portal.md` §10 |
| `settings-navigation` | OrgSettings god-object (D-07). | `settings-navigation.md` §10 |
| `integration-ports` | No health surface; key rotation absent (A-06). | `integration-ports.md` §10 |
| `packs` | Two-paths transition (D-01). | `packs.md` §10 |
| `vertical-profiles` | The orphans risk (A-01) lives here. | `vertical-profiles.md` §10 |
| `checklists` | Snapshot-vs-live template update unclear (E-06). | `checklists.md` §10 |
| `project-templates` | Skipped-schedule replay semantics undefined; UTC fire vs tenant zone. | `project-templates.md` §10 |
| `information-requests` | Item-rejection semantics; ghost-document garbage collection. | `information-requests.md` §10 |
| `tenancy-provisioning` | ScopedValue re-bind required for virtual threads / SSE; pattern documented but not enforced. | `tenancy-provisioning.md` §10 |
| `identity-access` | `clerkUserId` field-name lie (B-02). | `identity-access.md` §10 |
| `platform-administration` | Plan-tier graveyard (B-01). | `platform-administration.md` §10 |
| `domain-events` | No DLQ, no versioning (F-01, F-03). | `domain-events.md` §10 |

## M — Quick-win hygiene (S-effort, isolated)

These are good first-PRs for new contributors or for housekeeping sprints.

1. Delete `Subscription.SubscriptionStatus.{PENDING_CANCELLATION, GRACE_PERIOD, LOCKED}` and verify no callers (B-01, R-03).
2. Rename `Member.clerkUserId` → `Member.idpSub` with a Flyway migration (B-02).
3. Fix `accounting-za.json` slug `"deadlines"` → `"regulatory_deadlines"` (B-04, H-03). One-line.
4. Add `packs.automation` key to `legal-za.json` to wire the legal automation pack (B-05, H-04).
5. Remove `RetainerPeriod.PeriodStatus.INVOICED` if confirmed dead (E-03, H-09).
6. Add `verticalModuleGuard.requireModule("retainer_agreements")` to retainer service entry points (B-10).
7. Add a boot-time warning when `VerticalProfileRegistry` skips a malformed JSON (G-01, K-05).
8. Rename `ProjectStatus.VALID_TRANSITIONS` ↔ `ALLOWED_TRANSITIONS` to a single name (E-04).
9. Mark superseded ADRs (139, 156, 010, 013, 014, 219, 222) with explicit `Status: Superseded by ADR-XXX` headers in the ADR files (B-08).

## N — Recommended next ADRs

When the project next opens an ADR cycle, the highest-leverage decisions are:

1. **ADR — Pack uninstall / vertical-switch reverse-migration.** Closes A-01 / G-02. Three options: (a) reconciler removes orphaned content on profile change; (b) explicit admin "switch profile" flow with confirmation + opt-in cleanup; (c) accept the fragility and document the policy.
2. **ADR — Observability stack.** Closes A-02. Choose Prometheus + Micrometer + per-tenant tag, OR OpenTelemetry, OR a SaaS APM. Set SLO definitions for the 15 schedulers and the audit-emission path.
3. **ADR — Webhook tenant identification.** Closes A-04 (and re-opens ADR-096/099). Concrete strategy: per-tenant signed URL? path-prefix? subdomain?
4. **ADR — Three `BillingStatus` enum consolidation.** Closes C-02. Move to a single enum or three explicit type-aliased enums to prevent silent miswiring.
5. **ADR — Audit retention compaction.** Closes A-03 / A-07. After how long does an audit row become eligible for archival or deletion? Where does the chain-of-custody live for archived events?
6. **ADR — Frontend / portal shared package.** Closes D-02 / J-05. Either extract `@b2mash/ui` and `@b2mash/types` into a workspace package, or formally accept duplication with a sync-check.
7. **ADR — Event payload versioning.** Closes F-01 / F-05. Required if `DomainEvent` payloads start being persisted (e.g. for audit replay).
8. **ADR — Vertical-pack admin authoring workflow.** Closes G-06. Today: code-resource. Future: DB? Admin UI? Both?

## How this file is maintained

- Re-run the verification methodology (Phase E sweep + cross-doc consistency check) periodically — at minimum once per major release, ideally per PR via CI.
- When a debt item here is addressed, **delete the row.** Do not annotate "RESOLVED on date X". The record is the git log.
- When a new fragility surfaces during normal development (a module page open question, an ADR review, a production incident), add a row. Severity + effort + source pointer; no narratives.
- The "Top 5 risks" section is human-curated. When it changes, leave a one-line reason in the commit message.
- Quick-wins (section M) should churn fast — items there get picked up in housekeeping PRs and removed.

## Sources

- All `30-modules/<slug>.md` §10 (Open questions / known fragility) sections
- All `20-cross-cutting/*.md` §10 sections
- `_discovery/A4-adr-triage.md` (Stale + Superseded ADRs)
- `_discovery/A6-cross-cutting.md` (architectural drift findings)
- `_discovery/E1-batch1-verify.md`, `E2-batch2-verify.md`, `E3-batch3-verify.md` (anchor-resolution drift)
- `glossary.md` Watch-words and Known divergences sections
- User memory entries in `MEMORY.md` (no plan-tier subscriptions, Tiptap not Thymeleaf, etc.)
- ADRs cross-referenced in `90-adr-index.md`
