# QA Lifecycle: Admin-POV 30-Day Audit Capstone (Keycloak Mode)

**Vertical profile**: `legal-za` primary; `accounting-za` / `consulting-za` referenced for profile-gated checkpoints
**Story**: "The compliance month" — a firm Owner / Admin exercises the audit-log surfaces end-to-end across 30 days: seeding a baseline, observing security/compliance/financial events, exporting evidence, fulfilling a DSAR, and probing the export-row hard cap.
**Stack**: Keycloak dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025)
**Master doc**: `qa/testplan/demo-readiness-keycloak-master.md`
**Driver**: `/qa-cycle-kc qa/testplan/demos/admin-audit-30day-keycloak.md`

**Supersedes**: none — this is the first admin-audit capstone for Phase 69. Complements the firm-side 90-day spines (`legal-za-90day-keycloak.md`, `accounting-za-90day-keycloak-v2.md`, `consulting-agency-90day-keycloak.md`) and the portal client lifecycle (`portal-client-90day-keycloak.md`).

> **Scope note**: This is an **admin-POV** lifecycle. Every checkpoint is performed on the firm-side app (`http://localhost:3000`) by a firm user (Owner/Admin/Member). The script focuses on **what an administrator sees in the audit surfaces** — Audit Log page, presets, expanded rows, dashboard sensitive-events widget, per-entity Audit tabs, PDF/CSV exports, and DSAR pack inclusion. Other 90-day spines provide the underlying business activity; this script narrates and verifies the **observability layer over that activity**.

---

## Actors

| Role | Name | Keycloak email | Password |
|---|---|---|---|
| Owner | Alice | `alice@example.com` | `password` |
| Admin | Bob | `bob@example.com` | `password` |
| Member | Carol | `carol@example.com` | `password` |
| Platform Admin | (pre-seeded) | `padmin@docteams.local` | `password` |

**Org slug**: `acme-corp` (matches `/qa-cycle-kc` defaults and the seeded users from `compose/scripts/keycloak-bootstrap.sh`).

> The script uses **Alice (Owner)** as the primary narrator unless a checkpoint is explicitly role-gated. Day 5 switches to Carol (Member) for the permission-denial path. Day 22 onward exercises Bob (Admin) for evidence export and DSAR fulfilment.

## Vertical-profile gating

Most checkpoints are `[all profiles]`. The following are gated:

| Checkpoint | Gating | Reason |
|---|---|---|
| Day 0 trust-deposit seed | `[legal-za]` | Trust accounting only present on legal profile |
| Day 0 deadline seed | `[accounting-za]` | Engagement deadlines only present on accounting profile |
| Day 0 retainer seed | `[consulting-za]` | Hour-bank retainers only present on consulting profile |
| Day 10 trust approval | `[legal-za]` | Trust transactions are legal-only |
| Day 15 closure override | `[legal-za]` primary, available on all | Matter-closure surface is legal-headlined; override path universal |

## Demo wow moments (capture 📸 on clean pass)

> 12 screenshots, captured in slice 510B under `documentation/screenshots/phase69/`. Slots reserved here in order; numeric prefixes are the requirements §6.2 ordering.

1. Audit Log page — empty state (`/org/acme-corp/settings/audit-log`)
2. Audit Log page — populated (after Day 0 seeding)
3. Audit Log page — expanded row showing override justification (Day 15)
4. Audit Log page — Sensitive preset applied
5. Audit Log page — Compliance preset applied
6. Audit Log page — Security preset applied
7. Audit Log page — Financial approvals preset applied
8. Sensitive Events widget — populated on dashboard (`/org/acme-corp/dashboard`)
9. Per-entity Audit tab — Matter Closure detail (override visible)
10. Per-entity Audit tab — Customer detail
11. PDF export — first page (header + filter summary)
12. PDF export — middle page (row formatting + page numbering)

---

## Session 0 — Prep & Reset

Follow `qa/testplan/demo-readiness-keycloak-master.md` → "Session 0 — Stack startup & teardown" for shared steps (M.1–M.9). In addition, for this admin-audit lifecycle specifically:

