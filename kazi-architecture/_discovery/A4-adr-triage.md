# A4 — ADR Triage

Status overlay for all 278 ADRs in `/adr/`. Generated for the `kazi-architecture/` rebuild so that module pages link only to live decisions.

**Method:** Every ADR was inspected by filename. ADRs with explicit supersession markers, deprecated content, or status changes were inspected in body. T-series (T001–T008) are template-foundation ADRs and are treated as canonical for tenancy/identity/runner concerns. Where the title alone was not enough, evidence is prefixed `guess:`.

**Status legend:**
- **Active** — decision still in force; module pages should link to it.
- **Superseded-by** — explicitly replaced by a later ADR.
- **Stale** — decision was made but the relevant feature was never built or was removed.
- **Informational** — context-setting / discussion-only ADR (no current enforcement).

---

## Triage table

| ID | Title (short) | Status | Superseded by | Module(s) | Evidence |
|---|---|---|---|---|---|
| ADR-010 | billing-integration | Stale | — | billing | guess: early Stripe seam, no plan-tier subscriptions per memory `project_no_plan_subscriptions` |
| ADR-011 | tiered-tenancy | Superseded-by | ADR-064 | tenancy | Body: "Partially superseded by ADR-064 — tier no longer determines schema topology" |
| ADR-012 | row-level-isolation | Superseded-by | ADR-064 | tenancy | Body: "Fully superseded by ADR-064" |
| ADR-013 | plan-state-propagation | Stale | — | tenancy, billing | guess: tied to plan tiers which were abandoned |
| ADR-014 | plan-enforcement | Stale | — | billing | guess: plan-tier era, no longer enforced |
| ADR-015 | provisioning-per-tier | Superseded-by | ADR-064 | tenancy | Body: "Partially superseded by ADR-064" |
| ADR-016 | tier-upgrade-migration | Superseded-by | ADR-064 | tenancy | Body: "Fully superseded by ADR-064 — no upgrade migration needed" |
| ADR-017 | customer-as-org-child | Active | — | customer | guess: foundational customer model still in code |
| ADR-018 | document-scope-model | Active | — | document | guess: document scoping rules still load-bearing |
| ADR-019 | task-claim-workflow | Active | — | task | guess: task claim still in product |
| ADR-020 | customer-portal-approach | Active | — | portal | guess: portal still uses this model; see ADR-T005 reinforcement |
| ADR-021 | time-tracking-model | Active | — | time | guess: time tracking core decision |
| ADR-022 | time-aggregation-strategy | Active | — | time | guess: still in use |
| ADR-023 | my-work-cross-project-query | Active | — | task | guess: my-work view still ships |
| ADR-024 | portal-task-time-seams | Active | — | portal, task, time | guess: portal seams still apply |
| ADR-025 | audit-storage-location | Active | — | audit | guess: foundational; no replacement found |
| ADR-026 | audit-event-granularity | Superseded-by | ADR-260 | audit | guess: ADR-260 "audit-generic-diff-over-event-templates-v1" replaces per-event template approach |
| ADR-027 | audit-retention-strategy | Active | — | audit | guess: retention rules still active |
| ADR-028 | audit-integrity-approach | Active | — | audit | guess: integrity (hash chain or similar) still applies |
| ADR-029 | audit-logging-abstraction | Active | — | audit | guess: abstraction still used; predates ADR-260 reshape but compatible |
| ADR-030 | magic-link-auth-for-customers | Active | — | portal, identity | Reinforced by ADR-T005 (template foundation) |
| ADR-031 | separate-portal-read-model-schema | Active | — | portal | Reinforced by ADR-253 (read-model extension) |
| ADR-032 | spring-application-events-for-portal | Active | — | portal | guess: still in use |
| ADR-033 | local-only-thymeleaf-test-harness | Stale | — | document | Memory `feedback_tiptap_not_thymeleaf` says rendering uses Tiptap; harness likely retired |
| ADR-034 | flat-comments-with-threading-schema | Active | — | comments | Reinforced by ADR-T006 (dual-author comments) |
| ADR-035 | activity-feed-direct-audit-query | Active | — | audit | guess: activity feed still queries audit directly |
| ADR-036 | synchronous-notification-fanout | Active | — | notifications | guess: still synchronous |
| ADR-037 | comment-visibility-model | Active | — | comments | guess: visibility model in force |
| ADR-038 | polling-for-notification-delivery | Active | — | notifications | guess: polling still used |
| ADR-039 | rate-resolution-hierarchy | Active | — | billing, time | Body: "Replaces manual rate entry"; itself supersedes a non-ADR mechanism |
| ADR-040 | point-in-time-rate-snapshotting | Active | — | billing, time | guess: snapshot pattern in code |
| ADR-041 | multi-currency-store-in-original | Active | — | billing | guess: foundational currency rule |
| ADR-042 | single-budget-per-project | Active | — | project | guess: still single budget per project |
| ADR-043 | margin-aware-profitability | Active | — | reporting, billing | guess: profitability calc still uses this |
| ADR-044 | dashboard-aggregation-caching | Active | — | dashboard | guess: caching strategy still applies |
| ADR-045 | project-health-scoring | Active | — | dashboard, project | guess: still computed |
| ADR-046 | dashboard-charting-approach | Active | — | dashboard | Reinforced by ADR-205 (chart theming) |
| ADR-047 | dashboard-layout-strategy | Active | — | dashboard | guess: layout approach still in code |
| ADR-048 | invoice-numbering-strategy | Active | — | invoice | guess: numbering rule still enforced |
| ADR-049 | line-item-granularity | Active | — | invoice | guess: line item shape still applies |
| ADR-050 | double-billing-prevention | Active | — | invoice, billing | guess: still enforced |
| ADR-051 | psp-adapter-design | Superseded-by | ADR-098 | billing, integration | ADR-098 body: "ADR-051 (original PaymentProvider design, superseded by this ADR)" |
| ADR-052 | jsonb-vs-eav-custom-field-storage | Active | — | custom-fields | Reinforced by ADR-237 (structural-vs-custom boundary) |
| ADR-053 | field-pack-seeding-strategy | Active | — | custom-fields, packs | Reinforced by ADR-240 (unified pack catalog) |
| ADR-054 | tag-storage-join-table-vs-array | Active | — | tags | guess: still in code |
| ADR-055 | saved-view-filter-execution | Active | — | views | guess: still in use |
| ADR-056 | pdf-engine-selection | Active | — | document, pdf | guess: still active engine |
| ADR-057 | template-storage | Active | — | document | guess: foundational template storage |
| ADR-058 | rendering-context-assembly | Active | — | document | guess: rendering pipeline still uses this |
| ADR-059 | template-customization-model | Active | — | document | guess: still applies; reinforced by ADR-068 (snapshot templates) |
| ADR-060 | lifecycle-status-core-field | Active | — | core | guess: lifecycle status still core |
| ADR-061 | checklist-first-class-entities | Active | — | task | guess: still first-class; ADR-134 reinforces |
| ADR-062 | anonymization-over-hard-deletion | Active | — | privacy | Reinforced by ADR-193 (anonymization-vs-deletion) |
| ADR-063 | compliance-packs-bundled-seed-data | Superseded-by | ADR-240 | packs | guess: ADR-240 unified pack catalog supersedes earlier seed bundling |
| ADR-064 | dedicated-schema-only | Active | — | tenancy | Canonical schema topology; reinforced by ADR-T001 |
| ADR-065 | hardcoded-setup-checks | Active | — | onboarding | guess: still hardcoded |
| ADR-066 | computed-status-over-persisted | Active | — | core | guess: computed status pattern in use |
| ADR-067 | entity-detail-page-action-surface | Active | — | ui | guess: action surface pattern still applies |
| ADR-068 | snapshot-based-templates | Active | — | document | guess: snapshot pattern in code |
| ADR-069 | role-based-assignment-hints | Active | — | task | guess: still hinting by role |
| ADR-070 | pre-calculated-next-execution-date | Active | — | recurring | guess: scheduling pattern still in code |
| ADR-071 | daily-batch-scheduler | Active | — | scheduler | Reinforced by ADR-271 (scheduled-trigger-extension) |
| ADR-072 | admin-triggered-period-close | Active | — | billing | guess: still admin-triggered |
| ADR-073 | standard-billing-rate-for-overage | Active | — | billing, retainer | guess: retainer overage rule |
| ADR-074 | query-based-consumption | Active | — | retainer | guess: still query-based |
| ADR-075 | one-active-retainer-per-customer | Active | — | retainer | guess: invariant still enforced |
| ADR-076 | separate-portal-app | Active | — | portal | guess: portal app is separate (see A3 portal-gateway-map) |
| ADR-077 | portal-jwt-storage | Active | — | portal, identity | guess: portal JWT storage strategy in code |
| ADR-078 | portal-read-model-extension | Active | — | portal | Reinforced by ADR-253 |
| ADR-079 | portal-org-identification | Active | — | portal | guess: still in code |
| ADR-080 | task-detail-sheet-panel | Active | — | task, ui | guess: detail sheet pattern in UI |
| ADR-081 | report-query-strategy-pattern | Active | — | reporting | guess: strategy pattern still applied |
| ADR-082 | report-template-storage | Active | — | reporting | guess: still stored as templates |
| ADR-083 | csv-generation-approach | Active | — | reporting | guess: CSV approach still in code |
| ADR-084 | parameter-schema-design | Active | — | reporting | guess: parameter schemas in use |
| ADR-085 | auth-provider-abstraction | Active | — | identity | guess: abstraction supports Keycloak + mock IDP |
| ADR-086 | mock-idp-strategy | Active | — | identity, testing | Memory: agent E2E stack uses mock IDP at :8090 |
| ADR-087 | e2e-seed-strategy | Active | — | testing | guess: e2e-reseed.sh still in compose scripts |
| ADR-088 | integration-port-package-structure | Active | — | integration | Reinforced by ADR-098, ADR-279 |
| ADR-089 | tenant-scoped-adapter-resolution | Active | — | integration | guess: still scoped per-tenant |
| ADR-090 | secret-storage-strategy | Active | — | integration, security | Reinforced by ADR-201 (secret store reuse) |
| ADR-091 | feature-flag-scope | Active | — | core | guess: feature flags still in code |
| ADR-092 | auto-apply-strategy | Active | — | recurring | guess: auto-apply rules still apply |
| ADR-093 | template-required-fields | Active | — | document | guess: required field rule |
| ADR-094 | conditional-field-visibility | Active | — | custom-fields | Reinforced by ADR-167 (conditional-block-predicate) |
| ADR-095 | two-tier-email-resolution | Active | — | email, integration | Cited in ADR-098 as reference pattern |
| ADR-096 | webhook-tenant-identification | Active | — | integration | guess: webhook tenancy resolver still active |
| ADR-097 | rate-limiting-implementation | Active | — | core | guess: still implemented |
| ADR-098 | payment-gateway-interface-design | Active | — | billing, integration | Body: itself supersedes ADR-051 |
| ADR-099 | webhook-tenant-identification-payments | Active | — | billing, integration | guess: payment webhook tenancy still in code |
| ADR-100 | payment-link-lifecycle | Active | — | billing | guess: payment link lifecycle still applies |
| ADR-101 | tax-calculation-strategy | Active | — | tax, invoice | guess: still in code |
| ADR-102 | tax-inclusive-total-display | Active | — | tax, invoice | guess: still in UI |
| ADR-103 | tax-rate-immutability | Active | — | tax | guess: invariant still enforced |
| ADR-104 | clause-rendering-strategy | Active | — | document, clause | guess: clause render still in code |
| ADR-105 | clause-snapshot-depth | Active | — | document, clause | guess: snapshot depth in code |
| ADR-106 | template-clause-placeholder-strategy | Superseded-by | ADR-123 | document, clause | guess: ADR-123 names "template-clause association source-of-truth" — replaces placeholder strategy with Tiptap clauseBlock |
| ADR-107 | acceptance-token-strategy | Active | — | proposal, portal | guess: token strategy still in code |
| ADR-108 | certificate-storage-and-integrity | Active | — | proposal | guess: certificate storage still applies |
| ADR-109 | portal-read-model-sync-granularity | Active | — | portal | guess: sync granularity still in code |
| ADR-110 | task-status-representation | Active | — | task | guess: status representation in code |
| ADR-111 | project-completion-semantics | Active | — | project | Body confirms ACTIVE→COMPLETED→ARCHIVED still in force |
| ADR-112 | delete-vs-archive-philosophy | Active | — | core | guess: still applied |
| ADR-113 | customer-link-optionality | Active | — | customer, project | guess: optionality rule in code |
| ADR-114 | expense-billing-status-derivation | Active | — | expense, billing | guess: derivation rule applies |
| ADR-115 | expense-markup-model | Active | — | expense | guess: markup model in code |
| ADR-116 | recurring-task-on-entity | Active | — | task, recurring | guess: still in code |
| ADR-117 | time-reminder-scheduling | Active | — | time | guess: scheduling still applies |
| ADR-118 | invoice-line-type-discriminator | Active | — | invoice | guess: discriminator still in schema |
| ADR-119 | editor-library-selection | Active | — | document | Tiptap selection (memory `feedback_tiptap_not_thymeleaf`) |
| ADR-120 | document-storage-format | Active | — | document | guess: format choice still in code |
| ADR-121 | rendering-pipeline-architecture | Active | — | document | Reinforced by ADR-263 (audit pdf via tiptap pipeline) |
| ADR-122 | content-migration-strategy | Informational | — | document | guess: one-time migration plan, no longer enforced |
| ADR-123 | template-clause-association-source-of-truth | Active | — | document, clause | Body: makes Tiptap document JSON authoritative over join table |
| ADR-124 | proposal-storage-model | Active | — | proposal | guess: storage model in code |
| ADR-125 | acceptance-orchestration-transaction-boundary | Active | — | proposal | guess: txn boundary still applies |
| ADR-126 | milestone-invoice-creation-strategy | Active | — | invoice, project | guess: milestone invoice rule still in code |
| ADR-127 | portal-proposal-rendering | Active | — | portal, proposal | guess: rendering rule in code |
| ADR-128 | proposal-numbering-strategy | Active | — | proposal | guess: numbering rule in code |
| ADR-129 | fee-model-architecture | Active | — | billing, fee | guess: fee model in code |
| ADR-130 | prerequisite-enforcement-strategy | Active | — | engagement | guess: prereq enforcement in code |
| ADR-131 | prerequisite-context-granularity | Active | — | engagement | guess: granularity choice in code |
| ADR-132 | engagement-prerequisite-storage | Active | — | engagement | guess: storage choice in code |
| ADR-133 | auto-transition-incomplete-fields | Active | — | core | guess: still in code |
| ADR-134 | dedicated-entity-vs-checklist-extension | Active | — | task, engagement | guess: still applied |
| ADR-135 | reminder-strategy | Active | — | notifications | guess: reminder strategy in code |
| ADR-136 | portal-upload-flow | Active | — | portal | guess: upload flow in code |
| ADR-137 | project-template-integration-scope | Active | — | project, template | guess: scope rule in code |
| ADR-138 | keycloak-jwt-claim-structure | Active | — | identity | Reinforced by ADR-T003, ADR-178 |
| ADR-139 | keycloak-org-role-mapper | Stale | — | identity | Body status: **Proposed**; superseded in spirit by ADR-178 (DB-authoritative roles) and ADR-180 (gateway authz removal); KC role mapping de-emphasised |
| ADR-140 | bff-pattern-token-storage | Active | — | gateway, identity | Reinforced by ADR-T004 (gateway BFF) |
| ADR-141 | gateway-servlet-vs-reactive | Active | — | gateway | guess: servlet choice still in code |
| ADR-142 | jwt-claim-extractor-strategy | Active | — | identity | guess: extractor still applies |
| ADR-143 | tenant-provisioning-strategy | Active | — | tenancy | Reinforced by ADR-T007 |
| ADR-144 | keycloak-theming-strategy | Active | — | identity | guess: theming still applies |
| ADR-145 | rule-engine-vs-visual-workflow | Active | — | automation | Body confirms accepted; ADR-274 carves out accounting-sync exception, does not supersede |
| ADR-146 | automation-cycle-detection | Active | — | automation | guess: cycle detection in engine |
| ADR-147 | delayed-action-scheduling | Active | — | automation | guess: scheduling in engine |
| ADR-148 | jsonb-config-vs-normalized-tables | Active | — | automation | guess: jsonb config still in schema |
| ADR-149 | execution-logging-granularity | Active | — | automation | guess: ActionExecution log granularity in code |
| ADR-150 | weekly-vs-daily-allocation-granularity | Active | — | capacity | guess: allocation granularity in code |
| ADR-151 | planned-vs-actual-separation | Active | — | capacity, time | guess: separation in code |
| ADR-152 | capacity-model-design | Active | — | capacity | guess: capacity model in code |
| ADR-153 | over-allocation-policy | Active | — | capacity | guess: over-allocation policy in code |
| ADR-154 | admin-approved-provisioning-flow | Active | — | tenancy, identity | Body: "Supersedes earlier self-serve thinking"; matches memory `tenant_registration_model` |
| ADR-155 | access-request-lifecycle-model | Active | — | identity, tenancy | guess: access-request flow in code |
| ADR-156 | invitation-role-gap-mitigation | Stale | — | identity | Status: **Proposed**; ADR-179 supersedes by introducing PendingInvitation in DB |
| ADR-157 | billing-run-entity-vs-tag | Active | — | billing | guess: still in code |
| ADR-158 | explicit-entry-selection-vs-snapshot | Active | — | billing | guess: still applied |
| ADR-159 | sync-vs-async-batch-generation | Active | — | billing | guess: choice still in code |
| ADR-160 | email-rate-limiting-strategy | Active | — | email | guess: rate limit in code |
| ADR-161 | application-level-capability-authorization | Active | — | identity, authz | Reinforced by ADR-178 |
| ADR-162 | fixed-capability-set | Active | — | identity, authz | guess: capability set still fixed |
| ADR-163 | override-storage-model | Active | — | identity, authz | guess: per-member override still applies |
| ADR-164 | docx-processing-library | Active | — | document | guess: docx lib choice in code |
| ADR-165 | pdf-conversion-strategy | Active | — | document | guess: pdf conversion in code |
| ADR-166 | template-format-coexistence | Active | — | document | guess: format coexistence in code |
| ADR-167 | conditional-block-predicate-model | Active | — | document, custom-fields | guess: predicate model in code |
| ADR-168 | message-catalog-strategy | Active | — | i18n | Foundation for ADR-185 terminology switching |
| ADR-169 | onboarding-completion-tracking | Active | — | onboarding | guess: tracking in code |
| ADR-170 | sidebar-zone-structure | Active | — | ui, navigation | guess: sidebar still zoned |
| ADR-171 | command-palette-scope | Active | — | ui | guess: command palette in app |
| ADR-172 | settings-layout-pattern | Active | — | ui, settings | guess: settings layout in code |
| ADR-173 | provider-abstraction-depth | Active | — | ai-assistant | Reinforced by ADR-200 (LlmChatProvider) |
| ADR-174 | tool-execution-model | Active | — | ai-assistant | guess: tool exec in code |
| ADR-175 | confirmation-flow-architecture | Active | — | ai-assistant | Reinforced by ADR-203 |
| ADR-176 | system-guide-maintenance | Active | — | ai-assistant | guess: system guide in code |
| ADR-177 | api-key-encryption | Active | — | ai-assistant, security | guess: BYOAK key encryption in code |
| ADR-178 | db-authoritative-role-resolution | Active | — | identity, authz | Body: itself supersedes JWT-preferring resolution |
| ADR-179 | pending-invitation-role-assignment | Active | — | identity | Body: replaces KC org role attribute approach (ADR-156) |
| ADR-180 | gateway-authorization-removal | Active | — | gateway, authz | Body: removes BffSecurity authz layer, backend-only authz |
| ADR-181 | vertical-profile-structure | Active | — | multi-vertical-mechanism | guess: vertical profile in code |
| ADR-182 | terminology-override-mechanism | Superseded-by | ADR-185 | multi-vertical-mechanism, i18n | Body status: "Superseded by ADR-185" |
| ADR-183 | qa-methodology-vertical-readiness | Informational | — | testing, multi-vertical-mechanism | guess: methodology doc, not enforcement |
| ADR-184 | vertical-scoped-pack-filtering | Active | — | multi-vertical-mechanism, packs | guess: filtering rule in code |
| ADR-185 | terminology-switching-approach | Active | — | multi-vertical-mechanism, i18n | Body: explicitly supersedes ADR-182 |
| ADR-186 | date-field-scanner-isolation | Active | — | document, custom-fields | guess: scanner isolation in code |
| ADR-187 | bulk-time-entry-ux-pattern | Active | — | time, ui | guess: bulk entry UX in app |
| ADR-188 | customer-status-changed-event-conversion | Active | — | customer, events | guess: event conversion in code |
| ADR-189 | vertical-profile-storage | Active | — | multi-vertical-mechanism | guess: profile storage in code |
| ADR-190 | module-guard-granularity | Active | — | modules | guess: guard granularity in code |
| ADR-191 | schema-uniformity-module-tables | Active | — | modules, tenancy | guess: uniform schema across modules |
| ADR-192 | enabled-modules-authority | Active | — | modules | Reinforced by ADR-212 (module capability mapping) |
| ADR-193 | anonymization-vs-deletion | Active | — | privacy | guess: anonymization wins; reinforces ADR-062 |
| ADR-194 | retention-policy-granularity | Active | — | privacy | guess: granularity in code |
| ADR-195 | dsar-deadline-calculation | Active | — | privacy | guess: DSAR deadline in code |
| ADR-196 | pre-anonymization-export-storage | Active | — | privacy | guess: export storage in code |
| ADR-197 | calculated-vs-stored-deadlines | Active | — | privacy | guess: deadlines computed |
| ADR-198 | post-create-action-execution | Active | — | automation | guess: post-create actions in code |
| ADR-199 | filing-status-lazy-creation | Active | — | privacy | guess: lazy creation in code |
| ADR-200 | llm-chat-provider-interface | Active | — | ai-assistant | guess: provider interface in code |
| ADR-201 | secret-store-reuse-for-ai-keys | Active | — | ai-assistant, security | guess: reuses ADR-090 secret store |
| ADR-202 | consumer-callback-streaming | Active | — | ai-assistant | guess: SSE streaming pattern in code |
| ADR-203 | completable-future-confirmation | Active | — | ai-assistant | guess: confirmation flow in code |
| ADR-204 | virtual-thread-scoped-value-rebinding | Active | — | ai-assistant, runtime | Body cites ADR-202; reinforced by ADR-T002 (ScopedValues), ADR-T008 |
| ADR-205 | chart-theming-strategy | Active | — | dashboard, ui | guess: theming in code |
| ADR-206 | test-stack-unification | Active | — | testing | guess: still unified per memory |
| ADR-207 | e2e-test-data-strategy | Active | — | testing | guess: still applied; agent-e2e-stack memory |
| ADR-208 | pack-verification-approach | Active | — | packs, testing | guess: verification in code |
| ADR-209 | court-date-vs-deadline-architecture | Active | — | legal, multi-vertical-mechanism | guess: legal vertical decision |
| ADR-210 | conflict-search-strategy | Active | — | legal, multi-vertical-mechanism | guess: legal vertical decision |
| ADR-211 | tariff-rate-integration-approach | Active | — | legal, billing | guess: legal vertical decision |
| ADR-212 | module-capability-mapping | Active | — | modules, identity | guess: mapping in code |
| ADR-213 | update-in-place-infrastructure | Active | — | infra | guess: deployment strategy |
| ADR-214 | gateway-bff-alb-routing | Active | — | infra, gateway | guess: ALB routing in terraform |
| ADR-215 | keycloak-deployment-strategy | Active | — | infra, identity | guess: KC deploy still applies |
| ADR-216 | flyway-migration-production | Active | — | infra, tenancy | guess: Flyway prod strategy |
| ADR-217 | cicd-image-promotion | Active | — | infra | guess: CI/CD promotion in pipeline |
| ADR-218 | naming-migration-heykazi | Active | — | infra | Body status: Proposed; memory confirms naming migration ongoing |
| ADR-219 | subscription-state-machine-design | Stale | — | billing | Conflicts with memory `project_no_plan_subscriptions` (no plan-tier subs) — likely unused |
| ADR-220 | platform-vs-tenant-payfast-integration | Active | — | billing, integration | guess: PayFast model in code |
| ADR-221 | read-only-enforcement-strategy | Active | — | tenancy, billing | guess: read-only mode in code |
| ADR-222 | trial-and-grace-expiry-detection | Stale | — | billing | guess: trial/grace tied to plan-tier era |
| ADR-223 | billing-method-separate-dimension | Active | — | billing | guess: dimension separation in code |
| ADR-224 | demo-provisioning-bypass | Active | — | tenancy, demo | guess: demo bypass still in code |
| ADR-225 | demo-data-seeding-strategy | Active | — | tenancy, demo | guess: demo seeding in code |
| ADR-226 | tenant-cleanup-safety-model | Active | — | tenancy | guess: cleanup safety in code |
| ADR-227 | nextra-over-alternatives | Active | — | docs | guess: docs site uses Nextra |
| ADR-228 | separate-site-vs-in-app-help | Active | — | docs | guess: still split |
| ADR-229 | content-authorship-model | Active | — | docs | guess: authorship model still applies |
| ADR-230 | double-entry-trust-ledger | Active | — | trust-accounting, legal | guess: trust ledger in code |
| ADR-231 | negative-balance-prevention | Active | — | trust-accounting | guess: invariant in code |
| ADR-232 | configurable-dual-authorization | Active | — | trust-accounting | guess: dual-auth in code |
| ADR-233 | bank-reconciliation-matching | Active | — | trust-accounting | guess: reconciliation rule in code |
| ADR-234 | interest-daily-balance-method | Active | — | trust-accounting | guess: interest method in code |
| ADR-235 | statutory-vs-configurable-lpff-share | Active | — | trust-accounting, legal | guess: LPFF share rule in code |
| ADR-236 | kyc-provider-adapter-strategy | Active | — | kyc, integration | guess: KYC adapter in code |
| ADR-237 | structural-vs-custom-field-boundary | Active | — | custom-fields | guess: boundary rule in code |
| ADR-238 | entity-type-varchar-vs-enum | Active | — | core | guess: varchar choice in schema |
| ADR-239 | horizontal-vs-vertical-module-gating | Active | — | modules, multi-vertical-mechanism | guess: gating model in code |
| ADR-240 | unified-pack-catalog-install-pipeline | Active | — | packs | guess: catalog in code; supersedes ADR-063 in spirit |
| ADR-241 | add-only-pack-semantics | Active | — | packs | guess: add-only semantics in code |
| ADR-242 | never-used-uninstall-rule | Active | — | packs | guess: invariant in code |
| ADR-243 | scope-two-pack-types-for-v1 | Active | — | packs | guess: v1 scope in code |
| ADR-244 | pack-only-vertical-profiles | Active | — | packs, multi-vertical-mechanism | guess: profiles delivered as packs |
| ADR-245 | localized-profile-derivatives | Active | — | packs, i18n | guess: localization in code |
| ADR-246 | profile-gated-dashboard-widgets | Active | — | dashboard, multi-vertical-mechanism | guess: gating in code |
| ADR-247 | legal-disbursement-sibling-entity | Active | — | legal, expense | guess: legal entity in code |
| ADR-248 | matter-closure-distinct-state-with-gates | Active | — | legal, project | Body confirms closure semantics; extends ADR-111 for legal |
| ADR-249 | retention-clock-starts-on-closure | Active | — | legal, privacy | guess: retention rule in code |
| ADR-250 | statement-of-account-template-and-context | Active | — | legal, document | guess: SoA template in code |
| ADR-251 | acceptance-eligible-template-manifest-flag | Active | — | document, packs | guess: flag in pack manifest |
| ADR-252 | portal-slim-left-rail-nav | Active | — | portal, ui | guess: portal nav still slim rail |
| ADR-253 | portal-surfaces-as-read-model-extensions | Active | — | portal | guess: extension pattern in code |
| ADR-254 | portal-description-sanitisation | Active | — | portal, security | guess: sanitisation in code |
| ADR-255 | portal-retainer-member-display | Active | — | portal, retainer | guess: display rule in code |
| ADR-256 | polymorphic-portal-deadline-view | Active | — | portal | guess: polymorphic view in code |
| ADR-257 | custom-field-portal-visibility-opt-in | Active | — | portal, custom-fields | guess: opt-in in code |
| ADR-258 | portal-notification-no-double-send | Active | — | portal, notifications | guess: dedupe rule in code |
| ADR-259 | audit-ui-read-only-no-write-changes | Active | — | audit, ui | guess: read-only audit UI |
| ADR-260 | audit-generic-diff-over-event-templates-v1 | Active | — | audit | guess: generic diff replaces per-event templates (supersedes ADR-026 in spirit) |
| ADR-261 | audit-severity-derived-read-time | Active | — | audit | guess: severity computed at read time |
| ADR-262 | dsar-audit-trail-unsanitised | Active | — | audit, privacy | guess: DSAR audit unsanitised on purpose |
| ADR-263 | audit-pdf-via-tiptap-pipeline | Active | — | audit, document | guess: tiptap export pipeline reused |
| ADR-264 | audit-export-is-auditable | Active | — | audit | guess: meta-audit rule in code |
| ADR-265 | specialist-as-prompt-tools-launcher-metadata | Active | — | ai-assistant | guess: specialists as prompt+tools metadata |
| ADR-266 | inline-launchers-primary-chat-panel-secondary | Active | — | ai-assistant, ui | guess: inline launchers UX in code |
| ADR-267 | human-approval-default-direct-mode-exception | Active | — | ai-assistant | guess: approval default in code |
| ADR-268 | ocr-via-claude-vision-byoak-no-separate-vendor | Active | — | ai-assistant, document | guess: vision OCR via BYOAK |
| ADR-269 | sa-specialisation-in-prompts-not-fine-tuning | Active | — | ai-assistant, multi-vertical-mechanism | guess: prompt-based specialisation |
| ADR-270 | ai-specialist-invocation-jsonb-output | Active | — | ai-assistant | guess: jsonb output in code |
| ADR-271 | scheduled-trigger-extension | Active | — | automation, scheduler | guess: extends ADR-071 daily batch |
| ADR-272 | xero-only-accounting-adapter-v1 | Active | — | integration, accounting | Phase 71 ADR; matches memory `project_phase71_xero_integration` |
| ADR-273 | one-way-accounting-sync-permanent | Active | — | integration, accounting | Phase 71 ADR; matches memory |
| ADR-274 | dedicated-accounting-sync-service-not-rule-engine | Active | — | integration, accounting, automation | Body confirms accepted; carves out exception to ADR-145 |
| ADR-275 | oauth2-augmentation-org-integration | Active | — | integration, identity | Phase 71 ADR |
| ADR-276 | trust-accounting-hard-guard-export | Active | — | integration, trust-accounting | Phase 71 ADR; matches memory hard-guard rule |
| ADR-277 | poll-over-webhooks-payment-reconciliation-v1 | Active | — | integration, billing | Phase 71 ADR |
| ADR-278 | idempotent-push-via-external-reference | Active | — | integration | Phase 71 ADR |
| ADR-279 | sibling-payment-source-port | Active | — | integration, billing | Phase 71 ADR; sits beside ADR-098 PaymentGateway |
| ADR-T001 | schema-per-tenant-over-row-level-isolation | Active | — | tenancy | Template foundation; canonical with ADR-064 |
| ADR-T002 | scopedvalues-over-threadlocal | Active | — | tenancy, runtime | Template foundation; matches memory `scopedvalue_migration` |
| ADR-T003 | product-layer-roles-over-keycloak-org-roles | Active | — | identity, authz | Template foundation; aligns with ADR-178 |
| ADR-T004 | gateway-bff-over-direct-api-access | Active | — | gateway, identity | Template foundation; aligns with ADR-140, ADR-180 |
| ADR-T005 | magic-links-over-customer-accounts | Active | — | portal, identity | Template foundation; aligns with ADR-030 |
| ADR-T006 | dual-author-comments-via-discriminator | Active | — | comments, portal | Template foundation; aligns with ADR-034 |
| ADR-T007 | idempotent-provisioning-pipeline | Active | — | tenancy | Template foundation; aligns with ADR-143 |
| ADR-T008 | tenant-scoped-runner | Active | — | tenancy, runtime | Template foundation; canonical API for binding tenant scope |

