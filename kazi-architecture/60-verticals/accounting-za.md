# Accounting (South Africa)

**Status:** filled (Phase D part 2).
**See also:** [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md), [`30-modules/packs.md`](../30-modules/packs.md), [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md), [`60-verticals/legal-za.md`](legal-za.md), [`60-verticals/seeds-and-packs.md`](seeds-and-packs.md). Phase docs: [`architecture/phase47-vertical-qa-accounting.md`](../../architecture/phase47-vertical-qa-accounting.md), [`architecture/phase51-accounting-practice-essentials.md`](../../architecture/phase51-accounting-practice-essentials.md), [`architecture/phase71-xero-accounting-integration.md`](../../architecture/phase71-xero-accounting-integration.md).

---

## 1. Profile ID and scope

- **Profile ID:** `accounting-za` `→ backend/src/main/resources/vertical-profiles/accounting-za.json:2`.
- **Region / locale:** South Africa (`"locale": "en-ZA"`, `"currency": "ZAR"`, `→ accounting-za.json:6-7`).
- **Vertical type:** vertical (medium overlay). Smaller fork than `legal-za` (no statutory trust hierarchy, no LSSA tariff entities); larger than `consulting-za` (regulatory deadlines module + AML/SARS field packs + accounting-specific compliance and template packs). Per the vertical-fork strategy memory: "accounting = smallest fork gap" relative to the universal core, which is why phase47 picked it as the first non-legal vertical-QA target (`→ architecture/phase47-vertical-qa-accounting.md` §47.1).
- **Target persona:** small SA accounting practice — registered SARS tax practitioners, SAICA/SAIPA member accountants, and bookkeepers. Phase 47 worked the example as "Thornton & Associates", a 3-person Johannesburg practice (`→ phase47-vertical-qa-accounting.md` §47.1).
- **Tax defaults:** single VAT entry at 15% set as default (`→ accounting-za.json:29-31`). Reconciled into `OrgSettings.taxLabel` by `VerticalProfileReconciliationSeeder.applyTaxDefaults` (`→ vertical-profiles.md` §6.4).
- **Rate-card defaults:** Owner R1500/h, Admin R850/h, Member R450/h (billing); Owner R650, Admin R350, Member R180 (cost) (`→ accounting-za.json:16-28`). Phase 51's `RatePackSeeder` materialises these into `BillingRate` / `CostRate` rows on first profile selection (`→ phase51-accounting-practice-essentials.md` §1, §47.2).

---

## 2. Packs installed by this profile

The profile JSON's `packs` block declares six pack types (`→ accounting-za.json:8-15`). Universal packs install first via `TenantProvisioningService.installPacksViaPipeline(...)` `→ TenantProvisioningService.java:170-189`; profile-specific packs install second; the seven-pack list below is what's accounting-specific.

### 2.1 Field packs (`PackType.FIELD` — direct seeder)

Seeded by `FieldPackSeeder` (legacy direct path — not the unified `PackInstaller`; per [`packs.md`](../30-modules/packs.md) §1).

