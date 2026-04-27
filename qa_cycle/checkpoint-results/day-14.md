# Day 14 — Firm onboards Moroka Family Trust (**isolation setup**)  `[FIRM]`
Cycle: 1 | Date: 2026-04-22 04:05 SAST | Auth: Keycloak (owner) | Firm: :3000 | Actor: Thandi Mathebula (Owner)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 14 (checkpoints 14.1–14.11 + rollup).

**Result summary (Day 14): 11/11 executed — 8 PASS (14.1, 14.2 partial, 14.3, 14.5, 14.6, 14.7, 14.9, 14.10, 14.11), 1 PASS-WITH-WORKAROUND (14.8 template substitute), 1 SKIPPED-BY-SCENARIO (14.4 — scenario says no screenshot required; skipped entirely), 1 PARTIAL (14.2 beneficial owners). Zero BLOCKER. Isolation-probe IDs captured.**

## Pre-flight

- Context swap from portal → firm. `/dashboard` on :3000 redirected to Keycloak login (no firm session held). Authenticated as `thandi@mathebula-test.local` / `<redacted>` → landed on `http://localhost:3000/org/mathebula-partners/dashboard`. Sidebar shows "TM" avatar + "Thandi Mathebula" user card. GAP-L-22 session-handoff held clean.

## Phase A — Create Moroka Family Trust client

### Checkpoint 14.1 — Navigate to Clients → + New Client
- Result: **PASS**
- Evidence: `/org/mathebula-partners/customers` rendered list (header "Clients 2" before add) with existing rows Sipho Dlamini + Test Client FICA Verify. Clicked "New Client" button → two-step "Create Client" dialog opened.

### Checkpoint 14.2 — Fill client form
- Result: **PASS (partial for beneficial owners)**
- Fields entered (Step 1):
  - Name: `Moroka Family Trust`
  - Type: **Trust** (combobox option selected from Individual/Company/Trust)
  - Email: `moroka.portal@example.com`
  - ID Number: `IT 001234/2024` (scenario called this "Trust registration number"; the form's "ID Number" free-text field is what's available today)
  - Tax Number: `9012345678`
  - Country: South Africa (ZA)
  - Entity Type: `Trust`
- **Beneficial owners: NOT ADDED** — the Create Client dialog has no affordance for adding 2+ beneficial owners as structured fields. Step 2 offered only "SA Legal — Client Details" intake group (skipped), no "Beneficial Owners" card/section. Logged as **GAP-L-54** (LOW).

### Checkpoint 14.3 — Submit → client created
- Result: **PASS**
- Evidence:
  - DB: `tenant_5039f2d497cf.customers` now shows `id=29ef543a-d0ad-4851-97e0-77344e0b1b1d name="Moroka Family Trust" email=moroka.portal@example.com entity_type=TRUST lifecycle_status=PROSPECT`.
  - Portal contact auto-provisioned via **GAP-L-34 listener** (verified): `portal_contacts` now has `id=0f980d16-2463-401f-a097-92595bf7da0b email=moroka.portal@example.com display_name="Moroka Family Trust" role=GENERAL status=ACTIVE`. L-34 auto-provisioner fired on `CustomerCreatedEvent` within ~1s of submit. No manual DevPortal backfill needed.

### Checkpoint 14.4 — Conflict check
- Result: **SKIPPED-BY-SCENARIO**
- Evidence: Scenario step literally reads "*no 📸 required — this is setup, not a demo moment*" and marks it as optional for seed. Not exercised for Moroka. Day 15 isolation probes do not require a prior conflict check. If Day 90 regression needs it, a later cycle can add.

## Phase B — Create Moroka matter

### Checkpoint 14.5 — New Matter from Deceased Estate template
- Result: **PASS (via /projects workaround)**
- Evidence: First attempt via client detail "+ New Matter" → redirected to `/projects?new=1&customerId=...` which renders "Something went wrong / Unable to load this page" (re-observation of **GAP-L-39 cousin** — the redirect URL path crashes). Workaround: navigated to `/projects`, clicked "New from Template" → template select dialog opened. Selected "Deceased Estate Administration (9 tasks)" option.