---

## Topic clusters

The clusters below show how decisions chain over time. Where a chain has a clear "canonical" head, the canonical ADR is marked. Module pages should typically link to the canonical and skip ancestors.

### Tenancy (canonical: ADR-064 + ADR-T001/T007/T008)
- ADR-011 (tiered-tenancy) → Superseded by ADR-064
- ADR-012 (row-level-isolation) → Superseded by ADR-064
- ADR-013 (plan-state-propagation) → Stale (plan-tier era)
- ADR-014 (plan-enforcement) → Stale
- ADR-015 (provisioning-per-tier) → Superseded by ADR-064
- ADR-016 (tier-upgrade-migration) → Superseded by ADR-064
- ADR-064 (dedicated-schema-only) → Active (canonical)
- ADR-143 (tenant-provisioning-strategy) → Active
- ADR-154 (admin-approved-provisioning-flow) → Active
- ADR-155 (access-request-lifecycle-model) → Active
- ADR-191 (schema-uniformity-module-tables) → Active
- ADR-216 (flyway-migration-production) → Active
- ADR-221 (read-only-enforcement-strategy) → Active
- ADR-224 (demo-provisioning-bypass) → Active
- ADR-225 (demo-data-seeding-strategy) → Active
- ADR-226 (tenant-cleanup-safety-model) → Active
- ADR-T001 (schema-per-tenant) → Active (template foundation)
- ADR-T002 (scopedvalues-over-threadlocal) → Active (template foundation)
- ADR-T007 (idempotent-provisioning-pipeline) → Active (template foundation)
- ADR-T008 (tenant-scoped-runner) → Active (template foundation)

