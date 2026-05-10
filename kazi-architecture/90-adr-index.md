# ADR Status Index

**Purpose.** This file is a non-destructive **status overlay** for the 278 ADRs in `adr/`. The ADR files themselves are unmoved and unmodified — this index is the single source of truth for "is this ADR currently in force?". Module pages elsewhere in `kazi-architecture/` should link only to ADRs flagged **Active** below.

**Last reviewed:** 2026-05-10

> **Methodology footnote.** Status was derived from ADR titles and headers; explicit `Status: Superseded` markers; supersession statements in later ADRs; and cross-reference with the `phases_complete.md` memory. Explicit `Status:` markers are rare — most supersession is recorded only inline in the body of the superseding ADR.

## Counts

| Status | Count | Notes |
|---|---|---|
| Active | 259 | Includes 8 T-series template-foundation ADRs |
| Superseded | 9 | Replaced explicitly or in spirit by a later ADR |
| Stale | 8 | Decision was made but feature dropped or never built |
| Informational | 3 | Discussion / methodology notes, no enforcement |
| **Total** | **278** | 270 numeric (010–279) + 8 T-series (T001–T008) |

ADR numbering starts at 010; the 001–009 range is intentionally unused.

---

## Active ADRs by topic cluster

The clusters below match the canonical topic taxonomy used across `kazi-architecture/`. Within each cluster, ADRs appear in numeric order. Notes are terse; for full rationale, read the ADR.

### Tenancy & multitenancy

Canonical: **ADR-064** + **ADR-T001/T002/T007/T008**.

| ADR | Title | Note |
|---|---|---|
| ADR-064 | dedicated-schema-only | Canonical schema topology |
| ADR-143 | tenant-provisioning-strategy | Reinforced by ADR-T007 |
| ADR-154 | admin-approved-provisioning-flow | Matches `tenant_registration_model` |
| ADR-155 | access-request-lifecycle-model | |
| ADR-191 | schema-uniformity-module-tables | |
| ADR-216 | flyway-migration-production | |
| ADR-221 | read-only-enforcement-strategy | |
| ADR-224 | demo-provisioning-bypass | |
| ADR-225 | demo-data-seeding-strategy | |
| ADR-226 | tenant-cleanup-safety-model | |
| ADR-T001 | schema-per-tenant-over-row-level-isolation | Template foundation |
| ADR-T002 | scopedvalues-over-threadlocal | Template foundation |
| ADR-T007 | idempotent-provisioning-pipeline | Template foundation |
| ADR-T008 | tenant-scoped-runner | Template foundation |

### Identity, auth & RBAC

Canonical: **ADR-178** (DB-authoritative roles) + **ADR-180** (gateway authz removal) + **ADR-T003/T004**.

| ADR | Title | Note |
|---|---|---|
| ADR-085 | auth-provider-abstraction | |
| ADR-086 | mock-idp-strategy | E2E mock IDP at :8090 |
| ADR-138 | keycloak-jwt-claim-structure | |
| ADR-140 | bff-pattern-token-storage | |
| ADR-141 | gateway-servlet-vs-reactive | |
| ADR-142 | jwt-claim-extractor-strategy | |
| ADR-144 | keycloak-theming-strategy | |
| ADR-161 | application-level-capability-authorization | |
| ADR-162 | fixed-capability-set | |
| ADR-163 | override-storage-model | |
| ADR-178 | db-authoritative-role-resolution | Canonical RBAC source |
| ADR-179 | pending-invitation-role-assignment | Replaces ADR-156 approach |
| ADR-180 | gateway-authorization-removal | Backend-only authz |
| ADR-212 | module-capability-mapping | |
| ADR-215 | keycloak-deployment-strategy | |
| ADR-T003 | product-layer-roles-over-keycloak-org-roles | Template foundation |
| ADR-T004 | gateway-bff-over-direct-api-access | Template foundation |
| ADR-275 | oauth2-augmentation-org-integration | Phase 71 OAuth2 augmentation |

### Subscription & billing platform

> No plan-tier subscriptions per `project_no_plan_subscriptions` — see Stale section for the abandoned plan-tier ADRs.

Canonical: **ADR-098** (PaymentGateway interface).