| Pack ID | Entity | Auto-apply | Anchor / notes |
|---|---|---|---|
| `accounting-za-customer` | `CUSTOMER` | yes (`autoApply: true`) | `trading_as`, `sars_tax_reference` (required), `sars_efiling_profile`, `industry_sic_code`, plus AML/SARS extension fields (`vat_number`, `public_interest_score`, `acct_entity_type`). `→ field-packs/accounting-za-customer.json`. |
| `accounting-za-customer-trust` | `CUSTOMER` | yes | Conditional fields visible when `acct_entity_type == "TRUST"`: `trust_registration_number` (Master's Office IT-number, required), `trust_deed_date` (required), `trust_type`. `→ field-packs/accounting-za-customer-trust.json`. **Note:** `CustomerType.TRUST` here is the legal/tax entity classification (`→ glossary.md:99`) — unrelated to legal-trust-accounting. |
| `accounting-za-project` | `PROJECT` | yes | `tax_year`, `sars_submission_deadline` (DATE — feeds the deadlines module), `assigned_reviewer`, `complexity` (DROPDOWN: SIMPLE/MODERATE/COMPLEX). `→ field-packs/accounting-za-project.json`. |

The `financial_year_end` custom field (referenced by `DeadlineCalculationService`, `→ phase51-accounting-practice-essentials.md` §1) is the load-bearing per-customer anchor for SARS deadline computation. Stored in `Customer.customFields` JSONB, populated through the customer-edit dialog like every other custom field.

### 2.2 Compliance pack (`PackType.COMPLIANCE` — direct seeder)

| Pack ID | Notes |
|---|---|
| `fica-kyc-za` | Same FICA/AML pack used by `legal-za`. Seeded by `CompliancePackSeeder` and instantiated per-customer by `ChecklistInstantiationService` on customer create (`→ customer-lifecycle.md` § Vertical specifics). Provides AML risk assessment and KYC checklists keyed by `customerType`. |

### 2.3 Template pack (`PackType.DOCUMENT_TEMPLATE` — unified `PackInstaller`)

| Pack ID | Templates | Anchor |
|---|---|---|
| `accounting-za` | `engagement-letter-bookkeeping`, `engagement-letter-tax-return`, `engagement-letter-advisory`, `monthly-report-cover`, `statement-of-account`, `fica-confirmation`, `invoice-za` | `→ template-packs/accounting-za/{pack.json + 7 templates}` |

These templates back the SAICA-style engagement-letter flow that Phase 51's `post_create_actions` hook can auto-generate when `RecurringScheduleExecutor` creates an engagement (`→ phase51-accounting-practice-essentials.md` §1). Audit-letter templates are **not** included — audit work is out of scope for the v1 small-firm persona.

### 2.4 Clause pack (`PackType.CLAUSE` — direct seeder)

| Pack ID | Notes |
|---|---|
| `accounting-za-clauses` | Reusable Tiptap fragments referenced by the engagement-letter templates above. `→ clause-packs/accounting-za-clauses/pack.json`. |

### 2.5 Automation pack (`PackType.AUTOMATION_TEMPLATE` — unified `PackInstaller`)

| Pack ID | Notes |
|---|---|
| `automation-accounting-za` | Pack-shipped automation rules covering SARS deadline reminders (`FIELD_DATE_APPROACHING` triggers on `sars_submission_deadline`) and engagement-kickoff post-create-actions. `→ automation-templates/accounting-za.json`. |

### 2.6 Request pack (`PackType.REQUEST` — direct seeder)

| Pack ID | Notes |
|---|---|
| `year-end-info-request-za` | Year-end information-gathering request template for annual financial statements / tax-return engagements. Used both standalone and as the `sendInfoRequest.requestTemplateSlug` target of post-schedule actions (`→ phase51-accounting-practice-essentials.md` §2). |

### 2.7 Project-template, rate, schedule packs (direct seeder, not in profile JSON `packs` block)

These ship as classpath resources alongside the profile but are loaded by their own seeders rather than declared in the profile manifest:

| Resource | Seeder | Purpose |
|---|---|---|
| `project-template-packs/accounting-za.json` | `ProjectTemplatePackSeeder` | Engagement blueprints — annual financial statements, monthly bookkeeping, VAT201 return, income-tax return. |
| `rate-packs/accounting-za.json` | `RatePackSeeder` (Phase 51) | Materialises `rateCardDefaults` into `BillingRate` / `CostRate` rows. |
| `schedule-packs/accounting-za.json` | `SchedulePackSeeder` (Phase 51) | Recommended `RecurringSchedule` configurations, seeded in **disabled/draft state** so admins opt-in (`→ phase51-accounting-practice-essentials.md` §1). |

Per [`packs.md`](../30-modules/packs.md) §1, only `DOCUMENT_TEMPLATE` and `AUTOMATION_TEMPLATE` go through the unified `PackInstaller` API; the other six accounting packs ship via legacy direct-seeder paths and have no uninstall path (ADR-243).

---

## 3. Terminology overrides

Backend stores only the namespace key (`OrgSettings.terminologyNamespace = "en-ZA-accounting"`, declared as `terminologyOverrides` in the profile JSON `→ accounting-za.json:32`); the frontend owns the word map (`→ frontend/lib/terminology-map.ts:20-35`).

| Canonical term | Accounting-ZA UI label | Anchor |
|---|---|---|
| Customer | **Client** | `terminology-map.ts:3` (shared with all three verticals — `→ glossary.md:75`) |
| Project | **Engagement** | `terminology-map.ts:23` (`→ glossary.md:119`) |
| Projects | **Engagements** | `terminology-map.ts:24` |
| Proposal | **Engagement Letter** | `terminology-map.ts:31` (shared with `legal-za` — `→ glossary.md:120`) |
| Proposals | **Engagement Letters** | `terminology-map.ts:32` |
| Rate Card / Billing Rate | **Fee Schedule** | `terminology-map.ts:33` (`→ glossary.md:130`) |
| Rate Cards | **Fee Schedules** | `terminology-map.ts:34` |
| Invoice | (unchanged — **Invoice**) | not overridden — accounting firms call invoices "invoices", not "fee notes". Contrast `legal-za` Invoice→Fee Note. `→ invoicing.md` § Vertical specifics. |
| Period Close | (unchanged — **Period Close**) | retainer-period flow shared with universal core (`→ retainers.md`, glossary `Period Close`); admin-triggered per ADR-072. |

The portal mirror (`portal/lib/terminology-map.ts`) is a duplicated copy — renaming a key requires touching both files (`→ multi-vertical.md` §2c).

---

## 4. Modules gated ON

Per the profile JSON `enabledModules` array (`→ accounting-za.json:3`) plus modules whose `defaultEnabledFor` lists `accounting-za` in the registry (`→ VerticalModuleRegistry.java`):

| Module slug | Category | How enabled | Anchor |
|---|---|---|---|
| `regulatory_deadlines` | VERTICAL | declared in `enabledModules` | `VerticalModuleRegistry.java:85-94` (only profile listed in `defaultEnabledFor` is `accounting-za`). Surfaces `/deadlines` nav. SARS / CIPC filings calendar. |
| `information_requests` | HORIZONTAL | declared in `enabledModules` | `VerticalModuleRegistry.java:147-156` (also `defaultEnabledFor` for legal-za + consulting-za). Required for the year-end info-request flow. |
| `deadlines` (legacy slug in profile JSON) | — | declared in `enabledModules` `→ accounting-za.json:3` | The profile JSON currently writes `["deadlines", "information_requests"]`; the registry slug is `regulatory_deadlines`. **Open question §9.5** — slug drift between JSON and registry. |

**Universal core is always on:** `customer-lifecycle`, `projects`, `tasks`, `time-entry`, `expenses`, `invoicing`, `proposals-acceptance`, `retainers`, `documents-templates`, `automation`, `audit`, `notifications`, `settings-navigation`, `customer-portal`, `identity-access`. None of these are module-gated; they are foundational (`→ multi-vertical.md` §2d).

**Phase 71 cross-cut:** the **`ACCOUNTING` `IntegrationDomain`** (`→ IntegrationDomain.java:4`) is the integration-port plug-point for Xero. It is **not** a vertical module — it is gated by `OrgSettings.isAccountingEnabled()` (per `IntegrationGuardService.requireEnabled(ACCOUNTING)`, `→ IntegrationGuardService.java:38-44`) and is *opt-in for any tenant on any profile*. Accounting-za firms are the primary commercial driver for Phase 71 (`→ phase71-xero-accounting-integration.md` §11.1) but the mechanism is profile-agnostic — a `consulting-za` firm whose bookkeeper lives in Xero connects through the same integration card (`→ integration-ports.md` §7).

**Horizontal modules an admin may toggle on:** `bulk_billing`, `automation_builder`, `resource_planning`. None are profile-defaulted for `accounting-za`; the firm enables them at `Settings → Features` if scale warrants (`→ vertical-profiles.md` §6.2).

---

## 5. Modules gated OFF

Every legal-vertical module is absent from `accounting-za.enabledModules` and not listed in any `defaultEnabledFor` for accounting:

| Module slug | Why off |
|---|---|
| `trust_accounting` | Legal-only. Statutory LPA §86 obligation has no accounting analogue. **Critical:** if an accounting firm also did legal trust work, they would need the legal-za profile, not accounting-za. Mixed practice is unsupported (open question §9.1). |
| `court_calendar` | Legal-only (`VerticalModuleRegistry.java:64-72`). |
| `conflict_check` | Legal-only (`:74-83`). Some accountants do conflict-of-interest checks for AML reasons but the entity model targets matter conflicts. |
| `lssa_tariff` | Legal-only (`:96-105`). LSSA is the Law Society of South Africa — statutory court-tariff schedules. |
| `disbursements` | Legal-only (`:158-168`). Accounting firms use the universal `Expense` model instead — pass-through costs are categorised but not subject to Section 86 trust co-mingling rules. |
| `matter_closure` | Legal-only (`:170-180`). |
| `retainer_agreements` | NOT in `defaultEnabledFor` for accounting-za (`:182-192` lists only `legal-za`, `consulting-za`). **Note:** monthly-bookkeeping retainers are real for accounting firms — open question §9.3. The universal core retainer flow is available; the dedicated `retainer_agreements` module surface is not on by default. |
| `resource_planning` | Horizontal; not default-on. Accounting practices are typically smaller than the 10+ team threshold the module targets (`:107-121`). |

---

## 6. Vertical-specific entities or extensions

**No `verticals/accounting/` Java package exists.** Verified by `find backend/src/main/java -path '*verticals/accounting*'` — only `verticals/legal/` is populated. Accounting differentiation is currently **content-only** — vertical profile JSON, classpath packs, terminology overlay, the `regulatory_deadlines` module's universal-Java implementation. This makes `accounting-za` a textbook ADR-244 (`pack-only-vertical-profiles`) example *with* one supporting module — smaller fork than legal-za but not as minimal as `consulting-za`.

The shared (not accounting-only) entities Phase 51 introduced sit in non-vertical packages:

| Entity | Package | Notes |
|---|---|---|
| `FilingStatus` | `compliance/` (or similar) per `→ phase51-accounting-practice-essentials.md` §2.1 | `(customer_id, deadline_type_slug, period_key)` unique key; lazy-created on first user action (ADR-199). Universal table, populated only by `regulatory_deadlines` flows so non-accounting tenants leave it empty. |
| `RecurringSchedule.postCreateActions` (column) | `schedule/` | JSONB column added by Phase 51 V80 migration (`→ phase51-accounting-practice-essentials.md` §2.2). Universal column; populated only by accounting-flavoured schedule pack entries. |
| `DeadlineTypeRegistry` | static utility, `→ phase51-accounting-practice-essentials.md` §2.3 | Hard-coded SARS / CIPC deadline types in code (not DB) — regulatory constants change rarely and are not tenant-configurable. |

**Phase 71 entities** (`AccountingXeroConnection`, `AccountingSyncEntry`, `AccountingTaxCodeMapping` `→ phase71-xero-accounting-integration.md` §11.2) live under `integration/accounting/` and are also profile-agnostic — they exist in every tenant schema per ADR-191 (schema-uniformity-module-tables) and are populated only when a tenant configures Xero.

---

## 7. Phase 71 Xero integration — accounting-vertical primary unlock

Phase 71 is the first non-trivial accounting-specific integration and is explicitly framed as the **commercial unlock** for `accounting-za`: "small SA accounting practices cannot adopt a system that does not push invoices into the accountant's general ledger" (`→ phase71-xero-accounting-integration.md` §11.1). The seven Phase 71 ADRs are:

| ADR | Pin |
|---|---|
| **ADR-272** — xero-only-accounting-adapter-v1 | Xero is the only v1 adapter; Sage Pastel / QuickBooks deferred. YAGNI until a second tenant asks. |
| **ADR-273** — one-way-accounting-sync-permanent | Kazi → Xero only; bidirectional is **out of scope, not deferred**. |
| **ADR-274** — dedicated-accounting-sync-service-not-rule-engine | `AccountingSyncService` owns sync orchestration — Phase 37 rule engine is for tenant-authored automations only. |
| **ADR-275** — oauth2-augmentation-org-integration | New `AccountingXeroConnection` table augments `OrgIntegration` rather than bloating it with Xero-specific OAuth columns. |
| **ADR-276** — trust-accounting-hard-guard-export | Fail-closed `TrustBoundaryGuard` on the sync pipeline — see below. |
| **ADR-277** — poll-over-webhooks-payment-reconciliation-v1 | Scheduled polling using `AccountingXeroConnection.last_poll_at` cursor — no inbound webhook handler in v1. |
| **ADR-278** — idempotent-push-via-external-reference | `external_reference = "KAZI-INV-{uuid}"` is the idempotency key written to Xero's `Reference` field. |
| **ADR-279** — sibling-payment-source-port | `AccountingPaymentSource` is a sibling port to `AccountingProvider` so noop accounting adapters don't have to implement empty `pullPayments()`. |

### 7.1 Trust hard guard cross-cut (ADR-276)

The `TrustBoundaryGuard` runs **inside `AccountingSyncService` before any Xero call** (`→ phase71-xero-accounting-integration.md` §11.5, lines 793-799), refusing on three conditions: invoice flagged trust, any line item from a trust account, customer has active trust balances. Fail-closed on DB error.

The guard exists to protect the **boundary** even when the accounting vertical pushes invoices. The narrow current scenario — a single tenant runs only one profile at a time, and `legal-za` and `accounting-za` are not co-installed — does **not** make the guard redundant: trust-data tables exist in every schema regardless of profile per ADR-191 (`→ vertical-profiles.md` §10.1), so a profile that ever was `legal-za` and got switched to `accounting-za` would still carry trust tables and balances; without the export guard, those rows could be unintentionally surfaced through a Xero push. See [`integration-ports.md`](../30-modules/integration-ports.md) §6 and [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §3 layer 3 for the canonical write-up.

### 7.2 What this profile gets specifically

- **OAuth2 connect** in `Settings → Integrations → Accounting`. Xero appears as the only registered `AccountingProvider` slug for v1 (`→ integration-ports.md` §2).
- **Auto-push on `InvoiceApprovedEvent` / `InvoiceSentEvent`** through the dedicated `AccountingSyncService` (ADR-274).
- **One-time customer import** from Xero contacts on first connect (`→ phase71-xero-accounting-integration.md` §11.1).
- **Payment pull** via the `AccountingPaymentSource` sibling port — `PaymentEvent` rows of source `XERO_RECONCILE` materialise back into Kazi's AR aging.
- **Tax-code mapping** UI seeded with ZA defaults (`STANDARD_15 → OUTPUT2`, etc.; `→ phase71-xero-accounting-integration.md` §11.2.3).

---

## 8. Source material

- **Discovery:** `_discovery/A6-cross-cutting.md` § Multi-vertical mechanism.
- **Profile JSON:** `→ backend/src/main/resources/vertical-profiles/accounting-za.json`.
- **Pack JSONs:** seven accounting-za pack files under `field-packs/`, `template-packs/accounting-za/`, `clause-packs/`, `automation-templates/`, `project-template-packs/`, `rate-packs/`, `schedule-packs/`.
- **Phase docs:** `phase47-vertical-qa-accounting.md` (vertical profile + 90-day shakeout), `phase51-accounting-practice-essentials.md` (`regulatory_deadlines` module + post-create actions + `RatePackSeeder` + `SchedulePackSeeder`), `phase71-xero-accounting-integration.md` (Xero ADR cluster).
- **Sibling vertical pages:** [`legal-za.md`](legal-za.md), [`consulting-za.md`](consulting-za.md), [`base.md`](base.md), [`seeds-and-packs.md`](seeds-and-packs.md).
- **Cross-cutting:** [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §4 (per-vertical impact map row for accounting-za).
- **Glossary:** `Engagement` (`glossary.md:119`), `Engagement Letter` (`:120`), `Fee Schedule` (`:130`), `Period Close` (referenced via `retainers.md:5`).

---

## 9. Open questions / known fragility

### 9.1 Mixed practice — accounting + legal in one tenant

If a firm does both accounting and legal work (real for many small SA practices that grew sideways), neither `accounting-za` nor `legal-za` covers them. A tenant runs **one** profile; switching from one to the other triggers the adds-only orphan problem (`→ vertical-profiles.md` §10.1, `→ multi-vertical.md` §5). No `legal-and-accounting-za` profile exists; building one would require deciding which terminology overlay wins (Project→Matter or Project→Engagement?) and which compliance pack is canonical. **Currently unsupported; document it, do not solve it.**

### 9.2 Period-close mechanics for accounting

Period close is the universal admin-triggered retainer-period close (ADR-072, `→ retainers.md`, glossary entry). Accounting firms use it for monthly bookkeeping retainers, but the closure is not gated on accounting-specific checks (e.g. "VAT201 submitted before closing the month"). Verify whether accounting firms expect per-profile closure preconditions or are content with the universal flow. No ADR yet.

### 9.3 Retainer agreements module not default-on

`retainer_agreements` lists `defaultEnabledFor: ["legal-za", "consulting-za"]` only (`→ VerticalModuleRegistry.java:182-192`). Monthly bookkeeping retainers are core to accounting practice. Either this is a registry oversight (add `accounting-za` to the list) or the deliberate position is "use the universal retainer flow without the dedicated agreement entity." Phase 51 didn't address this. Flagging.

### 9.4 SARS submission integration scope

Phase 71 is **Xero only** (ADR-272). SARS eFiling is a separate beast — direct submission of VAT201, EMP201, ITR12 returns. The `sars_efiling_profile` field on the customer pack hints at intent but no integration exists. Out of scope through Phase 71+; treat as a future phase (71+n).

### 9.5 `enabledModules` slug drift between JSON and registry

The profile JSON writes `["deadlines", "information_requests"]` (`→ accounting-za.json:3`) but the registry slug is `regulatory_deadlines` (`→ VerticalModuleRegistry.java:86`). One of these is wrong. Either the JSON should be `["regulatory_deadlines", "information_requests"]`, or the registry should accept `"deadlines"` as the slug. The lenient JSON loader (`→ vertical-profiles.md` §10.4) silently accepts the typo'd slug into `OrgSettings.enabledModules`, where every `requireModule("regulatory_deadlines")` will fail-closed. **Likely active defect** — the deadlines module would never gate-pass for accounting-za tenants. Verify against a running tenant before claiming the module surfaces correctly. (Fix would be a one-line JSON edit; flagging as a known fragility, not a fix here.)

### 9.6 Profile-switch fragility

Same as every vertical: the reconciler is adds-only. Switching `accounting-za → consulting-generic` leaves regulatory-deadline data, FICA checklists, and engagement-letter templates orphaned in the schema. Switching back re-merges cleanly. Per `multi-vertical.md` §5, treat profile switches as "create a new tenant." No accounting-specific exemption.

### 9.7 No vertical-specific Java package

`verticals/accounting/` does not exist. If a future feature needs accounting-specific entity hierarchy (e.g. a `VAT201Submission` entity, or AML transaction-monitoring rules with their own state machine), the package boundary precedent set by `verticals/legal/` is the right home. Keeping accounting pack-only is currently fine but is a YAGNI bet that may flip on the next phase.