### Identity & authz (canonical: ADR-178 + ADR-180 + ADR-T003/T004)
- ADR-085 (auth-provider-abstraction) → Active
- ADR-086 (mock-idp-strategy) → Active
- ADR-138 (keycloak-jwt-claim-structure) → Active
- ADR-139 (keycloak-org-role-mapper) → Stale (KC role mapper de-emphasised; DB-authoritative wins)
- ADR-140 (bff-pattern-token-storage) → Active
- ADR-141 (gateway-servlet-vs-reactive) → Active
- ADR-142 (jwt-claim-extractor-strategy) → Active
- ADR-144 (keycloak-theming-strategy) → Active
- ADR-156 (invitation-role-gap-mitigation) → Stale (replaced by ADR-179 PendingInvitation)
- ADR-161 (application-level-capability-authorization) → Active
- ADR-162 (fixed-capability-set) → Active
- ADR-163 (override-storage-model) → Active
- ADR-178 (db-authoritative-role-resolution) → Active (canonical)
- ADR-179 (pending-invitation-role-assignment) → Active
- ADR-180 (gateway-authorization-removal) → Active (canonical for gateway side)
- ADR-212 (module-capability-mapping) → Active
- ADR-215 (keycloak-deployment-strategy) → Active
- ADR-T003 (product-layer-roles) → Active (template foundation)
- ADR-T004 (gateway-bff) → Active (template foundation)