| ADR | Title | Note |
|---|---|---|
| ADR-039 | rate-resolution-hierarchy | |
| ADR-040 | point-in-time-rate-snapshotting | |
| ADR-041 | multi-currency-store-in-original | |
| ADR-043 | margin-aware-profitability | |
| ADR-048 | invoice-numbering-strategy | |
| ADR-049 | line-item-granularity | |
| ADR-050 | double-billing-prevention | |
| ADR-072 | admin-triggered-period-close | |
| ADR-073 | standard-billing-rate-for-overage | |
| ADR-074 | query-based-consumption | Retainer consumption query |
| ADR-075 | one-active-retainer-per-customer | |
| ADR-098 | payment-gateway-interface-design | Canonical for payments |
| ADR-099 | webhook-tenant-identification-payments | |
| ADR-100 | payment-link-lifecycle | |
| ADR-101 | tax-calculation-strategy | |
| ADR-102 | tax-inclusive-total-display | |
| ADR-103 | tax-rate-immutability | |
| ADR-118 | invoice-line-type-discriminator | |
| ADR-126 | milestone-invoice-creation-strategy | |
| ADR-129 | fee-model-architecture | |
| ADR-157 | billing-run-entity-vs-tag | |
| ADR-158 | explicit-entry-selection-vs-snapshot | |
| ADR-159 | sync-vs-async-batch-generation | |
| ADR-220 | platform-vs-tenant-payfast-integration | |
| ADR-223 | billing-method-separate-dimension | |
| ADR-279 | sibling-payment-source-port | Phase 71; sits beside ADR-098 |

### Customer & lifecycle

| ADR | Title | Note |
|---|---|---|
| ADR-017 | customer-as-org-child | Foundational customer model |
| ADR-060 | lifecycle-status-core-field | |
| ADR-066 | computed-status-over-persisted | |
| ADR-091 | feature-flag-scope | |
| ADR-112 | delete-vs-archive-philosophy | |
| ADR-113 | customer-link-optionality | |
| ADR-133 | auto-transition-incomplete-fields | |
| ADR-188 | customer-status-changed-event-conversion | |
| ADR-238 | entity-type-varchar-vs-enum | |

### Project, task, time, expense

Canonical for projects: ADR-111. For tasks: ADR-019/110. For time: ADR-021/022. For expenses: ADR-114/115.

| ADR | Title | Note |
|---|---|---|
| ADR-019 | task-claim-workflow | |
| ADR-021 | time-tracking-model | |
| ADR-022 | time-aggregation-strategy | |
| ADR-023 | my-work-cross-project-query | |
| ADR-042 | single-budget-per-project | |
| ADR-045 | project-health-scoring | |
| ADR-061 | checklist-first-class-entities | |
| ADR-069 | role-based-assignment-hints | |
| ADR-080 | task-detail-sheet-panel | |
| ADR-110 | task-status-representation | |
| ADR-111 | project-completion-semantics | ACTIVE→COMPLETED→ARCHIVED |
| ADR-114 | expense-billing-status-derivation | |
| ADR-115 | expense-markup-model | |
| ADR-116 | recurring-task-on-entity | |
| ADR-117 | time-reminder-scheduling | |
| ADR-130 | prerequisite-enforcement-strategy | |
| ADR-131 | prerequisite-context-granularity | |
| ADR-132 | engagement-prerequisite-storage | |
| ADR-134 | dedicated-entity-vs-checklist-extension | |
| ADR-137 | project-template-integration-scope | |
| ADR-150 | weekly-vs-daily-allocation-granularity | Capacity planning |
| ADR-151 | planned-vs-actual-separation | Capacity planning |
| ADR-152 | capacity-model-design | Capacity planning |
| ADR-153 | over-allocation-policy | Capacity planning |
| ADR-187 | bulk-time-entry-ux-pattern | |

### Document, template, clause

Canonical: **ADR-119** (Tiptap) + **ADR-123** (template-clause association).

| ADR | Title | Note |
|---|---|---|
| ADR-018 | document-scope-model | |
| ADR-056 | pdf-engine-selection | |
| ADR-057 | template-storage | |
| ADR-058 | rendering-context-assembly | |
| ADR-059 | template-customization-model | |
| ADR-068 | snapshot-based-templates | |
| ADR-093 | template-required-fields | |
| ADR-104 | clause-rendering-strategy | |
| ADR-105 | clause-snapshot-depth | |
| ADR-119 | editor-library-selection | Canonical: Tiptap |
| ADR-120 | document-storage-format | |
| ADR-121 | rendering-pipeline-architecture | |
| ADR-123 | template-clause-association-source-of-truth | Canonical clause source-of-truth |
| ADR-164 | docx-processing-library | |
| ADR-165 | pdf-conversion-strategy | |
| ADR-166 | template-format-coexistence | |
| ADR-167 | conditional-block-predicate-model | |
| ADR-186 | date-field-scanner-isolation | |
| ADR-251 | acceptance-eligible-template-manifest-flag | |

