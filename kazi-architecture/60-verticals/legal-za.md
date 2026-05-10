# Legal (South Africa)

**Status:** filled (Phase D part 2). The deepest of Kazi's vertical overlays — every cross-cutting concern threads through this profile somewhere.
**See also:** [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) (the four-mechanism + nine-layer cross-cutting page), [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) (loader/registry/reconciler), [`30-modules/trust-accounting.md`](../30-modules/trust-accounting.md) (the worked example), [`60-verticals/base.md`](./base.md) (universal-core baseline this overlay sits on top of), [`60-verticals/seeds-and-packs.md`](./seeds-and-packs.md) (per-pack inventory).

---

## 1. Profile ID and scope

- **Profile ID:** `legal-za` `→ backend/src/main/resources/vertical-profiles/legal-za.json:2`.
- **Region:** South Africa. Locale `en-ZA`, currency `ZAR`, default tax `VAT 15%` (`legal-za.json:6,7,15-17`).
- **Vertical type:** **vertical** (heavy overlay) — not pack-only. This is the only profile that activates a *vertical-specific bounded context* (trust accounting `→ backend/.../verticals/legal/trustaccounting/`) and the only profile that pulls in five vertical-only entity sub-packages (trust, tariff, conflict-check, court-calendar, disbursement, closure, statement). Every other profile is universal-core + terminology + packs.
- **Practising firms:** South African attorneys' practices and conveyancing practices regulated by the **Legal Practice Council** under the **Legal Practice Act**, with **Legal Practitioners' Fidelity Fund (LPFF)** statutory interest obligations. The product anchors on this regulator: trust money, FICA, LSSA tariffs, and Section 86 are all *that body's* concerns, not generic "legal SaaS" features.
- **Module-gate slug** the profile activates as a vertical: nine module slugs declared in `legal-za.json:5` — `court_calendar, conflict_check, lssa_tariff, trust_accounting, disbursements, matter_closure, deadlines, information_requests, bulk_billing` (the last two are horizontals declared by floor; see [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.2 on profile-floor vs admin-toggle precedence).

---

## 2. Packs installed by this profile

The profile JSON's `packs` block (`legal-za.json:8-14`) drives five direct-seeder pack types plus two unified pack types (DOCUMENT_TEMPLATE, AUTOMATION_TEMPLATE — see [`packs.md`](../30-modules/packs.md) §1 for the unified-vs-legacy split). Cross-link [`60-verticals/seeds-and-packs.md`](./seeds-and-packs.md) for the full per-pack contents inventory.

### 2a. Field packs (auto-applied via ADR-092)

- **`legal-za-customer`** (`field-packs/legal-za-customer.json`) — group `legal_za_client`, auto-applied to Customer entity. Fields: `id_passport_number, postal_address, preferred_correspondence (EMAIL|POST|HAND_DELIVERY), referred_by`. (`autoApply: true`, line 10.) Does *not* directly carry FICA risk-rating or beneficial-ownership fields — those live on the FICA *compliance* pack's checklist (§2b).
- **`legal-za-project`** (`field-packs/legal-za-project.json`) — group `legal_za_matter`, auto-applied to Project entity. Fields: `case_number, court_name, opposing_party, opposing_attorney, advocate_name, date_of_instruction, estimated_value`.
- **`conveyancing-za-project`** — conveyancing-specific Project-entity fields (deeds-office, bond, transfer details). Profile-affinity-locked to `legal-za` (per ADR-184).

### 2b. Compliance packs (FICA + onboarding)

- **`fica-kyc-za`** — base FICA KYC pack (`compliance-packs/legal-za-individual-onboarding/pack.json` — individual FIC Act s21A checklist; the trust variant is `legal-za-trust-onboarding`). Items: certified ID, proof of address, source of funds, sanctions screening, FICA risk assessment.
- **`legal-za-trust-onboarding`** — checklist `legal-za-trust-client-onboarding` (`compliance-packs/legal-za-trust-onboarding/pack.json:11`). 12 items: trust deed, Master's Office Letters of Authority, trustee IDs, proof of trust banking, SARS trust tax number, beneficial-ownership declaration (FIC Act s21A(2)(b), line 70), source of funds, engagement letter, conflict check performed, FICA risk assessment, sanctions screening. `autoInstantiate: true`.

### 2c. Template packs (DOCUMENT_TEMPLATE — unified pipeline)

- **`legal-za`** (`template-packs/legal-za/pack.json` + 9 individual Tiptap templates):
  - `engagement-letter-general` — LSSA-aligned engagement letter.
  - `letter-of-demand` — pre-litigation demand.
  - `statement-of-account` — fee + disbursement statement on demand (Phase 67 §67.4).
  - `founding-affidavit` — motion-court founding affidavit boilerplate.
  - `power-of-attorney`, `power-of-attorney-transfer` — POA + transfer POA.
  - `bond-cancellation-instruction`, `offer-to-purchase` — conveyancing.
  - `section-35-cover-letter` — deceased-estate Section 35 (Administration of Estates Act).

### 2d. Clause packs

- **`conveyancing-za-clauses`** (`clause-packs/legal-za-clauses/pack.json`) — standard SA conveyancing clauses: limitation of liability, jurisdiction, mandate scope, voetstoots, suspensive conditions. Profile-locked.

### 2e. Project-template packs (matter blueprints)

`project-template-packs/legal-za.json` — five matter templates seeded as `ProjectTemplate` rows:

- **Litigation (Road Accident Fund — RAF)** — 8 phases including RAF1 claim, statutory medicals, RAF tariff correspondence, settlement, **prescription monitoring (3-year claim, 5-year damages)** (line 78).
- **Litigation (Personal Injury / General)** — 9 phases: consult → demand → summons → plea → discovery → pre-trial → trial → taxation → execution.
- **Deceased Estate Administration** — 9 phases: death cert → Master report → Letters of Executorship → Gazette ad → inventory → estate bank account → L&D account → lodge → distribute.
- **Conveyancing — Sale & Transfer** (added via Phase 67 conveyancing pack).
- **General matter** — fallback empty template.

### 2f. Request-template packs

- **`fica-onboarding-pack`** — FICA document request bundle for new clients.
- **`conveyancing-intake-za`** — conveyancing intake (deeds, bond, ID, marriage status).
- **`legal-za-liquidation-distribution`** — L&D account documentation request bundle (`request-packs/legal-za-liquidation-distribution.json`).

### 2g. Rate packs (LSSA tariff snapshots)

LSSA tariffs are seeded by `LegalTariffSeeder` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/LegalTariffSeeder.java` into `TariffSchedule` + `TariffItem` rows (not via the unified pack pipeline — pre-dates ADR-240). Snapshot semantics are an **open question** (§9 below).

### 2h. Automation packs

`automation-templates/legal-za.json` ships five legal-tuned automation rules:

- **Matter Onboarding Reminder** (line 8) — 7-day post-create reminder if engagement letter unsent.
- **Engagement Letter Follow-up** (line 30) — 5-day chase if SENT but not ACCEPTED.
- **Investment Maturity Reminder** (line 52) — `triggerType: FIELD_DATE_APPROACHING` 30 days before `TrustInvestment.maturityDate`.
- **Reconciliation Overdue Reminder** (line 78) — `FIELD_DATE_APPROACHING` against last reconciliation date.
- **Pending Approval Aging** (line 106) — `FIELD_DATE_APPROACHING` for trust transactions stuck in `AWAITING_APPROVAL`.

There is **no** generic "prescription-watch" automation today — prescription tracking is its own first-class entity (`PrescriptionTracker` `→ verticals/legal/courtcalendar/PrescriptionTracker.java`) with a dedicated reminder job (`CourtDateReminderJob.java`), not a `FIELD_DATE_APPROACHING` automation rule. Cross-link [`automation.md`](../30-modules/automation.md) for the trigger taxonomy.

---

## 3. Terminology overrides (`terminologyNamespace = en-ZA-legal`)

Backend stores only the namespace key (`OrgSettings.terminologyNamespace = "en-ZA-legal"` `→ OrgSettings.java:184`); the frontend owns the actual word-map at `frontend/lib/terminology-map.ts:44-90` (legal-za branch). Every override below is a *UI-rendering* rename — the underlying entity, table, and REST path stays canonical (per [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §2c). Cross-link `glossary.md` for canonical-term anchors.

| Canonical | Legal-ZA UI label | Glossary anchor |
|---|---|---|
| Customer | **Client** | `frontend/lib/terminology-map.ts:45` |
| Project | **Matter** | `terminology-map.ts:45-46` (Project→Matter, Projects→Matters) |
| Task | **Action Item** | `terminology-map.ts:49-50` |
| Invoice | **Fee Note** | `terminology-map.ts:67-68` |
| Expense | **Disbursement** | `terminology-map.ts:71-72` (UI-only; entity is the legal-only `LegalDisbursement` *sibling* — see §6) |
| Retainer | **Mandate** | `terminology-map.ts:79-80` |
| Rate Card / Billing Rate | **Tariff Schedule** | `terminology-map.ts:65-66` |
| Audit Log | **Audit Trail** | `terminology-map.ts:91` |
| Budget | **Fee Estimate** | (per glossary; legal-za UI label) |

> **Important:** in `legal-za` UI the canonical entity remains `Project` — there is no `Matter` JPA class. The renamed UI label is rendered through `t("Project")`. Routes, controller paths, and JSON keys all stay `Project`/`projects`. The same rule applies to every override above. (See [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §2c for why backend stores only the namespace key.)

The dashboard-widget renames (`terminology-map.ts:83-85`) — Active Projects→Active Matters, Project Health→Matter Health, Project Profitability→Matter Profitability — are pure copy and follow the same mechanism.

---

## 4. Modules gated ON

Nine module slugs are seeded into `OrgSettings.enabledModules` by the reconciler from `legal-za.json:5`:

| Slug | Category | What it lights up |
|---|---|---|
| `trust_accounting` | VERTICAL | Whole `verticals/legal/trustaccounting/` package (six service classes, nine-layer defence, ADR-276 export guard). Cross-link [`trust-accounting.md`](../30-modules/trust-accounting.md). |
| `lssa_tariff` | VERTICAL | `TariffService` + `TariffSchedule`/`TariffItem` (`tariff/TariffService.java:23` MODULE_ID). |
| `court_calendar` | VERTICAL | `CourtCalendarService` (`courtcalendar/CourtCalendarService.java:31`) + `PrescriptionTrackerService` (`courtcalendar/PrescriptionTrackerService.java:28` — same `court_calendar` slug). |
| `conflict_check` | VERTICAL | `ConflictCheckService` (`conflictcheck/ConflictCheckService.java:29`) + `AdversePartyService` (`conflictcheck/AdversePartyService.java:30`). |
| `disbursements` | VERTICAL | `DisbursementService` (`disbursement/DisbursementService.java:60` MODULE_ID) + `StatementService` (`statement/StatementService.java:62` — same `disbursements` slug, intentional reuse per Phase 67). |
| `matter_closure` | VERTICAL | `MatterClosureService` (`closure/MatterClosureService.java:76` MODULE_ID) — pre-closure gate evaluator (trust balance, unbilled time, open conflict checks). |
| `retainer_agreements` | VERTICAL | Retainer (`Mandate`) lifecycle gates. |
| `deadlines` | VERTICAL | Regulatory-deadlines subset relevant to litigation/conveyancing (subset of horizontal `regulatory_deadlines` per [`vertical-profiles.md`](../30-modules/vertical-profiles.md) registry). |
| `information_requests` | HORIZONTAL (declared by floor) | FICA + intake request packs depend on this module's machinery (see [`information-requests.md`](../30-modules/information-requests.md)). Profile-floor — admin cannot durably disable on a legal tenant. |
| `bulk_billing` | HORIZONTAL (declared by floor) | Tariff-driven bulk fee-note generation. Profile-floor. |

The two `(declared by floor)` rows are the open-question case from [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.2 — the reconciler re-adds them on every boot, making any admin "disable" non-durable.

Capabilities (orthogonal to module slugs, per ADR-212) added by this profile's domain:
- **Trust:** `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` (last is `OWNER_ONLY`) — `Capability.java:20-24`.
- **Legal generic:** `VIEW_LEGAL`, `MANAGE_LEGAL` — `Capability.java:17-18`.
- **Disbursements (Phase 67):** `MANAGE_DISBURSEMENTS`, `APPROVE_DISBURSEMENTS`, `WRITE_OFF_DISBURSEMENTS` — `Capability.java:27,29,33`.
- **Closure / statements (Phase 67):** `CLOSE_MATTER`, `OVERRIDE_MATTER_CLOSURE`, `GENERATE_STATEMENT_OF_ACCOUNT` — `Capability.java:33-37`.

---

## 5. Modules gated OFF

The profile does **not** declare these module slugs; absent from `enabledModules`, every `requireModule(...)` call from the corresponding service throws `ModuleNotEnabledException` (HTTP 403):

- `resource_planning` — consulting-only horizontal (default-on for `consulting-za` only, per `VerticalModuleRegistry.defaultEnabledFor`). Capacity widgets self-gate per ADR-246.
- `period_close` — accounting-only operational close. Cross-link [`60-verticals/accounting-za.md`](./accounting-za.md). (Note: this is *not* the same as `matter_closure`, which is legal-only.)

There are no profile-level "deny" mechanics — exclusion is by absence, not by negative declaration. (Per ADR-191 the *tables* exist in every schema regardless; only the runtime gate is profile-driven.)

---

## 6. Vertical-specific entities or extensions

All packages under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/`:

### 6a. `verticals/legal/trustaccounting/` — the regulatory core

The full entity inventory lives in [`trust-accounting.md`](../30-modules/trust-accounting.md) §2 (don't duplicate). Headlines:

- `TrustAccount`, `TrustAccountType` (`GENERAL`/`INVESTMENT`/`SECTION_86`), `TrustAccountStatus`.
- `TrustTransaction` + 10 transaction-type variants (DEPOSIT, PAYMENT, TRANSFER, FEE_TRANSFER, REFUND, REVERSAL, INTEREST_ALLOCATION, LPFF_PAYOUT, INVESTMENT_PLACEMENT, DISBURSEMENT_PAYMENT).
- `BankStatement` + `BankStatementLine` + `TrustReconciliation` (sub-package `reconciliation/`).
- `ClientLedgerCard` (sub-package `ledger/`).
- `LpffRate` (sub-package `lpff/`) — effective-dated LPFF interest rate per trust account.
- `InterestRun` + `InterestAllocation` (sub-package `interest/`).
- `TrustInvestment` + `InvestmentBasis` (sub-package `investment/`) — Section 86 mandates.

### 6b. `verticals/legal/tariff/` — LSSA tariffs

- `TariffSchedule`, `TariffItem` `→ verticals/legal/tariff/TariffSchedule.java`, `TariffItem.java`.
- `TariffService` `→ verticals/legal/tariff/TariffService.java:23` (MODULE_ID `lssa_tariff`).
- `LegalTariffSeeder` `→ verticals/legal/tariff/LegalTariffSeeder.java` — seeds LSSA snapshots.

### 6c. `verticals/legal/conflictcheck/` — conflict-check workflow

- `AdverseParty`, `AdversePartyLink` `→ verticals/legal/conflictcheck/AdverseParty.java`, `AdversePartyLink.java`.
- `ConflictCheck` `→ verticals/legal/conflictcheck/ConflictCheck.java`.
- `AdversePartyService` (`AdversePartyService.java:30`, MODULE_ID `conflict_check`) and `ConflictCheckService` (`ConflictCheckService.java:29`, same slug).

### 6d. `verticals/legal/courtcalendar/` — court dates + prescription tracker

- `CourtDate` `→ verticals/legal/courtcalendar/CourtDate.java`.
- `PrescriptionTracker` `→ verticals/legal/courtcalendar/PrescriptionTracker.java` — first-class prescription entity (3-year delict, 5-year damages, 30-year SLA debt etc.); `PrescriptionRuleRegistry.java` codifies the rule set.
- `CourtCalendarService` (`CourtCalendarService.java:31`) + `PrescriptionTrackerService` (`PrescriptionTrackerService.java:28`).
- `CourtDateReminderJob.java` — scheduled job for upcoming-court-date alerts.

### 6e. `verticals/legal/disbursement/` — sibling of Expense (Phase 67)

- `LegalDisbursement` `→ verticals/legal/disbursement/LegalDisbursement.java` — **sibling** of generic `Expense` per ADR-247 (not a subtype). Pass-through VAT, no markup, pre-bill approval.
- `DisbursementCategory` (SHERIFF_FEES, COUNSEL_FEES, SEARCH_FEES, DEEDS_OFFICE_FEES, COURT_FEES, ADVOCATE_FEES, EXPERT_WITNESS, TRAVEL, OTHER), `VatTreatment` (STANDARD_15, ZERO_RATED_PASS_THROUGH, EXEMPT), `DisbursementApprovalStatus`, `DisbursementBillingStatus`.
- `DisbursementPaymentSource` `→ verticals/legal/disbursement/DisbursementPaymentSource.java` — sibling-port pattern (ADR-279): `OFFICE_ACCOUNT | TRUST_ACCOUNT`. When `TRUST_ACCOUNT`, `DisbursementService` creates a linked `TrustTransaction(DISBURSEMENT_PAYMENT)`.
- `DisbursementService` (`DisbursementService.java:60`, MODULE_ID `disbursements`).

Cross-link [`expenses.md`](../30-modules/expenses.md) for the sibling-entity rationale.

### 6f. `verticals/legal/closure/` — matter-closure gate (Phase 67)

- `MatterClosureLog` `→ verticals/legal/closure/MatterClosureLog.java` — per-attempt audit row (closure can be repeated after reopen).
- `ClosureGate` (interface) + `gates/*` (implementations: zero-trust-balance gate, no-unbilled-time gate, no-open-conflict-check gate, etc.) — pluggable gate registry per Phase 67 §67.3.4.
- `MatterClosureService` (`MatterClosureService.java:76`, MODULE_ID `matter_closure`); cross-cuts `RetentionPolicy` (Phase 50) — closure starts the regulatory retention clock per ADR-249.

### 6g. `verticals/legal/statement/` — Statement of Account (Phase 67)

- `StatementService` (`StatementService.java:62`, MODULE_ID `disbursements` — reused intentionally) + `StatementOfAccountContextBuilder` (Tiptap context builder for the `statement-of-account` template).

### 6h. Invoicing extensions

- `InvoiceLine.lineSource = DISBURSEMENT` value + `disbursementId` FK (Phase 67). Cross-link [`invoicing.md`](../30-modules/invoicing.md).
- `Invoice.customFields["is_trust_invoice"]` flag — drives `TrustBoundaryGuard` refusal (ADR-276).

---

## 7. Source material

- **Discovery:** `_discovery/A6-cross-cutting.md` § Multi-vertical mechanism (§§237–247 — the nine-layer trust defence).
- **Phase docs:**
  - `architecture/phase55-legal-foundations.md` — court calendar, conflict check, LSSA tariff foundation.
  - `architecture/phase60-trust-accounting.md` — trust ledger, dual approval, reconciliation, interest, Section 86.
  - `architecture/phase64-legal-vertical-qa.md` — terminology fix, 4 matter templates (RAF / PI / Estate / General), 90-day law-firm lifecycle audit (5 code fixes L-22/L-29/L-37/L-61/L-64).
  - `architecture/phase67-legal-depth-ii.md` — `LegalDisbursement`, `MatterClosureLog`, Statement of Account, conveyancing pack.
  - `architecture/phase71-xero-accounting-integration.md` §11.6 — `TrustBoundaryGuard` hard guard (ADR-276).
- **Active ADR clusters** (full list in [`90-adr-index.md`](../90-adr-index.md), legal/trust topics):
  - **ADR-T-series foundations** — court calendar, conflict, tariff structure (Phase 55 cluster).
  - **ADR-230..235** — trust accounting core: double-entry ledger, negative-balance prevention, configurable dual authorisation, bank-rec matching, daily-balance interest method, statutory-vs-configurable LPFF share.
  - **ADR-238** — entity-type varchar vs enum (cross-cuts).
  - **ADR-247** — legal-disbursement-sibling-entity.
  - **ADR-248** — matter-closure-distinct-state-with-gates.
  - **ADR-249** — retention-clock-starts-on-closure.
  - **ADR-272..279** — Phase 71 / Xero accounting cluster: Xero-only adapter, one-way sync, dedicated sync service, OAuth2 augmentation, **ADR-276 trust-accounting-hard-guard-export** (the legal-relevant load-bearing one), poll-over-webhooks, idempotent push, sibling payment-source port.

---

## 8. The nine-layer trust defence (legal-anchored recap)

This vertical *is* the nine-layer defence — every layer below is here because the **Legal Practice Council audits these books** and a violation is a criminal/regulatory event, not a bug. Quoting [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §3 with the legal-vertical anchors:

1. **Profile registry** — `legal-za.json:5` is the **only** profile whose `enabledModules` array contains `"trust_accounting"`. Every other profile leaves the slug absent.
2. **Backend service gates** — six trust services, every public method calling `verticalModuleGuard.requireModule("trust_accounting")`: `TrustAccountService.java:26`, `TrustTransactionService.java:45`, `TrustReconciliationService.java:43`, `ClientLedgerService.java:28`, `interest/InterestService.java:41`, `investment/TrustInvestmentService.java:36`.
3. **Backend invoice export hard guard** — `TrustBoundaryGuard.evaluate(invoice)` runs **inside** `AccountingSyncService` before any Xero push (ADR-276; Phase 71 lines 793–795). Three refusal conditions: trust-flagged invoice, line item from a trust account, customer with active trust balances. **Fail-closed on DB error** (Phase 71 line 799). On refusal, sync entry `state=BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust`. *"No bypass, no override, no configuration. Fails closed."* (ADR-276:51.) **The single most load-bearing layer for the regulatory boundary.**
4. **Frontend nav gate** — `frontend/lib/nav-items.ts` declares trust nav with `requiredModule: "trust_accounting"`; `filterNavItems()` hides on non-legal tenants.
5. **Frontend page server gate** — `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` calls `isModuleEnabledServer("trust_accounting")` and short-circuits.
6. **Frontend capability gate** — `<RequiresCapability cap="VIEW_TRUST">`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` (`Capability.java:20-24`); the last is `OWNER_ONLY`-gated by default.
7. **Portal nav gate** — `portal/lib/nav-items.ts:43` declares trust nav with **both** `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]` (double-keyed).
8. **Portal page redirect** — `portal/app/(authenticated)/trust/page.tsx` checks `ctx.enabledModules` and `router.replace("/home")` on miss.
9. **Portal data endpoints** — `customerbackend/service/PortalTrustLedgerService.java:31` returns **404** (not 403) so the module's *existence* is hidden from portal callers.

A6 §247 verdict, quoted: *"Nine layers. Most are belt-and-braces UX defenses; the load-bearing ones are #2 (backend service gate) and #3 (the export guard)."*

The line is drawn at the **integration boundary**, not the domain boundary — trust invoices can be *created* in Kazi (legal vertical's whole point), they just cannot *leave* the system into a general-ledger that has no concept of statutory segregation. This is why the legal-za profile is the highest-stakes vertical and why the profile-switch fragility (§9.1) is the dominant operational risk.

---

## 9. Open questions / known fragility

### 9.1 Profile-switch fragility (the critical risk)

> The single most important architectural risk in the codebase, instantiated for legal-za. Read [`multi-vertical.md`](../20-cross-cutting/multi-vertical.md) §5 and [`vertical-profiles.md`](../30-modules/vertical-profiles.md) §10.1 first; this section adds the legal-specific consequences.

`VerticalProfileReconciliationSeeder` is **adds-only by design** (ADR-192 / ADR-244). Switching `legal-za → consulting-generic` via `PATCH /api/settings/vertical-profile`:

- Sets `OrgSettings.verticalProfile = "consulting-generic"` and switches `terminologyNamespace`. ✓
- Reconciler runs against the new profile's `enabledModules: []` → **no modules added, none removed**. Every legal slug (`trust_accounting`, `lssa_tariff`, `court_calendar`, `conflict_check`, `disbursements`, `matter_closure`, `retainer_agreements`, `deadlines`, …) **remains** in `enabledModules`.
- All six trust service guards still pass. `AdversePartyService`, `CourtCalendarService`, `TariffService`, `DisbursementService` all still pass.
- Trust accounts, transactions, client-ledger cards, LPFF rates, interest runs, Section 86 investments, FICA `field_definition` rows, conflict-check rows, tariff schedules, legal disbursements, matter-closure logs — all remain in the schema (per ADR-191 tables exist in every schema regardless of profile).
- Legal document templates, conveyancing clauses, FICA checklist templates remain installed; `pack_install` rows persist.
- UI hides everything (nav gate, page gate, `<ModuleGate>`) — **but the data is intact and the API is still reachable** to any caller that knows the path.

**Why it matters specifically for legal-za:** trust ledger data is **regulatory**. A profile-switch that orphans trust tables loses LPFF / LPC audit-trail accessibility through the canonical UI; the data is intact but no longer surfaceable through controllers. Critically, the export hard guard (§8 layer 3) **still runs on every sync attempt forever** — it has no module-presence check, only a data-presence check, so a "switched-away" legal tenant with non-zero trust balances continues to refuse Xero pushes silently. Switching `legal-za` *out* with non-zero trust balances is therefore not merely awkward — **it is a fiduciary problem**.

**Mitigations** (none implemented; no ADR yet):
- (a) `VerticalProfileDrainSeeder` that runs only on profile change (not on boot) and computes the symmetric difference.
- (b) Trapdoor: refuse profile-out if any `TrustAccount` exists; require guided export-then-disable.
- (c) Treat profile switches as create-a-new-tenant operations — i.e. don't advertise the switch.

Operational stance today: **(c)**. The system is **one-way safe** (consulting → legal lights everything up via boot reconciler) but **reversible-dirty**.

### 9.2 Section 86 dual-approval enforcement granularity

`TrustAccount.requireDualApproval` (boolean) and `paymentApprovalThreshold` (BigDecimal) live per-account (ADR-232). `APPROVE_TRUST_PAYMENT` is the second-leg capability. Open precedence question: if `requireDualApproval=false` but `paymentApprovalThreshold=10000`, does a 9 999.99 payment require dual approval? Code path (`TrustTransactionService`) evaluates threshold-or-flag; the precedence is not explicitly documented and there is no ADR clarification. Recommend an ADR. Cross-link [`trust-accounting.md`](../30-modules/trust-accounting.md) §10.3.

### 9.3 LPFF rate updates mid-period

`LpffRate` rows are effective-dated per trust account. Lookup is `findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc` (`TrustAccountService.java:324`). What happens when a regulator-mandated rate change lands mid-interest-period? The **daily-balance method** (ADR-234) means each day reads the rate effective on that day, so a mid-period change is mathematically well-defined. **Open:** there is no audit warning when a rate change is committed retroactively (effective_from earlier than the most recent posted `InterestRun` end-date) — silently invalidates a posted run's calculation basis. Should be a pre-write check that refuses retro-effective rates if any non-DRAFT `InterestRun` overlaps.

### 9.4 LSSA tariff snapshots: snapshot-at-quote vs live

LSSA tariffs change periodically. `TariffSchedule` + `TariffItem` are seeded by `LegalTariffSeeder` and surfaced via `TariffService`. The product-design question: when a matter quoted on a 2025 tariff is invoiced in 2026 after a tariff revision, does the line-item rate come from the *snapshot at point of quote* (immutable) or the *live tariff* (current)? Today the code reads live `TariffItem` at invoice-line creation — there is no snapshot mechanism on `Project` or `Proposal`. Cross-link [`proposals-acceptance.md`](../30-modules/proposals-acceptance.md) (Engagement Letter rate snapshotting) and [`invoicing.md`](../30-modules/invoicing.md) (line-item rate resolution). Open: should `TariffSchedule.effectiveFrom/effectiveTo` plus a `Project.tariffScheduleId` snapshot column close this? No ADR.

### 9.5 Conflict-check completeness — historical adverse parties

`ConflictCheckService.run(...)` matches a candidate party against `AdverseParty` rows (joined to matters via `AdversePartyLink`). **Open:** does the search span *closed* matters and *anonymized* customers? Anonymization (per [`customer-lifecycle.md`](../30-modules/customer-lifecycle.md)) PII-scrubs at lifecycle status `ANONYMIZED` — a previously-adverse party whose customer record has been anonymized may no longer be findable by name. For regulatory conflict-of-interest purposes the historical link still exists, but the surface is degraded. Recommend a `ConflictCheck.includeAnonymized` mode that searches a non-PII hash column kept past anonymization. Tracked here, no ADR.

### 9.6 Matter-closure gate completeness

`MatterClosureService` runs `ClosureGate` implementations (sub-package `closure/gates/`) and writes a `gateReport` JSONB to `MatterClosureLog` (Phase 67 §67.3.4). Documented gates: zero trust balance, no unbilled time, no open conflict-check, all checklists complete. **Open:** is the gate set extensible by tenant (e.g. firm-specific "all client documents returned" gate)? Today the gate registry is hard-coded Spring beans; a tenant-pack would require code changes. Phase 67 deliberately scoped this out. Tracked.

### 9.7 Statement-document signing and audit trail

Client trust statements (`GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}/statement`) are rendered on demand from `ClientLedgerStatementQuery` — *not* persisted as `GeneratedDocument` rows. Audit log records the download endpoint hit, but there is no permanent record of *what* the client received and *when*. For a regulatory artefact this is thin. Recommend hash-and-store at generation. Cross-link [`trust-accounting.md`](../30-modules/trust-accounting.md) §10.4.

---

**See also:** [`30-modules/trust-accounting.md`](../30-modules/trust-accounting.md) (entity inventory, REST surface, the nine-layer defence in full), [`30-modules/expenses.md`](../30-modules/expenses.md) (LegalDisbursement sibling pattern), [`30-modules/invoicing.md`](../30-modules/invoicing.md) (Fee Note + trust-flagged invoice + Xero export guard), [`30-modules/customer-lifecycle.md`](../30-modules/customer-lifecycle.md) (FICA via field/compliance packs), [`30-modules/proposals-acceptance.md`](../30-modules/proposals-acceptance.md) (Engagement Letter rendering), [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md), [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md), [`adr/ADR-276`](../../adr/ADR-276-trust-accounting-hard-guard-export.md), [`adr/ADR-247`](../../adr/ADR-247-legal-disbursement-sibling-entity.md), [`adr/ADR-279`](../../adr/ADR-279-sibling-payment-source-port.md).