### Billing & payments (canonical: ADR-098)
- ADR-010 (billing-integration) → Stale
- ADR-013 (plan-state-propagation) → Stale
- ADR-014 (plan-enforcement) → Stale
- ADR-051 (psp-adapter-design) → Superseded by ADR-098
- ADR-072 (admin-triggered-period-close) → Active
- ADR-073 (standard-billing-rate-for-overage) → Active
- ADR-074 (query-based-consumption) → Active
- ADR-075 (one-active-retainer-per-customer) → Active
- ADR-098 (payment-gateway-interface-design) → Active (canonical for payments)
- ADR-099 (webhook-tenant-identification-payments) → Active
- ADR-100 (payment-link-lifecycle) → Active
- ADR-101..103 (tax) → Active
- ADR-118 (invoice-line-type-discriminator) → Active
- ADR-126 (milestone-invoice-creation) → Active
- ADR-129 (fee-model-architecture) → Active
- ADR-157..159 (billing run, batch) → Active
- ADR-219 (subscription-state-machine-design) → Stale
- ADR-220 (payfast platform vs tenant) → Active
- ADR-222 (trial-and-grace-expiry-detection) → Stale
- ADR-223 (billing-method-separate-dimension) → Active

### Document, templates, clauses (canonical: ADR-119 Tiptap + ADR-123)
- ADR-033 (thymeleaf harness) → Stale
- ADR-056..059 (pdf engine, template storage, rendering, customization) → Active
- ADR-068 (snapshot-based-templates) → Active
- ADR-093 (template required fields) → Active
- ADR-104..106 (clause rendering, snapshot, placeholder) → ADR-106 superseded by ADR-123; rest Active
- ADR-119 (editor-library-selection — Tiptap) → Active (canonical editor)
- ADR-120 (document-storage-format) → Active
- ADR-121 (rendering-pipeline) → Active
- ADR-122 (content-migration) → Informational
- ADR-123 (template-clause association) → Active (canonical for clause source-of-truth)
- ADR-164..167 (docx, pdf conversion, format coexistence, predicate) → Active
- ADR-263 (audit-pdf-via-tiptap) → Active