### Invoice, tax, payment, retainer

Most invoice/tax ADRs live in the Subscription & billing cluster above. Proposal/acceptance and engagement-prerequisite ADRs anchor the deal-flow side:

| ADR | Title | Note |
|---|---|---|
| ADR-107 | acceptance-token-strategy | |
| ADR-108 | certificate-storage-and-integrity | |
| ADR-124 | proposal-storage-model | |
| ADR-125 | acceptance-orchestration-transaction-boundary | |
| ADR-127 | portal-proposal-rendering | |
| ADR-128 | proposal-numbering-strategy | |

### Audit, compliance, data protection

Canonical: **ADR-260** (generic-diff) + **ADR-264** (audit-export-is-auditable). Privacy canonical: **ADR-062** + **ADR-193..199**.

| ADR | Title | Note |
|---|---|---|
| ADR-025 | audit-storage-location | |
| ADR-027 | audit-retention-strategy | |
| ADR-028 | audit-integrity-approach | |
| ADR-029 | audit-logging-abstraction | |
| ADR-035 | activity-feed-direct-audit-query | |
| ADR-062 | anonymization-over-hard-deletion | |
| ADR-193 | anonymization-vs-deletion | |
| ADR-194 | retention-policy-granularity | |
| ADR-195 | dsar-deadline-calculation | |
| ADR-196 | pre-anonymization-export-storage | |
| ADR-197 | calculated-vs-stored-deadlines | |
| ADR-199 | filing-status-lazy-creation | |
| ADR-259 | audit-ui-read-only-no-write-changes | |
| ADR-260 | audit-generic-diff-over-event-templates-v1 | Canonical audit shape |
| ADR-261 | audit-severity-derived-read-time | |
| ADR-262 | dsar-audit-trail-unsanitised | |
| ADR-263 | audit-pdf-via-tiptap-pipeline | |
| ADR-264 | audit-export-is-auditable | |

### Notification, comment, activity

Canonical comments: **ADR-T006**. Canonical notifications: **ADR-036 + ADR-038**.

| ADR | Title | Note |
|---|---|---|
| ADR-032 | spring-application-events-for-portal | |
| ADR-034 | flat-comments-with-threading-schema | |
| ADR-036 | synchronous-notification-fanout | |
| ADR-037 | comment-visibility-model | |
| ADR-038 | polling-for-notification-delivery | |
| ADR-095 | two-tier-email-resolution | |
| ADR-135 | reminder-strategy | |
| ADR-160 | email-rate-limiting-strategy | |
| ADR-T006 | dual-author-comments-via-discriminator | Template foundation |

### Reporting & dashboards

Canonical: **ADR-046/047** (charts/layout) + **ADR-081..084** (reporting strategy).

| ADR | Title | Note |
|---|---|---|
| ADR-044 | dashboard-aggregation-caching | |
| ADR-046 | dashboard-charting-approach | |
| ADR-047 | dashboard-layout-strategy | |
| ADR-081 | report-query-strategy-pattern | |
| ADR-082 | report-template-storage | |
| ADR-083 | csv-generation-approach | |
| ADR-084 | parameter-schema-design | |
| ADR-205 | chart-theming-strategy | |
| ADR-246 | profile-gated-dashboard-widgets | |

### Custom field, tag, view

Canonical: **ADR-052** + **ADR-237**.

| ADR | Title | Note |
|---|---|---|
| ADR-052 | jsonb-vs-eav-custom-field-storage | Canonical custom-field storage |
| ADR-053 | field-pack-seeding-strategy | |
| ADR-054 | tag-storage-join-table-vs-array | |
| ADR-055 | saved-view-filter-execution | |
| ADR-094 | conditional-field-visibility | |
| ADR-237 | structural-vs-custom-field-boundary | |
| ADR-257 | custom-field-portal-visibility-opt-in | |

### Portal

Canonical: **ADR-076** + **ADR-T005** (magic links) + **ADR-253** (read-model extensions).

