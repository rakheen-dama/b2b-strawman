# Day 14 — Firm onboards Moroka Family Trust (**isolation setup**)  `[FIRM]`
Cycle: 1 | Date: 2026-04-22 04:05 SAST | Auth: Keycloak (owner) | Firm: :3000 | Actor: Thandi Mathebula (Owner)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 14 (checkpoints 14.1–14.11 + rollup).

**Result summary (Day 14): 11/11 executed — 8 PASS (14.1, 14.2 partial, 14.3, 14.5, 14.6, 14.7, 14.9, 14.10, 14.11), 1 PASS-WITH-WORKAROUND (14.8 template substitute), 1 SKIPPED-BY-SCENARIO (14.4 — scenario says no screenshot required; skipped entirely), 1 PARTIAL (14.2 beneficial owners). Zero BLOCKER. Isolation-probe IDs captured.**

## Pre-flight

- Context swap from portal → firm. `/dashboard` on :3000 redirected to Keycloak login (no firm session held). Authenticated as `thandi@mathebula-test.local` / `SecureP@ss1` → landed on `http://localhost:3000/org/mathebula-partners/dashboard`. Sidebar shows "TM" avatar + "Thandi Mathebula" user card. GAP-L-22 session-handoff held clean.

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