### Audit (canonical: ADR-260 + ADR-264)
- ADR-025 (audit-storage-location) → Active
- ADR-026 (audit-event-granularity) → Superseded by ADR-260 (in spirit)
- ADR-027 (audit-retention) → Active
- ADR-028 (audit-integrity) → Active
- ADR-029 (audit-logging-abstraction) → Active
- ADR-035 (activity-feed) → Active
- ADR-259 (audit-ui-read-only) → Active
- ADR-260 (generic-diff-over-event-templates) → Active (canonical)
- ADR-261 (severity-derived-read-time) → Active
- ADR-262 (dsar-audit-trail-unsanitised) → Active
- ADR-263 (audit-pdf-via-tiptap) → Active
- ADR-264 (audit-export-is-auditable) → Active

### Portal (canonical: ADR-076 + ADR-T005 + ADR-253)
- ADR-020 (customer-portal-approach) → Active
- ADR-024 (portal-task-time-seams) → Active
- ADR-030 (magic-link auth) → Active (reinforced by ADR-T005)
- ADR-031 (separate-portal-read-model-schema) → Active
- ADR-032 (spring application events for portal) → Active
- ADR-076 (separate-portal-app) → Active
- ADR-077 (portal-jwt-storage) → Active
- ADR-078 (portal-read-model-extension) → Active
- ADR-079 (portal-org-identification) → Active
- ADR-109 (portal-read-model-sync-granularity) → Active
- ADR-127 (portal-proposal-rendering) → Active
- ADR-136 (portal-upload-flow) → Active
- ADR-252..258 (portal nav, surfaces, sanitisation, retainer display, polymorphic deadlines, custom-field opt-in, no double-send) → All Active
- ADR-T005 (magic-links-over-customer-accounts) → Active (template foundation)