| ADR | Title | Note |
|---|---|---|
| ADR-020 | customer-portal-approach | |
| ADR-024 | portal-task-time-seams | |
| ADR-030 | magic-link-auth-for-customers | |
| ADR-031 | separate-portal-read-model-schema | |
| ADR-076 | separate-portal-app | |
| ADR-077 | portal-jwt-storage | |
| ADR-078 | portal-read-model-extension | |
| ADR-079 | portal-org-identification | |
| ADR-109 | portal-read-model-sync-granularity | |
| ADR-136 | portal-upload-flow | |
| ADR-252 | portal-slim-left-rail-nav | |
| ADR-253 | portal-surfaces-as-read-model-extensions | Canonical extension pattern |
| ADR-254 | portal-description-sanitisation | |
| ADR-255 | portal-retainer-member-display | |
| ADR-256 | polymorphic-portal-deadline-view | |
| ADR-258 | portal-notification-no-double-send | |
| ADR-T005 | magic-links-over-customer-accounts | Template foundation |

### Automation engine

Canonical: **ADR-145** (rule engine), with **ADR-274** carving out an explicit accounting-sync exception.

| ADR | Title | Note |
|---|---|---|
| ADR-070 | pre-calculated-next-execution-date | |
| ADR-071 | daily-batch-scheduler | |
| ADR-092 | auto-apply-strategy | |
| ADR-145 | rule-engine-vs-visual-workflow | Canonical automation shape |
| ADR-146 | automation-cycle-detection | |
| ADR-147 | delayed-action-scheduling | |
| ADR-148 | jsonb-config-vs-normalized-tables | |
| ADR-149 | execution-logging-granularity | |
| ADR-198 | post-create-action-execution | |
| ADR-271 | scheduled-trigger-extension | |
| ADR-274 | dedicated-accounting-sync-service-not-rule-engine | Carve-out, not supersession |

### AI assistant & specialists

Canonical: **ADR-200** + **ADR-265..270**.

| ADR | Title | Note |
|---|---|---|
| ADR-173 | provider-abstraction-depth | |
| ADR-174 | tool-execution-model | |
| ADR-175 | confirmation-flow-architecture | |
| ADR-176 | system-guide-maintenance | |
| ADR-177 | api-key-encryption | BYOAK |
| ADR-200 | llm-chat-provider-interface | Canonical chat provider |
| ADR-201 | secret-store-reuse-for-ai-keys | Reuses ADR-090 |
| ADR-202 | consumer-callback-streaming | |
| ADR-203 | completable-future-confirmation | |
| ADR-204 | virtual-thread-scoped-value-rebinding | |
| ADR-265 | specialist-as-prompt-tools-launcher-metadata | |
| ADR-266 | inline-launchers-primary-chat-panel-secondary | |
| ADR-267 | human-approval-default-direct-mode-exception | |
| ADR-268 | ocr-via-claude-vision-byoak-no-separate-vendor | |
| ADR-269 | sa-specialisation-in-prompts-not-fine-tuning | |
| ADR-270 | ai-specialist-invocation-jsonb-output | |

### Integration ports & packs

Canonical port shape: **ADR-088 + ADR-098**. Canonical packs: **ADR-240**.

| ADR | Title | Note |
|---|---|---|
| ADR-088 | integration-port-package-structure | Canonical port shape |
| ADR-089 | tenant-scoped-adapter-resolution | |
| ADR-090 | secret-storage-strategy | |
| ADR-096 | webhook-tenant-identification | |
| ADR-097 | rate-limiting-implementation | |
| ADR-208 | pack-verification-approach | |
| ADR-236 | kyc-provider-adapter-strategy | |
| ADR-240 | unified-pack-catalog-install-pipeline | Canonical packs |
| ADR-241 | add-only-pack-semantics | |
| ADR-242 | never-used-uninstall-rule | |
| ADR-243 | scope-two-pack-types-for-v1 | |
| ADR-272 | xero-only-accounting-adapter-v1 | Phase 71 |
| ADR-273 | one-way-accounting-sync-permanent | Phase 71 |
| ADR-277 | poll-over-webhooks-payment-reconciliation-v1 | Phase 71 |
| ADR-278 | idempotent-push-via-external-reference | Phase 71 |

### Multi-vertical mechanism (profile, seeds, packs)

Canonical: **ADR-185** (terminology) + **ADR-240** (packs) + **ADR-181/189** (profile).