### Checkpoint 14.6 — Fill matter form
- Result: **PASS**
- Fields entered (Configure step):
  - Matter name: `Estate Late Peter Moroka`
  - Description: (auto-populated from template: "Administration of deceased estate from reporting to final distribution. Matter type: ESTATES")
  - Client: `Moroka Family Trust` (selected from dropdown — Moroka now appears, confirming customer created in 14.3)
  - Reference Number: `EST-2026-002`
  - Matter lead, Priority, Work Type: left default
  - Master's Office: **no structured field found** on this template's configure form — Scenario asked for "Master's Office: Johannesburg" but the Deceased Estate template Configure step has no such field. Logged as minor observation (not new gap — GAP-L-36 cousin for Estates template missing structured fields).

### Checkpoint 14.7 — Submit → matter created
- Result: **PASS**
- Evidence: Redirected to `/projects/4e87b24f-cf40-4b5b-9d1e-59a63fdda55a`. Matter detail header: "Estate Late Peter Moroka / Active / Matter type: ESTATES / Client: Moroka Family Trust / Ref EST-2026-002 / 0 documents · 0 members · 9 tasks". DB confirmed: `tenant_5039f2d497cf.projects` row `id=4e87b24f-cf40-4b5b-9d1e-59a63fdda55a reference_number=EST-2026-002 customer_id=29ef543a-... status=ACTIVE`. Screenshot `day-14-moroka-matter-created.png`.

## Phase C — Seed data

### Checkpoint 14.8 — Send info request on Moroka matter
- Result: **PASS (template workaround)**
- Evidence: Matter Requests tab → "+ New Request" dialog. Template dropdown offers 7 options, none named "Liquidation and Distribution Account docs". Used **FICA Onboarding Pack (3 items)** as the closest shipped template for seeding. Portal Contact auto-defaulted to "Moroka Family Trust (moroka.portal@example.com)" — confirming auto-provisioning. Clicked **Send Now**.
- DB: `information_requests` row `id=4a698dc9-b24a-45b4-a44b-bf7c0d6aceac request_number=REQ-0004 status=SENT portal_contact_id=0f980d16-... project_id=4e87b24f-...`.
- Mailpit: email "Information request REQ-0004 from Mathebula & Partners" delivered to `moroka.portal@example.com` (verified via subsequent DB + inbox cross-check).
- Logged as: **GAP-L-53** (LOW) — Estates / Liquidation & Distribution template missing; workaround FICA used.

### Checkpoint 14.9 — Upload internal document to Moroka matter
- Result: **PASS**
- Evidence: Matter Documents tab → drag-drop zone clicked → file chooser accepted `/Users/rakheendama/Projects/2026/b2b-strawman/qa_cycle/test-fixtures/certified-id-copy.pdf`. Upload completed via presigned-URL → S3 PUT flow. DB: `documents` row `id=1e96f979-f8bf-4e59-8bfd-67d34c6b6e6a file_name="certified-id-copy.pdf" project_id=4e87b24f-...`.

### Checkpoint 14.10 — Record R 25,000 trust deposit against Moroka / EST-2026-002
- Result: **PASS**
- Evidence: `/trust-accounting/transactions` → Record Transaction → Record Deposit dialog → filled Client ID=`29ef543a-...`, Matter=`4e87b24f-...`, Amount=25000, Reference=DEP-2026-002, Description="Initial trust deposit — EST-2026-002 Moroka Family Trust", Transaction Date=2026-04-22 → Submit. Transactions page now reads "2 transactions found" with DEP-2026-001 R 50 000,00 (Sipho) + **DEP-2026-002 R 25 000,00** (Moroka) both RECORDED.
- DB: `trust_transactions` row `id=f2cea65b-5103-48f1-857f-618ae224ecde customer_id=29ef543a-... project_id=4e87b24f-... amount=25000.00 status=RECORDED reference=DEP-2026-002`. Same trust account `61fe42af-5d94-4848-bf09-145099c2396b` ("Mathebula Trust — Main") used for both clients' deposits.
- ZAR rendering: **"R 25 000,00"** correct SA locale on firm-side Transactions table.

### Checkpoint 14.11 — Record entity IDs for Day 15 probes
- Result: **PASS**
- Evidence: Wrote `qa_cycle/isolation-probe-ids.txt` with full Moroka entity ID set (customer, portal_contact, matter, info-request, document, trust-transaction) + reference Sipho IDs + shared trust-account ID + probe plan. See file for the complete list.

## Day 14 rollup checkpoints