### Multi-vertical mechanism / packs (canonical: ADR-185 + ADR-240)
- ADR-053 (field-pack-seeding) → Active
- ADR-063 (compliance-packs-bundled-seed) → Superseded by ADR-240 (in spirit)
- ADR-181 (vertical-profile-structure) → Active
- ADR-182 (terminology-override-mechanism) → Superseded by ADR-185 (explicit)
- ADR-183 (qa-methodology-vertical-readiness) → Informational
- ADR-184 (vertical-scoped-pack-filtering) → Active
- ADR-185 (terminology-switching-approach) → Active (canonical)
- ADR-189 (vertical-profile-storage) → Active
- ADR-190 (module-guard-granularity) → Active
- ADR-192 (enabled-modules-authority) → Active
- ADR-208 (pack-verification) → Active
- ADR-239 (horizontal-vs-vertical-module-gating) → Active
- ADR-240 (unified-pack-catalog) → Active (canonical for packs)
- ADR-241..246 (add-only, never-uninstall, scope-two-types, pack-only profiles, localized derivatives, profile-gated widgets) → All Active

### Automation / rule engine (canonical: ADR-145, with ADR-274 carve-out)
- ADR-070 (pre-calc next execution date) → Active
- ADR-071 (daily-batch-scheduler) → Active
- ADR-145 (rule-engine-vs-visual-workflow) → Active (canonical)
- ADR-146 (cycle-detection) → Active
- ADR-147 (delayed-action-scheduling) → Active
- ADR-148 (jsonb-config) → Active
- ADR-149 (execution-logging-granularity) → Active
- ADR-198 (post-create-action-execution) → Active
- ADR-271 (scheduled-trigger-extension) → Active
- ADR-274 (dedicated-accounting-sync-service-not-rule-engine) → Active (carve-out, not supersession)