| ADR | Title | Note |
|---|---|---|
| ADR-168 | message-catalog-strategy | Foundation for ADR-185 |
| ADR-181 | vertical-profile-structure | |
| ADR-184 | vertical-scoped-pack-filtering | |
| ADR-185 | terminology-switching-approach | Canonical (supersedes ADR-182) |
| ADR-189 | vertical-profile-storage | |
| ADR-190 | module-guard-granularity | |
| ADR-192 | enabled-modules-authority | |
| ADR-239 | horizontal-vs-vertical-module-gating | |
| ADR-244 | pack-only-vertical-profiles | |
| ADR-245 | localized-profile-derivatives | |

### Legal vertical (trust, tariffs, conflict, etc.)

Canonical trust: **ADR-230..235**. Canonical legal entities: **ADR-247..250**. Hard guard: **ADR-276**.

| ADR | Title | Note |
|---|---|---|
| ADR-209 | court-date-vs-deadline-architecture | |
| ADR-210 | conflict-search-strategy | |
| ADR-211 | tariff-rate-integration-approach | |
| ADR-230 | double-entry-trust-ledger | |
| ADR-231 | negative-balance-prevention | |
| ADR-232 | configurable-dual-authorization | |
| ADR-233 | bank-reconciliation-matching | |
| ADR-234 | interest-daily-balance-method | |
| ADR-235 | statutory-vs-configurable-lpff-share | |
| ADR-247 | legal-disbursement-sibling-entity | |
| ADR-248 | matter-closure-distinct-state-with-gates | Extends ADR-111 for legal |
| ADR-249 | retention-clock-starts-on-closure | |
| ADR-250 | statement-of-account-template-and-context | |
| ADR-276 | trust-accounting-hard-guard-export | Phase 71 |

### Operational (scheduler, reapers, observability, infra)

| ADR | Title | Note |
|---|---|---|
| ADR-065 | hardcoded-setup-checks | |
| ADR-067 | entity-detail-page-action-surface | UI shell |
| ADR-085 | auth-provider-abstraction | (also Identity) |
| ADR-087 | e2e-seed-strategy | |
| ADR-122 | content-migration-strategy | (Informational; see below) |
| ADR-169 | onboarding-completion-tracking | |
| ADR-170 | sidebar-zone-structure | |
| ADR-171 | command-palette-scope | |
| ADR-172 | settings-layout-pattern | |
| ADR-206 | test-stack-unification | |
| ADR-207 | e2e-test-data-strategy | |
| ADR-213 | update-in-place-infrastructure | |
| ADR-214 | gateway-bff-alb-routing | |
| ADR-217 | cicd-image-promotion | |
| ADR-218 | naming-migration-heykazi | Status: Proposed; treated Active per memory |
| ADR-227 | nextra-over-alternatives | Docs site |
| ADR-228 | separate-site-vs-in-app-help | Docs site |
| ADR-229 | content-authorship-model | Docs site |

---

## Superseded ADRs

| ADR | Title | Superseded by | Reason / evidence |
|---|---|---|---|
| ADR-011 | tiered-tenancy | ADR-064 | Body: "Partially superseded by ADR-064" — tier no longer determines schema topology |
| ADR-012 | row-level-isolation | ADR-064 | Body: "Fully superseded by ADR-064" |
| ADR-015 | provisioning-per-tier | ADR-064 | Body: "Partially superseded by ADR-064" |
| ADR-016 | tier-upgrade-migration | ADR-064 | Body: "Fully superseded by ADR-064 — no upgrade migration needed" |
| ADR-026 | audit-event-granularity | ADR-260 | ADR-260 generic-diff replaces per-event templates (in spirit; not explicit) |
| ADR-051 | psp-adapter-design | ADR-098 | ADR-098 body: "ADR-051 (original PaymentProvider design, superseded by this ADR)" |
| ADR-063 | compliance-packs-bundled-seed-data | ADR-240 | Unified pack catalog supersedes earlier seed bundling (in spirit) |
| ADR-106 | template-clause-placeholder-strategy | ADR-123 | Replaced by Tiptap clauseBlock as source-of-truth |
| ADR-182 | terminology-override-mechanism | ADR-185 | Body: explicit `Status: Superseded by ADR-185` |

---

## Stale ADRs

Decision was made but the relevant feature was abandoned, never built, or replaced without an explicit supersession marker. Module pages should not link to these.