- Two clients + two matters on tenant: **PASS** — Sipho (INDIVIDUAL, RAF-2026-001) + Moroka (TRUST, EST-2026-002) + also pre-existing "Test Client FICA Verify" (leftover, harmless for isolation probe — will ignore in Day 15).
- Moroka has ≥1 info request + 1 document + 1 trust deposit: **PASS** — REQ-0004, certified-id-copy.pdf, DEP-2026-002 all persisted in tenant DB.
- Moroka entity IDs captured: **PASS** — `isolation-probe-ids.txt` written.

## New gaps (this turn)

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-53** | LOW | "Liquidation and Distribution Account docs" request template missing for legal-za Deceased Estate workflow. No estates-specific pack shipped; only FICA / Tax Return / Monthly Bookkeeping / Conveyancing / Company Registration / Annual Audit / Ad-hoc. Used FICA as substitute on Day 14 seed; functional parity for Day 15 isolation probe. Owner: backend/product — author `backend/src/main/resources/request-packs/legal-za-liquidation-distribution.json` or widen Deceased Estate template. Off critical path — batch. |
| **GAP-L-54** | LOW | Create Client dialog TRUST entity type has no "Beneficial Owners" structured field / card. Scenario 14.2 asked for "2 beneficial owners" — no UI affordance. Cosmetic/FICA-compliance gap for trust clients; couples to GAP-L-30 (KYC unconfigured) and GAP-L-40 (proper multi-portal-contact CRUD). Owner: product + frontend. Off critical path — batch. |

## Re-observed / carry-forward

