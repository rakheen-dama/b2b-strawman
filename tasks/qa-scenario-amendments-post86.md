# QA scenario amendments after Phase 86 — behaviour-coherence PR (pre-Phase 87)

**Status**: BUILT — PR #1586 (2026-10-19); §8 records the reviewer corrections applied before merge
**Decided by**: founder, `/ideate` session 2026-10-19 (log: `.claude/ideas/qa-demo-plans-post86-ideation-2026-10-19.md`)
**Input**: `qa/testplan/legal-testplan-audit-2026-10-19.md` (read-only audit of both legal plans against `main` @ `4cfa0917e`)
**Governs**: `qa/testplan/demos/legal-za-90day-keycloak.md`, `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`, `qa/testplan/MIGRATION-NOTES.md`, `qa/testplan/demo-readiness-keycloak-master.md`
**Does not touch**: any `*.spec.ts`, any product code, any ADR

---

## 1. Decisions (the authorisation record for CLAUDE.md §6)

| # | Decision | Chosen |
|---|---|---|
| D1 | Split the drift by kind. **What a step does** is fixed now, in one docs PR before Epic 607. **What things are called** (URLs, labels, nav paths, tab names, selectors) is left to the Phase 87 IA PRs under ADR-344, which rewrite those lines anyway. | Behaviour coherence now, labels in 87 |
| D2 | Add the ADR-336 engagement-letter replacement (generated document → Send for Acceptance → client accepts on the portal) in **both** plans. | Yes, both |
| D3 | Drivers: Epic 617C walks the **90-day** plan on the Keycloak stack (as ADR-344 §5 and 617C.1 already say); the next `/qa-cycle-kc` drives the **full-lifecycle** plan. Both plans stay maintained; neither is retired. | 90-day = capstone, full-lifecycle = QA cycle |
| D4 | No new scenario files, no `-v2` copies. ADR-344 option 4 (rewrite) stays rejected: the 653 verified steps and the LZKC amendment lineage are the regression value. Git history plus inline annotations are the versioning. | Amend in place |
| D5 | Steps blocked by a tracked product bug (`FIXME(depth-ii:…)`) are **not edited**. They stay red until their own one-fix PR lands. | Keep C items verbatim |

**Boundary rule for D1** (so a reviewer can classify every hunk): a hunk belongs in this PR if a QA driver following the text literally **cannot complete the step or reaches a wrong state** (missing precondition, deleted feature, non-existent task/pack/field/URL that must be selected, wrong expected amount or value). A hunk belongs to Phase 87 if the step **completes but reads stale** (old label, old path, old selector, old count).

**Annotation style** (mirrors LZKC-029 at `legal-za-full-lifecycle-keycloak.md:214`):
`(Post-86 coherence amendment, user-authorised 2026-10-19 — tasks/qa-scenario-amendments-post86.md §<n>: <one-line reason>)`
Pure deletions of removed-feature steps carry: `Removed — Epic 596 (#1565), ADR-336` (or the epic that deleted the feature).

---

## 2. PR mechanics

