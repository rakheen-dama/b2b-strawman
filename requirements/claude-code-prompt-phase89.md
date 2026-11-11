# Phase 89 — Go-Live Readiness: Trust, Compliance, Documents and Portal Correctness

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-11-03). Runs after Phase 88 (75/75 slices merged, closure **PARTIAL — exit test FAIL, not demo-ready**) and before Phase 84 (take-on). Phase 88's end-of-phase re-walk left **63 open gaps (3 HIGH · 15 MEDIUM · 45 LOW) + 1 hypothesis** (`qa_cycle/evidence/phase88-rewalk/full/gaps.md`). The walk's severity scale measures "can the step be demo'd"; several MEDIUMs are **go-live blockers for a design-partner firm** under that lens — no Section 35 trust reports, an interest run nobody can approve, a POPIA export and anonymisation that crash, a conflict check that crashes on an unlinked adverse party, and a client ledger whose running balance is wrong. Phase 84 puts real firms on Kazi; this phase makes that safe.
>
> **Pre-phase status (2026-11-04) — already merged, do NOT re-plan:** P1 #1716 (G88-F48 + F49, SA clock), P2 #1717 (G88-F33), P3 #1718 (G88-F12 + Letter of Demand → `opposing_party` + Bond Cancellation mortgagor = seller; scenario 14.4/14.5/14.13 amended; wording verified by the founder on the attorney's behalf), #1719 (G88-F38, G-D10b, G88-F46, **G88-F17** distinct audit labels incl. `security.*` / `ai.gate.*`, audit-trail React #418), #1720 (G88-F36 confirmed + fixed, G88-M10), #1721 (G88-F47 `typecheck` = `next typegen && tsc --noEmit` + CI step), and G88-F35 (`/epic_v2` marker commit, `291e1a082`). So: E2 is F43 + F44 only; E3 drops M10 and F46; E6 drops F35, F36, F47 and G-D10b and keeps the scenario amendments. New follow-up candidates surfaced by these PRs (triage at `/architecture`): generation-time warning for unset required matter fields (CodeRabbit on #1718 → fits E3); mention recipients are not filtered by matter access (#1720); `compliance/requests/[id]` renders UTC times; remaining audit-label polish ("REPORT EXPORTED", "Paia", "Lpff", "Matter closure closed"); `BankStatementParseException` → HTTP 500; SoA dialog defaults the next period start after the period end; respondent-side litigation templates; standalone bond cancellation (no sale) renders a blank mortgagor.
>
> **Founder decisions (2026-11-03, all recommendations accepted):**
> 1. **Sequence:** the three HIGH gaps ship first as **standalone fix PRs outside this phase** (§ Prerequisite below), then Phase 89, then Phase 84. The 32 LOW product bugs go to a backlog unless a Phase 89 slice already edits the same file.
> 2. **Exit bar:** **0 HIGH and 0 MEDIUM** open on the walked path, every scenario mismatch ruled on, LOW gaps tracked but not blocking. This replaces Phase 88's "0 FAIL outside register §5".
> 3. **Exit gate:** a **targeted** Keycloak re-walk (the step ids of every closed gap + every amended step), not a full 90-day walk, plus the three Playwright suites on a fresh stack.
> 4. **"Today" is South African time product-wide** (`Africa/Johannesburg`, no DST; Kazi is SA-only). No per-firm timezone setting.
> 5. **OTP parties are explicit matter fields** (`seller_name`, `purchaser_name`), not inferred from the client.
> 6. **Build:** a client **payment receipt** on the portal (G88-F32) and the **portal deadline sync** for court dates and prescriptions (register §5b row 19, without mark-read).
> 7. **Scenario authorisations (root CLAUDE.md §6) — authorised 2026-11-03:**
>    - the 7 **design-proven** mismatches are amended to match the product: G88-F1 (27 rows), F7 (add matter-team steps), F11, F14, F21 (add a second-member approval step for the 72.1 PAYMENT), F22 (resolve the template tasks before 75.1), F25 (put a court date inside the 14-day Upcoming window, or re-word);
>    - G88-F37 (10 missing-feature rows) → **amend** the scenario, do not build;
>    - G88-F34 (final / quarterly digest) → **drop** from the portal scenario;
>    - G88-F28 → **add** a step to the 90-day file in which the firm sends Sipho an information request (the portal file's prerequisite);
>    - register §5b row 20 (portal profile editing) → **amend**: the portal profile is read-only, the firm owns contact data;
>    - register §5b row 19 → **amend** to drop mark-read; keep the deadline steps (now built);
>    - P-E.2b / P-E.10 → **retire** the committed-visual-baseline reading; the walker reviews the captured frames instead. No baseline PNGs are committed.
> 8. **G-D10b:** the audit Event-type combobox **hides** the stored key from display but keeps it **searchable** (matches on key and label). ADR-357 / ADR-342 stand unamended.
> 9. **G88-F47:** add a `typecheck` script (`tsc --noEmit`, whole project including tests) to `frontend/` and `portal/`, rather than amend E.11c.
> 10. **Phase 87 rulings ratified:** 615D D-1 "All clients" default view; OQ #18 retirement of `project-expenses.spec.ts`. The 611A.2 "Mandate accepted" badge (needs backend `templateCategory`) is **carried to Phase 84**.
> 11. **AI-core walk:** still DEFERRED; the founder supplies a funded key and schedules it separately.
>
> **Scouting note (verified 2026-11-03 against `origin/main` `710db1b59`; product tree unchanged since #1709 `87784b799` — `git log 87784b799..origin/main -- backend frontend portal packages gateway compose` is empty; 629C re-read every HIGH/MEDIUM file:line at HEAD on 2026-10-30. Do NOT re-derive; the gap rows in `full/gaps.md` hold the full evidence):**
> - **Clock.** `BackendApplication.java:19-21` defines `Clock clock()` as `Clock.systemDefaultZone()` (UTC in containers; no `TZ` / `user.timezone` in compose). `LocalDate.now()` (no clock) appears at **57 sites in 32 files** under `backend/src/main/java`; only 7 sites use `LocalDate.now(clock)`. Trust sites: `TrustTransactionService.java:622,1114,1139,1264,1281,1328,1334`, `TrustAccountService.java:147,244`, `TrustInvestmentService.java:243,289`. Frontend server "today": `my-work/page.tsx:109`, `dashboard/page.tsx:92` (`new Date().toLocaleDateString("en-CA")` in the Next.js process zone). (G88-F48, F49)
> - **OTP.** `clause-packs/conveyancing-za-clauses/pack.json:34` binds `customer.name` ("accepts the property…"), `:36` binds **`org.name`** ("shall not be liable"). `template-packs/legal-za/offer-to-purchase.json:55,179` render `project.customFields.seller_name`; **no field pack defines `seller_name` or any purchaser field** (grep over `field-packs/` → 0). (G88-F12)
> - **S3 keys.** `StatementService.java:130-133` `s3Key = "org/" + tenantId + "/generated/" + buildFileName(slug, project, periodEnd)`; `:384-388` = slug + slugified matter name + `periodEnd`. `GeneratedDocumentService.java:245-246` + `PdfRenderingService.java:329-334` = slug + entity name + `LocalDate.now()`. Collisions: statements on matter + period end; generic templates on entity + generation day. (G88-F33)
> - **Trust reports.** `TrustReportPackSeeder.java:38` `seedForTenant` has **no caller** (only its own class references it) → no Section 35 report definitions on any tenant. (G88-F40, the 627E N-2 remainder)
> - **Interest approval.** `InterestRunWizard.tsx:173` holds the DRAFT run in session state; `trust-accounting/interest/page.tsx:135-186` runs table has no actions; `InterestService.java:455` blocks self-approval → a DRAFT run created by one member can never be approved by another. (G88-F39)
> - **Client ledger.** `trust-accounting/client-ledgers/[clientId]/page.tsx:44-45,190-205` and `transactions/page.tsx:48-49` treat REFUND as an inflow and walk rows in the wrong order; `ClientLedgerService.java:51,157`. (G88-F23)
> - **POPIA.** Export: API returns `ExportResult(exportId, status, estimatedFiles)` (`datarequest/ExportResult.java:6`); the UI renders "files · NaN undefined" and "Download Archive" has no href. Anonymise: `anonymize-customer-dialog.tsx:148-154` reads `preview.affectedEntities.*` (`lib/types/data-protection.ts:100-114`), a shape the API does not return → TypeError. (G88-F43, F44)
> - **Conflict check.** `components/legal/conflict-check-result.tsx:141` `match.relationship.replace(…)` with `relationship` null for an unlinked adverse-party match. (G88-F38)
> - **Portal deadlines.** `DeadlinePortalSyncService` (ADR-256) wires only `CUSTOM_FIELD_DATE`; `COURT_DATE` and `PRESCRIPTION_TRACKER` are documented **dormant** — the domain events (`CourtDateScheduledEvent`, `CourtDateCancelledEvent`, `PrescriptionDeadlineSetEvent`) do not exist. `FILING_SCHEDULE` is an archived accounting concept (ADR-330) — leave dormant. (§5b row 19)
> - **Portal read model / copy.** Template instantiation publishes no `PortalTaskCreatedEvent` (`ProjectTemplateService.java:589-609`; projection fed only by `TaskService.java:1082`) → portal shows 2 of 9 tasks. Raw enums/ids to clients: `portal/components/status-badge.tsx:47-49,65-67` (task OPEN/DONE), `requests/[id]/page.tsx:99,190` + `requests/page.tsx:93` (SENT/IN_PROGRESS/SUBMITTED), request h1 = empty `matterName` for client-level requests (`requests/[id]/page.tsx:92-93`), trust page "Matter d42337ad" + REFUND/PAYMENT/DEPOSIT (`portal/lib/api/trust.ts:107-110`, `balance-card.tsx:30`, `transaction-list.tsx:181`). Receipt: the paid fee note's only download is the pre-payment PDF (`PortalInvoiceController.java:114-120`). (G88-F19, F20, F29, F30, F31, F32)
> - **Documents.** SoA, closure letter and closure SoA print no letterhead block and blank "VAT Reg:" / "File reference:" labels (`template-packs/legal-za/statement-of-account.json:16,78`; `matter-closure-letter.json:6,11,111-135`) although U8's `LetterheadSettings` now exist (626D reconciled only the fee note and engagement letter). Tariff line stored with `invoice_lines.sort_order` 0 → prints second. Client overflow "Generate Document" pushes `?action=generate-document` that nothing reads (`client-overflow-menu.tsx:120`; `GenerateDocumentDropdown` no longer mounted since Epic 560A). (G88-F16, M10, F46)
> - **Firm UI.** Field-group membership: FE sends `fieldDefinitionIds: []` (`custom-fields-content.tsx:281-287`, `FieldGroupDialog.tsx:74,163`), BE ignores membership (`UpdateFieldGroupRequest.java:8-13`, `FieldGroupService.java:111-140`). Matter Team tab shows org roles, no lead marker (G88-M2). Matter Overview "OUTSTANDING FEE NOTES" / "Trust balance" tiles are stubs reading "--" (`components/projects/kpi-dashboard.tsx:199-213`). Disbursement tables 1249/1257 px hide BILLING/ACTIONS behind an inner scroll at 1440 (G88-M4). Comment notification lands on the task list, not the comment (G88-M6). Activity feed shows acceptance events with no source-PORTAL marker (`activity-feed-client.tsx:38-39`, G-B8). (G88-F3, M2, M13, M4, M6, G-B8)
> - **Audit filter key.** `audit-log-client.tsx:322` (current value) and `:342` (list item) render the stored key next to the label. (G-D10b)
> - **@mention.** `NotificationService.java:817` `MENTION_PATTERN = "@(\\w+(?:\\s+\\w+)?)"` greedily takes the next word; no test references it. **HYPOTHESIS** — reproduce red-first. (G88-F36)
> - **Process.** 5 Epic-624 PRs have no committed verify marker: the `/epic_v2` post-merge docs commit never `git add`s `.claude/markers/verify-*.json`. (G88-F35)
> - **Migrations:** latest tenant **V9**, global **V2** → next free tenant **V10**, global **V3** (`backend/db/BASELINE.md:70`). Take the next free number at build time.

## System Context

Kazi after Phase 88 is a legal-only (`legal-za`) practice-management product: matters with teams, clients with checklist-driven FICA, time and narrative fee notes with part payments, disbursements with write-off, trust accounting with interest runs and investments, one diary, conflict checks, POPIA data-subject requests, Tiptap document templates and clause packs with a pack-reconcile framework (625E), firm letterhead and banking settings, acceptance with decline, comment attachments, retention persisted at close, and a client portal with a global email→firm directory. Phase 88 closed most of the Phase 87 capstone at the root, and its re-walk showed the remaining defect layer: **time-of-day** (UTC server dates against SAST browsers), **document identity** (storage keys derived from names and dates), **seeders and listeners that exist but are never wired** (Section 35 reports, portal deadline sync), **frontend/backend shape drift** on rarely-walked compliance screens (POPIA, conflict check), and **raw enums and ids on client-facing surfaces**.

## Objective

A design-partner firm can run trust, compliance, documents and the client portal on Kazi without a defect that an auditor, a client or a regulator would notice. Concretely: (1) every trust posting, report and interest run is correct on the SA calendar day, and the Section 35 reports exist on every tenant; (2) the POPIA export and anonymisation and the conflict check work end to end; (3) every client-facing document carries the letterhead and is stored under its own key; a paid client can download a receipt; (4) the portal shows a client every task, their court dates and prescription deadlines, and never a raw enum or id; (5) the firm-side MEDIUMs are closed; (6) the authorised scenario amendments are applied, and a targeted Keycloak re-walk plus the three suites observe **0 HIGH and 0 MEDIUM**.

## Constraints & Assumptions

- **Prerequisite:** the three HIGH fix PRs (below) are merged before Phase 89 epic 1 starts. Phase 89 slices rebase on them.
- **Reproduce-first, observed PASS** (root CLAUDE.md §3, §4). Each slice starts by reproducing its gap on the :3001 mock-auth stack or the Keycloak stack; the 628E/628F `*-BLOCKER` fixme specs are the red tests where they exist (`frontend/e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts:292` F38, `day-60-interest.spec.ts:160` F39, `day-90-review.spec.ts:123` F40, `customers/customer-data-protection.spec.ts:48,76,107` F43/F44, `documents/document-generation.spec.ts:86` F46). Each slice that closes a gap removes its fixme in the same PR.
- **Real-write-path tests.** Every backend fix adds an integration test that creates its rows through the service path the UI uses (Phase 88 lesson: tests green because they never walked the real path).
- **Frontend/backend shape drift is a contract bug.** F43 and F44 are fixed by aligning the TypeScript type to the backend record **and** adding a contract test that fails if they drift again (follow the 628-era wire-contract pin pattern).
- **Wiring an existing seeder or listener reaches existing tenants.** F40 ships a reconcile through the 625E framework (version bump + idempotent re-apply) so the walked Keycloak tenant and demo tenants gain the report definitions; the portal deadline sync ships a one-time backfill of existing future court dates and prescriptions.
- **One fix per PR** (root CLAUDE.md §7). Same-class clusters authorised for this phase: portal raw-enum/id copy (F20, F29, F30, F31) as one slice; the scenario amendments as one docs PR.
- **ADR-342 / ADR-357 stand.** No stored literal changes; G-D10b hides the key in the UI only.
- **Quality gates unchanged:** full `./mvnw verify` via `backend/scripts/verify.sh` for any backend change; `pnpm lint && pnpm build && pnpm test` + `format:check` (+ the new `typecheck`) for frontend and portal; two reviewer passes; CodeRabbit where it runs; merge-gate hook; **verify markers committed in the post-merge bookkeeping step** (F35).
- **No re-proposal of removed features** (ADR-330); no tier/plan UI.

## Prerequisite — three HIGH fix PRs (outside this phase, in this order)

Each is one PR, reproduce-first, full gates, two reviewer passes.

### P1 — SA-time clock (G88-F48 HIGH + G88-F49 LOW, same-class cluster authorised)

**Change:** `BackendApplication.clock()` returns `Clock.system(ZoneId.of("Africa/Johannesburg"))`; every `LocalDate.now()` in `backend/src/main/java` becomes `LocalDate.now(clock)` (57 sites / 32 files; trust sites first); an ArchUnit (or source-scan) test bans zone-less `LocalDate.now()` / `LocalDateTime.now()` in main code. Frontend server "today" (`my-work/page.tsx:109`, `dashboard/page.tsx:92`, `resolveMyWorkDateRange` `:45-71`) uses an `Africa/Johannesburg` formatter from `@b2mash/shared`. No firm setting.
**Acceptance:** red-first lifecycle run with the Playwright host in a zone whose date has rolled over ahead of UTC (in the 00:00–02:00 SAST window, or `TZ=Pacific/Kiritimati` 10:00–24:00Z): `day-60-interest.spec.ts:96` and `day-07-14-work-trust.spec.ts:167,306` fail before, pass after. Then a **separate, normal, out-of-window** lifecycle run re-scores the 17 Phase 88 run-1 DEFERRED tests, `day-90-review.spec.ts:95` (carried) and `:192` (`full/gaps.md:256-258`).
**Tests:** integration test with a fixed `Clock` at 23:30Z: a fee transfer is dated the SAST day; the interest walk for that day uses the SAST balance.

### P2 — Unique generated-document storage keys (G88-F33 HIGH)

**Change:** every generated document's S3 key includes the `GeneratedDocument` / `Document` id (or a UUID), for `StatementService` and `GeneratedDocumentService`; the human-readable file name stays as the download name. Existing collided rows: record which tenants have ≥2 `documents` rows sharing an `s3_key` (read-only report in the PR); overwritten bytes cannot be recovered — the remedy is "regenerate", documented, not automated.
**Acceptance:** generate a portal-shared SoA and then an internal closure SoA for the same matter and period end; the portal Download serves the shared one (bytes differ, both rows have distinct keys). Same for two generic documents for one entity on one day.
**Tests:** integration tests for both collision shapes.

### P3 — OTP parties (G88-F12 HIGH)

**Change:** add `seller_name` and `purchaser_name` field definitions to the conveyancing field pack; the OTP template and the Voetstoots clause bind to them (purchaser accepts, seller not liable); reconcile existing tenants through the 625E framework (field pack + clause pack + template). Audit every conveyancing clause and template for the same `org.name` / `customer.name` party-binding mistake and fix any found in the same PR (same-bug-class, authorised). The clause text is **legal content**: the PR body requests attorney review of the final wording; merge does not wait on it, but the gap is recorded VERIFIED only after sign-off.
**Acceptance:** on the Keycloak tenant, a conveyancing matter with both fields filled renders an OTP whose Seller line and Voetstoots clause name the seller, and whose purchaser clause names the purchaser.

## Epic specifications

Each epic lists: closes · change · acceptance (observed) · tests. Gap rows in `full/gaps.md` hold the file:line evidence.

### E1 — Trust accounting correctness

**Closes:** G88-F40 (MEDIUM), G88-F39 (MEDIUM), G88-F23 (MEDIUM).
**Change:** (a) wire `TrustReportPackSeeder.seedForTenant` into tenant provisioning for `legal-za` and reconcile existing tenants; the trust reports page lists the Section 35 reports and each one runs. (b) Interest runs: the runs table lists DRAFT runs with **Approve** / **Post** actions gated on the trust-approval capability; a second member approves a run created by the first; self-approval stays blocked (`InterestService.java:455`); the wizard no longer holds the only handle to a DRAFT. (c) Client ledger running balance: REFUND is an outflow; rows are walked in posting order (date, then created-at) on both the ledger card page and the transactions page; the backend supplies the running balance if that is the simpler single source (architecture decides).
**Acceptance:** Day 60 interest run created by Thandi, approved by Bob; Day 90 Section 35 reports generated on the Keycloak tenant; Moroka ledger running balance matches `calculateClientBalanceAsOfDate` row by row after a refund.
**Tests:** un-fixme `day-60-interest.spec.ts:160` and `day-90-review.spec.ts:123`; integration tests for the reconcile and for running-balance order with a refund.

### E2 — POPIA data-subject requests and conflict check

**Closes:** G88-F43 (MEDIUM), G88-F44 (MEDIUM), G88-F38 (MEDIUM).
**Change:** data export "Export Ready" reads the backend `ExportResult` shape and the Download Archive link resolves to the export file; the anonymise preview renders the shape the backend returns (align type to API; extend the API only if the preview genuinely needs counts it lacks); conflict-check result handles a null `relationship` (label "Adverse party — not linked to a client" or equivalent; copy via the frontend specialist). Contract pins for both POPIA DTOs.
**Acceptance:** on the Keycloak tenant, export a client's data and download the archive; preview and complete an anonymisation; run a conflict check that hits an unlinked adverse party.
**Tests:** un-fixme `customer-data-protection.spec.ts:48,76,107` and `day-07-14-work-trust.spec.ts:292`; FE/BE contract tests.

### E3 — Client-facing documents

**Closes:** G88-F16 (MEDIUM), G88-F32 (MEDIUM, build), G88-M10 (LOW, on wow frame 6), G88-F46 (LOW, same file family).
**Change:** (a) SoA, closure letter and closure SoA templates render the letterhead block from `LetterheadSettings` like the fee note; empty optional labels ("VAT Reg:", "File reference:") are omitted, not printed blank; pack reconcile. (b) **Payment receipt:** a PAID or PARTIALLY_PAID fee note offers "Download receipt" on the portal (and in the firm app), rendered through the Tiptap pipeline from the payment events (date, method, amount, reference, balance remaining) on the letterhead; no new table unless architecture proves one is needed. (c) Tariff lines get a `sort_order` that keeps them in billed order with time lines. (d) Client overflow "Generate Document" opens the generate dialog (mount it or read the query param — architecture decides).
**Acceptance:** observed on the Keycloak tenant: SoA and closure letter PDFs with letterhead; Sipho downloads a receipt for INV-0001 after paying; fee-note PDF line order matches the editor; Generate Document from the client page works.
**Tests:** un-fixme `document-generation.spec.ts:86`; template render tests for omitted empty labels; receipt integration test for full and part payments.

### E4 — Portal client experience

**Closes:** G88-F19 (MEDIUM), register §5b row 19 deadlines (build), G88-F20, F29, F30, F31 (LOW, same-class cluster).
**Change:** (a) template instantiation publishes `PortalTaskCreatedEvent` per task (or the projection subscribes to the instantiation event) and existing matters get a one-time backfill of missing portal task rows. (b) **Portal deadline sync:** introduce the court-date and prescription domain events, wire `DeadlinePortalSyncService` listeners for `COURT_DATE` and `PRESCRIPTION_TRACKER`, backfill future rows, and show them on portal `/deadlines`; cancelled court dates disappear. Visibility rule is an ADR (default: every court date and prescription on a matter the contact's client owns). No mark-read control. (c) Portal status badges and request/trust pages use human labels; the trust page names the matter, not an id prefix; a client-level request h1 shows a sensible title.
**Acceptance:** Sipho's portal matter shows all 9 template tasks with readable statuses; his court date appears on `/deadlines`; requests and trust pages show no raw enum or id.
**Tests:** un-fixme `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts:166`; integration tests for the task projection on instantiation and for each deadline event; a portal guard test banning raw status enums in rendered copy (extend the existing terminology guard).

### E5 — Firm-side MEDIUMs

**Closes:** G88-F3, G88-M2, G88-M13, G88-M4 (residual), G88-M6, G-B8 (all MEDIUM).
**Change:** (a) field groups: Edit Field Group sends the selected field ids and the backend persists membership; membership is visible in the list. (b) matter Team tab shows the **matter** role with a Lead marker. (c) matter Overview tiles "Outstanding fee notes" and "Trust balance" read real values (existing debtor-book predicate and client ledger balance), or are removed if architecture finds no single correct source. (d) disbursement tables fit at 1440 (column priority / container query, no hidden actions column). (e) comment notifications deep-link to the comment (task sheet open + scroll/highlight, or document comment). (f) activity feed marks portal-originated events with a source "Client portal" marker; the DTO carries the source.
**Acceptance:** each observed in the browser at 1024 and 1440 on the Keycloak tenant.
**Tests:** BE integration for field-group membership; vitest for tiles, team role, feed marker; Playwright for the notification deep link.

### E6 — Scenario amendments, tooling and the hypothesis

**Closes:** the 12 scenario mismatches (G88-F1, F7, F11, F14, F21, F22, F25, F28, F32 — now built, F34, F37, F47), register §5b rows 19–20 (amended as authorised), G-D10b, G88-F35, G88-F36 (reproduce or refute), P-E.2b / P-E.10 wording.
**Change:** one docs PR applies decision 7 to `qa/testplan/demos/legal-za-90day-keycloak.md` and `portal-client-90day-keycloak.md` in the LZKC-029 annotation style, citing "Phase 89 decision 7 / gap id"; F32 and §5b row 19 steps stay as written because the product now does them. Add `typecheck` scripts to `frontend/` and `portal/` and fix any type errors they surface in tests (separate slice if large). Audit combobox hides the key, matches on key and label. `/epic_v2` post-merge docs-commit step `git add`s the verify markers and the epic gate checks one marker per code PR. F36: red-first integration test for "@Bob please review"; fix only if it fails.
**Acceptance:** the amended files read consistently (no step references a removed control); `pnpm typecheck` green in both apps; the next `/epic_v2` merge commits its marker.

### E7 — Exit gate and deck re-shoot

**Change:** after E1–E6 merge: (a) the three suites (E2E-LEGAL lifecycle + depth-ii, default config, portal) on a fresh stack (`e2e-down && e2e-up`, `docker builder prune -af` first), **outside 00:00–02:00 SAST**; (b) a **targeted Keycloak re-walk**: the step ids listed against every gap closed by P1–P3 and E1–E5, plus every step amended in E6, walked in day order on a fresh Keycloak tenant; prerequisite steps between them may be driven quickly and are recorded but not scored; G-D5 re-observed with a court date inside 14 days; (c) a gap register in `qa_cycle/evidence/phase89-rewalk/`; (d) deck re-shoot on a **production frontend build** (no Next.js dev indicator) at 1440 × 900: deck lines 84, 85, 86, 95, 96 and the wow frames, then `exec-deck.html` updated and every `<img>` re-checked (Phase 88 C.12 (a)).
**Exit:** 0 HIGH, 0 MEDIUM open; every scenario mismatch ruled; suites at or above their 628I baselines with the un-fixme'd specs passing; verify markers committed for every code PR. LOW findings are filed, not blocking.

## Out of Scope

- The 32 LOW product bugs in `full/gaps.md` (backlog) except where a slice already edits the file — notably F2, F4, F5, F6, F9, F10, F13, F15, F17, F18, F24, F26, F27, F41, F42, F45, M5, M7, M9, M11, M14, G-D10a, G-D10c, G-D17, G-D20; N-9…N-12.
- Portal mark-read for deadlines; portal profile editing; a final / quarterly portal digest; the ten G88-F37 features.
- A per-firm timezone setting; non-SA deployments.
- Phase 84 take-on and the 611A.2 Mandate badge; Phase 87 / 88 C.12 (c) next-phase items (`portal_notification_doc_types` column drop, header-field pack flag, "Approve & send", real document-acceptance trigger, backend `GOVERNMENT` party type, `/api/calendar` deletion, profitability cost leg, redirect-table removal).
- Committed visual baselines; the AI-core walk; any horizontal feature (ADR-330); any tier/plan UI.

## ADR topics for `/architecture`

- ADR: product-wide `Africa/Johannesburg` clock and the ban on zone-less `now()` (why not per-firm; why not container `TZ`). *(Written for P1; architecture records it.)*
- ADR: generated-document storage keys are identity-based, not name/date-based; download name vs storage key.
- ADR: conveyancing party fields are explicit matter fields; clauses never infer a contractual party from `org.*` or `customer.*`.
- ADR: client-ledger running balance — computed where (backend vs frontend) and the ordering rule.
- ADR: interest-run lifecycle as a persisted DRAFT with a second-member approval surface.
- ADR: portal deadline visibility rule for court dates and prescriptions; event model and backfill (extends ADR-256).
- ADR: payment receipt as a generated document from payment events (no receipt table).
- ADR: FE/BE DTO contract pins for compliance screens.

## Style and boundaries

- Order: P1 → P2 → P3 (prerequisites), then E1, E2 (go-live blockers), E3, E4, E5, E6 (E6's docs PR may run in parallel once E3/E4 settle which steps are built), then E7.
- Every slice's PR body cites the gap id(s), the reproduction evidence, and the observed PASS after the fix. "Code looks right" is not PASS.
- Agents may not edit `qa/testplan/demos/*.md` beyond decision 7; new scenario mismatches are filed as gaps (next free id after G88-F49 — start a `G89-` series in the Phase 89 register).
- Lifecycle suite runs that score results are run **outside 00:00–02:00 SAST** (Phase 88 lesson: time of day is a test variable); red-first time-zone reproductions are separate runs.
- Backend verify via `backend/scripts/verify.sh`; `./mvnw -q clean compile -DskipTests` before a Keycloak walk; follow `backend/CLAUDE.md` and `frontend/CLAUDE.md`; UI questions to the `vercel:nextjs` / `vercel:shadcn` / `frontend-design` skills; browser-check every UI slice at 1024 and 1440 px.