- [ ] **0.A** Confirm Keycloak realm `docteams` has the seeded users `alice@example.com`, `bob@example.com`, `carol@example.com` mapped to org `acme-corp` with roles `org:owner`, `org:admin`, `org:member` respectively. Re-run `compose/scripts/keycloak-bootstrap.sh` if missing.
- [ ] **0.B** Confirm tenant schema for `acme-corp` is freshly migrated and contains zero `audit_event` rows attributable to the current run. (A baseline of seeded historical events from the dev fixture is acceptable; this script does not require a perfectly empty audit table.)
- [ ] **0.C** Confirm Mailpit (`http://localhost:8025`) is reachable — Day 22 PDF export and Day 25 DSAR fulfilment may produce notification email artefacts referenced in checkpoints.
- [ ] **0.D** Confirm the dashboard sensitive-events widget (Epic 509A) is visible at `/org/acme-corp/dashboard` for an Owner login. The widget renders the top-5 most recent CRITICAL/WARNING events; it is the load-bearing surface for Day 15.
- [ ] **0.E** Confirm `POST /api/audit-events/export.pdf` is reachable and returns `application/pdf` for a small-window request — sanity check before Day 22.

---

## Day 0 — Baseline seeding + first audit-log render  `[all profiles]`

**Actor**: Alice (Owner, `alice@example.com`) — authenticated via Keycloak.

### Phase A: Seed baseline activity

- [ ] **0.1** Log in as Alice via Keycloak — confirm landing on `/org/acme-corp/dashboard` without console errors. Login itself should emit an `auth.login.*` audit event.
- [ ] **0.2** Create a matter via `/org/acme-corp/projects/new` (or POST `/api/projects` from a fixture). Title: `Audit Capstone Matter Day 0`. Confirm matter-creation audit row appears in the backing tenant's `audit_event` table.
- [ ] **0.3** `[legal-za]` Post a trust deposit against the new matter (`/org/acme-corp/trust-accounting` or POST `/api/trust-transactions`). Amount: ZAR 5 000. Confirm `trust_transaction.deposited` row recorded.
- [ ] **0.4** `[accounting-za]` Seed an engagement deadline via the deadlines surface for the new matter. Confirm deadline-creation audit row recorded.
- [ ] **0.5** `[consulting-za]` Seed a retainer / hour-bank entry via the retainer surface for the new matter. Confirm retainer-setup audit row recorded.

### Phase B: Open Audit Log page for the first time

- [ ] **0.6** Navigate to `/org/acme-corp/settings/audit-log` — page renders without 500 / JS console errors.
- [ ] **0.7** Verify h1 reads "Audit log" (case-insensitive); page heading is visible.
- [ ] **0.8** Verify the row list shows the freshly seeded events from Phase A (login, matter creation, profile-gated trust/deadline/retainer seed). Row count ≥ 2.
- [ ] **0.9** Apply the **Sensitive** preset via `[data-testid="audit-preset-select"]` → option "Sensitive". URL updates with `severities=` param.
- [ ] **0.10** Verify the Sensitive-preset list is **empty** (no CRITICAL/WARNING events seeded yet) — empty-state copy "No audit events" visible.
- [ ] **0.11** 📸 **Screenshot** slot **#1**: `phase69-01-audit-log-empty-sensitive.png` — Audit Log page with Sensitive preset, empty state.
- [ ] **0.12** Clear the preset (back to default "All"); list re-populates.
- [ ] **0.13** 📸 **Screenshot** slot **#2**: `phase69-02-audit-log-populated.png` — Audit Log page with Day 0 seeded rows visible.
- [ ] **0.14** Click the **Export → CSV** action with the Sensitive preset re-applied — download a CSV file. Open it; verify it contains a header row and zero data rows (sanity: empty filter → empty CSV).