| ADR | Title | Why stale | Action |
|---|---|---|---|
| ADR-010 | billing-integration | Plan-tier era; no plan-tier subs per `project_no_plan_subscriptions` | Could be marked Stale in-file in future maintenance pass |
| ADR-013 | plan-state-propagation | Plan-tier era | Could be marked Stale in-file in future maintenance pass |
| ADR-014 | plan-enforcement | Plan-tier era | Could be marked Stale in-file in future maintenance pass |
| ADR-033 | local-only-thymeleaf-test-harness | Rendering uses Tiptap (`feedback_tiptap_not_thymeleaf`); harness retired | Leave as historical |
| ADR-139 | keycloak-org-role-mapper | Status: Proposed; superseded in spirit by ADR-178 + ADR-180 | Could be marked Stale in-file in future maintenance pass |
| ADR-156 | invitation-role-gap-mitigation | Status: Proposed; superseded by ADR-179 PendingInvitation | Could be marked Stale in-file in future maintenance pass |
| ADR-219 | subscription-state-machine-design | Conflicts with no-plan-tier reality | Could be marked Stale in-file in future maintenance pass |
| ADR-222 | trial-and-grace-expiry-detection | Plan-tier era; trial/grace not enforced | Could be marked Stale in-file in future maintenance pass |

---

## Informational ADRs

These ADRs are context-setting / methodology notes — they record discussion or migration plans, not enforced decisions.

| ADR | Title | Why informational |
|---|---|---|
| ADR-122 | content-migration-strategy | One-time migration plan; not currently enforced |
| ADR-183 | qa-methodology-vertical-readiness | QA methodology doc, not a decision in code |
| ADR-218 | naming-migration-heykazi | Status: Proposed; ongoing rename per memory — treated Active for linking but informational in nature |

---

## Notes on coverage

- **ADR-100 series clusters around payments** because Phase 25 introduced the PSP layer (ADR-098 PaymentGateway interface, ADR-099 webhook tenant identification, ADR-100 payment-link lifecycle, ADR-101..103 tax).
- **ADR-260+ series clusters around audit** because Phase 65 reshaped audit from per-event templates (ADR-026) to generic diff (ADR-260). ADR-026 is functionally retired, but no explicit `Status: Superseded` marker was applied.
- **Phase 71 ADRs (272–279) form a tight family** — all eight relate to the Xero accounting integration. ADR-274 carves out an explicit exception to ADR-145 (rule engine). The accounting module page should list all eight together.
- **T-series (T001–T008) is a foundation overlay** — most T-series ADRs duplicate or generalise an existing numbered ADR (T001↔064, T005↔030, T006↔034, T007↔143). They are canonical for tenancy/identity/runner concerns and should be linked from a "Foundations" section on relevant module pages.
- **Plan-tier era is a quiet graveyard.** ADR-010, 013, 014, 219, 222 all encode subscription/plan-tier mechanics that have been abandoned. None carries an explicit "Stale" marker.
- **Identity has two-layer implicit supersession.** ADR-139 (KC role mapper) and ADR-156 (KC invitation role gap) are both functionally retired by ADR-178/179/180 without explicit markers.
- **Status unclear / revisit candidates:** ADR-218 (naming migration — Status: Proposed but ongoing), ADR-145 vs ADR-274 (rule engine canonical with one carve-out — confirm no further carve-outs land), and the eight Stale candidates above. A future maintenance pass could retro-stamp `Status: Withdrawn` or `Status: Superseded` lines into these files for grep-ability.
- **Explicit `Status:` markers are rare.** Only ADR-185↔ADR-182 carries a fully-explicit `Status: Superseded by ADR-185` line. All other supersession is recorded only inline in the body of the superseding ADR or inferred from feature reality.
- **Three "deprecated" mentions are red herrings.** ADR-111, ADR-145, ADR-203, ADR-204, ADR-248 use the word "deprecated" in their bodies to refer to internal sub-concepts, not to retire the ADR itself. They remain Active.

---

## How to use this index

- **Module pages should link only to Active ADRs** from this index. Do not link to Superseded, Stale, or Informational entries from a "currently in force" section.
- **When an ADR's decision is questioned, check this index first.** If Active here, the decision still holds — don't re-litigate from the original ADR title alone, since many older ADRs sit in chains where a later ADR is canonical.
- **If a new ADR supersedes an older one, update this overlay** in the same PR: move the older ADR from the Active topic cluster to the Superseded section, and add the new ADR to the appropriate Active cluster. The ADR file itself does not need to move.
- **Do not edit ADR files to record status here.** ADR files at `adr/` are not moved or modified by this index. This is the single source of truth for status; the ADR files remain the source of truth for *content*.
- **For canonical-vs-ancestor questions,** prefer the canonical ADR called out at the top of each cluster. Module pages can mention an ancestor for historical context but should hyperlink the canonical.