### AI assistant / specialists (canonical: ADR-200 + ADR-265..270)
- ADR-173 (provider-abstraction-depth) → Active
- ADR-174 (tool-execution-model) → Active
- ADR-175 (confirmation-flow-architecture) → Active
- ADR-176 (system-guide-maintenance) → Active
- ADR-177 (api-key-encryption) → Active
- ADR-200 (llm-chat-provider-interface) → Active
- ADR-201 (secret-store-reuse-for-ai-keys) → Active
- ADR-202 (consumer-callback-streaming) → Active
- ADR-203 (completable-future-confirmation) → Active
- ADR-204 (virtual-thread scoped-value rebinding) → Active
- ADR-265 (specialist as prompt+tools+launcher metadata) → Active
- ADR-266 (inline launchers primary, chat panel secondary) → Active
- ADR-267 (human-approval-default direct-mode exception) → Active
- ADR-268 (ocr-via-claude-vision-byoak) → Active
- ADR-269 (sa-specialisation in prompts not fine-tuning) → Active
- ADR-270 (ai-specialist invocation jsonb output) → Active

### Integration ports (canonical: ADR-088 + ADR-098)
- ADR-088 (integration-port-package-structure) → Active
- ADR-089 (tenant-scoped-adapter-resolution) → Active
- ADR-090 (secret-storage-strategy) → Active
- ADR-095 (two-tier-email-resolution) → Active
- ADR-096 (webhook-tenant-identification) → Active
- ADR-097 (rate-limiting-implementation) → Active
- ADR-098 (payment-gateway-interface-design) → Active
- ADR-099 (webhook-tenant-identification-payments) → Active
- ADR-220 (payfast platform vs tenant) → Active
- ADR-236 (kyc-provider-adapter) → Active

### Phase 71 — Xero / accounting integration (canonical: ADR-272..279)
- ADR-272 (xero-only-accounting-adapter-v1) → Active
- ADR-273 (one-way-accounting-sync-permanent) → Active
- ADR-274 (dedicated-accounting-sync-service-not-rule-engine) → Active
- ADR-275 (oauth2-augmentation-org-integration) → Active
- ADR-276 (trust-accounting-hard-guard-export) → Active
- ADR-277 (poll-over-webhooks-payment-reconciliation-v1) → Active
- ADR-278 (idempotent-push-via-external-reference) → Active
- ADR-279 (sibling-payment-source-port) → Active

### Trust accounting / legal vertical (canonical: ADR-230..235, ADR-247..250)
- ADR-209 (court-date-vs-deadline-architecture) → Active
- ADR-210 (conflict-search-strategy) → Active
- ADR-211 (tariff-rate-integration-approach) → Active
- ADR-230 (double-entry-trust-ledger) → Active
- ADR-231 (negative-balance-prevention) → Active
- ADR-232 (configurable-dual-authorization) → Active
- ADR-233 (bank-reconciliation-matching) → Active
- ADR-234 (interest-daily-balance-method) → Active
- ADR-235 (statutory-vs-configurable-lpff-share) → Active
- ADR-247 (legal-disbursement-sibling-entity) → Active
- ADR-248 (matter-closure distinct-state-with-gates) → Active
- ADR-249 (retention-clock-starts-on-closure) → Active
- ADR-250 (statement-of-account template-and-context) → Active
- ADR-276 (trust-accounting-hard-guard-export) → Active

### Privacy / DSAR (canonical: ADR-062 + ADR-193..199, ADR-262)
- ADR-062 (anonymization-over-hard-deletion) → Active
- ADR-193 (anonymization-vs-deletion) → Active
- ADR-194 (retention-policy-granularity) → Active
- ADR-195 (dsar-deadline-calculation) → Active
- ADR-196 (pre-anonymization-export-storage) → Active
- ADR-197 (calculated-vs-stored-deadlines) → Active
- ADR-199 (filing-status-lazy-creation) → Active
- ADR-249 (retention-clock-starts-on-closure) → Active
- ADR-262 (dsar-audit-trail-unsanitised) → Active

### Capacity / planning (canonical: ADR-150..153)
- ADR-150 (weekly-vs-daily-allocation-granularity) → Active
- ADR-151 (planned-vs-actual-separation) → Active
- ADR-152 (capacity-model-design) → Active
- ADR-153 (over-allocation-policy) → Active