**Day 0 checkpoints**
- [ ] Login emits an audit event (Alice's `auth.login.*`).
- [ ] All Phase-A seed actions emit their respective audit events.
- [ ] Audit Log page renders without errors as Owner.
- [ ] Sensitive preset URL param `severities=` is applied and produces empty result on a fresh tenant.
- [ ] Empty CSV export succeeds (header-only file).

**Audit assertion**
- `data-testid="audit-preset-select"` is rendered.
- At least one row matches `[data-testid^="audit-row-"]` (excluding `audit-row-toggle-` / `audit-row-details-`) on the default view.

---

## Day 5 — Permission denial → Security preset  `[all profiles]`

**Actor**: Carol (Member, `carol@example.com`) for the denied attempt; Alice (Owner) returns to verify on Audit Log.

- [ ] **5.1** Log in as Carol. Navigate to a known owner-only action (e.g. `/org/acme-corp/settings/team` → attempt to remove a member; or attempt a customer DELETE via the row action menu).
- [ ] **5.2** Verify the action is rejected — UI shows a 403 / "not authorised" toast OR the action button is hidden + the equivalent API call returns 403.
- [ ] **5.3** `// TODO[510B-OR-PHASE-70]` — emission gap: `security.permission.denied` is registered (`AuditEventTypeRegistry.java:43`) but no backend emitter exists today. The fixture stages the 403; the audit row will be **absent** until a backend emitter is added. Phase 70 backlog candidate.
- [ ] **5.4** Log out as Carol. Log in as Alice.
- [ ] **5.5** Navigate to `/org/acme-corp/settings/audit-log` → apply **Security** preset.
- [ ] **5.6** **Expected (post-emission)**: a row of severity WARNING with `eventType` matching `security.permission.denied`, actor=Carol, timestamp ≈ Day 5 step 5.2.
- [ ] **5.7** **Actual (Day 5, 510A)**: the row is **absent**. Record the gap in the cycle status. Do NOT fail the cycle here in 510B if backend emission is still pending; mark the day PARTIAL with a citation to Phase 70.
- [ ] **5.8** 📸 **Screenshot** slot **#6**: `phase69-06-audit-log-security-preset.png` — Audit Log page with Security preset applied (whatever state — empty until emission gap closed).

**Day 5 checkpoints**
- [ ] Carol's 403 attempt was actually rejected (UI or API).
- [ ] Audit Log page Security preset renders without error as Owner.
- [ ] Emission gap documented (not silently passed).

**Audit assertion**
- `eventType="security.permission.denied"` should appear under preset `Security` (`severity=WARNING`) once emission lands. Today, assertion is "preset renders, list may be empty".

---

## Day 10 — Trust transaction approval → Financial approvals preset  `[legal-za]`

**Actor**: Alice (Owner). Skip for `accounting-za` and `consulting-za`.

- [ ] **10.1** Navigate to `/org/acme-corp/trust-accounting` → confirm the Day 0 trust deposit is visible in pending state.
- [ ] **10.2** Post a new trust transaction (e.g. transfer or payout) against the Day 0 matter. Submit for approval if the workflow requires a separate approve step.
- [ ] **10.3** Approve the trust transaction (Owner action). Backend emits `trust_transaction.approved` (`TrustTransactionService.java:256+`).
- [ ] **10.4** Navigate to `/org/acme-corp/settings/audit-log` → apply **Financial approvals** preset.
- [ ] **10.5** Verify a row appears with eventType `trust_transaction.approved`, actor=Alice, severity matching the registry's classification.
- [ ] **10.6** Expand the row → confirm payload includes the trust transaction ID and amount.
- [ ] **10.7** 📸 **Screenshot** slot **#7**: `phase69-07-audit-log-financial-approvals-preset.png` — Audit Log page, Financial approvals preset, with the new approval row visible.

**Day 10 checkpoints**
- [ ] Trust transaction approve action succeeded (200 / UI confirmation).
- [ ] `trust_transaction.approved` row visible under Financial approvals preset.
- [ ] Expanded-row detail contains the approval-relevant fields.

**Audit assertion**
- `eventType="trust_transaction.approved"` present; preset URL param `severities=` (or category equivalent) reflects the Financial approvals selection.

---

## Day 15 — Matter-closure override (CRITICAL) + dashboard widget  `[legal-za primary, all profiles]`

**Actor**: Alice (Owner).

- [ ] **15.1** Navigate to the Day 0 matter detail page (`/org/acme-corp/projects/{id}`).
- [ ] **15.2** Trigger the **Close matter with override** path: open the closure dialog, set `override=true`, supply a non-empty `overrideJustification` (e.g. `"Client returned funds — trust account zero"`).
- [ ] **15.3** Submit. Backend emits `matter.closure.override_used` (CRITICAL / COMPLIANCE; `MatterClosureService.java:271-272`) plus the standard `matter_closure.closed` (Phase 70 backlog item 70-D will rename).
- [ ] **15.4** Navigate to `/org/acme-corp/settings/audit-log`. Default view: locate the new CRITICAL row near the top.
- [ ] **15.5** Click the row's expand toggle. Confirm:
  - severity pill shows `CRITICAL`,
  - `eventType=matter.closure.override_used`,
  - the expanded body contains the literal `overrideJustification` text.
- [ ] **15.6** 📸 **Screenshot** slot **#3**: `phase69-03-audit-log-expanded-override.png` — Audit Log expanded row with override justification.
- [ ] **15.7** Apply the **Sensitive** preset → row remains visible (CRITICAL satisfies Sensitive).
- [ ] **15.8** 📸 **Screenshot** slot **#4**: `phase69-04-audit-log-sensitive-preset.png` — Audit Log Sensitive preset populated.
- [ ] **15.9** Apply the **Compliance** preset → row remains visible (override is COMPLIANCE-tagged).
- [ ] **15.10** 📸 **Screenshot** slot **#5**: `phase69-05-audit-log-compliance-preset.png` — Audit Log Compliance preset populated.
- [ ] **15.11** Navigate to `/org/acme-corp/dashboard` → locate the **Sensitive Events** widget (Epic 509A).
- [ ] **15.12** Verify the override row appears in the widget's top-5.
- [ ] **15.13** 📸 **Screenshot** slot **#8**: `phase69-08-dashboard-sensitive-events-widget.png` — dashboard with Sensitive Events widget populated.
- [ ] **15.14** Navigate to the **per-entity Audit tab** for the closed matter (`/org/acme-corp/projects/{id}` → Audit tab inside `<ClosureHistorySection>`). Confirm the override row surfaces here too.
- [ ] **15.15** 📸 **Screenshot** slot **#9**: `phase69-09-per-entity-matter-closure-audit.png` — per-entity Matter Closure detail showing override row.

**Day 15 checkpoints**
- [ ] Closure-with-override succeeded (200 from `POST /api/matters/{id}/closure/close` with `override=true`).
- [ ] CRITICAL `matter.closure.override_used` row visible on Audit Log default + Sensitive + Compliance presets.
- [ ] Expanded-row body contains the override justification verbatim.
- [ ] Dashboard Sensitive Events widget shows the row in top-5.
- [ ] Per-entity Audit tab on the closed matter shows the row.

**Audit assertion**
- `eventType="matter.closure.override_used"`, `entityType="matter_closure"`, `severity="CRITICAL"`.
- `data-testid^="closure-row-override-"` is visible on the matter detail page.
- `data-testid="severity-pill"[data-severity="CRITICAL"]` matches at least one timeline row in the embedded audit timeline.

---

## Day 20 — Per-entity Audit tab on a customer  `[all profiles]`

**Actor**: Bob (Admin, `bob@example.com`).

- [ ] **20.1** Log in as Bob. Pick a known customer (one whose matter Alice created on Day 0).
- [ ] **20.2** Navigate to the customer detail page (`/org/acme-corp/customers/{id}`) → open the **Audit** tab.
- [ ] **20.3** Verify the tab renders an event list scoped to that customer's entity tree (matters, documents, notes touching the customer).
- [ ] **20.4** Cross-check: every event visible here is for `entityType in {customer, matter, matter_closure, document, ...}` AND its entity ID resolves to (or is owned by) the current customer. No events from unrelated customers leak in.
- [ ] **20.5** Confirm the activity from Day 0 → Day 20 for this customer is present: matter creation (Day 0), trust deposit (Day 0, legal-za), trust approval (Day 10, legal-za), closure override (Day 15).
- [ ] **20.6** 📸 **Screenshot** slot **#10**: `phase69-10-per-entity-customer-audit.png` — Customer detail Audit tab populated.

**Day 20 checkpoints**
- [ ] Customer Audit tab is visible to Admin role (no 403).
- [ ] No cross-customer leak — every row's entity belongs to the current customer.
- [ ] Day-0-to-Day-20 activity present and ordered chronologically (newest-first or oldest-first per UI convention).

**Audit assertion**
- `data-testid="customer-audit-tab"` (or analogous) visible.
- Each row's `data-entity-id` (where exposed) maps to the current customer subtree.

---

## Day 22 — PDF export + reflexive `audit.export.generated`  `[all profiles]`

**Actor**: Bob (Admin).

- [ ] **22.1** Navigate to `/org/acme-corp/settings/audit-log`. Set filter range = **last 30 days**. Apply a non-trivial preset (e.g. Sensitive) so the export has structure.
- [ ] **22.2** Click **Export → PDF** (or invoke `POST /api/audit-events/export.pdf` directly with the same filter window). Receive a `application/pdf` body. Save to disk.
- [ ] **22.3** Open the PDF. Verify:
  - **Header**: tenant name, generated-at timestamp, generated-by actor (Bob).
  - **Filter summary**: range = last 30d, preset = Sensitive (or whichever was applied).
  - **Row formatting**: each row shows timestamp, actor, eventType, severity pill / text, brief detail.
  - **Page numbering**: footer "Page N of M" present on every page.
- [ ] **22.4** 📸 **Screenshot** slot **#11**: `phase69-11-pdf-export-first-page.png` — PDF first page (header + filter summary visible).
- [ ] **22.5** 📸 **Screenshot** slot **#12**: `phase69-12-pdf-export-middle-page.png` — PDF a middle page (row formatting + page numbering visible).
- [ ] **22.6** Re-open `/org/acme-corp/settings/audit-log` → top of the list now shows a **reflexive** event `audit.export.generated` (`AuditExportService.java:114, 187`) with actor=Bob, recent timestamp.
- [ ] **22.7** Expand the reflexive row → payload contains the export filter summary (range, preset/severities).

**Day 22 checkpoints**
- [ ] PDF download succeeded with `Content-Type: application/pdf`.
- [ ] PDF passes header / filter-summary / row-formatting / page-numbering visual sanity.
- [ ] Reflexive `audit.export.generated` event present and correctly attributed to Bob.

**Audit assertion**
- `eventType="audit.export.generated"`, actor matches the exporter, payload reflects filter inputs.

---

## Day 25 — DSAR pack: `audit-trail/events.csv` scope correctness  `[all profiles]`

**Actor**: Bob (Admin) executes the DSAR pipeline; the data subject is a Day 0 customer.

- [ ] **25.1** Submit a DSAR for a chosen Day 0 customer via the Phase 50 pipeline (`/org/acme-corp/settings/dsar/new` or POST `/api/dsar/requests`).
- [ ] **25.2** Allow the pipeline to run to completion. Open the DSAR detail page → confirm pack ZIP is downloadable.
- [ ] **25.3** Download and unzip. Locate `audit-trail/events.csv` (shipped in slice 505A per task 510.1).
- [ ] **25.4** Open the CSV. Verify:
  - the subject's events are present (matter creation, document uploads, closure override if applicable to this customer's matter, etc.),
  - **events belonging to other customers are absent** (zero leakage).