- **Worktree**, never the shared `main` checkout (memory `feedback_no_commits_in_shared_main_checkout`). Branch `qa/scenario-coherence-post86`.
- **Docs-only.** No code, no specs. The PR body carries the CLAUDE.md §6 justification: a link to this file, and a per-hunk class (A / A-trim / B-86 / B-pre / new) in the description table.
- **Review**: CodeRabbit (bounded wait) + one `feature-dev:code-reviewer` pass that re-reads both plans for the cross-step dependencies in §6 below. Reviewer must confirm every hunk is in §3–§5 of this file and nothing else moved.
- **Merge gate**: if `.claude/hooks/pre-pr-merge-gate.sh` demands verify markers for a docs-only diff, raise it; do not bypass, do not fabricate a marker.
- **Verification before "done"** (docs-only, so the bar is coherence, not a green suite):
  1. `grep -n -i -E "pipeline|DEAL-0001|deal\.|win-loop|Phase 80" qa/testplan/demos/legal-za-*.md` → zero hits except the removal annotations.
  2. Wow lists: 90-day still 7 entries (wow #2 replaced, not deleted); full-lifecycle now 7 (Day 8 added) so E.2 "7" is correct in both.
  3. Dependency chains in §6 each resolve by reading the amended text.
  4. `qa/testplan/MIGRATION-NOTES.md` has the new table (§7) and the 90-day row the Phase 86 sweep missed.
  5. Optional smoke (recommended if the Keycloak stack is already up): drive 90-day Days 0–4 as written. This is the stretch that was unrunnable; PASS = Sipho reaches ACTIVE and the engagement letter is accepted on `:3002`.

---

## 3. `legal-za-90day-keycloak.md` — hunks

### 3.1 Authorised deletions (class A — removed features; Phase 86 rule + ADR-344 "12 stale Pipeline steps", actual count 21)

| Where | Action |
|---|---|
| L5 stack line | Add `/ **portal 3002**` (needed by 4.3b and 68.1 below; same form as the full-lifecycle header). |
| Wow #2 (L33) | **Replace**, do not delete, so the list stays at 7 and E.2 is untouched: `2. **Day 4** — Engagement letter generated from the matter, sent for acceptance, and accepted by Sipho on the client portal (ADR-336 mandate flow)` |
| Phase H2 heading + 0.56a–0.56e (L170–176) | Delete the whole sub-section. Leave one line: `> Phase H2 (Pipeline stage pack) removed — Epic 596 (#1565), ADR-336.` |
| Day-0 checkpoint "Pipeline stage pack verified" (L206) | Delete the line. |
| "Day 1 (cont.) — Pipeline" section incl. the navigation note and 1.12–1.24 (L231–249) | Delete the whole section. Leave: `### Day 1 (cont.) — removed (Phase 86)` + `> The Phase 80 pipeline (enquiry → engagement won, DEAL-0001, win-loop nudge) was removed in Epic 596 (#1565), ADR-336. Steps 1.12–1.24 are intentionally absent; Sipho stays PROSPECT until Day 2.` |
| 90.7 (L578) | Delete. |

### 3.2 Deleted-feature fragments (class A-trim — flagged in the PR body)

| Step | Change |
|---|---|
| 0.67 | → `Verify page shows the subscription status badge (TRIALING or ACTIVE) and the "Managed Account" card; no member count, monthly amount, tier or PayFast self-service UI (tenant subscription billing removed, Epic 602 #1574)` |
| 3.10 | `five` → `four`; drop `Staffing` (Epic 599 #1570). |

### 3.3 Phase 86 consequences (class B-86 — user-authorised here)

| Step | New text |
|---|---|
| 2.1 | `On Sipho's client detail (header card, data-testid="client-header-card"), the lifecycle badge (data-testid="lifecycle-badge") reads **PROSPECT**. Click the header's primary action **Start Onboarding** → confirm **Start Onboarding** in the alert dialog → badge reads **ONBOARDING** and the onboarding checklist is instantiated.` (annotate: the deleted Day 1 deal-win was the only PROSPECT→ONBOARDING step) |
| 68.1 | `**Portal**: Sipho's portal contact was auto-created when the client was created (PortalContactAutoProvisioner). In an incognito context open http://localhost:3002/login → enter sipho's email → **Send link** → Mailpit → open the login link (lands on /auth/exchange?token=…) → portal /home loads with Sipho's matters, fee notes and documents visible.` (annotate: firm-side magic-link generator and the in-app /portal copy were removed, Epic 597 #1566) |

### 3.4 Pre-86 drift that blocks a step (class B-pre — expected values, names, preconditions)

| Step | New text / value |
|---|---|
| 0.47 | Field list → `id_passport_number, postal_address, preferred_correspondence, referred_by` (the pack `legal-za-customer.json` has exactly these; client type, physical address, registration number are native client fields). |
| 0.48 | Field list → `case_number, court_name, opposing_party, opposing_attorney, advocate_name, date_of_instruction, estimated_value`; drop `matter_type` (matter type is the native **Work Type**). |
| 0.57–0.58 | → `0.57 Navigate to **Settings > Features**` / `0.58 Verify the page lists only the horizontal toggles (Bulk billing, Information requests). The legal modules court_calendar, conflict_check, lssa_tariff, trust_accounting are fixed by the legal-za profile and are not toggles here — confirm them via 0.59 (sidebar) and GET /api/modules.` |
| 2.2 | Pack → `**Legal Individual Client Onboarding** (pack legal-za-individual-onboarding)`; tab path unchanged (Compliance group → Onboarding sub-tab). |
| 2.3 | → `Verify the checklist shows **9 items, 8 required**: Proof of Identity, Proof of Address, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, Power of Attorney Signed (optional), FICA Risk Assessment, Sanctions Screening. Six of them show "Requires document: …".` |
| 2.4 | → `Precondition for document items: on the client's **Work** group → **Documents** sub-tab click **Upload Document** → upload one small PDF named "Sipho Dlamini — certified FICA pack.pdf" → row shows Uploaded.` |
| 2.5 | → `Back on Compliance → Onboarding: for each of the 9 items in order click **Mark Complete**; where the row shows "Requires document", pick "Sipho Dlamini — certified FICA pack.pdf" in the document combobox; click **Confirm** → item reads **Completed**. (Power of Attorney Signed depends on Engagement Letter Signed — complete that first. If a completed item still renders Pending, reload — FIXME(depth-ii:checklist-stale-after-complete), intermittent.)` |
| 2.6 | → `Progress bar reads **9/9 completed**; checklist instance status COMPLETED.` |
| 2.7 | ~~explicit Activate Customer~~ **Corrected in §8 H1**: the client auto-transitions to ACTIVE on the last required Confirm (`checklist/ChecklistInstanceService.checkLifecycleAdvance`) when the activation prerequisites are on file; Activate Customer is only the fallback after a `PREREQUISITE_BLOCKED_ACTIVATION` notification. |
| 2.8–2.9 | Unchanged. |
| 3.3 | `matter_type = Litigation` → `Work Type = **LITIGATION** (inherited from the template; #1580/#1581 normalise to upper-case)`. |
| 8.3 | → `Beneficial owners are recorded via the Trust checklist item **Beneficial Ownership Declaration** (8.5), not a custom field — note the three names (Grace, James, Ruth Moroka) in that item's completion note.` |
| 8.4–8.6 | 8.4 → Start Onboarding as in 2.1. 8.5 → `Complete **Legal Trust Client Onboarding** (pack legal-za-trust-onboarding, 12 items, 11 required: Trust Deed, Letters of Authority, Trustee 1 ID, Trustee 2 ID (optional), Proof of Trust Banking, SARS Trust Tax Number, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, FICA Risk Assessment, Sanctions Screening) — upload one client PDF first and link it where required, as on Day 2.` 8.6 → `**Activate Customer** → ACTIVE.` |
| 9.3 | `Estates` → `Work Type = **ESTATES**`. |
| 9.4 | Task list → `Obtain death certificate & will; Report estate to Master of High Court; Obtain Letters of Executorship; Advertise for creditors (Gazette + local newspaper); Inventory of assets & liabilities; Open estate bank account; Prepare Liquidation & Distribution account; Lodge L&D account with Master; Distribute estate & file final account` (9 tasks). 9.5 → `first 4 to Thandi, remaining 5 to Bob`. |
| 12.1 | `"L&D documents"` → `"Obtain death certificate & will"`. |
| 14.2 | `complete FICA checklist → ACTIVE` → `Start Onboarding → complete the 9-item individual checklist (as Day 2) → Activate Customer → ACTIVE`. |
| 14.3 | `matter_type = CONVEYANCING` → `Work Type **CONVEYANCING** inherited from the template (#1580)`. |
| 22.3 | Same rewrite as 14.2. |
| 22.4 | → `Create matter from template **Litigation (Road Accident Fund -- RAF)** → Work Type **LITIGATION** (the RAF template carries LITIGATION; "Road Accident Fund" is the template, not a work type), case_number = "RAF/2026/0042"`. |
| 80.5 | Drop `and upcoming court events` (My Work's upcoming list is tasks; court dates are on Court Calendar). |

### 3.5 New steps (class new — D2)

Insert after 4.3:

```
- [ ] **4.3a** On the saved engagement letter (Work → Generated Docs) click **Send for Acceptance** → Recipient = Sipho Dlamini (portal contact), Expiry (days) = 7 → **Send** → acceptance status badge on the document reads **Awaiting Acceptance** (Post-86 coherence amendment, user-authorised 2026-10-19 — tasks/qa-scenario-amendments-post86.md §3.5: ADR-336 replaces the deleted proposal flow with document + acceptance)
- [ ] **4.3b** Mailpit → email to sipho's address with subject `Mathebula & Partners -- Please review engagement letter for acceptance: <file name>` → open the link in an incognito context → public page http://localhost:3002/accept/<token> renders the PDF preview → type the full name "Sipho Dlamini" → click **I Accept** → accepted state with timestamp renders
- [ ] **4.3c** Mailpit → `Confirmed: You have accepted <file name>`; firm-side the document's acceptance badge reads **Accepted**; 📸 `day-04-engagement-letter-accepted.png`
```

4.6 → append `, engagement letter sent for acceptance, engagement letter accepted (actor: Sipho, source PORTAL)`.

### 3.6 Left as-is (class C — tracked product bugs; keep red)

5.9–5.10, 5.12, 5.14 (`trust-link-payment-type`, `matter-unbilled-disbursements-summary`), 14.8 (`otp-clause-associations`), 30.2–30.4, 30.7 (`soa-preview-unbounded-scale`), 45.5–45.11 (`no-write-off-action`), 75.10 (`retention-period-unconfigurable`), 85.7–85.8 (`admin-offered-closure-override`), 8.5/14.2 intermittent (`checklist-stale-after-complete`).

### 3.7 Deferred to Phase 87 (class D — reads stale, still completes; ADR-344 scope)

0.17 (profile curl), 0.26 (Team path), 0.51 (Work Type wording), 0.53–0.54 (Matter Templates, 6 not 4), 0.56 (automations now 4 toggles), 3.8 (breadcrumb shape), 5.5 (no Disbursements nav item), 14.9 (causal claim), 14.12 (stack line fixed above makes this runnable), 23.1 ("Other"), 32.1 (tariff line on fee note), 36.1 (no Billing tab), 38.1 (Approve then Send), 75.2 (selector), 80.3 (Profitability), 85.2/85.3/85.17 (filter wording). Record them in MIGRATION-NOTES §7 so 617B.1's final sweep has the list.

---

## 4. `legal-za-full-lifecycle-keycloak.md` — hunks

### 4.1 Deleted-feature fragments (class A-trim)

| Where | Change |
|---|---|
| Selector ref L35 | Drop `, staffing`. |
| 3.5 | Drop `Staffing` from the Work group list and the sentence `Note: the old flat "Members" tab is replaced by Work > Staffing (?tab=staffing).` (Epic 599 #1570). |
| 75.5, E.8 | **Keep** `8` — Day 8 exists again (§4.4). No change. |
| E.2 | **Keep** `7` — a seventh wow moment is added (§4.4). No change. |

### 4.2 Phase 86 consequences (class B-86)

| Where | Change |
|---|---|
| Day 5, after 5.6 | Add `5.7` (Bob): `On Sipho's client detail the lifecycle badge reads **PROSPECT**. Click the header card's **Start Onboarding** → confirm → badge **ONBOARDING**; **Compliance** group → **Onboarding** sub-tab shows **Legal Individual Client Onboarding**, 0/9 completed (9 items, 8 required; five show "Requires document").` Day 5 checkpoints: add `- [ ] Sipho ONBOARDING with the individual checklist instantiated`. |
| Day 10 Phase A, before 10.2 | Add 10.1a–10.1d (Thandi), see §4.4. 10.2 unchanged. Day 10 checkpoints: add `- [ ] Sipho ACTIVE (required by the Day 28 billing run: BillingRunSelectionService selects lifecycle_status = ACTIVE only)`. |
| Day 28 header note | Append one line to the existing cycle-16 note: `Sipho's activation is now scripted at 10.1a–10.1d (Post-86 coherence amendment, 2026-10-19).` |

### 4.3 Pre-86 drift that blocks a step (class B-pre)

| Step | New text / value |
|---|---|
| 3.3 | `"Litigation — Road Accident Fund"` → `template **Litigation (Road Accident Fund -- RAF)**; Work Type shows **LITIGATION**`. |
| 14.6 | `"Estates — Deceased"` → `template **Deceased Estate Administration**; Work Type **ESTATES**`. |
| 14.8 | Template name → `**Liquidation and Distribution Account Pack**` (request pack `legal-za-liquidation-distribution`). |
| 15.10 | → `Navigate directly to /requests/[morokaInfoRequestId] → expect 404 / 403 / redirect. MUST NOT show "Liquidation and Distribution Account Pack"`. |
| 15.11 | → `Navigate directly to /trust/[morokaMatterId] → expect 404 / 403 / redirect. MUST NOT render Moroka's R 25,000 deposit or any EST-2026-002 movement`. |
| 15.12 | → `Removed — the portal has no per-document or per-transaction route; 15.11 and 15.16 cover the trust leak, 15.17 the document leak.` (keep the id, mark removed) |
| 15.14 | → `curl -sS http://localhost:8080/portal/projects/[morokaMatterId]/summary -H "Authorization: Bearer $SIPHO_JWT"` → 403/404. |
| 15.15 | → `curl -sS http://localhost:8080/portal/requests/[morokaInfoRequestId] …` → 403/404. |
| 15.16 | → `curl -sS http://localhost:8080/portal/trust/matters/[morokaMatterId]/transactions …` → 403/404. |
| 15.17 | → `curl -sS http://localhost:8080/portal/trust/matters/[morokaMatterId]/statement-documents …` → 403/404 or empty; never Moroka document bytes. |
| 15.18 | → `curl -sS http://localhost:8080/portal/trust/summary … \| jq` and `curl -sS http://localhost:8080/portal/requests … \| jq` → only Sipho's ids; balance R 50,000 not R 75,000. |
| Day 15 Phase B/C intro | Add: `Routes/endpoints are today's (/projects, /portal/projects); Phase 87 renames them to /matters (609F, 617A) under ADR-344.` |
| 21.5 | `"Draft particulars of claim"` → `"File RAF1 claim form + supporting documents"` (the RAF template task that Day 60.7 later expects to carry 1h30m). |
| 45.3 | Description → `"Top-up per engagement letter (accepted Day 8)"`. |
| 45.4 | → `Approve (if dual-approval) → client ledger shows trust balance **R 70,000** (R 50,000 Day 10 + R 20,000 Day 45).` Delete the cycle-18 R 71,000 note (it recorded a prior-cycle verification artefact, not scripted behaviour). |
| 45.5, 46.4, 46.5 | `R 71,000` → `R 70,000`; `three deposits` → `two deposits (Day 10 R 50,000, Day 45 R 20,000)`; delete the OBS-1101 carry-over sentences. |
| 60.11a (new, before 60.12) | `**Actor swap → Thandi.** Navigate to Trust Accounting → Sipho Dlamini / RAF-2026-001 → **Record Payment**: amount **R 70,000**, payee Sipho Dlamini, description "Settlement proceeds paid out to client" → submit → note the generated payment reference (PAY/2026/001 on a fresh tenant) → status **AWAITING_APPROVAL** (exceeds the R 50,000 threshold from 1.5). **Actor swap → Bob** for 60.12.` |
| 61.4 | Already reads R 70,000 / two deposits — unchanged; now consistent with 45.x. |

### 4.4 New steps (class new — D2)

Replace the `## Day 7 / Day 8 — removed (Phase 86)` block with two sections (keep the removal note as the first line of Day 7 for history):

```
## Day 7 — Firm issues the engagement letter for acceptance  `[FIRM]`

> Day 7/8 were removed in Phase 86 (Epic 597, ADR-336: proposals deleted). This Day 7/8 pair is the ADR-336 replacement — a generated engagement letter plus the Phase 28 acceptance flow. (Post-86 coherence amendment, user-authorised 2026-10-19 — tasks/qa-scenario-amendments-post86.md §4.4)

**Actor**: Bob Ndlovu (login as Bob, port 3000)

- [ ] **7.1** On matter RAF-2026-001 → **Generate Document** dropdown → **Engagement Letter — Litigation** → preview renders with Mathebula letterhead, brand colour and Sipho's details → **Save**
- [ ] **7.2** **Work** group → **Generated Docs** sub-tab lists the engagement letter with today's date
- [ ] **7.3** On that row click **Send for Acceptance** → Recipient = Sipho Dlamini (sipho.portal@example.com), Expiry (days) = 7 → **Send** → acceptance badge **Awaiting Acceptance**
- [ ] **7.4** Mailpit → email to sipho.portal@example.com, subject `Mathebula & Partners -- Please review engagement letter for acceptance: <file name>`, body link to http://localhost:3002/accept/<token>
- [ ] **7.5** Activity feed shows "engagement letter generated" and "acceptance requested"

**Day 7 checkpoints**
- [ ] Engagement letter generated from the legal-za template pack and saved to the matter
- [ ] Acceptance request sent; Awaiting Acceptance badge; email in Mailpit

---

## Day 8 — Sipho accepts the engagement letter  `[PORTAL]`

**Context swap** — firm → portal (fresh incognito context, port 3002). The acceptance route is public (token-authenticated), so no portal login is required.

**Actor**: Sipho Dlamini

- [ ] **8.1** Open the link from 7.4 → `/accept/<token>` renders the firm name, the letter's PDF preview and an acceptance panel
- [ ] **8.2** Type full name "Sipho Dlamini" → click **I Accept** → page shows the accepted state with acceptor name and timestamp
- [ ] **8.3** Mailpit → `Confirmed: You have accepted <file name>` to Sipho
- [ ] **8.4** Re-open the same link → still shows the accepted state; no second Accept offered
- [ ] **8.5** 📸 **Screenshot**: `day-08-portal-engagement-letter-accepted.png`

**Day 8 checkpoints**
- [ ] Acceptance recorded with actor = portal contact (firm-side audit shows source PORTAL, not SYSTEM — LZKC-025 class)
- [ ] Firm-side acceptance badge on the generated document reads **Accepted** (verify at 10.1d)
```

Day 10 Phase A, new steps before 10.2:

```
- [ ] **10.1a** Sipho's client detail → **Work** group → **Documents** sub-tab → **Upload Document** → upload one small PDF named "Sipho Dlamini — certified FICA pack.pdf" (the checklist document picker lists client documents; if the Day 4 portal uploads already appear in the picker, link those instead and say so in the run log)
- [ ] **10.1b** **Compliance** group → **Onboarding** sub-tab → for each of the 9 items in order: **Mark Complete** → where "Requires document" shows, pick the PDF from 10.1a → **Confirm** → **Completed**. Complete "Engagement Letter Signed" before "Power of Attorney Signed" (dependency). Progress reads **9/9 completed**. (Intermittent stale row → reload; FIXME(depth-ii:checklist-stale-after-complete))
- [ ] **10.1c** (as built, after §8 H1) auto-transition to **ACTIVE** on the last required Confirm; **Activate Customer** only as the prerequisite-blocked fallback
- [ ] **10.1d** **Work** → **Generated Docs**: engagement letter acceptance badge reads **Accepted** (from Day 8)
```

Wow list: add `7. **Day 8** — Sipho accepts the engagement letter on the public /accept page — the ADR-336 mandate flow, firm badge flips to Accepted \`[PORTAL]\``.

### 4.5 Left as-is (class C)

60.23, 85.3 (`retention-period-unconfigurable`).

### 4.6 Deferred to Phase 87 (class D)

0.16, 0.H, 1.3, 2.3 (Name field), 4.2 (`/auth/exchange`), 28.1 ("Billing Runs" → 87 "Fee runs"), 60.1/60.6 (Requests under Client group), 60.16 (selector), 85.4, B.1–B.4 (matter-sidebar baselines → retire in the 87 screenshot slice 617C.3).

---

## 5. Other files in the same PR

| File | Change |
|---|---|
| `qa/testplan/legal-testplan-audit-2026-10-19.md` | Add (copy of the audit; the evidence for every hunk above). |
| `qa/testplan/MIGRATION-NOTES.md` | (a) Add the missing 90-day row to "Amended plans (deleted-feature steps removed only)": `demos/legal-za-90day-keycloak.md — this PR — wow #2, Phase H2 0.56a–e, Day-0 Pipeline checkpoint, Day 1 (cont.) 1.12–1.24, 90.7`. (b) New section **"Post-86 coherence amendments (user-authorised 2026-10-19)"** listing §3.3–3.5 and §4.2–4.4 by step id, and a **"Known-stale wording left for Phase 87 (ADR-344)"** list from §3.7 and §4.6 so 617B.1 has its sweep list. |
| `qa/testplan/demo-readiness-keycloak-master.md` | Scripts table → legal only (the accounting and agency rows were deleted in Epic 604 #1576); add the full-lifecycle, portal, admin-audit and AI-core rows; "Purpose" → one vertical; date bump. Everything else unchanged. |

---

## 6. Cross-step dependencies the reviewer must re-verify

1. 90-day: 1.12–1.24 deleted → **2.1 Start Onboarding** → 2.5–2.7 complete + Activate → 3.1 matter. Without 2.1 rewritten, Sipho stays PROSPECT and no checklist exists.
2. 90-day: wow #2 replaced by Day 4 → 4.3a–4.3c exist → stack line has portal 3002.
3. 90-day: 9.4 task names → 12.1 → 20.1 (check 20.x for any other estate task name).
4. Full-lifecycle: 5.7 ONBOARDING → 7.x letter → 8.x accepted → 10.1a–10.1d ACTIVE → **28.2 billing run finds Sipho** (`BillingRunSelectionService.java:533-535`).
5. Full-lifecycle: 45.4 R 70,000 → 60.11a R 70,000 payout → 60.14 balance R 0.00 → 61.4 SoA two deposits / one payment. Do **not** "fix" 60.12 to R 71,000.
6. Full-lifecycle: 15.10–15.18 now hit real routes/endpoints, so a 200 is a genuine BLOCKER and a 404 is a genuine denial. Phase 87 (609F/617A) must re-amend these to `/matters`.
7. Full-lifecycle: 75.5 and E.8 cite Day 8 — valid again because of §4.4.

---

## 7. Follow-ups surfaced (not in this PR)

- **Product observation**: the onboarding checklist's document picker lists client-uploaded documents only; an accepted engagement letter (GeneratedDocument + acceptance) cannot satisfy "Engagement Letter Signed" directly. Worth a Phase 84/take-on decision, not a scenario edit.
- ~~Spec claim unverified: the depth-ii fixme asserts auto-activation; no such transition exists~~ **Withdrawn (§8 H1)**: the transition exists in `checklist/ChecklistInstanceService.checkLifecycleAdvance`; the fixme's assertion is right.
- The nine `FIXME(depth-ii:…)` product bugs, `trust-link-payment-type` first (trust-account disbursements impossible in the UI). One fix per PR; the C steps above go green on their own.
- 617C.1 walks the 90-day plan on the Keycloak stack. After this PR it is coherent Days 0–4 on paper; its first observed Days 0–4 run is still owed (optional smoke in §2.5 pays it early).

---

## 8. Reviewer corrections applied before merge (PR #1586, feature-dev:code-reviewer pass, 2026-10-19)

The independent review re-verified every hunk and found the following; each was confirmed against the code and corrected in the same PR.

| # | Finding | Evidence | Correction |
|---|---|---|---|
| **H1** | §3.4 2.7 / §4.4 10.1c (and 8.6, 14.2, 22.3) claimed there is no automatic ONBOARDING→ACTIVE transition. False: it lives one package over. | `checklist/ChecklistInstanceService.java:449-480` completes the instance when all required items are COMPLETED/SKIPPED and calls `checkLifecycleAdvance` (`:522-553`), which transitions to ACTIVE when `LIFECYCLE_ACTIVATION` prerequisites pass, else raises `PREREQUISITE_BLOCKED_ACTIVATION`; header action for ACTIVE reads **Edit** (`client-header-card.tsx:39-41`). | 2.7, 10.1c, 8.6, 14.2, 22.3 now script the auto-transition, with **Activate Customer** only as the blocked fallback. §7 bullet withdrawn. |
| **M1** | Badge after **Send** is not "Pending". | `AcceptanceService.java:231` `markSent()`; `AcceptanceStatusBadge.tsx` SENT → "Awaiting Acceptance". | 90-day 4.3a; full-lifecycle 7.3 + Day 7 checkpoint → **Awaiting Acceptance**. |
| **M2** | Activation prerequisites missing from the client-creation steps that now feed activation. | `StructuralPrerequisiteCheck.java:65-70`: `address_line1`, `city`, `country` for all; `tax_number` for COMPANY/TRUST (INDIVIDUAL exempt, `:105-111`). Dialog has separate Address Line 1 / City / Country / Tax Number inputs. | 90-day 1.8, 8.2 (+ Tax Number), 14.2, 22.2; full-lifecycle 2.3 now give explicit Address Line 1 / City / Country (= **ZA**). Boundary rule: a missing precondition is a behaviour hunk → this PR. |
| **M3** | "Five" document-bearing items; the pack has six (Power of Attorney Signed also requires one). | `legal-za-individual-onboarding/pack.json` — six `requiresDocument: true`. | 90-day 2.3, full-lifecycle 5.7 → six. |
| **M4** | Master doc edited beyond §5 ("everything else unchanged"). | Tier-removal Pass text (Epic 602 #1574) and Progressive-disclosure Pass lines (accounting/consulting rows, Settings > Features). | **Recorded here as authorised**: both are deleted-feature consequences (same class as A-trim) and were left in. |
| L1 | Phase H2 and 90.7 keep a one-line "Removed —" stub rather than vanishing. | — | Kept: the stub preserves step-id continuity and the §2.5 grep allows removal annotations. |
| L2 | Day 10 sub-heading renamed "Phase A: Client activation + matter lifecycle". | — | Recorded here; trivial. |
| L3 | 21.5 uses the short RAF task name. | `legal-za.json:22` has the suffix "(within 3-year prescription)". | Kept: 60.7 already uses the short form; both are unambiguous. |
| L4 | MIGRATION-NOTES Work-group rows still list `staffing`. | Epic 599 #1570. | Added to "Selector notes superseded by Phase 86". |
| L5 | 90-day stack line cites 14.12 as well as 4.3b/68.1. | Accurate. | Kept. |

**Lesson** (also in `tasks/lessons.md`): when asserting a behaviour does *not* exist, grep the whole backend, not the package the feature is named after — the activation hook sits in `checklist/`, not `compliance/`.