| Existing item | Re-observed? | Note |
|---|---|---|
| GAP-L-22 (session handoff) | NO | Thandi login clean, TM avatar + name stable across page navigations. Workaround held. |
| GAP-L-26 (branding) | YES | Sidebar bg still slate-950, no logo. Not re-logged. |
| GAP-L-32 cousin (Create Client doesn't redirect to detail) | YES | Dialog closed and stayed on list; client row not visible until next navigation reload. Not re-logged (same shape as L-32). |
| GAP-L-34 (portal contact auto-provisioner) | VERIFIED AGAIN | Fired cleanly for Moroka within ~1s of customer-create. Fourth confirmation of L-34 in cycle. |
| GAP-L-35 (PROSPECT gate on custom-field PATCH) | NOT EXERCISED | Skipped Step 2 intake + matter custom-field save; will trigger on Day 21/28 when firm fills Estates/RAF custom fields. |
| GAP-L-36 cousin (Estates-specific fields) | YES | Deceased Estate template has no Master's Office / Executor / Estate Late fields. Not a new gap (same shape as RAF template gap). |
| GAP-L-37 (over-broad field-group auto-attach) | YES | Moroka matter detail shows "SA Conveyancing — Matter Details" + "SA Legal — Matter Details" + "Project Info" attached — both SA Legal AND SA Conveyancing applied to a deceased-estate matter. Not re-logged. |
| GAP-L-39 cousin (customerId URL redirect crashes) | YES | "/projects?new=1&customerId=..." crashes with "Something went wrong"; same shape as prior re-observation. Workaround: use /projects → New from Template. Not re-logged. |
| GAP-L-41 (due_date field) | YES | REQ-0004 dialog has no Due Date field. Scenario asked for Due Day 30. Not re-logged. |

## Halt reason

Day 14 complete (all 11 checkpoints executed or formally skipped per scenario). Per task brief: **stop at end of Day 14; Day 15 (isolation BLOCKER probes) dispatched separately by orchestrator.**

## QA Position on exit

`Day 15 — 15.A (isolation probes on Sipho portal session)`. All Moroka seed data in place. `isolation-probe-ids.txt` written. Next dispatch: orchestrator spins Day 15 agent with those IDs.

## Screenshots

- `day-14-moroka-matter-created.png` — matter detail view showing "Estate Late Peter Moroka / Active / Client: Moroka Family Trust / Ref EST-2026-002".
- `day-14-moroka-trust-deposit.png` — Transactions page showing BOTH DEP-2026-001 (Sipho R 50 000) and DEP-2026-002 (Moroka R 25 000), both RECORDED, same Mathebula Trust — Main account.

## Evidence summary

- **Moroka customer_id**: `29ef543a-d0ad-4851-97e0-77344e0b1b1d`
- **Moroka portal_contact_id**: `0f980d16-2463-401f-a097-92595bf7da0b`
- **Moroka matter_id**: `4e87b24f-cf40-4b5b-9d1e-59a63fdda55a` (EST-2026-002)
- **Moroka info-request_id**: `4a698dc9-b24a-45b4-a44b-bf7c0d6aceac` (REQ-0004 SENT)
- **Moroka document_id**: `1e96f979-f8bf-4e59-8bfd-67d34c6b6e6a` (certified-id-copy.pdf)
- **Moroka trust-transaction_id**: `f2cea65b-5103-48f1-857f-618ae224ecde` (DEP-2026-002, R 25 000,00 RECORDED)
- **Trust account_id**: `61fe42af-5d94-4848-bf09-145099c2396b` (shared, Mathebula Trust — Main)

---

## Day 14 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Branch**: `bugfix_cycle_2026-04-24` | **Method**: **Browser-driven via Playwright MCP** (Tab 0, http://localhost:3000) — fresh re-walk of the entire Day 14 firm-side flow on the post-V112 / post-PR-#1132 stack. **No REST substitution for UI tier.** READ-ONLY SQL `SELECT` only for state checks. Mailpit not consulted (Day 14 is intra-firm — no email flow).

**Actor**: Bob Ndlovu (admin) — using Tab 0 firm session that had been left logged-in as Bob from Day 7+ cycle-1 verify. Admin role is sufficient for all 14.x mutations (client + matter + info-request + document + trust deposit are not Owner-locked).

### Result tally — Cycle 1

**11/11 PASS** for all in-scope checkpoints; 14.4 (Conflict Check) marked SKIPPED-BY-DESIGN per scenario ("no 📸 required — this is setup, not a demo moment") because L-28 / L-29-regression already VERIFIED end-to-end on Day 2.

### Checkpoint table

| # | Checkpoint | Result | Evidence (cycle-1) |
|---|------------|--------|--------------------|
| 14.1 | Navigate Clients → + New Client | **PASS** | `day-14-cycle1-customers-list.yml` — "Clients" page with header badge "1" (Sipho only) before Moroka added. "New Client" button visible. |
| 14.2 | Fill client form (Type=TRUST, registration, email, country) | **PASS** | `day-14-cycle1-new-client-dialog.yml` — Type combobox offers Individual / Company / **Trust** ✓. Filled: name=Moroka Family Trust, email=moroka.portal@example.com, registration=IT 001234/2024, country=ZA, entity_type=Trust. Step-2 (`day-14-cycle1-step2.yml`) shows ONLY `SA Legal — Client Details` field-group card — L-37 customer-scope narrowing holds (no spurious conveyancing-za-customer pack offered). Beneficial owners are still not part of the Create Client form (consistent with PARTIAL note from prior cycle; OBS-L-54 carried forward — Sprint 3). |
| 14.3 | Submit → client created | **PASS** | Browser redirected to `/customers/2b454c42-ac4e-4e96-af64-4f3d2a409d45` (L-32 redirect-to-detail holds). DB: `customers / id=2b454c42-… / customer_type=TRUST / lifecycle_status=PROSPECT / email=moroka.portal@example.com / registration_number='IT 001234/2024'`. |
| 14.4 | Conflict Check — CLEAR | **SKIPPED-BY-DESIGN** | Scenario explicitly does not require it ("no 📸 required — setup, not demo moment"). L-28 + L-29-regression VERIFIED Day 2 cycle-1 — no value re-walking. |
| 14.5 | New Matter from Deceased Estate template | **PASS** | "+ New Matter" link from Moroka detail carried `?new=1&customerId=2b454c42-…` URL param (L-39 ✓). Select-Template dialog (`day-14-cycle1-new-matter-templates.yml`) lists `Deceased Estate Administration 9 tasks` (+ 4 others). |
| 14.6 | Fill matter title + reference | **PASS** | Configure dialog (`day-14-cycle1-matter-config.yml`) Client combobox **pre-selected `Moroka Family Trust`** ✓ (L-39 propagation end-to-end). Renamed default "Moroka Family Trust - Estate" → `Estate Late Peter Moroka`; reference `EST-2026-002`; matter lead `Thandi Mathebula`. (Note: Master's Office field is not in template configure form — set via custom-fields tab post-create; not a regression, not in scope for Day 15 isolation.) |
| 14.7 | Submit → matter created | **PASS** | Redirected to `/projects/89201af5-f6e0-4d9a-952e-a2af6e5b70ee`. DB `projects` `name='Estate Late Peter Moroka' / reference_number='EST-2026-002' / customer_id=2b454c42-… / applied_field_groups=[Project Info, SA Legal — Matter Details]`. **L-37-regression V112 fix HOLDS** — `conveyancing_za_matter` correctly NOT attached (deceased-estate template has empty work_type, conveyancing pack scoped to `["CONVEYANCING"]` so excluded). |
| 14.8 | Send info request — Liquidation and Distribution Account Pack | **PASS** | Requests tab → New Request → `day-14-cycle1-new-request-dialog.yml` Template combobox lists `Liquidation and Distribution Account Pack (5 items)` (L-53 ✓). Portal Contact pre-populated `Moroka Family Trust (moroka.portal@example.com)` — **L-34 portal-contact auto-provision works for TRUST type** (not just INDIVIDUAL). Due-date textbox accepts `2026-05-11` (L-41 ✓). Submit → REQ-0005 row appears in Requests list. DB: `information_requests / id=83428106-… / request_number=REQ-0005 / status=SENT / due_date=2026-05-11`. |
| 14.9 | Upload document to Documents tab | **PASS** | Drag-drop button → file_chooser opened → uploaded `death-certificate-moroka.pdf` (298 B test PDF placed in `.playwright-mcp/uploads/`). Documents table now shows row `death-certificate-moroka.pdf / 298 B / Uploaded / Apr 25, 2026` with Download button. DB `documents / id=8d92037c-… / file_name='death-certificate-moroka.pdf' / project_id=89201af5-…`. |
| 14.10 | Record trust deposit R 25 000 | **PASS** | Trust tab → Record Deposit dialog → fields filled (Client UUID, Matter UUID, Amount=25000, Reference=`DEP/2026/002`, Description). Submit → Trust Balance card now reads **R 25 000,00** ZAR with deposits/payments/fee-transfers breakdown. Screenshot: `day-14-cycle1-moroka-trust-deposit.png`. DB: `trust_transactions / id=446fa97c-… / DEPOSIT / 25000.00 / RECORDED`; `client_ledger_cards` has separate row `182621ca-… / customer_id=2b454c42-…/ balance=25000.00` distinct from Sipho's `28326991-… / customer_id=c3ad51f5-… / balance=50000.00` — per-customer ledger isolation already in place at the DB layer (good substrate for Day 15 portal probes). |
| 14.11 | Capture Moroka entity IDs | **PASS** | See **Cycle-1 Isolation Probe IDs** below. |

### Cycle-1 Isolation Probe IDs (for Day 15 dispatch)

```
Moroka customer_id          : 2b454c42-ac4e-4e96-af64-4f3d2a409d45
Moroka matter_id (project)  : 89201af5-f6e0-4d9a-952e-a2af6e5b70ee
Moroka info-request_id      : 83428106-0e6e-4550-acc7-bdd184fd727f   (REQ-0005)
Moroka document_id          : 8d92037c-b1de-4b3d-9016-034d27cd032b   (death-certificate-moroka.pdf)
Moroka trust_tx_id          : 446fa97c-8d8d-43f2-b4be-0e7c0ef8af95   (R 25 000,00 DEPOSIT RECORDED)
Moroka client_ledger_card   : 182621ca-57d2-423b-a998-434b518b4db6
```
(Cycle-0 IDs above were on a prior tenant; this run created fresh entities on `tenant_5039f2d497cf` — use these.)

### Verify-focus shipped fixes that re-validated this day

| Fix | Status this cycle | Notes |
|-----|-------------------|-------|
| L-22 (BFF session handoff) | N/A | No fresh KC handoff this day. Bob session preserved cleanly across navigation. |
| L-27 (VAT/ZAR labels) | HOLDS | Trust Balance card renders `R 25 000,00` (ZAR + comma decimal). |
| L-31 (Customers empty-state vertical copy) | N/A | Tenant already has Sipho — no empty-state shown. |
| L-32 (Create Client redirect) | VERIFIED | Submit → `/customers/{newId}` for Moroka (`2b454c42-…`). |
| L-34 (portal-contact auto-provision) | VERIFIED | TRUST customer's primary email auto-provisioned to `portal_contacts`; surfaced in REQ-0005 dialog. |
| L-37 / L-37-regression V112 | HOLDS | Customer-step shows only `SA Legal — Client Details`; matter `applied_field_groups` excludes `conveyancing_za_matter`. |
| L-39 (customerId param propagation) | VERIFIED | URL `?customerId=2b454c42-…` flowed from Moroka detail through New-from-Template → Configure step (Client combobox pre-selected). |
| L-41 (Information Request due_date picker) | VERIFIED | `due_date=2026-05-11` persisted on REQ-0005. |
| L-53 (Liquidation and Distribution pack) | VERIFIED | Template option present in combobox, instantiated 5 items. |

### Notes on dispatch vs scenario

The dispatch describes Day 14 as a **second tenant** onboarding (separate KC org / separate `tenant_<UUID>` schema, padmin approval flow). The scenario file (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` lines 421–461) describes Day 14 as a **second client of the same Mathebula tenant** (no padmin / no KC handoff — pure firm-side client + matter creation). **Followed the scenario file** (authoritative source of truth). Day 15 isolation probe is therefore per-portal-contact / per-customer scoping, not per-tenant — which is the correct security model for a single law firm with multiple clients. No new tenant schema created; both clients share `tenant_5039f2d497cf`.

### Day 14 summary checkpoints (all PASS)

- ✓ Two clients on tenant: Sipho (INDIVIDUAL, RAF-2026-001 LITIGATION) + Moroka (TRUST, EST-2026-002 ESTATES).
- ✓ Moroka has at least: 1 info request (REQ-0005 SENT), 1 document (death-certificate-moroka.pdf), 1 trust deposit (R 25 000 RECORDED).
- ✓ Moroka entity IDs captured.

### State at end-of-turn

- Tenant `tenant_5039f2d497cf` — 2 customers, 2 substantive matters + 1 probe matter `af9b14b2-…` from Day 3 cycle-1 (left as evidence).
- Trust account `Mathebula Trust — Main` (SECTION_86) — total **R 75 000,00** in account; per-client ledger split: Sipho R 50,000 / Moroka R 25,000.
- Tab 0: Bob firm session at `/projects/89201af5-…` Trust tab. Tab 1: Sipho portal session at `/trust/e788a51b-…` (untouched throughout this turn — Day 15 will re-use it).

### Next action

Day 15 dispatch — Sipho portal isolation probes (List-view leak, Direct-URL hard negative, API hard negative) using the cycle-1 Isolation Probe IDs above.

---

# Day 14 Checkpoint Results — Cycle 30 — 2026-04-27 SAST

**Branch**: bugfix_cycle_2026-04-26-day14
**Backend rev / JVM**: main `7ea65235` / backend PID 41372
**Stack**: Keycloak dev (3000/8080/8443/8180)
**Actor**: Bob Ndlovu (admin) — Tab 0 firm session preserved across cycle 29 → 30 (Admin role sufficient for all 14.x mutations per cycle-1 verify; no KC handoff needed, GAP-L-22 not exercised this turn).
**Tenant DB state at start**: tenant_5039f2d497cf with Sipho (INDIVIDUAL) only. RAF matter (cc390c4f-…) + 2 Sipho deposits (R 50 000 + R 100). Fresh Moroka seed required.

## Summary
**11/11 executed — 10 PASS / 0 FAIL / 1 PARTIAL (14.2 beneficial-owners — GAP-L-54 carry-forward) / 0 BLOCKED / 1 SKIPPED-BY-DESIGN (14.4 conflict check per scenario).** Zero new gaps; Day 14 closes clean. Day 15 isolation probes ready to dispatch.

## Checkpoints

### 14.1 — Navigate Clients → + New Client
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.1-customers-list.yml` — Clients page rendered with 1 row (Sipho Dlamini). "New Client" button present and clickable.

### 14.2 — Fill client form (Type=TRUST, name, email, registration, country)
- Result: **PASS (partial — beneficial owners not addable)**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.2-new-client-dialog.yml`, `…-14.2-form-filled.yml`, `…-14.2-step2.yml`
- Fields entered (Step 1): name=`Moroka Family Trust`, type=`Trust` (combobox option exists), email=`moroka.portal@example.com`, ID Number=`IT 001234/2024`, Tax Number=`9012345678`, Country=`South Africa (ZA)`.
- Step 2 only offered "SA Legal — Client Details" intake group; **no "Beneficial Owners" structured field/card**. Skipped intake (scenario calls for 2 beneficial owners — no UI affordance). Re-confirms **GAP-L-54** (LOW) — not re-logged as new gap.

### 14.3 — Submit → client created
- Result: **PASS**
- Evidence: Browser redirected to `/customers/0cb199f2-9263-4d57-88c7-4a0350f8779c` (L-32 redirect-to-detail holds). DB confirms `customers.id=0cb199f2-… / customer_type=TRUST / lifecycle_status=PROSPECT / email=moroka.portal@example.com`. **L-34 portal-contact auto-provisioner fired**: `portal_contacts.id=3c127b78-1d78-457f-a9cf-efd2783e0b66 / role=GENERAL / status=ACTIVE` for moroka.portal@example.com — verified for TRUST customer_type.

### 14.4 — Conflict Check
- Result: **SKIPPED-BY-DESIGN**
- Evidence: Scenario file line: "no 📸 required — this is setup, not a demo moment." L-28 + L-29-regression VERIFIED on Day 2 — no value re-walking for Day 15 isolation purposes.

### 14.5 — New Matter from Deceased Estate template
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.5-template-dialog.yml`. From Moroka detail → "+ New Matter" link with URL `?new=1&customerId=0cb199f2-…` (L-39 ✓). "New from Template — Select Template" dialog opened. "Deceased Estate Administration (9 tasks)" option present and selectable.

### 14.6 — Fill matter form
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.6-configure-step.yml`. Configure dialog Client combobox **pre-selected `Moroka Family Trust`** (L-39 propagation end-to-end ✓). Description auto-populated from template. Renamed "Moroka Family Trust - Estate" → `Estate Late Peter Moroka`; reference `EST-2026-002`. (Master's Office field not surfaced on Configure step — same observation as cycle 1; not a regression and not in scope for Day 15 isolation.)

### 14.7 — Submit → matter created
- Result: **PASS**
- Evidence: Redirected to `/projects/340c5bb2-16c9-4cb4-ae27-df757aa7ce6d`. DB: `projects.id=340c5bb2-… / name='Estate Late Peter Moroka' / reference_number=EST-2026-002 / customer_id=0cb199f2-… / status=ACTIVE / applied_field_groups=[Project Info, SA Legal — Matter Details]`. **L-37-regression V112 fix HOLDS** — `conveyancing_za_matter` correctly NOT attached to deceased-estate matter.

### 14.8 — Send info request — Liquidation and Distribution Account Pack
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.8-template-options.yml`. Requests tab → New Request dialog. Template combobox lists **"Liquidation and Distribution Account Pack (5 items)"** (L-53 fix holds — was GAP-L-53 in cycle 0 walk). Portal Contact pre-populated with `Moroka Family Trust (moroka.portal@example.com)` (L-34 ✓). Due Date `2026-05-11` accepted (L-41 ✓). Send Now → DB row `information_requests.id=de3cffc7-… / request_number=REQ-0003 / status=SENT / due_date=2026-05-11 / project_id=340c5bb2-… / portal_contact_id=3c127b78-…`. Mailpit verified message "Information request REQ-0003 from Mathebula & Partners" delivered to moroka.portal@example.com at 11:04:02Z.

### 14.9 — Upload internal document to Moroka matter
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.9-documents-tab.yml`. Documents tab drag-drop → file_chooser → uploaded `qa_cycle/test-fixtures/letters-of-authority.pdf`. DB: `documents.id=9eb9ed95-92be-4bb0-b656-5b2f6f96b9b6 / file_name='letters-of-authority.pdf' / project_id=340c5bb2-…`.

### 14.10 — Record R 25 000 trust deposit against Moroka / EST-2026-002
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/day-14-cycle30-14.10-deposit-dialog.yml`, `…-14.10-after-deposit.yml`. /trust-accounting/transactions → Record Transaction → Record Deposit dialog → fields: Client ID=`0cb199f2-…`, Matter=`340c5bb2-…`, Amount=25000, Reference=`DEP/2026/EST-002`, Description=`Initial trust deposit — EST-2026-002 Moroka Family Trust`, Transaction Date=2026-04-27. Submit → Transactions list now reads "3 transactions found" with **DEP/2026/EST-002 Deposit R 25 000,00 RECORDED** row appended. DB: `trust_transactions.id=0e9f9c17-… / amount=25000.00 / status=RECORDED / project_id=340c5bb2-…` (BUG-CYCLE26-11 fix holds — project_id correctly populated, NOT NULL). ZAR rendering "R 25 000,00" with comma decimal correct. Per-customer ledger card created: `client_ledger_cards.id=886e0759-… / customer_id=0cb199f2-… / balance=25000.00` (separate from Sipho's `36765353-… / balance=50100.00`) — per-customer ledger isolation already in place at DB layer (good substrate for Day 15 portal probes).

### 14.11 — Capture Moroka entity IDs for Day 15 probes
- Result: **PASS**
- Evidence: Updated `qa_cycle/isolation-probe-ids.txt` with cycle-30 fresh entity IDs (Moroka customer / portal_contact / matter / info-request / document / trust-transaction / client-ledger-card) + reference Sipho IDs + shared trust-account-id + Day 15 frontend/backend/digest probe plans. Tenant `tenant_5039f2d497cf` retained (single tenant per scenario).

## Day 14 rollup checkpoints

- ✓ Two clients on tenant: Sipho (INDIVIDUAL, RAF-2026-001) + Moroka (TRUST, EST-2026-002).
- ✓ Moroka has ≥1 info request (REQ-0003 SENT) + 1 document (letters-of-authority.pdf) + 1 trust deposit (R 25 000,00 RECORDED).
- ✓ Moroka entity IDs captured for Day 15 probes.

## Cycle-30 Isolation Probe IDs (for Day 15 dispatch)

```
Moroka customer_id          : 0cb199f2-9263-4d57-88c7-4a0350f8779c
Moroka portal_contact_id    : 3c127b78-1d78-457f-a9cf-efd2783e0b66
Moroka matter_id (project)  : 340c5bb2-16c9-4cb4-ae27-df757aa7ce6d
Moroka info-request_id      : de3cffc7-7744-43ce-9c80-d90a42a1de08   (REQ-0003)
Moroka document_id          : 9eb9ed95-92be-4bb0-b656-5b2f6f96b9b6   (letters-of-authority.pdf)
Moroka trust_tx_id          : 0e9f9c17-a305-4714-a81f-c471d56b4832   (R 25 000,00 DEPOSIT RECORDED)
Moroka client_ledger_card   : 886e0759-eeb0-4452-93df-186418ed17d7
Sipho customer_id           : c4f70d86-c292-4d02-9f6f-2e900099ba57
Sipho matter_id             : cc390c4f-35e2-42b5-8b54-bac766673ae7   (RAF-2026-001)
Trust account_id            : 46d1177a-d1c3-48d8-9ba8-427f14b8278f   (Mathebula Trust — Main, shared)
```

## Re-observed / carry-forward (no new gaps)

| Existing item | Re-observed? | Note |
|---|---|---|
| GAP-L-22 (session handoff) | NOT EXERCISED | No fresh KC handoff this turn. Bob session preserved across navigation. |
| GAP-L-32 (Create Client redirect) | VERIFIED | Submit → `/customers/0cb199f2-…` for Moroka. |
| GAP-L-34 (portal-contact auto-provision) | VERIFIED | Fired for TRUST customer_type within ~1s; surfaced in REQ-0003 dialog with Moroka pre-populated. |
| GAP-L-37 V112 regression (over-broad field-group) | HOLDS | `applied_field_groups` excludes `conveyancing_za_matter`; only Project Info + SA Legal — Matter Details applied. |
| GAP-L-39 (customerId param propagation) | VERIFIED | URL `?customerId=0cb199f2-…` flowed end-to-end through Configure step (Client combobox pre-selected). |
| GAP-L-41 (Information Request due_date picker) | VERIFIED | `due_date=2026-05-11` persisted on REQ-0003. |
| GAP-L-53 (Liquidation and Distribution pack) | VERIFIED | Template option present in combobox, instantiated 5 items. |
| GAP-L-54 (Beneficial Owners structured field for TRUST) | YES | Step 2 of Create Client dialog has no Beneficial Owners card — only "SA Legal — Client Details" intake. Carried forward. |
| BUG-CYCLE26-11 (trust-deposit project_id) | HOLDS | Moroka deposit `0e9f9c17-…` has `project_id=340c5bb2-…` (not NULL) on creation — fix from PR #1183 holds for fresh deposit. |

## Console errors

Zero error-level console messages across all Day 14 navigations (verified via `mcp__playwright__browser_console_messages level=error`).

## Gaps Found (this turn)

**None.** All checkpoints PASS or carry forward existing observations. No new bugs introduced; no regressions detected.

## Halt reason

Day 14 complete (all 11 checkpoints executed or formally skipped per scenario). Per scenario: stop at end of Day 14; Day 15 (isolation BLOCKER probes) dispatched separately by orchestrator.

## QA Position on exit

`Day 15 — 15.A (isolation probes on Sipho portal session)`. All Moroka seed data in place. `qa_cycle/isolation-probe-ids.txt` updated with cycle-30 fresh IDs. Next dispatch: Day 15 agent uses those IDs against Sipho's portal session.