- [ ] **25.5** Spot-check 3 random rows by `entity_id` against the live DB (`SELECT entity_id, customer_id FROM audit_event WHERE id = ...` — read-only sanity, NOT a fix-via-SQL).

**Day 25 checkpoints**
- [ ] DSAR pipeline completed, pack ZIP downloadable.
- [ ] `audit-trail/events.csv` present in the pack.
- [ ] Subject's events present; no foreign-customer leakage.

**Audit assertion**
- DSAR pack file `audit-trail/events.csv` exists.
- Every row in the CSV is scoped to the subject (by `customer_id` or equivalent join column).

---

## Day 30 — Export-row 10 000 cap + graceful narrowing  `[all profiles]`

**Actor**: Bob (Admin).

- [ ] **30.1** Navigate to `/org/acme-corp/settings/audit-log`. Set filter range = **last 2 years** on a tenant with sufficient history. (For dev tenants without 2-year history, the cap won't trigger — flag and skip with rationale.)
- [ ] **30.2** Click **Export → PDF**. Expected: a 413-equivalent error response ("Too many rows for PDF export — please narrow the range") rendered as a toast / inline error, NOT a hung request and NOT a 500.
- [ ] **30.3** Confirm no half-written PDF is downloaded (browser does not save a 0-byte file).
- [ ] **30.4** Narrow the range to **last 30 days**. Re-export. Receive a successful PDF.
- [ ] **30.5** Confirm a fresh `audit.export.generated` row appears for the successful narrowed export (and **none** for the failed wide export — failed exports must NOT emit a reflexive row).

**Day 30 checkpoints**
- [ ] Wide-window export fails gracefully (toast / inline error), no 500, no hung request.
- [ ] Narrowed export succeeds.
- [ ] Reflexive `audit.export.generated` event recorded only for the successful run.

**Audit assertion**
- For the failed run: backend response status reflects the 10 000-row cap (e.g. 413 Payload Too Large or 422 with a structured error code).
- For the successful run: `eventType="audit.export.generated"` row present.

---

## Exit checkpoints (ALL must pass for capstone-ready)

- [ ] **E.1** Every step above is checked across the exercised vertical profiles (or clearly marked N/A with rationale for profile-gated skips).
- [ ] **E.2** All 12 📸 screenshot slots captured without visual regression against the Playwright baselines (once 510B populates them under `documentation/screenshots/phase69/`).
- [ ] **E.3** Zero BLOCKER or HIGH items in gap report (`tasks/phase69-gap-report.md` — produced in slice 510B). Day 5 emission gap may be MEDIUM if Phase 70 owns the fix.
- [ ] **E.4** **Preset coverage** verified — Sensitive, Compliance, Security, Financial approvals presets all render without error and produce role-appropriate result sets.
- [ ] **E.5** **Severity rendering** verified — CRITICAL, WARNING, INFO pills all render distinctively (colour + text) on the Day 15 expanded row and the dashboard widget.
- [ ] **E.6** **Per-entity scope** verified — Day 20 customer Audit tab and Day 15 matter-closure Audit tab show events scoped to the entity, with zero cross-entity leakage.
- [ ] **E.7** **Export evidence quality** — Day 22 PDF passes header / filter-summary / row-formatting / page-numbering checks; Day 30 wide-window export fails gracefully.
- [ ] **E.8** **Reflexive audit** — every successful export emits `audit.export.generated`; failed exports do not.
- [ ] **E.9** **DSAR scope** — Day 25 `audit-trail/events.csv` contains exactly the subject's events; zero leakage.
- [ ] **E.10** **Cycle completed on one clean pass** — no dev subagent dispatches mid-loop for blockers (Day 5 emission gap is the documented exception, scoped to Phase 70).
- [ ] **E.11** **Test suite gate** (mandatory — see master doc "Test suite gate" section):
  - [ ] `cd backend && ./mvnw -B verify` → BUILD SUCCESS, zero failures, zero newly-skipped tests
  - [ ] `cd frontend && pnpm test` → all vitest suites pass
  - [ ] `cd frontend && pnpm typecheck` → zero TS errors
  - [ ] `cd frontend && pnpm lint` → zero lint errors
  - [ ] Every fix PR merged during this cycle satisfied the same four gates before merging (not just the final run)

**If any checkpoint fails**: log finding to `tasks/phase69-gap-report.md` (produced in slice 510B) using the severity/format defined in the master doc, and let `/qa-cycle-kc` dispatch a fix before re-running the failing step. **Fix PRs that do not pass the test suite gate (E.11) must NOT be merged** — either extend the fix to cover broken tests or revert and re-approach.