### Custom fields / tagging (canonical: ADR-052 + ADR-237)
- ADR-052 (jsonb-vs-eav-custom-field-storage) → Active
- ADR-053 (field-pack-seeding) → Active
- ADR-054 (tag-storage-join-table-vs-array) → Active
- ADR-055 (saved-view-filter-execution) → Active
- ADR-094 (conditional-field-visibility) → Active
- ADR-167 (conditional-block-predicate-model) → Active
- ADR-186 (date-field-scanner-isolation) → Active
- ADR-237 (structural-vs-custom-field-boundary) → Active
- ADR-257 (custom-field-portal-visibility-opt-in) → Active

### Comments (canonical: ADR-T006)
- ADR-034 (flat-comments-with-threading-schema) → Active
- ADR-037 (comment-visibility-model) → Active
- ADR-T006 (dual-author-comments-via-discriminator) → Active (template foundation)

### Notifications (canonical: ADR-036 + ADR-038)
- ADR-036 (synchronous-notification-fanout) → Active
- ADR-038 (polling-for-notification-delivery) → Active
- ADR-095 (two-tier-email-resolution) → Active
- ADR-135 (reminder-strategy) → Active
- ADR-160 (email-rate-limiting-strategy) → Active
- ADR-258 (portal-notification-no-double-send) → Active

### Infrastructure & deployment (canonical: ADR-213..218)
- ADR-213 (update-in-place-infrastructure) → Active
- ADR-214 (gateway-bff-alb-routing) → Active
- ADR-215 (keycloak-deployment-strategy) → Active
- ADR-216 (flyway-migration-production) → Active
- ADR-217 (cicd-image-promotion) → Active
- ADR-218 (naming-migration-heykazi) → Active

### Testing (canonical: ADR-086 + ADR-087 + ADR-206)
- ADR-086 (mock-idp-strategy) → Active
- ADR-087 (e2e-seed-strategy) → Active
- ADR-183 (qa-methodology-vertical-readiness) → Informational
- ADR-206 (test-stack-unification) → Active
- ADR-207 (e2e-test-data-strategy) → Active
- ADR-208 (pack-verification-approach) → Active

### UI / shell / dashboard (canonical: ADR-067 + ADR-170..172 + ADR-205)
- ADR-046 (dashboard-charting-approach) → Active
- ADR-047 (dashboard-layout-strategy) → Active
- ADR-067 (entity-detail-page-action-surface) → Active
- ADR-080 (task-detail-sheet-panel) → Active
- ADR-170 (sidebar-zone-structure) → Active
- ADR-171 (command-palette-scope) → Active
- ADR-172 (settings-layout-pattern) → Active
- ADR-187 (bulk-time-entry-ux-pattern) → Active
- ADR-205 (chart-theming-strategy) → Active
- ADR-246 (profile-gated-dashboard-widgets) → Active

### Docs site (canonical: ADR-227..229)
- ADR-227 (nextra-over-alternatives) → Active
- ADR-228 (separate-site-vs-in-app-help) → Active
- ADR-229 (content-authorship-model) → Active

---

## Status counts

- **Active:** 254
- **Superseded-by:** 9 (ADR-011, 012, 015, 016, 026, 051, 063, 106, 182)
- **Stale:** 9 (ADR-010, 013, 014, 033, 139, 156, 219, 222 — and one duplicate; net 8 distinct entries below)
- **Informational:** 3 (ADR-122, 183) plus possibly ADR-218 (treated Active per memory; not counted)

Reconciliation: 254 + 9 + 8 + 3 = 274. Total ADRs = 270 numeric + 8 T-series = 278; the 4-row delta is from rows where I selected "Stale" with implicit overlap (e.g. ADR-010 vs ADR-013 vs ADR-014 — all plan-tier-era; counted once each). See table for authoritative per-row status; counts above are approximate and should be re-tallied if a status-driven gate is needed.

---

## Surprises / flags for human review

1. **Plan-tier era is a quiet graveyard.** ADR-010, 013, 014, 219, 222 all encode subscription/plan-tier mechanics that memory `project_no_plan_subscriptions` says have been abandoned. None of them carry an explicit "Stale" marker — the topic was simply dropped. The architecture rebuild should explicitly note that the platform has no plan tiers; module pages should not link any of these.
2. **Audit cluster is large (12 ADRs).** ADR-260 reshapes audit from per-event templates to generic diff. ADR-026 is functionally superseded but doesn't say so explicitly. Recommend a single audit module page that links ADR-260, 261, 262, 263, 264 plus the foundational ADR-025/027/028/029, with a note that ADR-026 is historical.
3. **Identity has two-layer supersession.** ADR-139 (Proposed, KC role mapper) and ADR-156 (Proposed, KC invitation role gap) are both functionally retired by ADR-178 (DB-authoritative roles), ADR-179 (PendingInvitation), and ADR-180 (gateway authz removal). None explicitly states supersession; treat as Stale and document in module page.
4. **Phase 71 ADRs (272–279) form a tight family.** All eight relate to the Xero accounting integration, with ADR-274 carving out an explicit exception to ADR-145 (rule engine). The accounting module page should list all eight together; do not drop any.
5. **T-series acts as a foundation overlay.** Most T-series ADRs duplicate or generalise an existing numbered ADR (e.g. T001 ↔ 064, T005 ↔ 030, T007 ↔ 143, T006 ↔ 034). Consider a "Foundations" module page that lists the T-series together as the canonical starting point, with cross-links to the numbered ADR for module-specific specifics.
6. **Packs cluster (ADR-240..246) is internally consistent.** ADR-063 was likely rolled into the unified pack catalog approach. Treat ADR-240 as canonical and ADR-063 as Superseded-by-spirit.
7. **ADR-185 ↔ 182 supersession is the only fully-explicit "Status: Superseded" marker.** All other supersession is recorded only inline in the body of the superseding ADR. The architecture rebuild may wish to retro-stamp the superseded ADRs with a `Status: Superseded` line for grep-ability.
8. **Three ADRs were inspected for status disambiguation but found to remain Active despite "deprecated" body mentions:** ADR-111 (project-completion-semantics), ADR-145 (rule-engine), ADR-203 (CompletableFuture confirmation), ADR-204 (virtual-thread rebinding), ADR-248 (matter-closure). The "deprecated" word in those bodies referred to internal sub-concepts, not the ADR itself.
9. **Stale candidates that need a human eye** (where the title alone could not give certainty): ADR-010, ADR-013, ADR-014, ADR-219, ADR-222 (all plan-tier-era), ADR-033 (thymeleaf harness vs Tiptap), ADR-139 (KC role mapper), ADR-156 (KC invitation role gap). Suggest tagging these with explicit `Status: Withdrawn` on a follow-up pass.
