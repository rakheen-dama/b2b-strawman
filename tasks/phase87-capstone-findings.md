# Phase 87 Capstone — Findings, Gaps, Failures and Passes

**Product:** Kazi, legal-za profile · **Phase:** 87 (legal-native shell), slice 617C capstone
**Walk date:** 2026-10-22 · **Code under test:** main `dac048f12` (product code identical to the merge `28834fa8f`, PR #1641)
**Scenario:** `qa/testplan/demos/legal-za-90day-keycloak.md` (307 checkbox steps)
**Firm in the story:** Mathebula & Partners; users Thandi (Owner), Bob (Admin), Carol (Member); clients Sipho Dlamini, Moroka Family Trust, Khumalo Property Holdings, Lerato Mthembu, Nomvula Khoza

This file consolidates the capstone in one place. The evidence it is built from is the authority. Where this file and the evidence disagree, the evidence wins:

| Source | What it holds |
|---|---|
| `qa_cycle/evidence/phase87-capstone/index.md` | the single detailed count table and the normalisation rules |
| `qa_cycle/evidence/phase87-capstone/{session-0,day-00,days-*}.md` | one row per scenario step, with evidence and screenshot paths |
| `qa_cycle/evidence/phase87-capstone/exit-checkpoints.md` | exit checkpoints E.1–E.11 and the demo-readiness verdict |
| `qa_cycle/evidence/phase87-capstone/gaps.md` | the 59-gap register and the Phase 87 attribution table |
| `qa_cycle/evidence/phase87-capstone/e2e-runs.md` | the Playwright runs on the :3001 mock-auth stack |
| `tasks/phase87-legal-native-shell.md` § Closure report (C.1–C.13) | build-level verification, open questions, follow-ups, lessons |

**Status vocabulary** (root `CLAUDE.md` §3): **PASS** = observed working in the browser, as the step is written. **FAIL** = observed not working. **DEFERRED** = not observed, with the reason. **N/A** = removed step. There is no PASS-with-note.

---

## 1. Executive summary

- **Verdict: not demo-ready.** The legal-za 90-day story was walked end to end on the real Keycloak stack, every step through the browser. 5 of the 11 exit checkpoints fail (E.1 all steps checked, E.2 wow moments, E.3 gap register, E.8 terminology sweep, E.9 audit filterable).
- **Scorecard:** of 307 steps, **228 PASS · 53 FAIL · 24 DEFERRED · 2 N/A**.
- **Gaps:** **59** found. 0 BLOCKER, **5 HIGH**, 24 MEDIUM and 30 LOW.
- **Phase 87 is not the main cause.** By code archaeology, **52 of the 59 gaps predate Phase 87**, including **all 5 HIGH**. **6 were introduced by Phase 87** (2 MEDIUM, 4 LOW), and **1 is undetermined**.
- **The Phase 87 build itself holds up:**
  - The wire rename and terminology-removal mechanism are **VERIFIED**: contract tests are green, legacy literals are down to 0, and remnant scans are at 0.
  - The new shell and the portal are **PARTIAL**: most of it was observed working, but some surfaces were observed failing.
  - The legal Playwright failing set is **unchanged** from before the last settings slice (616F), so Phase 87 added no automated-test failures.
- **The five HIGH gaps** (all pre-existing):
  1. **G-B3:** a matter can't get more than one member, and there's no screen to add members. Carol (Member) is locked out of every matter.
  2. **G-B7:** the portal acceptance page can't show the PDF the client is accepting (X-Frame-Options DENY).
  3. **G-C9:** there's no way to write off a disbursement in the UI.
  4. **G-C10:** fee-note lines print the task title, not the narrative the attorney logged.
  5. **G-D1:** portal sign-in at `/login` can't find the firm, and client emails don't carry the firm id.
- **The six Phase-87-introduced gaps:**
  - G-D11: Matters "Fee earner —", and Clients "Outstanding —" / "FICA Not started".
  - G-D21: Home "No client owes fees" and "5 of 5 FICA not started".
  - G-A2: the Home breadcrumb says "Dashboard".
  - G-D17: the "Tariffs" settings tab shows rates.
  - G-D18: the "Xero" settings tab shows every integration.
  - G-D20: Diary week-view titles are cut off.
- **Not run:** the AI-core walk, which needs a funded Anthropic API key (DEFERRED).

---

## 2. Scorecard

### 2.1 Grand totals

| | PASS | FAIL | DEFERRED | N/A | Total |
|---|---|---|---|---|---|
| Walk (Session 0 → Day 90) | 223 | 48 | 19 | 1 | 291 |
| Exit checkpoints E.1–E.11 | 5 | 5 | 5 | 1 | 16 |
| **All steps** | **228 (74.3%)** | **53 (17.3%)** | **24 (7.8%)** | **2** | **307** |

The PASS count was **238** before the 617C architecture review. The review found 10 PASS rows that rested on state a walker had substituted to get past a product gap, and moved them to DEFERRED (§6.2).

### 2.2 By section (normalised counts, as in `index.md`)

| Section | Walker | PASS | FAIL | DEFERRED | N/A | Notes |
|---|---|---|---|---|---|---|
| Session 0 | A | 2 | 0 | 0 | 0 | |
| Day 0 — firm onboarding, setup | A | 80 | 1 | 0 | 0 | 0.50 and CP0.5 closed as PASS by Walker B's back-reference row |
| Days 1–7 | B | 43 | 14 | 2 | 0 | 5.10 and 6.4 were BLOCKED, counted as DEFERRED |
| Days 8–21 | B | 31 | 8 | 0 | 0 | |
| Days 22–35 | C1 | 19 | 3 | 1 | 0 | |
| Days 36–60 | C1 | 21 | 9 | 5 | 0 | |
| Days 61–75 | C2 | 5 | 4 | 10 | 0 | 10 reclassified PASS → DEFERRED by the review |
| Days 76–90 | C2 | 22 | 9 | 1 | 1 | |
| Exit checkpoints | C2 | 5 | 5 | 5 | 1 | |
| **Total** | | **228** | **53** | **24** | **2** | |

### 2.3 Exit checkpoints

| # | Checkpoint | Result | Why |
|---|---|---|---|
| E.1 | Every step checked | **FAIL** | 48 walk FAILs and 19 walk DEFERREDs |
| E.2 | 7 wow moments | **FAIL** | 2 of 7 fail outright (5.4 matter detail, 88.3 activity feed); 2 more carry visible defects (4.3 portal acceptance, 47.3 fee-note PDF) |
| E.3 | Gap register | **FAIL** | 59 gaps, 5 HIGH |
| E.4 | No plan tiers | PASS | Subscription page, team invite and member count are all flat |
| E.5 | Field promotion on create dialogs | PASS | Client, Matter, Task, Fee note |
| E.6 | Progressive disclosure (legal modules only) | PASS | Trust zone, Diary, Conflict check, Tariffs; no horizontal-era items |
| E.7 | Real Keycloak onboarding (no mock IDP) | PASS | Access request → OTP → admin approval → KC registration → invites |
| E.8 | Terminology sweep | **FAIL** | Sidebar is clean, but breadcrumbs, 15 of 31 settings tabs, dialogs, audit labels, reports and fee-note pages still say Project / Customer / Invoice |
| E.9 | Audit recorded and filterable | **FAIL** | 243 events recorded, but filtering needs raw internal keys (actor UUID, entity "project" not "matter") |
| E.10 | One unbroken pass | PASS | Same service PIDs for all walkers; no restart, reset or code change |
| E.11 (+a–d) | Test-suite gate | DEFERRED | 617C changes no code; the last gate runs are the verify markers (§8.4). There is no `typecheck` script |
| E.11 (e) | Every fix PR met the gates | N/A | 617C merged no fix PR |

### 2.4 The seven wow moments

| # | Moment | Step | Result |
|---|---|---|---|
| 1 | Day 0 Home, six-zone nav + firm brand | 0.25 | PASS. Minor: breadcrumb "Dashboard" (G-A2); brand-coloured org name barely legible (G-A3) |
| 2 | Day 4 engagement letter accepted on the portal | 4.3b / 4.3c | 4.3c PASS (acceptance recorded, confirmation email), **4.3b FAIL**: the portal page can't display the PDF (G-B7) |
| 3 | Day 2 conflict check CLEAR | 1.4 | PASS |
| 4 | Day 5 matter detail with promoted fields inline | 5.4 | **FAIL**: case number and court are not shown inline (G-B9) |
| 5 | Day 33 profitability | 33.5 | PASS |
| 6 | Day 47 fee-note PDF with letterhead + LSSA lines | 47.3 | PASS as a capture, but the PDF has no brand colour, address letterhead or banking details (G-C7) |
| 7 | Day 88 activity feed | 88.3 | **FAIL**: raw event keys in frame and trust movements missing (G-B8, G-D9) |

---

## 3. What passed

These areas were observed working as the scenario describes. The full list of all 228 PASS rows is in **Appendix A**.

**Onboarding and access (Session 0, Day 0, E.7)**
- Real access request at `/request-access`, with an OTP from Mailpit (0.1–0.8).
- Platform-admin approval provisions the tenant with the `legal-za` profile (0.15–0.17).
- Keycloak invitations: owner, admin and member all register through KC and land on the org Home (0.18–0.36).
- The Keycloak two-step login works for all three users throughout.

**Firm setup (Day 0)**
- ZAR currency, brand colour #1B3A4B and logo all persist after reload (0.38–0.40).
- Billing and cost rates were created for all three members (0.42–0.43).
- VAT 15% is pre-seeded (0.45).
- The legal-za custom-field packs are loaded for clients and matters (0.47–0.48).
- Create-client and create-matter dialogs render promoted fields as native inputs (0.49, 0.51, E.5).
- 6 legal matter templates are present (0.54), and 10 automation rules are seeded (0.56).
- Trust account creation and the LPFF rate work (0.61–0.64).
- The subscription page is flat, with no tier or upgrade UI anywhere (0.65–0.68, 90.4, E.4).

**Navigation and shell (Phase 87 surfaces)**
- The six-zone sidebar, TODAY · MATTERS · CLIENTS · FEES · TRUST · FIRM, has the legal modules and no horizontal-era items (0.24, 0.59–0.60, 90.3, E.6).
- Home renders its widget matrix (0.25).
- Diary: create a court date, jump to its week and find it there, plus the List view (23.2–23.4, 23.6).
- The settings families load at their new URLs, e.g. `/settings/firm?tab=general` and `/settings/fees?tab=tax`.
- Zero console errors across 28 top-level pages and 31 settings tabs (90.5). Zero undelivered emails (90.6).

**Clients, compliance and matters (Days 1–22)**
- Conflict check CLEAR for new clients (1.2–1.4, 8.1, 22.1).
- Client create with promoted fields (1.6–1.11, 8.2, 14.2, 22.2, 61.1).
- FICA onboarding: the 9-item individual checklist and the 12-item trust checklist complete, and the client auto-transitions Prospect → Onboarding → Active (2.1–2.8, 8.4–8.6, 22.3).
- New matter from a template, with tasks pre-created and the work type pre-filled (3.1–3.6, 9.1–9.4, 14.3, 22.4–22.5).
- The matter tab groups (Work · Fees · Activity …) load (3.9–3.10, 5.3).
- Conveyancing custom fields save and re-render (14.5–14.6).

**Documents and acceptance**
- Engagement letter generated from a template with client and matter context (4.1).
- Save to Documents, then Send for Acceptance (4.3–4.3a).
- The client accepts, and the acceptance is recorded with a confirmation email (4.3c).
- An Offer to Purchase is generated, sent for acceptance and emailed (14.7, 14.9–14.11).
- Document uploads to matters and clients (6.5, 10.2, 18.1, 28.1).

**Time, fees, disbursements, trust and payments (Days 4–72)**
- Time logged on tasks by Thandi and Bob, with the rates applied (4.4–4.5, 10.1, 11.1, 16.1, 24.1, 26.1).
- Office-account disbursement create → submit → approve (5.6–5.8, 5.11).
- A fee estimate (budget) is set and tracks consumption, showing "On Track" (15.1–15.4, 21.1).
- Fee notes:
  - fetch unbilled time;
  - VAT arithmetic is correct;
  - the fee-notes list (36.1–36.6);
  - Approve → Send, with an email (45.4);
  - LSSA tariff lines can be added to a draft (45.3).
- Generated fee-note PDF download (47.1, 47.3) and a Statement of Account saved to the matter (30.1–30.3, 30.5).
- Record payment, after which the fee note shows Paid (55.2).
- Trust deposit R 50 000 and payment R 12 000 post correctly to the Overview and the client ledger (12.2–12.3, 72.1–72.2).

**Reporting, audit and closure (Days 33–90)**
- Profitability: matter and client profitability in ZAR with non-zero margins, plus team utilisation (33.1–33.5, 80.1–80.3).
- The audit trail records the walk: 243 events, with severity and event labels (58.1, 85.1, 85.4).
- Matter closure:
  - gates block when a trust balance remains (85.5–85.6, 85.11);
  - an Owner can override with a justification and close the matter (85.10–85.16, 85.18);
  - Complete Matter works (75.1).

---

## 4. Failures (53)

Each failure links to the gap that explains it. The full row text for every FAIL is in **Appendix B**.

### 4.1 By theme (48 walk FAILs + 5 exit-checkpoint FAILs)

| Theme | FAIL steps | Count | Gaps |
|---|---|---|---|
| Matter team / member access | 3.7, 6.1, 6.3, 9.5, 13.1, 15.3, 30.1, 70.1 | 8 | G-B3 (HIGH) |
| Documents and rendering | 4.2, 14.4, 14.8, 14.13, 30.4, 30.6, 30.7, 38.3, 47.2 | 9 | G-B5, G-B6, G-B17, G-B18, G-C4, G-C7 |
| Activity feed and audit | 2.9, 4.6, 6.6, 23.5, 85.3, 85.17, 88.2, 88.3 | 8 | G-B2, G-B8, G-D6, G-D8, G-D9 |
| Disbursements | 5.9, 5.12, 5.14, 45.6 | 4 | G-B11, G-B12, G-C9 (HIGH) |
| Matter detail | 3.8, 5.2, 5.4, 14.1 | 4 | G-B4, G-B9, G-B10, G-B15 |
| Matter closure and retention | 75.2, 75.10, 85.7, 85.8 | 4 | G-D2, G-D3, G-D7 |
| Client portal | 4.3b, 14.12, 68.1 | 3 | G-B7 (HIGH), G-B19, G-D1 (HIGH) |
| Fee notes and payments | 36.4, 38.1, 55.1 | 3 | G-C6, G-C8, G-C10 (HIGH), G-C11 |
| Reports and My work | 80.4, 80.5 | 2 | G-D4, G-D5 |
| Adverse parties | 23.1 | 1 | G-C1, G-C2, G-C3 |
| Terminology sweep | 90.1 | 1 | G-D10 (+ G-A4, G-A5, G-B1, G-C3) |
| Platform admin | 0.14 | 1 | G-A1 |
| **Walk subtotal** | | **48** | |
| Exit checkpoints | E.1, E.2, E.3, E.8, E.9 | 5 | see §2.3 |
| **Total** | | **53** | |

### 4.2 Where the failures cluster

- **G-B3 (matter membership) is the largest single cause.** It is behind 8 FAILs, 2 DEFERREDs (6.4, 62.1) and the "Carol can't do anything" pattern across the whole walk. It also drove one of the three walker substitutions (making Carol a matter lead).
- **Days 1–7 had the most FAILs (14).** That's where the first matter, engagement letter, disbursements and Carol's first steps all land.
- **The Day 45 write-off sub-flow (45.6–45.11) can't be done at all:** 1 FAIL, then 5 DEFERRED. Because there was no write-off, the Day 75 closure could only pass by billing the disbursement instead, which the review then reclassified.

---

## 5. Gap register (59)

The full rows, with observed vs expected, evidence paths and suspected owners, are in `qa_cycle/evidence/phase87-capstone/gaps.md`.

**Attribution** is from code archaeology (comparing baseline `41cb211fb` with `dac048f12`, plus read-only SQL). It was **not** observed on a baseline stack. The copy/terminology clusters were classified by sampling strings. **P87** = introduced by Phase 87; **Pre** = pre-existing; **Undet.** = undetermined.

### 5.1 HIGH (5)

| ID | Steps | Gap | Attribution |
|---|---|---|---|
| G-B3 | 3.7, 6.1, 6.3, 6.4, 9.5, 13.1, 15.3 | Template matters start with 0 members (or only the chosen lead) and there is **no UI to add members**. So the assignee picker offers only "Unassigned", and Carol (Member) gets "0 matters" and "Something went wrong" on every matter link | Pre (members panel unreachable since Epic 599, Phase 86) |
| G-B7 | 4.3b, 14.12 | Portal `/accept/<token>` can't display the PDF: the backend sends `X-Frame-Options: DENY` for the cross-origin iframe. The client accepts a document they can't see | Pre (Spring Security default; check same-origin prod first) |
| G-C9 | 45.6–45.11 | No **Write Off** action for a disbursement anywhere in the UI. The API exists (Epic 491) | Pre (`FIXME(depth-ii:no-write-off-action)`) |
| G-C10 | 36.4, 45.2, 50.1 | Fee-note lines from time entries use `<task title> -- <date> -- <member>` and drop the entry's own narrative, so two different entries can be indistinguishable on the client's bill | Pre (Epic 82A, `InvoiceCreationService.java:1180`) |
| G-D1 | 68.1 | Portal bare `/login` refuses every sign-in ("couldn't determine which organization"), and no client email carries an `orgId`. Only the `?orgId=` QA shim works | Pre (`portal/app/login/page.tsx`, GAP-L-66) |

### 5.2 MEDIUM (24)

| ID | Steps | Gap | Attribution |
|---|---|---|---|
| G-A6 | 0.51 | New Matter dialog shows a "Conflict Check — Coming Soon" card although conflict check is a live module | Pre |
| G-B5 | 4.2 | Engagement letter has no firm branding (no logo, no brand colour) | Pre |
| G-B8 | 4.6, 6.6 | Matter Activity feed omits the acceptance lifecycle and prints raw keys ("performed project.updated on project") | Pre |
| G-B9 | 5.2, 5.4 | `case_number` / `court_name` are saved but not shown inline on the matter detail (wow moment 4) | Pre |
| G-B11 | 5.9, 5.10, 5.12, 5.14 | A trust-account disbursement can't be created: the trust-link dialog finds no eligible transaction | Pre (`FIXME(depth-ii:trust-link-payment-type)` + scenario order) |
| G-B12 | 5.12, 5.14 | Matter Disbursements tab has no unbilled summary | Pre (`FIXME(depth-ii:matter-unbilled-disbursements-summary)`) |
| G-B14 | 10.2 | On a matter opened with `?tab=…`, clicking another tab changes the label but not the panel | Pre |
| G-B18 | 14.8 | Offer to Purchase is generated with no conveyancing clauses ("Conditions of Sale" empty) | Pre (`FIXME(depth-ii:otp-clause-associations)`) |
| G-C2 | 23.1 | Adverse-party registry › Link to Matter crashes the page (`.map` of undefined) | Pre (610B D1 FIXME) |
| G-C4 | 30.4, 30.6, 30.7 | Statement of Account preview widens without bound; Download PDF drifts off-screen | Pre (`FIXME(depth-ii:soa-preview-unbounded-scale)`) |
| G-C5 | 30.3, 30.5 | SoA "Preview & Save" emails the client immediately, with a legacy `/projects/` link | Pre |
| G-C7 | 36.3, 38.3, 47.2, 47.3 | Fee-note PDF: no brand colour, no address letterhead, no banking details, blank labels; draft preview shows raw numbers | Pre |
| G-C11 | 55.1 | Record Payment has no amount (no part payments) and no payment method | Pre |
| G-D2 | 75.1, 75.2 | A COMPLETED matter offers no Close action; it must be reopened first | Pre |
| G-D3 | 75.10 | Closing records no per-matter retention end date; the card says retention "isn't configured" | Pre (`FIXME(depth-ii:retention-period-unconfigurable)`) |
| G-D4 | 80.4 | Report export CSV/PDF fails ("Failed to export"); Profitability has no export | Pre (code unchanged; root cause unverified) |
| G-D5 | 80.5 | My work doesn't show upcoming court dates | Pre |
| G-D6 | 85.2, 85.3, 85.17, E.9 | Audit filters need raw keys (actor UUID; entity "project", not "matter") | Pre (made more visible by the Matter wording) |
| G-D7 | 85.7, 85.8 | Admin is offered the closure override, then refused server-side | Pre (`FIXME(depth-ii:admin-offered-closure-override)`) |
| G-D9 | 88.2, 88.3 | Estate matter Activity feed has no trust events; raw keys (wow moment 7) | Pre |
| **G-D11** | 61.1, 90.x | Dense lists show wrong aggregates: Matters "Fee earner —" on matters with a lead; Clients "Outstanding —" while fee notes are unpaid; Clients FICA "Not started" for all | **P87**: fee earner from 615A's `'lead'` vs stored `LEAD`; outstanding is a P87 surface on the pre-existing debtor rule; FICA rule is Pre |
| G-D14 | 75.9, 85.16 | Close Matter silently emails the client (closure letter + SoA); the closure letter prints raw amounts | Pre |
| G-D15 | 617C.3 shot 11 | Settings › Documents › Templates overflows horizontally at 1440 px | **Undet.** (needs a baseline A/B) |
| **G-D21** | 617C.3 shots 1–2 | Home **Debtors** says "No client owes fees." while two fee notes are unpaid; Home **FICA outstanding** says 5 of 5 not started | **P87** surface (614B) on pre-existing rules |

### 5.3 LOW (30)

| ID | Steps | Gap | Attribution |
|---|---|---|---|
| G-A1 | 0.14 | Platform-admin access request has no detail view | Pre |
| **G-A2** | 0.25 | Home breadcrumb still reads "Dashboard" | **P87** (612A rename missed the breadcrumb map) |
| G-A3 | 0.39 | Brand colour used as sidebar org-name text colour: barely legible | Pre |
| G-A4 | 0.45, 0.56, 0.58 | Settings copy says invoice / customer / project (tax, features, automations) | Pre (sampled) |
| G-A5 | 0.48 | Custom-field pack descriptions say "Project" | Pre (sampled) |
| G-A7 | 0.54 | Pack-seeded templates labelled "Manual" | Pre |
| G-B1 | many (1.10 … 15.2) | Client/matter page copy: "engagements", "Customer", "Project Summary Report", etc.; double status badges ("Active Active") | Pre (sampled) |
| G-B2 | 2.9 | Client audit tab omits the FICA checklist events | Pre |
| G-B4 | 3.8 | Matter breadcrumb ends in a generic "Matter" | Pre |
| G-B6 | 4.3 | "Download PDF" in preview persists a duplicate generated-document row | Pre |
| G-B10 | 5.4 | Matter FICA card says "Not Started" for a client whose FICA is complete | Pre (same root as G-D11 FICA) |
| G-B13 | 6.5 | Uploading a PDF triggers "Document accepted" notifications | Pre |
| G-B15 | 14.1 | Comments can't carry attachments | Pre (product decision) |
| G-B16 | 14.11 | OTP acceptance email subject hard-codes "engagement letter" | Pre |
| G-B17 | 14.4, 14.13 | 2 of 10 conveyancing fields hidden until an earlier date is filled (pack visibility rule) | Pre (product decision) |
| G-B19 | 14.12 | Acceptance page has no Decline | Pre (product decision) |
| G-C1 | 23.1 | No "Government" adverse-party type (RAF registered as Other) | Pre (product decision) |
| G-C3 | 23.1 … 45.3 | More legacy copy: reports, profitability, fee-note pages, tariff dialog | Pre (sampled) |
| G-C6 | 36.3, 38.1 | Pre-check claims a tax number is required to send, but sending works without one | Pre |
| G-C8 | 38.1, 45.4 | Approve and Send are two steps (scenario collapses them) | Pre (product decision) |
| G-C12 | 45.5, 47.1, 30.4 | Raw UUID breadcrumb; unnamed icon buttons; raw "SHERIFF_FEES" on the SoA | Pre |
| G-D8 | 85.17 | Override justification sits in a separate audit event | Pre (product decision) |
| G-D10 | 75.1 … E.8 | Day-90 terminology leaks: dialogs, guards, Home feed, settings tabs, audit labels, raw breadcrumb slugs | Pre (sampled; the "Dashboard" crumb is G-A2) |
| G-D12 | 90.4, E.4 | Team page lists accepted members as pending invitations | Pre |
| G-D13 | 62.1 | Carol, the matter lead, gets 403 on the per-member time breakdown | Pre (`lead` vs `LEAD`, same as G-D11) |
| G-D16 | shot 13 | "PRO" badge on the AI Assistant card (no tiers exist) | Pre |
| **G-D17** | shot 12 | "Tariffs" settings tab shows Rates & Currency | **P87** (architecture naming, 616A/616C) |
| **G-D18** | shot 13 | "Xero" settings tab shows every integration card | **P87** (616A label + 616D move) |
| G-D19 | shot 8 | Portal matter card has no status (a Closed matter looks open) | Pre |
| **G-D20** | shot 4 | Diary week view cuts the court-date title to "G…" | **P87** (new Diary surface, 613B/613C) |

### 5.4 Attribution summary

| Class | Count | Gaps |
|---|---|---|
| Phase-87-introduced | 6 (2 MEDIUM, 4 LOW) | G-D11, G-D21, G-A2, G-D17, G-D18, G-D20 |
| Undetermined | 1 (MEDIUM) | G-D15 |
| Pre-existing | 52 (incl. all 5 HIGH) | everything else |

Two root causes behind several Phase-87-introduced gaps are worth fixing at the source rather than per surface:
- **`lead` vs `LEAD`.** Template instantiation writes `"LEAD"` (`ProjectTemplateService.java:569,739`), while lookups compare `'lead'` (`MatterListRowRepository.java:46`, `ProjectMemberRepository.java:40`, `ProjectAccessService.java:47`). This explains G-D11's fee-earner column and G-D13.
- **The debtor rule.** It requires `status = SENT AND due_date IS NOT NULL`, but the walk's fee-note flow produced SENT fee notes with no due date. This explains G-D11's outstanding column and G-D21's Debtors widget.

---

## 6. Deferred (24) and N/A (2)

### 6.1 Deferred because an upstream gap blocked the step (9)

| Steps | Blocked by |
|---|---|
| 5.10 | G-B11: no trust-linked disbursement exists to approve (was BLOCKED) |
| 6.4 | G-B3: Carol can't open the matter or task to reply (was BLOCKED) |
| 32.1 | Conditional step: the matter has no tariff overrides to check |
| 45.7, 45.8, 45.9, 45.10, 45.11 | G-C9: no write-off can be performed, so its badge, audit event, exclusion and wording can't be observed |
| 85.9 | The 409 status can't be observed from the browser (server action); REST is not allowed in QA |

### 6.2 Reclassified by the 617C review: ran on substituted pre-state (10)

A walker worked around a product gap three times to keep the story moving. The rows that depended on that substitution are not earned PASSes, so the review moved them to DEFERRED and kept the observed behaviour in the evidence:

| Steps | Substitution | Gap |
|---|---|---|
| 62.1 | Carol was made lead of the collections matter so she could log time | G-B3 |
| 68.2 | Portal session came from the `?orgId=` shim after 68.1 failed | G-D1 |
| 75.3, 75.4, 75.5, 75.6, 75.7, 75.8, 75.9, 75.11 | Sipho's sheriff disbursement was billed and paid (INV-0004) instead of written off, so the closure gates went green | G-C9 |

Kept as PASS after review: 61.1, 65.1 and 80.3. Their own outcome doesn't depend on the substitution.

### 6.3 Exit checkpoint E.11 (5 DEFERRED)

E.11 and E.11 (a)–(d) were not re-run by the capstone, which changes no code. The last observed gate runs are the verify markers in §8.4. `pnpm typecheck` can't be run as written: no such script exists, and `next build` does the type check.

### 6.4 N/A (2)

- 90.7: removed step (Epic 596, ADR-336).
- E.11 (e): 617C merged no fix PR.

### 6.5 Not run at all

- **AI-core walk:** needs a funded Anthropic API key.

---

## 7. Automated tests (Playwright, 617C.2)

These ran on the :3001 mock-auth stack, using 4 fresh stacks built from `dac048f12`. Run 1 on a fresh checkout writes missing screenshot baselines; run 2 is the meaningful one.

### 7.1 Legal suites

| Suite | Run 1 | Run 2 (meaningful) | vs 616F baseline |
|---|---|---|---|
| legal-lifecycle | 65 passed / 15 failed / 6 skipped / 6 did not run | **63 / 4 / 6 / 19** | failing titles identical in both runs |
| legal-depth-ii | 13 / 15 / 15 | **28 / 0 / 15** | identical in both runs |

- Run 1 failures:
  - legal-lifecycle: 6 carried + 9 missing-baseline + **0 new**.
  - legal-depth-ii: 15 missing-baseline only.
- **Phase 87 introduced no new failure in either legal config.**

### 7.2 The carried failures (six, not seven)

| Test (cite by title; line numbers drift) | Status |
|---|---|
| day-00 · Alice: Trust account settings page loads | still failing |
| day-30 · Alice: Create fee note for Sipho Ndlovu (hourly + tariff + disbursement) | still failing |
| day-30 · Alice: Create fee note for Apex Holdings (fixed fee R35,000 + VAT) | still failing when reached (serial-skipped in run 2) |
| day-45 · Alice: Start new bank reconciliation | still failing |
| day-90 · Alice: Fee notes list shows multiple fee notes | still failing |
| day-90 · Alice: Profitability dashboard — revenue, costs, margins for 4 clients | still failing when reached |
| depth-ii day-05 · Bob: Create sheriff-fee OFFICE_ACCOUNT disbursement + submit | **resolved** by #1582 before Phase 87; passed in run 2 |

No Phase 87 slice resolved any of the six as a side effect.

### 7.3 Default-config and portal suites

| Suite | Result | Note |
|---|---|---|
| navigation | 16 passed | matches 612C |
| settings | 13 / 1 / 15 / 1 dnr | identical to 616F; only failure `settings/packs.spec.ts` PACK-01 (pre-existing) |
| projects (`e2e/tests/projects/`) | 10 / 2 / 1 / 12 dnr | PROJ-02/03 time out on `getByRole('tab', {name:'Tasks'})`. Tasks is a dropdown item inside the Work group; pre-existing by code archaeology. PROJ-01 4b (expenses URL → Disbursements redirect) **passed** |
| customers (`e2e/tests/customers/`) | 4 / 2 / 2 / 12 dnr | stable on 2 stacks. CUST-01 ambiguous `getByLabel('Name')`; CUST-02 "Change Status" has had no importer since Epic 560A (pre-existing) |
| frontend portal-data-isolation | 2 passed | matches 617B |
| portal-client-90day (4 files) | 5 / 3 / 1 / 3 dnr | identical to 617B; all 3 failures are seed data prerequisites (Day 3 info request, Day 60 SoA, Day 85 cadence). Non-vacuous: written screenshots are authenticated pages |
| portal responsive | 21 passed | matches 617B |

**Process finding P-1:** `pnpm test:e2e -- projects` matches every spec, because the checkout path contains `/Projects/`. Use path-anchored filters (`e2e/tests/<dir>/`).

---

## 8. Phase 87 build-level verification (closure report C.1–C.7)

### 8.1 Overall status by part

| Part | Status |
|---|---|
| Wire rename project→matter / customer→client (607–609) | **VERIFIED.** `WireVocabularyContractTest` 2/2 with DTO and ROUTE offenders 0; legacy wire-literal files 23 → 0 |
| Terminology removal (611) | **VERIFIED for the mechanism.** Remnant scans 73 / 10 / 2 → 0 / 0 / 0, and the guard tests are green. User-visible legacy copy still leaks from literals and pack JSON (E.8 FAIL) |
| Shell: nav, Diary, Home, lists, settings (612–616) | **PARTIAL.** See §3 for what works; G-A2, G-D11, G-D15, G-D17, G-D18, G-D20 and G-D21 failed |
| Portal vocabulary + nav (617A/617B) | **PARTIAL.** Rail labels, `/matters` routes, the `/projects` 308 redirect and responsive 21/21 all work; 68.1 FAIL (G-D1) and G-D19 |
| Capstone walk (617C.1) | **Observed end to end:** 228 / 53 / 24 / 2, not demo-ready |
| Legal Playwright (617C.2) | **DEFERRED, not green:** the six carried failures remain; no new failures |
| AI-core walk | **DEFERRED:** needs the user (funded key) |
| **Phase 87 overall** | **Not ALL_GREEN.** It stays that way until the carried six and the capstone gaps are fixed or explicitly accepted as deferred (ADR-344) |

### 8.2 Before / after (baseline `41cb211fb` → `dac048f12`)

| Surface | Before | After |
|---|---|---|
| Frontend `page.tsx` (all / settings) | 100 / 44 | **71 / 17** |
| Portal `page.tsx` | 18 | 18 (routes moved `projects/` → `matters/`) |
| Backend top-level packages / `@RestController` | 62 / 121 | 63 / 122 (+ `diary/`) |
| `@Entity` files / audit event types / capabilities / modules | 103 / 29 / 27 / 8 | unchanged |
| Nav zones / items | 6 / 25 | **6 / 28** |
| Settings nav sources / tabs | 2 / — | **1 / 31** |
| Matter / client tab groups | 7/22, 6/14 | 7/21, 6/14 (`expenses` dropped, ADR-340) |
| Redirect rows (frontend / portal) | 1 / 0 | **51 / 1** |
| Terminology files (fe / portal / shared) | 73 / 12 / 2 | **0 / 0 / 0** |
| Backend legacy wire-literal files | 23 | **0** |
| Tenant / global migrations | V3 / V1 | V3 / V1 (Phase 87 shipped no migration; next free tenant V4, global V2) |

### 8.3 Contract and stability tests (targeted run, 24 testcases, 0 F/E/S)

| Test | Result |
|---|---|
| `WireVocabularyContractTest` | 2/2, offenders 0 |
| `EntityTypeLiteralStabilityTest` | 3/3 (ADR-342 literal pins hold) |
| `McpToolContractTest` | 1/1 (21 tools, 3 resources) |
| `BaselineSchemaParityTest` | 5/5 |
| `AuditEventTypeRegistryTest` | 13/13 (catalogue 29) |

### 8.4 Last observed gate runs (verify markers)

| Gate | Commit | Result |
|---|---|---|
| Backend `./mvnw verify` | `43bb21a94` (#1628); backend unchanged since | 5444 testcases / 0 F / 0 E / 26 skipped |
| Frontend `pnpm test` | `26fa65ef2` (#1640) | vitest 406 files / 3008 passed / 2 skipped |
| Frontend `pnpm lint` | `26fa65ef2` | 0 errors / 92 warnings |
| Portal | `ba3063ea4` | 42 files / 193 passed |

### 8.5 Redirect probes (`curl -sI`, all 308)

Frontend: `/projects/x` → `/matters/x`, `/customers/x` → `/clients/x`, `/calendar` → `/diary`, `/court-calendar?tab=prescription` → `/diary?…`, `/settings/general` → `/settings/firm?tab=general`, `/settings/project-templates/x` → `/settings/matters/templates/x`. Portal: `/projects` → `/matters`, `/projects/abc-123` → `/matters/abc-123`.

---

## 9. Recommended next steps and decisions needed

### 9.1 Fix order (each fix is its own reproduce-first PR, per CLAUDE.md §4 and §7)

1. **Phase-87-introduced list/Home aggregates** (G-D11, G-D21):
   - first fix the `lead`/`LEAD` mismatch at the data/compare layer (also fixes G-D13);
   - then the debtor rule for SENT fee notes with no due date;
   - then FICA status from checklists (also G-B10).

   These defects are on the Phase 87 deck surfaces. Re-shoot `home-*.png`, `matters-table.png` and `clients-table.png` after the fix.
2. **The five HIGH gaps:**
   - G-B3: matter-team UI.
   - G-C9: write-off action; the API exists.
   - G-C10: fee-note line narrative.
   - G-D1: portal org resolution.
   - G-B7: acceptance PDF framing; check same-origin prod first.
3. **The remaining Phase-87-introduced LOWs:** G-A2 breadcrumb, G-D18 Xero tab content, G-D20 Diary title truncation, and G-D17 (needs a wording decision).
4. **MEDIUMs:** start with the ones that break demo flows (G-C4 SoA preview, G-C7 fee-note PDF, G-B9 promoted fields, G-B8/G-D9 activity feed, G-D4 report export).
5. **The carried six legal Playwright failures**, one PR each, plus the stale default-config specs (PACK-01, PROJ-02/03, CUST-01, CUST-02) and the portal seed prerequisites.
6. **A shell-wide `min-w-0` fix.** Slices 615C and 616A each worked around page widening locally, and G-D15 may be the same cause.

### 9.2 Product decisions needed (scenario edits need explicit authorisation, CLAUDE.md §6)

- **Scenario wording:**
  - "Billing tab" in 36.1/45.1;
  - the close order 75.1 → 75.2 and the stale `sidebar-lifecycle-action` locator;
  - REST-phrased steps 0.17, 45.7 and 85.9;
  - the brief's 302-step count vs the actual 307;
  - E.3's `qa/gap-reports/…` path vs `gaps.md`.
- **Keep the behaviour or amend the step:** G-A1 (access-request detail view), G-B15 (comment attachments), G-B17 (progressive conveyancing fields), G-B19 (Decline on acceptance), G-C1 (Government party type), G-C8 (two-step Approve/Send), G-D8 (justification in a separate event).
- **Behaviour:** G-C5 and G-D14 (implicit client emails on SoA save and matter closure), G-C11 (part payments), G-D1 (portal org-resolution model).

### 9.3 Earlier rulings awaiting your ratification

- 615C: Work type and Opened are not shown on the default matters views (shell width).
- 615D D-1: "All clients" as the default client view.
- OQ #18: retiring `project-expenses.spec.ts` (the replacement redirect assertion passed).
- 611A.2 / 617A D-2: the "Mandate accepted" wording; both need a backend `templateCategory`.
- Status acceptance: the carried six and the AI-core walk stay DEFERRED until fixed or accepted (ADR-344).

---

## 10. Caveats on the evidence

- **Attribution is code archaeology, not an observed A/B.** No baseline stack was run. The copy clusters (G-A4, G-A5, G-B1, G-C3, G-D10) were attributed by sampling strings, not by checking every one.
- **Some hypotheses are unverified:** the root causes of G-C2, G-D4 and G-D11 are stated as hypotheses in `gaps.md`, and none was reproduced by a test.
- **Walk data is compressed:** all walk data is dated 2026-10-22 (one-day compressed walk), so date-based behaviour (prescription, ageing, due dates) was only partly exercised.
- **Some logs are ephemeral:** the `/tmp/617c-*` logs will be lost. The durable excerpts are under `qa_cycle/evidence/phase87-capstone/logs/`.
- **Screenshots** of the walk are in `qa_cycle/evidence/phase87-capstone/screens/`. The deck shots are in `marketing/decks/screens/phase87/`.

---

## Appendix A — Every PASS row

Generated from the evidence files; the "what was observed" text is truncated to about 170 characters. These are **raw** rows as written in each file:
- Day 0 lists 78 PASS. Steps 0.50 and CP0.5 become PASS through Walker B's back-reference row ("0.50 (back-ref)" in Days 1–7).
- Days 1–7 lists 44 PASS including that back-reference row, which isn't counted twice in the totals.
- Days 61–75 already reflects the review's reclassification.
- Raw total = 227 PASS rows; normalised total = **228** (§2).

### Session 0 — 2 PASS rows (`session-0.md`)

| Step | What was observed |
|---|---|
| 0.A | Read-only `SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'` → only `tenant_56399d06a773` (Phase 86 Squash Smoke). No `tenant_mathebu… |
| 0.B | Read-only KC DB: `docteams` realm users = `padmin@docteams.local` only. No `@mathebula-test.local` users; nothing to delete. KC `org` table empty |

### Day 0 — firm onboarding (Phases A–K) — 78 PASS rows (`day-00.md`)

| Step | What was observed |
|---|---|
| 0.1 | `http://localhost:3000/` landing "Practice management, built for Africa" rendered (cookies+storage cleared first); console 0 errors / 0 warnings (2 info: React DevTools,… |
| 0.2 | Clicked nav "Get Started" → `/request-access` ("Request Access" card) |
| 0.3 | Fields visible: Work Email, Full Name, Organisation Name, Country (select), Industry (select); submit disabled until filled. Industry options: Legal Services, Other |
| 0.4 | Filled thandi@mathebula-test.local / Thandi Mathebula / Mathebula & Partners / South Africa / Legal Services |
| 0.5 | "Request Access" → same page step 2 "Check Your Email", Verification Code input, "Code expires in 10:00" |
| 0.6 | Mailpit API: 1 message to thandi@mathebula-test.local, subject "Your Kazi verification code" (contains "verification"), 15:14:06Z |
| 0.7 | OTP 432022 extracted from message Text, entered, Verify clicked |
| 0.8 | Card "Request Submitted": "Your access request has been submitted for review. We'll notify you by email once it's been reviewed." (wording equivalent to doc) |
| 0.9 | Cleared cookies + localStorage/sessionStorage via `context().clearCookies()` |
| 0.10 | `/dashboard` → redirected to Keycloak `localhost:8180/realms/docteams/.../auth?client_id=gateway-bff` "Sign in to Kazi" (single-step Email+Password form). Only console er… |
| 0.11 | padmin@docteams.local / password → landed on `/platform-admin/access-requests` (platform-admin nav: Access Requests · Billing · Demo) |
| 0.12 | Already at `/platform-admin/access-requests` after login (heading "Access Requests") |
| 0.13 | Pending tab row: Mathebula & Partners · thandi@mathebula-test.local · Thandi Mathebula · South Africa · Legal Services · "just now" · PENDING |
| 0.15 | Approve → AlertDialog "Approve Access Request" ("…create a Keycloak organization, provision a tenant schema, and send an invitation to thandi@mathebula-test.local.") → Ap… |
| 0.16 | Pending tab → "No access requests"; Approved tab row status APPROVED; no error banner. Backend: "Successfully provisioned tenant tenant_5039f2d497cf for org mathebula-par… |
| 0.17 | Approval UI does not show the profile; verified by read-only SELECT (brief A.7): `tenant_5039f2d497cf.org_settings.vertical_profile = legal-za`, `default_currency = ZAR`.… |
| 0.18 | Mailpit: "Invitation to join the Mathebula & Partners organization" to thandi@mathebula-test.local, 15:15:19Z; link `…/realms/docteams/protocol/openid-connect/registratio… |
| 0.19 | Fresh context (cookies + storage cleared); opened the Mailpit invite link (KC `/protocol/openid-connect/registrations?…token=…`) |
| 0.20 | KC page heading "Create an account to join the Mathebula & Partners organization"; Email pre-filled thandi@mathebula-test.local; fields First name, Last name, Email, Pass… |
| 0.21 | Filled Thandi / Mathebula / SecureP@ss1 / SecureP@ss1 |
| 0.22 | Register → `/dashboard` → `/org/mathebula-partners/dashboard` (slug mathebula-partners) |
| 0.23 | Sidebar: org name "Mathebula & Partners"; footer avatar "TM" + "Thandi Mathebula" / thandi@mathebula-test.local |
| 0.24 | Sidebar zones in order TODAY (Home · My work · Diary) · MATTERS (Matters · New matter · Conflict check · Adverse parties · AI reviews) · CLIENTS (Clients · FICA & complia… |
| 0.25 | 📸 Home ("Home — What needs you today": Today's diary, Unbilled WIP, Debtors, FICA outstanding, Matter Health, Recent Activity, Sensitive events) + six-zone nav + org name… |
| 0.26 | Firm zone (collapsed) → expand → items Team · Reports · Audit trail · Settings; Team → `/org/mathebula-partners/team` (heading "Team", "1 member") |
| 0.27 | Members tab: Thandi Mathebula · thandi@mathebula-test.local · Owner. Inline "Invite a team member" form (Email address, Role, Send Invite) — no "Upgrade to Pro", no plan/… |
| 0.28 | The control is an inline form, not an "Invite Member" button: Email bob@mathebula-test.local, Role → Admin (options: Member, Admin under "System") → Send Invite → "Invita… |
| 0.29 | Email carol@mathebula-test.local, Role Member (default) → Send Invite → "Invitation sent to carol@mathebula-test.local."; Pending Invitations tab: carol Member 22 Oct 202… |
| 0.30 | Mailpit: two "Invitation to join the Mathebula & Partners organization" — bob 15:16:50Z, carol 15:16:56Z |
| 0.31 | Fresh context; Bob's KC registration link → "Create an account to join the Mathebula & Partners organization", email pre-filled bob@mathebula-test.local |
| 0.32 | Registered Bob / Ndlovu / SecureP@ss2 (password + confirm) |
| 0.33 | Redirected to `/org/mathebula-partners/dashboard` (h1 "Home", sidebar footer "Bob Ndlovu / bob@mathebula-test.local") → User menu › Sign out → `/signed-out` |
| 0.34 | Fresh context; Carol's link → same KC registration page, email pre-filled carol@mathebula-test.local |
| 0.35 | Registered Carol / Mokoena / SecureP@ss3 |
| 0.36 | Redirected to `/org/mathebula-partners/dashboard` (h1 "Home", footer "Carol Mokoena") → Sign out → `/signed-out`. Observation: Carol (Member) sees zones Today · Matters (… |
| CP A–D.1 | Org created through real access request (0.1–0.8) → padmin approval (0.15–0.16) → KC registration (0.19–0.22). Read-only: `org` row `2c4034d3-…` "Mathebula & Partners" al… |
| CP A–D.2 | KC realm `docteams` users (read-only): thandi/bob/carol@mathebula-test.local + padmin. Each logged in through the browser: Thandi at registration + Phase E re-login; Bob… |
| CP A–D.3 | No Upgrade/plan/tier UI seen on landing → request-access → KC → Team invite (0.1–0.36) |
| CP A–D.4 | `vertical_profile = legal-za` (0.17, read-only SELECT); legal nav (Conflict check, Adverse parties, Diary) visible |
| 0.37 | Thandi re-login (two-step KC: email → password SecureP@ss1) → `/org/mathebula-partners/settings/firm?tab=general`, title "Firm — General · Settings"; settings nav familie… |
| 0.38 | Default Currency combobox = "ZAR — South African Rand" (pre-seeded; DB `default_currency = ZAR`) |
| 0.39 | Brand Color text → #1B3A4B → Save Settings → "Settings saved successfully."; swatch #1b3a4b; after reload input still #1B3A4B and CSS `--brand-color` = #1B3A4B. Observati… |
| 0.40 | Upload Logo → 128×128 PNG (386 B) → preview "Organization logo" + Remove button; Save Settings; after reload the logo img loads (naturalWidth 128) from LocalStack `doctea… |
| 0.41 | `/settings/fees?tab=tariffs` → title "Fees & Trust — Tariffs · Settings", panel heading "Rates & Currency" (Default Currency ZAR; Billing Rates / Cost Rates sub-tabs) |
| 0.42 | Not pre-seeded (all three members "Not set"). Created via "Add Rate" dialog (Rate Type Billing Rate, ZAR, effective 2026-10-22): Thandi R 2 500,00 · Bob R 1 200,00 · Caro… |
| 0.43 | Cost Rates tab → Add Rate (Rate Type Cost Rate → field "Hourly Cost"): Thandi R 1 000,00 · Bob R 500,00 · Carol R 200,00 ZAR; persisted after reload |
| 0.44 | `/settings/fees?tab=tax` → "Fees & Trust — Tax · Settings", heading "Tax Settings" |
| 0.45 | Pre-seeded: "VAT — Standard" 15.00% Default Active (+ Zero-rated 0.00%, Exempt 0.00%). Tax Label = VAT. Observation: this tab's copy still says "Label shown on invoices a… |
| 0.46 | `/settings/matters?tab=custom-fields` → "Matters — Custom fields · Settings", heading "Custom Fields", entity tabs Matters · Tasks · Clients · Fee notes |
| 0.47 | Clients tab → Field Groups include "SA Legal — Client Details" (DB slug `legal_za_client`, pack `legal-za-customer`) with exactly ID / Passport Number, Postal Address, Pr… |
| 0.48 | Matters tab → "SA Legal — Matter Details" (slug `legal_za_matter`, pack `legal-za-project`) = Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Ins… |
| 0.49 | Clients › New Client → "Create Client — Step 1 of 2": all 7 promoted slugs render as native inline inputs: Type (Individual/Company/Trust) = `client_type`; Address sectio… |
| 0.51 | Matters › New Matter → "Create Matter": matter type is the native inline Work Type input (datalist LITIGATION, CONVEYANCING, ESTATES, COLLECTIONS, COMMERCIAL). Observatio… |
| 0.52 | Client dialog: Back → Cancel; Matter dialog: Escape/Cancel. After reload Clients "0 clients", Matters "0 matters" — nothing saved |
| 0.53 | `/settings/matters?tab=templates` → "Matters — Templates · Settings", heading "Matter Templates" |
| 0.54 | 6 templates (≥4): Collections (Debt Recovery) 9 tasks · Commercial (Corporate & Contract) 9 · Deceased Estate Administration 9 · Litigation (Personal Injury / General) 9… |
| 0.55 | `/settings/connections?tab=automations` → "Connections — Automations · Settings", heading "Automations" |
| 0.56 | 10 rules listed (doc allowed zero): Pending Approval Aging, Reconciliation Overdue Reminder, Investment Maturity Reminder, Matter Onboarding Reminder, Request Complete Fo… |
| 0.57 | `/settings/firm?tab=features` → "Firm — Features · Settings", heading "Features" |
| 0.58 | Only two toggles: Information Requests (on) and Bulk Billing Runs (on). No court_calendar / conflict_check / lssa_tariff / trust_accounting toggles. Observation: copy "Cu… |
| 0.59 | Sidebar (all zones expanded): TRUST = Overview · Transactions · Client ledgers · Reconciliation · Interest · Investments · Trust reports (7); Today zone has Diary; Matter… |
| 0.60 | Full sidebar text contains no "Engagements", "Year-End Packs", "Campaigns", "Projects", "Customers", "Invoices" |
| 0.61 | Trust zone › Overview → `/org/mathebula-partners/trust-accounting`, heading "Trust Accounting — LSSA-compliant trust account management for client funds", empty state "No… |
| 0.62 | Add Account dialog: Account Name "Mathebula & Partners Trust Account", Bank Name "Standard Bank", Branch Code 051001, Account Number 1234567890 (test values; dialog requi… |
| 0.63 | No LPFF field on the account dialog; set via Trust › Interest (`/trust-accounting/interest`) › LPFF Rate History › Add Rate: Effective 2026-10-22, Rate % 6.5, LPFF Share… |
| 0.64 | Overview dashboard: Trust Balance R 0,00 ("Mathebula & Partners Trust Account cashbook balance"), Active Clients 0, Pending Approvals 0, Reconciliation "Not yet reconcile… |
| 0.65 | `/settings/firm?tab=subscription` → "Firm — Subscription · Settings"; panel heading "Billing" |
| 0.66 | Panel text is only "Billing · Trial · Manual · Managed Account — Your account is managed by your administrator." No Starter/Pro/Business picker, no Upgrade button, no pla… |
| 0.67 | Status badge "Trial" (= TRIALING; read-only `public.subscriptions` row TRIALING, billing method MANUAL) + "Managed Account" card; no member count, monthly amount, tier or… |
| 0.68 | 📸 Settings › Firm › Subscription, flat UI, no tier selector (Read back: real app, logged in as Thandi, branded logo in sidebar) |
| CP0.1 | Currency ZAR (0.38), brand #1B3A4B (0.39), logo set (0.40); DB `brand_color=#1B3A4B`, `logo_s3_key=org/tenant_5039f2d497cf/branding/logo.png` |
| CP0.2 | Billing + cost rates for 3 members (0.42, 0.43) |
| CP0.3 | VAT — Standard 15% default, active (0.45) |
| CP0.4 | `legal-za-customer` and `legal-za-project` groups loaded (0.47, 0.48) |
| CP0.6 | 4 named matter templates present (6 total) (0.54) |
| CP0.7 | Diary, Conflict check, Tariffs, Trust zone visible (0.59); `enabled_modules` includes court_calendar, conflict_check, lssa_tariff, trust_accounting; no foreign vertical t… |
| CP0.8 | Trust account created (0.62), LPFF 6.5% (0.63) |
| CP0.9 | Subscription page flat, no upgrade UI (0.66–0.68) |

### Days 1–7 — 44 PASS rows (`days-01-07.md`)

| Step | What was observed |
|---|---|
| 1.1 | Cookies cleared; `/dashboard` → KC two-step login bob@mathebula-test.local / SecureP@ss2 → `/org/mathebula-partners/dashboard`; sidebar footer "Bob Ndlovu"; Bob (Admin) s… |
| 1.2 | Matters zone › "Conflict check" → `/org/mathebula-partners/conflict-check`, h1 "Conflict Check", tabs Run Check / History (0) |
| 1.3 | Name "Sipho Dlamini", Check Type New Client → Run Conflict Check → green card with check icon, heading "No Conflict", "Checked "Sipho Dlamini" at 22/10/2026, 17:28:09", b… |
| 1.4 | 📸 green "No Conflict" result card (Read back: real data, Bob logged in) |
| 1.5 | Clients zone › Clients → `/clients`, h1 "Clients", "0 clients", empty state "No clients yet" |
| 1.6 | "New Client" → dialog "Create Client — Step 1 of 2" |
| 1.7 | Name "Sipho Dlamini", Email `sipho.dlamini@email.co.za`, Phone "+27-82-555-0101" filled on step 1 |
| 1.8 | Promoted fields render inline on step 1 as native inputs: Type = Individual (client_type), Address Line 1 "42 Commissioner St", City "Johannesburg", Postal Code "2001", C… |
| 1.9 | Create navigated straight to the client detail; back on `/clients` the list shows "1 client": Sipho Dlamini · Individual · Lifecycle Prospect · FICA "Not started" · 0 ope… |
| 1.10 | `[data-testid=client-header-card]` present; `[data-testid=lifecycle-badge]` text "Prospect". The header also shows a second green "Active" badge (record status) next to "… |
| 1.11 | `tab-group-details` dropdown = Details · Custom fields · Tags. `tab-item-details` (`?tab=details`) renders Address "42 Commissioner St / Johannesburg, 2001 / ZA" + Primar… |
| 0.50 (back-ref, closes day-00 0.50 / CP0.5) | On Sipho's saved client (`?tab=fields`), CustomFieldSection labels = {ID / Passport Number, Postal Address, Preferred Correspondence, Referred By}; inputs `cf-id_passport… |
| 2.1 | Header card lifecycle badge "Prospect" → header primary action "Start Onboarding" → AlertDialog "Start Onboarding — This will move the customer to Onboarding status and a… |
| 2.2 | `tab-group-compliance` ("FICA & compliance") dropdown = FICA onboarding · Information requests → `tab-item-onboarding` (`?tab=onboarding`) shows "Legal Individual Client… |
| 2.3 | "0/9 completed (0/8 required)". Items in order: Proof of Identity, Proof of Address, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Sign… |
| 2.4 | Client `tab-group-work` (label "Matters") → `tab-item-documents` → Upload Document → dialog "Upload Customer Document" → chose "Sipho Dlamini — certified FICA pack.pdf" (… |
| 2.5 | Mark Complete on each item in order; the six document items got "Sipho Dlamini — certified FICA pack.pdf" from the combobox (Confirm stays disabled until a document is ch… |
| 2.6 | Checklist header "Legal Individual Client Onboarding — Completed — 9/9 completed (8/8 required)"; DB `checklist_instances` `6eafdf75-1c91-444f-be52-8f9e5a67b12a` status C… |
| 2.7 | On the last Confirm the client auto-transitioned: `lifecycle-badge` "Active", header primary action "Edit" (no manual Activate Customer). Audit `checklist.instance.comple… |
| 2.8 | `client-header-card` shows lifecycle badge "Active" (next to the record-status badge, also "Active"; see G-B1) |
| 3.1 | Client `tab-group-work` ("Matters") › Matters → "New Matter" link (`/matters?new=1&customerId=ce83c576…`) → dialog "New from Template — Select Template" listing the 6 tem… |
| 3.2 | Picked "Litigation (Personal Injury / General)" → Next → "New from Template — Configure", "Creating from: Litigation (Personal Injury / General)", Client pre-selected "Si… |
| 3.3 | Matter name "Sipho Dlamini v. Standard Bank (civil)"; Work Type pre-filled LITIGATION from the template. The configure dialog has no case_number / court_name inputs, so t… |
| 3.4 | Create Matter → `/matters/458bc331-d51e-4a94-99d8-32ecad3de00e`; header "Sipho Dlamini v. Standard Bank (civil) · Active · LITIGATION · Sipho Dlamini"; Work › Tasks shows… |
| 3.5 | Client › Matters tab: "Matters 1" row "Sipho Dlamini v. Standard Bank (civil)", created 22 Oct 2026, Unlink action. Header subtitle now "1 engagement" (G-B1) |
| 3.6 | Work › Tasks "Tasks 9": Initial consultation & case assessment, Letter of demand, Issue summons / combined summons, File plea / exception / counterclaim, Discovery -- req… |
| 3.9 | Work › Correspondence (`tab-item-correspondence`): "No correspondence yet — Inbound correspondence filed against this matter will appear here. Email is filed via your fir… |
| 3.10 | `tab-group-work` dropdown = exactly four: Tasks · Documents · Correspondence · Generated documents (no Staffing) |
| 4.1 | Matter header "Generate Document" menu (13 templates incl. "Engagement Letter — Litigation"; also lists "Project Summary Report", G-B1) → "Generate: Engagement Letter — L… |
| 4.3 | "Save to Documents" → "Document saved successfully" + inline "Send for Acceptance". The letter is listed under Work › Generated documents (the location 4.3a names): "Enga… |
| 4.3a | Row 1 (the saved letter, generated_document `9c8491ef-1c45-4bf5-84d7-f21c9bc565a7`) → Send for Acceptance → dialog "Send for Acceptance: Engagement Letter — Litigation":… |
| 4.3c | Mailpit "Confirmed: You have accepted engagement-letter-litigation-sipho-dlamini-v-standard-bank-civil-2026-10-22.pdf" to Sipho 15:35:55Z. Cleared cookies, logged back in… |
| 4.4 | Matter Fees › Time recording says "Log time on tasks…" (no direct entry), so logged on task "Issue summons / combined summons" › Log Time: 1 h 30 m, date 2026-10-22, "Dra… |
| 4.5 | Task detail › Time Entries row: "22 Oct 2026 · 1h 30m · Bob Ndlovu · Unbilled · R 1 800,00 · Drafting particulars of claim". DB `time_entries` billing_rate_snapshot 1200.… |
| 5.1 | `/matters/458bc331-d51e-4a94-99d8-32ecad3de00e` (Sipho's litigation matter) loaded, h1 "Sipho Dlamini v. Standard Bank (civil)" |
| 5.3 | Work › Tasks (9 tasks), Fees › Time recording (1h 30m, Bob), Work › Documents (engagement letter row), Client › Client comments ("No customer comments yet. Comments share… |
| 5.5 | Sidebar Fees zone = Unbilled work · Fee notes · Fee runs · Disbursements (`/legal/disbursements`). Page h1 "Disbursements — Manage client disbursements — matter costs pai… |
| 5.6 | As Bob: "New Disbursement" (`create-disbursement-trigger`) → `[data-testid=create-disbursement-dialog]` "New Disbursement" open |
| 5.7 | Matter "Sipho Dlamini v. Standard Bank (civil)", Client Sipho Dlamini, Category Sheriff Fees (`SHERIFF_FEES`), Description "Sheriff fee — service of process", Amount 750,… |
| 5.8 | List "1 disbursement": 2026-10-22 · matter · Sheriff Fees · R 750,00 · Draft · Unbilled → row → detail `/legal/disbursements/a5aafdc4-20be-4bc8-a940-e77172002cca` "Sherif… |
| 5.11 | As Bob on the sheriff-fee detail: Approve → dialog "Approve Disbursement" (optional notes) → Approve Disbursement → badge "Approved", Unbilled. DB `approval_status` APPRO… |
| 5.13 | Disbursements page, matter tab and dialogs say "Disbursement(s)"; page-text scan for "Expense"/"expense" = 0 hits |
| 6.2 | As Bob: internal comment on matter task "Initial consultation & case assessment" (task detail › Comments; Visibility "Internal only"): "Need to confirm court date by Mond… |
| 6.5 | As Bob: matter Work › Documents → drop zone → "Particulars of Claim — draft v1.pdf" (613 B) → row "Particulars of Claim — draft v1.pdf · 613 B · Uploaded · 22 Oct 2026" (… |

### Days 8–21 — 31 PASS rows (`days-08-21.md`)

| Step | What was observed |
|---|---|
| 8.1 | As Thandi (KC two-step login, sidebar footer "Thandi Mathebula"): Conflict check "Moroka Family Trust" → "No Conflict" (17:44:09 local); "Peter Moroka" → "No Conflict" (1… |
| 8.2 | New Client step 1: Name "Moroka Family Trust", Type Trust, Tax Number "9123456789", Address Line 1 "8 Rissik St", City "Johannesburg", Country South Africa (ZA), Business… |
| 8.3 | No beneficial_owners custom field offered (step 2 pack = ID/Passport, Postal Address, Preferred Correspondence, Referred By). Names recorded in the checklist item instead… |
| 8.4 | Header "Start Onboarding" → alert dialog → Start Onboarding → `lifecycle-badge` "Onboarding" |
| 8.5 | Client Matters › Documents: uploaded "Moroka Family Trust — FICA pack.pdf" (the walker's script and the MCP file chooser both fired, so the file is listed twice; walker a… |
| 8.6 | On the 12th Confirm (Sanctions Screening) the badge flipped Onboarding → Active without any manual action; header action "Edit". DB checklist COMPLETED 15:45:35Z, lifecyc… |
| 9.1 | Moroka client › Matters › New Matter → "New from Template — Select Template" → "Deceased Estate Administration" (9 tasks) → Next; Client pre-set to Moroka Family Trust |
| 9.2 | Matter name "Estate Late Peter Moroka" (pattern preview had offered "Moroka Family Trust - Estate"); Matter lead set to Thandi Mathebula (senior partner handles estate wo… |
| 9.3 | Work Type pre-filled ESTATES from the template; DB `projects.work_type = ESTATES` |
| 9.4 | Work › Tasks "9": Obtain death certificate & will, Report estate to Master of High Court, Obtain Letters of Executorship, Advertise for creditors (Gazette + local newspap… |
| 10.1 | As Thandi: task "Obtain death certificate & will" › Log Time 3 h 0 m "Initial consultation with heirs + document gathering", Billable, dialog "Billing rate: R 2 500,00/hr… |
| 10.2 | Matter Work › Documents → uploaded "Trust Deed — Moroka Family Trust.pdf" → row "614 B · Uploaded · 22 Oct 2026". (Clicking group/sub-tab items did not switch tabs while… |
| 11.1 | As Bob (Admin; not a matter member but can open the matter): task "Report estate to Master of High Court" › Log Time 1 h 30 m "Master of High Court filing preparation", r… |
| 12.1 | As Thandi: task detail status combobox Open → (options Open / In Progress / Cancelled only) → In Progress → Done → status "Done" with a Reopen action. DB task status DONE… |
| 12.2 | Trust › Transactions › Record Transaction › Record Deposit: Client Moroka Family Trust, Matter Estate Late Peter Moroka, Amount 50000, Reference "Initial fund for estate… |
| 12.3 | Trust › Overview: Trust Balance R 50 000,00 ("Mathebula & Partners Trust Account cashbook balance"), Active Clients 1, Pending Approvals 0, Recent Transactions shows the… |
| 14.2 | As Thandi: New Client "Khumalo Property Holdings", Type Individual, Address Line 1 "12 Rivonia Road", City "Sandton", Country ZA; email required by the dialog, test value… |
| 14.3 | Client › New Matter → "Property Transfer (Conveyancing)" (12 tasks) → name "Khumalo Property Holdings — Erf 1234 Sandton Transfer", lead Thandi; Work Type pre-filled CONV… |
| 14.5 | Filled Property Address "12 Rivonia Road, Sandton, 2196", Erf Number "1234", Deeds Office "Johannesburg" → Save Custom Fields → "Saved successfully". DB `custom_fields` {… |
| 14.6 | After reload the Custom fields inputs re-render the saved values (`cf-property_address`, `cf-erf_number`, `cf-deeds_office=JOHANNESBURG`); the OTP preview (14.7) also pic… |
| 14.7 | Generate Document › "Offer to Purchase" → preview (no clause-selection step) "Mathebula & Partners / Offer to Purchase", Purchaser Khumalo Property Holdings, Property Add… |
| 14.9 | Generated documents › OTP row › "Send for Acceptance" (button present for this template) → dialog "Send for Acceptance: Offer to Purchase" |
| 14.10 | Recipient "Khumalo Property Holdings (khumalo.holdings@email.co.za)" (portal contact auto-created `ca957639-a8ad-4172-b725-c16181572c70`), Expiry 7 → Send → badge "Awaiti… |
| 14.11 | Mailpit message to khumalo.holdings@email.co.za 15:52:14Z with link `http://localhost:3002/accept/<redacted>`. Its subject reads "Mathebula & Partners -- Please review en… |
| 15.1 | Estate matter › `tab-group-finance` (Fees) › "Fee estimate" → empty state "No fee estimate set — … Choose between fixed-price or time-and-materials." + "Set fee estimate" |
| 15.2 | "Set fee estimate" → dialog "Set Budget — Configure budget limits for this project…" (G-B1): Budget Hours 40, Budget Amount 80000, Currency "ZAR — South African Rand", al… |
| 15.4 | "Budget Status: On Track" (both Hours and Amount bars "On Track"; < 50% consumed) |
| 16.1 | 5 more entries (all Log Time on estate tasks, today's date): Thandi 3h "Reporting estate to the Master — J294 + inventory", Thandi 2h30 "Letters of Executorship applicati… |
| 18.1 | Estate matter Work › Documents: uploaded "Inventory draft.pdf" and "Bank statements.pdf" (one multi-file pick) → both rows "597 B · Uploaded · 22 Oct 2026" alongside Trus… |
| 20.1 | Task detail status Open → In Progress → Done on "Report estate to Master of High Court" and "Obtain Letters of Executorship"; Tasks "Done" filter lists them plus "Obtain… |
| 21.1 | As Bob: Fee estimate "Budget Status: On Track" — Hours 40h / consumed 18h / remaining 22h / used 45%; Amount R 80 000,00 / consumed R 35 900,00 / remaining R 44 100,00 /… |

### Days 22–35 — 19 PASS rows (`days-22-35.md`)

| Step | What was observed |
|---|---|
| 22.1 | As Bob: Matters zone › Conflict check (`/conflict-check`) → Name "Lerato Mthembu", Check Type New Client → Run Conflict Check → result card "No Conflict — Checked "Lerato… |
| 22.2 | Clients › New Client → Step 1: Name "Lerato Mthembu", Type Individual (client_type), Email `lerato.mthembu@email.co.za` (walker test value; Email is required, HANDOFF-B g… |
| 22.3 | Header "Start Onboarding" → AlertDialog → Start Onboarding → lifecycle "Onboarding", "Legal Individual Client Onboarding — In Progress 0/9 (0/8 required)". Uploaded "Lera… |
| 22.4 | Client › Matters › "New Matter" link → "New from Template — Select Template" (6 templates) → "Litigation (Road Accident Fund -- RAF)" → Next → Configure: Client pre-selec… |
| 22.5 | Matter name typed "Lerato Mthembu — RAF Claim (MVA 2025-11-03)" (template default was "Lerato Mthembu - RAF Claim"); h1 and DB `projects.name` match exactly |
| 23.2 | Today zone › Diary → `/diary`, h1 "Diary — Court dates, prescription dates, matter due dates and task due dates.", Week view 19–25 Oct 2026 "Nothing in the diary for this… |
| 23.3 | New court date → dialog "Schedule Court Date": Matter "Lerato Mthembu — RAF Claim (MVA 2025-11-03)", Type Case Management (closest option to "Case Management Conference";… |
| 23.4 | Diary "Go to date" 2026-12-06 → `?from=2026-11-30`, Week "30 November to 6 December 2026": Sun 6 Dec card "Court date · 10:00 · Gauteng High Court — Case Management Confe… |
| 23.6 | 📸 Diary week 30 Nov – 6 Dec with the RAF court date card on Sun 6 Dec (hovered); second shot List view December 2026 with the full event text. Both PNGs read back: real d… |
| 24.1 | As Bob: RAF matter Work › Tasks › "Statutory medical reports & assessments" (assigned Bob) › Log Time → dialog shows "Billing rate: R 1 200,00/hr (member default)" → 4 h,… |
| 26.1 | Same task › Log Time → 3 h "Expert witness statements" → saved. DB `e35ea964-d771-43a4-9263-999925883e17` 180 min, 1200 / 500 |
| 28.1 | Matter Work › Documents (empty state) → drop zone → picked "RAF medical report — Dr Naidoo.pdf" + "RAF medical report — hospital records.pdf" → after reload both rows "61… |
| 33.1 (Days 24–35) | As Thandi: RAF task "Court action if no settlement (Section 24 RAF Act)" › Comments → typed "Ready for summons @Bob" (Internal only) → Post Comment → shown under "Thandi… |
| 35.1 | As Bob: task detail status Open → In Progress → Done (Done is not offered from Open: options Open / In Progress / Cancelled) on 4 template tasks: Initial RAF claim assess… |
| 33.1 (Day 33) | As Thandi: Firm zone › Reports (`/reports`) → card "Firm performance — Profitability — Team utilization, matter profitability, and client profitability…" → Run report → `… |
| 33.2 | Matter Profitability (From 2026/10/01 To 2026/10/22) lists the three story matters with Billable Hours / Revenue / Cost / Margin / Margin %: Estate Late Peter Moroka 18.0… |
| 33.3 | Every amount is ZAR: CURRENCY column "ZAR", amounts "R 35 900,00" (space thousands separator, comma decimals) in all three tables |
| 33.4 | No "N/A" or "–": every matter and client row has non-zero revenue, cost and margin. Team Utilization: Bob 15.5h, Thandi 11.0h, 100% billable (Carol absent: 0 h, G-B3) |
| 33.5 | 📸 full-page Profitability: Team Utilization + Matter Profitability (3 matters, Margin and Margin % columns in green) + Client Profitability, ZAR throughout. Read back: re… |

### Days 36–60 — 21 PASS rows (`days-36-60.md`)

| Step | What was observed |
|---|---|
| 30.1 | As Thandi: Sipho's matter (the oldest active matter) › Fees group › "Statements of account" → `[data-testid=project-statements-tab]` "Statements of Account — No statement… |
| 30.2 | "Generate Statement of Account" (`open-generate-statement-btn`) → `[data-testid=statement-of-account-dialog]` "Generate a statement of account for Sipho Dlamini v. Standa… |
| 30.3 | Period start = matter-opened date 2026-10-22 (all walk data is dated today), end 2026-10-22. The dialog has one action, "Preview & Save" (not "Generate" then "Save"); cli… |
| 30.5 | Preview & Save persisted it: after closing, Fees › Statements of account lists "22 Oct 2026 · Closing balance owing R 2 820,00 · Trust balance held R 0,00 · Download"; Wo… |
| 36.1 | Sipho's matter has no "Billing" tab (Fees group = Time recording · Disbursements · Fee estimate · Tariffs · Financials · Statements of account · Trust); 617B flagged this… |
| 36.2 | Fetch Unbilled Time → "Select Unbilled Items": matter "Sipho Dlamini v. Standard Bank (civil) — 1 of 1 selectable": "Issue summons / combined summons · Bob Ndlovu · 22 Oc… |
| 36.3 | All time entries selected. The sheriff disbursement was deliberately deselected so it stays UNBILLED for the Day-45 write-off (45.5 needs an APPROVED/UNBILLED Sipho disbu… |
| 36.5 | Subtotal R 1 800,00 · VAT — Standard (15%) R 270,00 · Total R 2 070,00 (15% × 1 800 = 270 ✓) |
| 36.6 | `/invoices` "Fee notes 1": FEE NOTE "Draft" · CLIENT Sipho Dlamini · STATUS Draft · R 2 070,00 · ZAR. Fee note `00939152-e774-464f-874f-c18c3a92fa1a` |
| 38.2 | Fee note › Audit trail panel shows rows (all labelled just "Invoice" · Thandi Mathebula, plus "Payment Session Created · System"); DB `invoice.created` 16:13:25 → `invoic… |
| 38.4 | New fee note for Sipho → Fetch Unbilled Time → "No unbilled time entries found for this period."; only the Sheriff fee disbursement (R 750,00) remains → Cancel. The bille… |
| 45.1 | Moroka matter has no "Billing" tab (same Fees group as 36.1; 617B-flagged pre-existing mismatch). Used the 36.1 alternative: client Moroka Family Trust › Fees › Fee notes… |
| 45.2 | Fetch Unbilled Time → "Estate Late Peter Moroka — 7 of 7 selectable": Bob 1h30 / 3h / 2h30, Thandi 2h30 / 3h / 2h30 / 3h = 18 h, Total R 35 900,00 (Days 10–21 entries) →… |
| 45.3 | The LSSA tariff module does surface tariff line items, on the fee-note draft: "Add Tariff Items" → dialog lists "LSSA 2024/2025 High Court Party-and-Party" Sections 1–7 (… |
| 45.4 | Approve → "INV-0002 · Approved" → Send fee note → "Sent" (email to moroka.trust@) → Generate Document › Fee Note → preview → Save to Documents. Same two-step Approve/Send… |
| 45.5 | Fees zone › Disbursements (`/legal/disbursements`, filters Matter / Category / Approval / Billing) → "1 disbursement: 2026-10-22 · Sipho Dlamini v. Standard Bank (civil)… |
| 47.1 | INV-0002 › Generated Documents › saved "Fee Note" row › icon button "Download PDF" (title only; the three row icons have no accessible name) → `fee-note-inv-0002-2026-10-… |
| 47.3 | 📸 taken (two shots: header with logo, then scrolled to line items and Amount Summary). Read back: real fee note INV-0002 in the "Generate: Fee Note" PDF preview, showing… |
| 50.1 | As Thandi: client Lerato Mthembu › Fees › Fee notes › New fee note → Fetch Unbilled Time → "Lerato Mthembu — RAF Claim (MVA 2025-11-03) — 2 of 2 selectable" (Bob 4h R 4 8… |
| 55.2 | Header "INV-0001 · Paid", "Payment Received — Paid on: 22 Oct 2026 — Reference: EFT — Sipho Dlamini — INV-0001"; Payment History "Completed · Manual · R 2 070,00" (the mo… |
| 58.1 | Firm › Audit trail (`/settings/firm?tab=audit-trail`, "Audit log … 180 total"): top row "22 Oct 2026, 18:21 · INFO · Invoice / invoice.paid · Thandi Mathebula · invoice 0… |

### Days 61–75 — 5 PASS rows (`days-61-75.md`)

| Step | What was observed |
|---|---|
| 61.1 | As Thandi: Clients › New Client → "Nomvula Khoza", Individual, `nomvula.khoza@email.co.za` (walker test value), +27-83-555-0144, 18 Jan Smuts Avenue, Johannesburg, Gauten… |
| 65.1 | Fees zone › Fee notes › New fee note → client picker "Nomvula Khoza" → client Fees › Fee notes → New fee note → Fetch Unbilled Time: "4 of 4 selectable", Total R 6 137,50… |
| 72.1 | As Thandi: Trust › Transactions › Record Transaction → Record Payment (menu: Deposit / Payment / Transfer / Fee Transfer / Refund) → Client Moroka Family Trust, Matter Es… |
| 72.2 | Trust › Client ledgers: Moroka Family Trust — Trust Balance R 38 000,00, Total Deposits R 50 000,00, Total Payments R 12 000,00, Fee Transfers R 0,00 (50 000 − 12 000 ✓).… |
| 75.1 | Sipho's matter › Complete Matter → dialog "Mark … as completed? This will prevent new tasks and time entries" → Complete → refused inline "Project has 9 open task(s). Com… |

### Days 76–90 — 22 PASS rows (`days-76-90.md`)

| Step | What was observed |
|---|---|
| 80.1 | As Thandi: sidebar Firm zone (Team · Reports · Audit trail · Settings) → Reports → `/reports`, h1 "Reports": Firm performance › Profitability; Financial › Invoice Aging R… |
| 80.2 | Reports › Profitability (`/reports/profitability`): Team Utilization, Matter Profitability and Client Profitability load with all walk data across 4 matters / 4 clients:… |
| 80.3 | Team Utilization: Bob 17.5 h (17.5 billable, 0 non-billable) 100.0%; Thandi 12.0 h 100.0%; Carol 2.3 h 100.0% (no non-billable time was ever logged, so 100% is the correc… |
| 85.1 | As Thandi: Firm › Audit trail → `/settings/firm?tab=audit-trail`, "Audit log — Read-only feed of audit events", 243 total (DB count 243), filters From / To / Actor ID / E… |
| 85.2 | Actor filter accepts only a UUID ("Actor ID", placeholder UUID; no member picker). Thandi's id is shown in the hover tooltip on her name ("Actor ID: 26ee05c9-159c-4fb8-89… |
| 85.4 | Each row: Time "22 Oct 2026, 18:37", Severity INFO/NOTICE/CRITICAL, Event label + key (e.g. "Matter Closure Closed · matter_closure.closed"), Actor, Entity (linked). Expa… |
| 85.5 | Pre-state: estate matter ACTIVE, trust R 38 000,00. As Bob (Admin): estate matter header "Close Matter" (rendered for Admin) → `matter-closure-dialog` opens |
| 85.6 | Step 1: "Matter trust balance is R38000.00. Transfer to client or office before closure. Fix this" (failing), also "6 tasks remain open" (failing); the other 7 gates pass |
| 85.10 | As Thandi (Owner): same matter → Close Matter → dialog opens |
| 85.11 | Step 1 shows the same failing gate "Matter trust balance is R38000.00 …" (+ "6 tasks remain open") |
| 85.12 | Continue → Step 2 with the "Override failing gates — This matter has failing gates. Override requires justification and will be logged." section (toggle present; justific… |
| 85.13 | Toggled `matter-closure-override-toggle` → `matter-closure-override-justification-input` appears ("Override justification (≥ 20 characters)") |
| 85.14 | Entered verbatim "Trust remainder of R12,000 earmarked for final Section 35 payout pending master approval — closing under owner override" (doc text; the real remainder i… |
| 85.15 | Close matter (`matter-closure-confirm-close-btn`) → toast "Matter closed" |
| 85.16 | Header "Estate Late Peter Moroka · Closed · ESTATES", action "Reopen Matter". DB `matter_closure_log` override_used true, justification stored verbatim, gate_report all_p… |
| 85.18 | 📸 The product splits this across two dialog steps, so it is two shots: Step 1 failing trust gate (`85.11-thandi-gates-trust-failing.png`) and Step 2 with override toggle… |
| 88.1 | Estate matter → "Activity" group tab (opens a menu: Activity / Audit trail) → Activity → feed with filters All · Tasks · Documents · Comments · Members · Time + "All acto… |
| 90.2 | Reopened Client (step 1 + step 2), Matter (`/matters/new` page and the template Configure dialog), Task, Fee Note dialogs: promoted fields render as native inputs (client… |
| 90.3 | Six zones TODAY · MATTERS · CLIENTS · FEES · TRUST · FIRM; Trust zone 7 items; Diary (Today), Conflict check (Matters), Tariffs (Fees) present; no Projects/Customers/Invo… |
| 90.4 | `/settings/firm?tab=subscription`: "Billing · Trial · Manual · Managed Account — Your account is managed by your administrator."; no tier picker, no Upgrade, no plan text… |
| 90.5 | Console listener on every navigation: 28 top-level nav pages + 31 settings tabs → 0 console errors, 0 page errors (only React DevTools/HMR info and the dev "scroll-behavi… |
| 90.6 | Mailpit: 19 messages total; search subject "Undelivered" 0, from mailer-daemon 0; backend.log mail-failure lines 0; tenant `email_delivery_log` SENT 6, no FAILED |

### Exit checkpoints E.1–E.11 — 5 PASS rows (`exit-checkpoints.md`)

| Step | What was observed |
|---|---|
| E.4 | Tier removal observed on 3+ screens: Settings › Firm › Subscription flat ("Trial · Manual · Managed Account", no tier/upgrade; 0.65–0.68 Walker A, 90.4 C2 fresh); team in… |
| E.5 | Field promotion on create dialogs: Client (0.49 + 0.50 back-ref PASS, 1.x, 90.2 fresh), Matter (0.51 Work Type, 90.2 fresh: Reference/Work Type/Priority native), Task (90… |
| E.6 | Progressive disclosure: Trust zone (7 items), Diary, Conflict check, Tariffs present (0.59, 90.3 fresh); `enabled_modules` legal set (CP0.7); sidebar has no Projects/Cust… |
| E.7 | Walker A observed the full Keycloak path with no mock IDP: `/request-access` → OTP (Mailpit) → padmin approval → owner KC invitation → Thandi registers → Bob + Carol invi… |
| E.10 | One pass, Session 0 → Day 90, on one stack with the same service PIDs for all walkers (backend 60278 · gateway 60449 · frontend 60581 · portal 60640); no restart, no rese… |

## Appendix B — Every FAIL, DEFERRED, BLOCKED and N/A row

Generated from the evidence files; text truncated to about 320 characters. BLOCKED rows (5.10, 6.4) count as DEFERRED in the totals. Day 0 rows 0.50 and CP0.5 show as DEFERRED here but count as PASS (closed by the Days 1–7 back-reference row).

### Day 0 — firm onboarding (Phases A–K) (`day-00.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 0.14 | FAIL | No detail view exists: clicking the row does nothing (no navigation, no drawer). All five submitted fields + submitted time + status are shown inline in the table row instead (`access-requests-table.tsx` renders only a row with Approve/Reject). Doc expects "Click into the request → detail view" | G-A1 |
| 0.50 | DEFERRED | The doc's location (client detail › `tab-group-details` → `tab-item-fields`) needs a saved client; Day 0 has none and 0.52 forbids saving. Supplementary observation (not the doc's check): the dialog's Step 2 "Additional Information" CustomFieldSection lists only SA Legal — Client Details (ID / Passport Number, Postal A… | — |
| CP0.5 | DEFERRED | Inline parts PASS (0.49 seven client slugs, 0.51 Work Type); the "no duplicates in CustomFieldSection" part depends on 0.50 (DEFERRED to Walker B, needs a saved client) | — |

### Days 1–7 (`days-01-07.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 2.9 | FAIL | The client's own audit view (Activity tab, `?tab=audit`) lists only 3 rows: "Customer Lifecycle Transitioned" ×2 and "Customer Created" (Bob). None of the FICA completion events (9× `checklist.item.completed`, `checklist.instance.completed`) appear for the client, and the transition rows do not say from/to. The events… | G-B2 |
| 3.7 | FAIL | Task detail sheet for "Initial consultation & case assessment" → Assignee combobox lists only "Unassigned" (search "Search members…"). Bob and Carol cannot be picked. The task assignee options come from the matter's members, and the matter has 0 member rows (`project_members` count 0). The matter page offers no way to… | G-B3 |
| 3.8 | FAIL | h1 is the matter name; header actions read "Close Matter / Complete Matter", More actions "Edit Matter / Archive Matter"; no "Project" in the header. But the breadcrumb reads "Mathebula & Partners › Matters › Matter", not "Clients > Sipho Dlamini > Matter > …". Copy leaks on the same page: Overview card "Customer: Siph… | G-B4, G-B1 |
| 4.2 | FAIL | Preview (Step 2 of 2) fills client context correctly ("Dear Sipho Dlamini", Client, Matter, Case Number JHB/CIV/2026/001, Court Gauteng High Court, Johannesburg). But no Mathebula letterhead branding: the header is the plain text "Mathebula & Partners" in default #1a1a2e; no logo image and brand colour #1B3A4B not appl… | G-B5, G-B1 |
| 4.3b | FAIL | Mailpit message to sipho.dlamini@email.co.za, subject exactly "Mathebula & Partners -- Please review engagement letter for acceptance: engagement-letter-litigation-sipho-dlamini-v-standard-bank-civil-2026-10-22.pdf", link `http://localhost:3002/accept/<redacted>`. Fresh context (cookies + local/session storage cleared)… | G-B7 |
| 4.6 | FAIL | Matter Activity › Activity (newest first): "Bob Ndlovu logged 1h 30m on task …", "generated document … from template "Engagement Letter — Litigation"" ×2, "Bob Ndlovu performed project.updated on project", "Bob Ndlovu performed project.created_from_template on project". Missing: sent for acceptance and accepted (actor… | G-B8 |
| 5.2 | FAIL | Header shows only Active + LITIGATION (matter_type) + client link. `case_number` (JHB/CIV/2026/001) and `court_name` (Gauteng High Court, Johannesburg) are saved (3.3) but are not rendered inline at the top: not in the header, not on Details › Details (only Description, Created, Tags). They appear only as inputs on Det… | G-B9 |
| 5.4 | FAIL | 📸 taken (Overview after reload: Healthy, 3/5 setup steps, 1.5h hours logged, 0% task completion). The shot cannot show the wow moment as the doc describes it: case_number/court_name are not inline (5.2). It also shows "Customer: Sipho Dlamini" and a FICA card "Not Started — No FICA onboarding request yet." although Sip… | G-B9, G-B10, G-B1 |
| 5.9 | FAIL | Deeds-office disbursement: Deeds Office Fees, "Deeds office search fee", 1250, Payment Source Trust Account → dialog shows "Link a trust transaction (required)" → "Link trust transaction" opens "Link Trust Transaction — Select an approved trust disbursement payment from this matter. No approved disbursement-payment tru… | G-B11 |
| 5.10 | BLOCKED | Blocked by 5.9: no trust-linked disbursement exists for Thandi to approve | G-B11 |
| 5.12 | FAIL | Matter Fees › Disbursements (`[data-testid=project-disbursements-tab]` present) shows the table only (Sheriff Fees · R 750,00 · Approved · Unbilled). There is no "Unbilled Disbursements" summary at all (neither R2,000.00 nor R750). Matches brief A.7 `FIXME(depth-ii:matter-unbilled-disbursements-summary)`; R2,000 was al… | G-B12, G-B11 |
| 5.14 | FAIL | 📸 taken of the Disbursements list and the matter tab, but the doc's shot cannot exist: the list has a single row (Approved, no mixed statuses, since 5.9 failed) and the matter tab has no unbilled summary (5.12) | G-B11, G-B12 |
| 6.1 | FAIL | Logged in as Carol (Member). `/matters` reads "0 matters — You're not on any matters yet." Opening the matter URL directly renders the error page "Something went wrong — An unexpected error occurred while loading this page." My work › Timesheet has no task rows. Carol has no way to log 2.0 h on Sipho's matter: she is n… | G-B3 |
| 6.3 | FAIL | Carol logs in → header bell "1 unread notifications" → dropdown "Bob Ndlovu commented on task "Initial consultation & case assessment" — just now" → click routes to `/matters/458bc331…?tab=tasks` (the task list, not the comment/task) and renders the error page "Something went wrong" for Carol (not a matter member, G-B3… | G-B3 |
| 6.4 | BLOCKED | Blocked by 6.3: Carol cannot open the matter or the task, so she cannot reply | G-B3 |
| 6.6 | FAIL | After reload, Activity › Activity lists 11 events strictly newest-first (upload, document created, comment, disbursement approved/submitted/created, time logged, generated ×2, project.updated, project.created_from_template). Not "all events": the engagement letter sent/viewed/accepted events (audit `acceptance.*`) are… | G-B8 |

### Days 8–21 (`days-08-21.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 9.5 | FAIL | Choosing a Matter lead made Thandi a matter member (`project_members` Thandi = LEAD, the only member) and the template pre-assigned 7 of 9 tasks to her ("Advertise for creditors" and "Open estate bank account" Unassigned). First 4 → Thandi: done (assigned "Advertise for creditors" to Thandi via the task Assignee picker… | G-B3 |
| 13.1 | FAIL | As Carol: `/matters` "0 matters — You're not on any matters yet."; estate matter URL → "Something went wrong". Carol cannot log 1.0 h (not a member; no UI to add her) | G-B3 |
| 14.1 | FAIL | Comments cannot carry attachments: the task and document comment forms have only text + Visibility + Post Comment. Closest path used: uploaded "Master's appointment.pdf" to the estate matter's Documents, expanded its comment thread, posted "Master's appointment received" (Thandi, Internal only) → shown under the docume… | G-B15 |
| 14.4 | FAIL | Details › Custom fields shows group "SA Conveyancing — Matter Details" (slug `conveyancing_za_matter`) with 8 fields: Conveyancing Type, Property Address, Erf Number, Deeds Office, Lodgement Date, Purchase Price (+ currency), Transfer Duty (+ currency), Bond Institution. Registration Date and Deed Number are not render… | G-B17 |
| 14.8 | FAIL | The generated OTP has no conveyancing clauses: the generate flow skipped the "Select Clauses" step (the engagement letter had 6), and the preview's "Conditions of Sale" heading is followed by nothing but the signature block. Matches brief A.7 `FIXME(depth-ii:otp-clause-associations)` | G-B18 |
| 14.12 | FAIL | Fresh context (cookies + storage cleared) → `/accept/<token>` renders firm name, file name, Full name + "I Accept" only. The OTP preview does not render (same X-Frame-Options DENY console error as 4.3b, G-B7) and there is no Decline action. Not accepted (doc asks only to verify the panel); badge now "Viewed" (DB VIEWED… | G-B7, G-B19 |
| 14.13 | FAIL | 📸 taken: conveyancing matter, Work › Generated documents with "Offer to Purchase" and acceptance badge "Viewed" (Read back: real data, Thandi logged in). The doc's single shot of "10 custom fields + OTP listed + badge" is not possible: only 8 fields render (14.4), and fields and documents sit on different tabs; second… | G-B17 |
| 15.3 | FAIL | Panel: Hours budget 40h, consumed 4h 30m, 11% used; Amount R 80 000,00, consumed R 9 300,00, 12%. The doc expects ~5.5 h / ~R11,000. The tracker is arithmetically right for what exists (Thandi 3 h × R2,500 + Bob 1.5 h × R1,200 = R9,300), but Carol's 1.0 h (13.1) could not be logged (G-B3), so the expected burn is not r… | G-B3 |

### Days 22–35 (`days-22-35.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 23.1 | FAIL | Matters zone › Adverse parties (`/legal/adverse-parties`, "0 parties") → Add Party → Party Type offers only Natural Person / Company / Trust / Close Corporation / Partnership / Other. There is no "Government" type. Created "Road Accident Fund" as Other (aliases "RAF", note "Government entity (statutory body…)") → list… | G-C1, G-C2, G-C3 |
| 23.5 | FAIL | Matter › Activity › Activity: the newest row is "Bob Ndlovu performed court_date.created on court_date · just now" — a raw event key with no date, type or court, so the CMC cannot be identified from the feed (same formatter defect as G-B8; also "performed adverse_party.linked on adverse_party_link", "performed project.… | G-B8 |
| 30.1 | FAIL | Logged in as Carol (KC two-step): `/matters` "0 matters — You're not on any matters yet"; RAF matter URL `?tab=tasks` → "Something went wrong". Carol cannot reach any task, so no Log Time. Matter has one member (Bob, LEAD) and no UI to add Carol | G-B3 |
| 32.1 | DEFERRED | Conditional step. The LSSA tariff module is present (Fees zone › Tariffs `/legal/tariffs`: "LSSA 2024/2025 High Court Party-and-Party · Effective from 2024-04-01 · 19 items", action Clone only). The matter has no tariff-line UI: matter Fees › "Tariffs" sub-tab is member billing-rate overrides ("No project rate override… | — |

### Days 36–60 (`days-36-60.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 30.4 | FAIL | The statement HTML is correct inside the preview iframe (read from its DOM): Professional Fees (Bob 1,5 h × R1 200,00 = R1 800,00; VAT R270,00; incl. R2 070,00), Disbursements (22 Oct 2026, SHERIFF_FEES (raw enum), Sheriff fee — service of process, R750,00), Trust Activity (opening/closing R0,00), Summary (closing bala… | G-C4, G-B11, G-B3 |
| 30.6 | FAIL | "Download PDF" (`statement-download-btn`) cannot be clicked: it sits off-screen (x ≈ 335 729 px, then 347 249 px) and Playwright reports "element is not stable" for 30 s because the preview keeps widening. A diagnostic DOM-dispatched click (not a user path) did download a 2-page 4.5 KB PDF with the same content as 30.4… | G-C4; logs/walkC1-day30-soa.txt |
| 30.7 | FAIL | 📸 taken: the dialog shows "Period start 2026/10/22" and a blank white preview running off the right edge; no HTML preview content or summary block is visible (Read back: real dialog, Thandi logged in, statement row "22 Oct 2026" behind the overlay) | G-C4 |
| 36.4 | FAIL | Draft Line Items (DESCRIPTION · PROJECT · QTY · RATE · AMOUNT · TAX): "Issue summons / combined summons -- 2026-10-22 -- Bob Ndlovu · Sipho Dlamini v. Standard Bank (civil) · 1.5 · R 1 200,00 · R 1 800,00 · VAT — Standard (15%) R 270,00". Date, attorney, hours (QTY), rate and amount are there, but the description is th… | G-C10, G-C3 |
| 38.1 | FAIL | Approve → header "INV-0001 · Approved" (not SENT; actions now Preview / Send fee note / Void). A second click, "Send fee note" → "Sent" + Online Payment Link (mock provider) + Payment History "Created R 2 070,00". So Approve and Send are separate actions; the doc expects Approve → SENT. The send went through although S… | G-C8, G-C6 |
| 38.3 | FAIL | Generate Document › "Fee Note" (menu also offers "Invoice Cover Letter") → dialog "Generate: Fee Note" with the PDF preview: firm logo present (uploaded Mathebula logo), "FEE NOTE", From Mathebula & Partners, To Sipho, Matter, Fee Note Number INV-0001, Issue Date 22 October 2026, line 1,5 × R1 200,00, VAT R270,00, Tota… | G-B5, G-B6 |
| 45.6 | FAIL | No Write Off action exists: the detail page has zero buttons (no action bar, no menu); the list row ACTIONS cell is empty; the matter's Fees › Disbursements row ACTIONS cell is empty. Matches brief A.7 `FIXME(depth-ii:no-write-off-action)` | G-C9 |
| 45.7 | DEFERRED | Cannot be attempted: no UI issues `POST …/write-off` (45.6). REST calls are forbidden, so the endpoint was not exercised. DB still `billing_status` UNBILLED | G-C9 |
| 45.8 | DEFERRED | No write-off happened (45.6), so there is no WRITTEN_OFF badge or reason to render | G-C9 |
| 45.9 | DEFERRED | No write-off audit event can exist (45.6); filter not exercised for this step | G-C9 |
| 45.10 | DEFERRED | Exclusion of a written-off disbursement cannot be observed without a write-off (45.6). Observed instead at 38.4: the still-UNBILLED sheriff fee is offered in a new Sipho draft | G-C9 |
| 45.11 | DEFERRED | No write-off reason is ever displayed (45.6), so the "Disbursement written off" wording cannot be checked | G-C9 |
| 47.2 | FAIL | Present: firm logo, "FEE NOTE", From Mathebula & Partners, To Moroka Family Trust + Client VAT Number 9123456789, Matter, INV-0002, 22 October 2026, ZAR, 8 line items incl. the LSSA tariff line, VAT per line, Subtotal R36 680,00, VAT R5 502,00, Total Due R42 182,00. Missing: brand colour (org `brand_color` = #1B3A4B, n… | G-C7, G-B5 |
| 55.1 | FAIL | INV-0001 (Sent) › Record Payment opens an inline form with only "Payment Reference (optional)": no amount field (always the full balance; part-payments impossible) and no payment-method field. Entered reference "EFT — Sipho Dlamini — INV-0001" → Confirm Payment. The full amount was recorded, but EFT is captured only as… | G-C11 |

### Days 61–75 (`days-61-75.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 62.1 | DEFERRED | Four billable entries logged per task (Log Time in the task row) across the team: Thandi 1 h "Reviewed debtor account and settled s129 demand letter" (Letter of demand, rate R 2 500,00); Bob 2 h "Drafted summons and particulars for debt claim" (Issue summons, R 1 200,00); Carol 1 h 30 m "Prepared default judgment appli… | G-D13; logs/walkC2-db-days61-62.txt, logs/walkC2-62.1-carol-403.txt; G-B3 (617C… |
| 68.1 | FAIL | Fresh context (cookies + localStorage + sessionStorage cleared) → `http://localhost:3002/login` exactly as the doc says → "Sign in to your client portal" (no firm branding) → email sipho.dlamini@email.co.za → Send Magic Link → red error "We couldn't determine which organization to sign you into. Please use the original… | G-D1 |
| 68.2 | DEFERRED | Portal (as Sipho, via the shim session): `/matters/458bc331-…` shows the matter name + status badge ACTIVE (Day 68; it closes on Day 75), Tasks "No tasks yet.", Documents table (SoA PDF with Download), Comments (Add a comment). No status/field edit controls. `/invoices` "Fee notes" table: INV-0001 PAID R 2 070,00 with… | G-D1 (617C review) |
| 70.1 | FAIL | Logged in as Carol: estate matter URL `/matters/bb2ce7c7-…` → "Something went wrong — An unexpected error occurred while loading this page." She is not a member of the estate matter and there is no UI to add her (G-B3), so she cannot log time on it. (Carol can work her own lead matter, 62.1) | G-B3 |
| 75.2 | FAIL | Two defects. (a) After 75.1 the COMPLETED matter offers no Close Matter action anywhere: header shows only "Archive" + overflow "Reopen Matter" (`matter-header-card.tsx:87` renders `MatterClosureAction` only for ACTIVE, although the component and backend `ProjectStatus` allow COMPLETED → CLOSED). The doc's 75.1 → 75.2… | G-D2 |
| 75.3 | DEFERRED | `matter-closure-dialog` › `matter-closure-step-1` lists 9 gates. First look (16:35Z) Sipho's matter failed 2 gates: "1 approved disbursements are unbilled" and "Final bill issued but … 1 unbilled approved disbursements remain" — the R 750 sheriff fee that Day 45 should have written off (impossible, G-C9). To meet the d… | G-C9 (pre-state workaround); G-C9 (617C review) |
| 75.4 | DEFERRED | Continue (`matter-closure-next-btn`) → `matter-closure-step-2` with Reason, Notes, Generate closure letter, Generate Statement of Account; no override section (all gates passed) (reclassified 617C review: PASS → DEFERRED — blocked by G-C9; ran on substituted pre-state: Sipho's R 750 sheriff disbursement was billed on I… | G-C9 (617C review) |
| 75.5 | DEFERRED | `matter-closure-reason-select` reads "Concluded" (options Concluded / Client terminated / Referred out / Other); left as is (reclassified 617C review: PASS → DEFERRED — blocked by G-C9; ran on substituted pre-state: Sipho's R 750 sheriff disbursement was billed on INV-0004 and paid (75.3) instead of being written off o… | G-C9 (617C review) |
| 75.6 | DEFERRED | `matter-closure-generate-letter-checkbox` checked by default; left ticked. ("Generate Statement of Account" is also ticked by default; left as is) (reclassified 617C review: PASS → DEFERRED — blocked by G-C9; ran on substituted pre-state: Sipho's R 750 sheriff disbursement was billed on INV-0004 and paid (75.3) instead… | G-C9 (617C review) |
| 75.7 | DEFERRED | `matter-closure-confirm-close-btn` (label "Close matter", not "Confirm Close") → toast "Matter closed" (reclassified 617C review: PASS → DEFERRED — blocked by G-C9; ran on substituted pre-state: Sipho's R 750 sheriff disbursement was billed on INV-0004 and paid (75.3) instead of being written off on Day 45, which the U… | G-C9 (617C review) |
| 75.8 | DEFERRED | Header badge Closed; header action now "Reopen Matter"; Overview "Closure history — 22 Oct 2026 · Concluded · Closed by Thandi Mathebula · View audit". DB `projects.status` CLOSED 16:37:28Z; `matter_closure_log` all_passed true, override false (reclassified 617C review: PASS → DEFERRED — blocked by G-C9; ran on substit… | logs/walkC2-db-days65-75.txt; G-C9 (617C review) |
| 75.9 | DEFERRED | Work › Documents lists `matter-closure-letter-sipho-dlamini-v-standard-bank-civil-2026-10-22.pdf` 2.3 KB Uploaded (plus a second SoA PDF from the default-ticked SoA box); Generated documents "Matter Closure Letter · Thandi Mathebula". Downloaded PDF: letterhead logo + "Mathebula & Partners", "Matter Closure Letter", Da… | G-D14; G-C9 (617C review) |
| 75.10 | FAIL | No row with an `end_date` exists anywhere. Closing inserted a firm-wide `retention_policies` row (MATTER · MATTER_CLOSED · 1825 days · ARCHIVE, 16:37:28.203Z) and `projects.retention_clock_started_at` = 16:34:39 (the earlier Complete, not reset by Reopen/Close). The matter Overview contradicts it: "Retention clock star… | G-D3; logs/walkC2-db-days65-75.txt |
| 75.11 | DEFERRED | 📸 two artefacts: the closure dialog with all 9 gates green (`screens/75.11-closure-dialog-all-green.png`, = 75.3 shot) and the generated closure letter (no in-app preview after close; the downloaded PDF rendered to PNG, `screens/75.11-closure-letter-pdf-page1.png`). Both read back: real data (reclassified 617C review:… | G-C9 (617C review) |

### Days 76–90 (`days-76-90.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| 80.4 | FAIL | Profitability has no export control. Reports › Timesheet Report → From 2026-07-24, To 2026-10-22 → Run Report (results render) → Export CSV → inline "Failed to export CSV." (twice); Export PDF → "Failed to export PDF." No file downloaded. frontend.log shows `exportReportCsvAction("timesheet", …)` returning in 2–25 ms (… | G-D4; logs/walkC2-80.4-export.txt |
| 80.5 | FAIL | As Bob: Today › My work (`/my-work`, h1 "My Work"): tasks PASS (My Tasks 4 on the RAF matter + Available Tasks 1), time summary PASS (Time today 17h 30m, This week 17.5 h, Time Breakdown by matter, today's entry list with narratives), but upcoming court events do not render: "Today's diary — Nothing in the diary today"… | G-D5 |
| 85.3 | FAIL | Entity type is free text. "matter" (the legal term the doc uses) → 0 total. Only the internal key "project" works → 11 total: project.created_from_template, project.updated, project.completed, project.reopened, matter_closure.closed (rows read "project 458bc331", "Project Reopened") | G-D6, G-D10 |
| 85.7 | FAIL | Continue → Bob lands on Step 2 (`matter-closure-step-2`, count 1) with an "Override failing gates" section; `matter-closure-step-3` count 0 ("Cannot close" never shown). Matches `FIXME(depth-ii:admin-offered-closure-override)` | G-D7 |
| 85.8 | FAIL | `matter-closure-override-toggle` is present (count 1) for Bob; ticking it reveals the justification input | G-D7 |
| 85.9 | DEFERRED | The 409 status code is not observable from the browser: the dialog POSTs a Next.js server action, which calls the backend server-side; REST is forbidden. Observed instead through the browser network panel: (1) Bob's "Close matter" without override → server-action response `{"success":false,"kind":"gates_failed","report… | logs/walkC2-85-closure-actions.txt |
| 85.17 | FAIL | Audit trail, Entity type "project" (85.3: "matter" finds nothing) + Event type "matter_closure.closed" → 2 total; expanded the estate row: `override_used: true`, reason CONCLUDED, closure_log_id — no justification in this event's details. The justification is in a separate CRITICAL event `matter.closure.override_used`… | G-D8, G-D6; logs/walkC2-db-days80-90.txt |
| 88.2 | FAIL | After "Load more" until exhausted: 30 entries. Present: matter created (raw "performed project.created_from_template on project"), task assignment, task status changes (IN_PROGRESS/DONE), time entries (Thandi, Bob), document uploads (trust deed, Master's appointment, inventory, bank statements), a document comment, fee… | G-B8, G-D9 |
| 88.3 | FAIL | 📸 taken (viewport + full page). Real data, but the wow frame shows a raw event key ("Thandi Mathebula performed invoice.sent on invoice") and the feed lacks the trust movements (88.2); breadcrumb ends "Matter" (G-B4) | G-B8, G-D9 |
| 90.1 | FAIL | Sweep of all 28 sidebar pages, 31 settings tabs and 4 create dialogs (method + full list in the log). Leaks remain: Home recent activity "performed invoice.sent / project.reopened / project.completed"; My work "By Project"; Reports index (Invoice Aging, Project Profitability, "…per project"); New Task dialog "Add a new… | G-D10 (+ G-A4, G-A5, G-B1, G-C3); logs/walkC2-day90-sweeps.txt |
| 90.7 | N/A | Removed step (Epic 596 #1565, ADR-336) | — |

### Exit checkpoints E.1–E.11 (`exit-checkpoints.md`)

| Step | Result | What was observed | Gap |
|---|---|---|---|
| E.1 | FAIL | Not every step is checked. Walk rows (291 checkbox lines Session 0 → Day 90, normalised as in index.md): PASS 223 · FAIL 48 · DEFERRED 19 (incl. 5.10 and 6.4, BLOCKED → DEFERRED, and the 10 rows the 617C review reclassified from PASS because they ran on substituted pre-state: 62.1, 68.2, 75.3–75.9, 75.11) · N/A 1. FAIL… | all |
| E.2 | FAIL | 7 wow moments: (1) Day 0 Home six-zone nav + brand — 0.25 PASS (minor G-A2 breadcrumb "Dashboard", G-A3 brand-coloured org name barely legible); (2) Day 4 engagement letter accepted on the portal — 4.3c PASS but 4.3b FAIL (portal page cannot display the PDF, G-B7 HIGH); (3) Day 2 conflict CLEAR — 1.4 PASS; (4) Day 5 ma… | G-B7, G-B9, G-C7, G-B8, G-D9 |
| E.3 | FAIL | Gap register (`gaps.md`, kept instead of `qa/gap-reports/demo-readiness-{DATE}/legal-za.md`): 59 gaps — BLOCKER 0 · HIGH 5 (G-B3 no way to add matter members; G-B7 portal acceptance PDF blocked by X-Frame-Options; G-C9 no disbursement write-off UI; G-C10 fee-note lines drop the time-entry narrative; G-D1 portal bare /l… | G-B3, G-B7, G-C9, G-C10, G-D1 |
| E.8 | FAIL | Terminology sweep not clean: sidebar 0 leaks, but breadcrumbs (raw slugs, "Dashboard"), settings (15 of 31 tabs), dialogs (New Task "…this project"), audit labels, reports, Home feed, fee-note pages and guard messages still say Project / Customer / Invoice | G-A2, G-A4, G-A5, G-B1, G-C3, G-D10 |
| E.9 | FAIL | Recorded: yes — 243 audit events at 85.1 (DB count equal), covering Day 0 → Day 90 actions (all dated 2026-10-22 in this compressed walk). Filterable: only with internal keys — actor needs a raw UUID (from a hover tooltip), entity type "matter" returns 0 (must type "project"), event type needs raw keys; override justif… | G-D6, G-D8 |
| E.11 | DEFERRED | Test-suite gate is Part B / closure-agent territory (brief A.8). 617C changes no code; the last observed gate runs are the verify markers below. Not re-run by Walker C2; no PASS claimed | — |
| E.11 (a) `./mvnw -B verify` | DEFERRED | `.claude/markers/verify-backend.json`: commit 43bb21a94 (PR #1628), exit 0, 5444 testcases / 0 F / 0 E / 26 skipped. `git diff --stat 43bb21a94..HEAD -- backend` is empty (re-checked by C2), so HEAD's backend is the same code. Closure agent decides "PASS (by marker)" or a re-run | — |
| E.11 (b) `pnpm test` | DEFERRED | `.claude/markers/verify-frontend.json`: commit 26fa65ef2 (PR #1640), vitest 406 files / 3008 passed / 2 skipped (portal marker ba3063ea4: 42 files / 193 passed). Closure agent to confirm SHAs | — |
| E.11 (c) `pnpm typecheck` | DEFERRED | No `typecheck` script exists in `frontend/package.json` (scripts: lint `eslint`, test `vitest run`, build, format:check, test:e2e*). `next build` type-checks the app (build OK in the frontend marker); the e2e tree has a known tsc baseline of 30 errors (616F follow-up). The step as written cannot be run | — |
| E.11 (d) `pnpm lint` | DEFERRED | Frontend marker: lint 0 errors / 92 warnings on 26fa65ef2. Closure agent to confirm | — |
| E.11 (e) every fix PR met the gates | N/A | 617C merged no fix PR during this cycle (QA/docs only; hard rule 7). Phase 87 PR markers are listed in the closure report (Part D) | — |

## Appendix C — Evidence index

| Path (under `qa_cycle/evidence/phase87-capstone/` unless noted) | Contents |
|---|---|
| `index.md` | count table, normalisation rules n1–n3, svc status per walker |
| `session-0.md`, `day-00.md`, `days-01-07.md`, `days-08-21.md`, `days-22-35.md`, `days-36-60.md`, `days-61-75.md`, `days-76-90.md` | per-step evidence rows |
| `exit-checkpoints.md` | E.1–E.11 |
| `gaps.md` | 59 gaps + Phase 87 attribution |
| `e2e-runs.md` | Playwright runs, carried six, findings F-1/F-2/P-1 |
| `screenshots.md` | 617C.3 deck shots and observations |
| `HANDOFF-A.md` … `HANDOFF-C2.md` | walker hand-offs |
| `screens/` | walk screenshots |
| `logs/` | durable log excerpts (DB reads, contract tests, redirects, attribution, e2e) |
| `marketing/decks/screens/phase87/` (repo root) | 13 exec-deck screenshots |
| `tasks/phase87-legal-native-shell.md` § Closure report (repo root) | C.1–C.13 |
