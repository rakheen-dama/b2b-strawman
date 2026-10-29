# Phase 88 end-of-phase re-walk (629) — Keycloak — evidence index

Scenario `qa/testplan/demos/legal-za-90day-keycloak.md` (as amended by 628H). Stack: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080, Keycloak :8180, Mailpit :8025, portal :3002 — prod build for portal steps) served from the main checkout at `138119b8116a0ee3e32e86293815cd6479a1211f` (= origin/main) after `./mvnw -q clean compile -DskipTests`. Walk date 2026-10-29 (UTC). Walkers: 629A runs W1a…W4 (branch `phase88/12A-full-rewalk-days-00-35`); 629B (Days 36–90 + portal 90-day + E.1–E.11).

## KC-STACK (629.1)

Start time 2026-10-29T13:41:04Z. Host clock is SAST (UTC+2); Postgres `installed_on` values are local time.

| # | Command | Result |
|---|---|---|
| 1 | `pmset -g log` (last 5 sleep/wake) + `pmset -g batt` + `nohup caffeinate -dimsu -t 43200 &` | last sleep 2026-10-27 13:10:47 (Maintenance Sleep), wake 13:13:32; "Now drawing from 'AC Power'", battery 100 % charged; caffeinate hold PID 99323 (phase runner PID 6817 `caffeinate -is ./scripts/run-phase.sh 88` left alone) — `logs/kc-stack-pmset.txt`, `logs/kc-stack-identity.txt` |
| 2 | `git rev-parse HEAD origin/main`; `git status --short -- backend frontend portal gateway packages compose` | both `138119b8116a0ee3e32e86293815cd6479a1211f`; status empty; worktree HEAD identical — `logs/kc-stack-identity.txt` |
| 3 | zombie check `pgrep -fl 'surefire\|ForkedBooter\|embedded-pg'` | none |
| 4 | `svc.sh stop backend gateway frontend portal`; ports free | before: backend `ext` 5527, gateway `ext` 68482, frontend 45819, portal 88764; all stopped; :8080/:8443/:3000/:3002 all empty — `logs/kc-stack-stop.txt` |
| 5 | flyway BEFORE (stack down) | `logs/kc-stack-flyway-before.txt` — both tenants V8 (`phase88 retention comment attachments`, 2026-10-28 12:23:09), public V2 |
| 6a | `docker builder prune -f` | "Total: 2.177GB" reclaimed (`logs/kc-stack-builder-prune.txt`) |
| 6b | `./mvnw -q clean compile -DskipTests` | exit 0, wall 16 s; `target/classes/db/migration/tenant/` = V1…V9 (`V9__phase88_comment_attachments.sql`) — `logs/kc-stack-compile.txt` |
| 6c | `pnpm install --frozen-lockfile` | "Lockfile is up to date … Already up to date" — `logs/kc-stack-pnpm-install.txt` |
| 7 | `svc.sh start all` | backend ready 27 s, gateway 6 s, frontend 3 s, portal 3 s (wall 41 s) — `logs/kc-stack-start.txt` |
| 7b | health | 8080 200 `{"status":"UP"}`, 8443 200 `{"status":"UP"}`, 3000 200, 3002/login 200, :8180 realm 200 — `logs/kc-stack-svc-status.txt` |
| 8 | backend ERROR lines this start | 0 |

Result: **629.1 PASS** (fresh processes from HEAD, V9 applied to both tenant schemas, global stays V2, all healthy).

Spec drift note for 629D (ruling C-2, no gap): 629.1's "Flyway lines showing V4–V7 + global V2", the 629A row's "KC is at tenant V5 / global V2" and 629.12's "tenant V7" are stale. Observed: KC tenants V8 before → **V9** after this restart; global **V2**.

## `svc.sh status` (verbatim)

```
  SERVICE      PID      RUNNING  HEALTHY  PORT
  -------      ---      -------  -------  ----
  backend      99912    yes      yes      8080
  gateway      207      yes      yes      8443
  frontend     344      yes      yes      3000
  portal       441      yes      yes      3002
```

Port holders (pid / etime at 13:42:5xZ / cwd):

```
:8080 pid 175 etime 00:33 cwd /Users/rakheendama/Projects/2026/b2b-strawman/backend
:8443 pid 334 etime 00:16 cwd /Users/rakheendama/Projects/2026/b2b-strawman/gateway
:3000 pid 421 etime 00:12 cwd /Users/rakheendama/Projects/2026/b2b-strawman/frontend
:3002 pid 494 etime 00:08 cwd /Users/rakheendama/Projects/2026/b2b-strawman/portal
```

## Flyway — V9 application (verbatim log lines, `logs/kc-stack-backend-startup.txt`)

```
2026-10-29T13:42:20.268760Z DbMigrate Current version of schema "public": 2
2026-10-29T13:42:20.271063Z DbMigrate Schema "public" is up to date. No migration necessary.
2026-10-29T13:42:28.593328Z BackendApplication Started BackendApplication in 10.625 seconds (process running for 10.815)
2026-10-29T13:42:28.658302Z TenantMigrationRunner Running tenant migrations for 2 schemas
2026-10-29T13:42:28.685747Z DbMigrate Current version of schema "tenant_56399d06a773": 8
2026-10-29T13:42:28.694933Z DbMigrate Migrating schema "tenant_56399d06a773" to version "9 - phase88 comment attachments"
2026-10-29T13:42:28.724151Z DbMigrate Successfully applied 1 migration to schema "tenant_56399d06a773", now at version v9 (execution time 00:00.020s)
2026-10-29T13:42:28.748715Z DbMigrate Current version of schema "tenant_5039f2d497cf": 8
2026-10-29T13:42:28.751055Z DbMigrate Migrating schema "tenant_5039f2d497cf" to version "9 - phase88 comment attachments"
2026-10-29T13:42:28.762294Z DbMigrate Successfully applied 1 migration to schema "tenant_5039f2d497cf", now at version v9 (execution time 00:00.006s)
2026-10-29T13:42:28.789289Z PortalContactDirectoryBackfillRunner Portal contact directory backfill: 0 tenants scanned, 2 skipped (already marked), 0 failed, 0 rows upserted
2026-10-29T13:42:28.792862Z PackReconciliationRunner Running pack reconciliation for 2 tenants
2026-10-29T13:42:29.467190Z PackReconciliationRunner Pack reconciliation: checked 2 tenants, 2 succeeded, 0 failed
```

The same start reconciled the 628C pack texts into both old tenants (common-project v2→v3, conveyancing-za-project v1→v2, legal-za-project v2→v3, template pack common → v4, legal-za-clauses v1→v2, standard-clauses v1→v2, standard-reports v1→v2, automation-common v3→v4) — full list in `logs/kc-stack-backend-startup.txt`.

| Schema | Before | After | V9 row (installed_on SAST) |
|---|---|---|---|
| `tenant_5039f2d497cf` (mathebula-partners, 623/624I data — dropped by Session 0) | 8 | 9 | 2026-10-29 15:42:28.74666 |
| `tenant_56399d06a773` (org_phase86_squash_smoke — durable record) | 8 | 9 | 2026-10-29 15:42:28.683291 |
| `public` (global) | 2 | 2 | — |

## Sections

Scenario-step totals (the headline). Rows added by rulings or the brief (LH, K-PRO, TEAM-sipho/-estate/-raf, INFRA-1…5) are **unscored**: they sit in an "Added rows (unscored)" table in each file with status `NOTE`, and are listed separately below. Recounted after the PR #1711 review (2026-10-29) and again after the orchestrator split 14.8 into 14.8a PASS / 14.8b FAIL (G88-F12 HIGH), and after review pass 3 split 33.1-a (Days 24–35) into 33.1-aa FAIL (§5a row 4) / 33.1-ab PASS.

| Section | Doc lines | File | Scenario rows | P / F / PARTIAL / D / N/A |
|---|---|---|---|---|
| Session 0 (629.2) | 43–48 + master 115–152 | session-0.md | 18 | 13 / 2 / 2 / 0 / 1 |
| Day 0 (629.2, W1a + W1b; 0.50 / CP0.5 closed by W2a after 1.9) | 52–206 | day-00.md | 88 | 81 / 7 / 0 / 0 / 0 |
| Days 1–7 (629.3, W2a + W2b) | 209–301 | days-01-07.md | 70 | 61 / 9 / 0 / 0 / 0 |
| Days 8–21 (629.3, W3a + W3b) | 304–366 | days-08-21.md | 47 | 39 / 8 / 0 / 0 / 0 |
| Days 22–35 + Day 33 (629.3, W4) | 369–409 (Day 30 SoA 414–424 → 629B) | days-22-35.md | 27 | 22 / 4 / 0 / 0 / 1 |
| Day 30 SoA + Days 36–60 (629.5, W5a 30.1-soa … 38.4 + W5b 45.1 … 58.1; file complete) | 414–474 | days-36-60.md | 38 (W5a 19 + W5b 19) | 35 / 3 / 0 / 0 / 0 (+0 NOTE) |
| Days 61–75 (629.6, W6a 61.1 + 62.1; W6b 65.1 … 72.2; W6c to come) | 478–505 | days-61-75.md | 8 so far (W6a 2 + W6b 6) | 8 / 0 / 0 / 0 / 0 (+4 NOTE: TEAM-nomvula, INFRA-6, INFRA-7, APPROVE-72) |
| **629A scenario total** | — | 5 day files | **250** | **216 / 30 / 2 / 0 / 2** |

Unscored rows (status `NOTE`, not in the totals above): **10** — `day-00.md` LH, K-PRO; `days-01-07.md` TEAM-sipho, INFRA-1, INFRA-2; `days-08-21.md` TEAM-estate, INFRA-3, INFRA-4, INFRA-5 (**incident**: the walker's `pkill -9 -f next-server` killed the svc frontend; it was restarted and no state was lost — not a PASS); `days-22-35.md` TEAM-raf. Slice gate 629.1 (KC-STACK, this file) PASS — reported on its own, not a scenario row.

Per-file totals (scenario rows: P / F / PARTIAL / D / N/A; + unscored `NOTE`): `session-0.md` 18: 13 / 2 / 2 / 0 / 1 (+0; PARTIAL = M.9, CP-5 — observed, no artefact captured) · `day-00.md` 88: 81 / 7 / 0 / 0 / 0 (+2) · `days-01-07.md` 70: 61 / 9 / 0 / 0 / 0 (+3) · `days-08-21.md` 47: 39 / 8 / 0 / 0 / 0 (+4) · `days-22-35.md` 27: 22 / 4 / 0 / 0 / 1 (+1). Recount with `for f in session-0.md day-00.md days-01-07.md days-08-21.md days-22-35.md; do for s in PASS FAIL PARTIAL DEFERRED N/A NOTE; do printf "%s %s %s\n" $f $s $(grep -cE "^\| [^|]+ \| $s \|" $f); done; done`.

FAILs by source (30): **16 × G88-F1** LOW wording/behaviour drift, each row citing the code (file:line) that proves the product behaviour (0.5a, 0.15a, 0.28a, 0.29a, 0.41a, 1.3a, 3.8a, 4.1a, 6.5a, 8.1a, 12.1a, 12.2a, 14.7a, 22.1a, 23.3a, 35.1a); **7 × §5a** pending founder authorisation (0.Aa + M.6a row 1 / G88-M1, 3.3a row 2, 6.2a + 33.1-aa row 4, 6.3a row 5, 14.13a row 6); **2 × G88-F3** MEDIUM product bug (0.47b, 0.48b); **1 × G-B8** incomplete fix (4.6b; U5 625A/B → follow-up); **1 × G88-M6** MEDIUM (6.3b); **1 × G88-F11** LOW (12.3a); **1 × G88-F14** LOW (15.3b); **1 × G88-F12 HIGH** (14.8b — OTP voetstoots clause names the firm, Seller line empty; 14.8 split by the orchestrator ruling on PR #1711 because clause injection includes variable binding: 14.8a PASS clauses present / 14.8b FAIL binding) — the walk's only HIGH so far, routed to a follow-up phase.

## Wow-moment candidates shot in 629A
| Wow | Step | File | Shows |
|---|---|---|---|
| 1 (Day 0) | after 0.40 / LH (ruling C-6) | screens/wow-1-candidate.png | Thandi's Home, six-zone legal nav, Mathebula logo tile + brand accent bar, 1440×900 light (brand colour visible only as logo + accent bar; empty Day-0 widgets) |
| 2 (Day 4) | 4.3b | screens/wow-2-candidate.png | Portal acceptance page (prod build) with the engagement letter PDF inline (letterhead logo, firm block, navy headings) and the green "Sipho Dlamini accepted this document on 29 Oct 2026, 16:35." state, 1440×900 light |
| 3 (Day 2 per the list; walked at 1.4) | 1.4 | screens/wow-3-candidate.png | Bob, Conflict check: green "No Conflict — Checked "Sipho Dlamini" at 29/10/2026, 16:11:17" card, 1440×900 light. Caveat for 629B: the "CLEAR" label the scenario names is absent (the card reads "No Conflict"), and 1.3a is a FAIL (G88-F1) — the 629.8 "clean PASS only" rule applies to this candidate |
| 4 (Day 5) | 5.4 | screens/wow-4-candidate.png | Bob, Sipho's litigation matter Overview: "Active · LITIGATION", facts strip "Case no. JHB/CIV/2026/001 · Gauteng High Court, Johannesburg", grouped tabs, 0 Project/Customer/Invoice, 1440×900 light. Wow 4's text says "a RAF claim" — wrong for Day 5 (Sipho's matter); §5a row 3 pending founder authorisation — not re-shot on the RAF matter |
| 5 (Day 33) | 33.5 | screens/wow-5-candidate.png | Thandi, Reports › Profitability: Team Utilization + Matter Profitability with Estate (18.0h · R 31 025,00 · R 12 500,00 · R 18 525,00 · 59.7%), RAF (9.0h · R 9 500,00 · R 3 900,00 · R 5 600,00 · 59.0%), Sipho (3.5h · R 2 900,00 · R 1 150,00 · R 1 750,00 · 60.3%), ZAR, "Client" headers, 1440×900 light. Caveats for 629B: the Margin % column's "%" is clipped by an 11 px inner scroll (G88-F4) and RAF reads 59.0% vs 58.9% in the Client table (G88-F15) |

## Wow moments (629B)
| Wow | Step | File | Shows |
|---|---|---|---|
| 6 (Day 47) | 47.3 (PASS; 47.2 PASS) | screens/47.3-moroka-fee-note-pdf-preview.png = screens/wow-6-final-source.png (+ screens/47.3b-moroka-fee-note-preview-totals-banking.png) | Thandi, INV-0002 › Generate Document › Fee Note preview: Mathebula letterhead (logo tile, address / Tel / Email / Web), brand-navy headings, To Moroka Family Trust + Client VAT Number, INV-0002 / due 28 November 2026, and the LSSA "3(a) - Letters written (per folio) · 4 · R195,00" tariff line; second shot: all 9 lines, Total Due R36 575,75, Banking details; 1440×900 light. Caveat: the tariff line prints second, between time lines (G88-M10, still open) |

## Candidate gaps (629A + 629B) — index
Owner rule (PR #1711 review): the 629 re-walk writes gaps and never fixes them; Epics 625, 626 and 628 are Done, so a product gap a closed slice introduced or did not cover is owned by **"new — follow-up phase"**, with the slice named as attribution.

| ID | Severity | Step | Class | Owner | File |
|---|---|---|---|---|---|
| G88-F12 | **HIGH** (re-rated by the review; the only HIGH in 629A so far) | 14.8b (FAIL; split from 14.8 by the orchestrator ruling) | product bug (legal content) — OTP "Voetstoots (As Is)" clause names the firm (`org.name`) as the party not liable for defects (`backend/src/main/resources/clause-packs/conveyancing-za-clauses/pack.json:33-37`, `org.name` at `:36`); separately, the "Seller:" line is empty because the OTP template's `project.customFields.seller_name` (`template-packs/legal-za/offer-to-purchase.json:54-55`) has no field definition in any field pack. The OTP cannot be demonstrated to a conveyancer as-is | new — follow-up phase (attribution: U10 626G associated the clause with the OTP, `pack.json:5`, and did not cover the `org.name` binding; Seller line pre-existing) | days-08-21.md |
| G88-M1 (existing) | LOW | 0.Aa, M.6a | scenario mismatch — §5a row 1 pending founder authorisation | founder (§5a, routed by 628H) | session-0.md |
| G88-F1 | LOW | 0.5a, 0.15a, 0.28a, 0.29a, 0.41a, 1.3a, 3.8a, 4.1a, 6.5a, 8.1a, 12.1a, 12.2a, 14.7a, 22.1a, 23.3a, 35.1a; 629B: 30.3-soa-a, 30.5-soa-a (W5a), 45.10a (W5b — matter has no fee-note entry point, `matter-tab-groups.ts:51-62` vs `client-tab-groups.ts:47-53`) (`days-36-60.md`) | scenario mismatch needing authorisation (wording/behaviour drift outside §5 rows 1–15 and §5a rows 1–20, ruling C-5). CLAUDE.md §6 evidence (review pass 3): every F1 FAIL row cites the file:line confirming the observed product behaviour in code. For the label/transition rows the code shows a deliberate choice; for 6.5 (no document label) and 12.2 (no deposit source) it shows only that the field is absent, so design intent is **unproven** there and 629C must classify them (scenario mismatch vs missing feature) rather than inherit the mismatch label (review pass 4) — labels `request-access-form.tsx:396-412` (0.5), `approve-dialog.tsx:68-79` (0.15), `team/page.tsx:40-51` + `invite-member-form.tsx:300-302` (0.28/0.29), `settings-nav.ts:221-224` (0.41), `conflict-check-result.tsx:22-31` (1.3/8.1/22.1), path-derived `breadcrumbs.tsx:136-138,168-171` (3.8), `GenerateDocumentDropdown.tsx:81-91` (4.1/14.7), `DocumentController.java:116-123` no label attribute (6.5), `TaskStatus.java:13-18` OPEN↛DONE (12.1/35.1), `RecordDepositDialog.tsx:126-213` + `TrustTransactionService.java:92-98` no source field (12.2), `create-court-date-dialog.tsx:38-48` + `CourtCalendarService.java:34-44` (23.3). No row needed the "unclassified — evidence pending (629C)" fallback | new — founder §6 | day-00.md, days-01-07.md, days-08-21.md, days-22-35.md |
| G88-F2 | LOW | Team page (0.27 / CP-AD-2) | product bug — "Joined" column hard-coded "—" (`member-list.tsx:75,124`) | new | day-00.md |
| G88-F3 | MEDIUM | 0.47b, 0.48b | product bug — group membership is invisible in the UI and the Edit Field Group dialog's field picker is a silent no-op: the frontend sends `fieldDefinitionIds: []` (`custom-fields-content.tsx:281-287` passes no `initialFieldIds`; `FieldGroupDialog.tsx:74,163`) and the backend ignores it (`UpdateFieldGroupRequest.java:8-13` has no membership field; `FieldGroupService.java:111-140` never touches members) | new | day-00.md |
| G88-F4 | LOW | 0.48a, 0.56, 4.5, 33.5 (side observations) | product bug — settings tables (Custom fields, Automations) overflow an 856 px wrapper at 1440; Status/Actions cut; task-sheet Time Entries table 713 px in 525 px (4.5); Profitability Margin % clipped (33.5) | new — follow-up phase (attribution: 628B (628.5) container-query work did not cover these tables) | day-00.md, days-01-07.md, days-22-35.md |
| G88-F5 | LOW | 1.8 (side observation) | product bug (copy) — New Client labels activation prerequisite "Address Line 1 (optional)" (`create-customer-dialog.tsx:440-441` vs `StructuralPrerequisiteCheck.java:65-70`) | new | days-01-07.md |
| G88-F6 | LOW | 3.3b, 14.4 (side observations) | product bug (UI) — matter Custom fields shows "Field Groups" twice (`matter-fields-tab.tsx:53-54` + `FieldGroupSelector.tsx:101-102`) | new | days-01-07.md, days-08-21.md |
| G88-F7 | LOW | TEAM-sipho, TEAM-estate, TEAM-raf (unscored rows); 629B: TEAM-nomvula (W6a, `days-61-75.md`) | scenario mismatch needing authorisation — scenario lacks matter-team steps (ADR-346 / 619; ruling C-4) | new — founder §6 | days-01-07.md, days-08-21.md, days-22-35.md |
| G88-F8 | — | 4.6b | **retired: alias of G-B8 (incomplete fix)** — id kept so the numbering stays stable for 629B | — | days-01-07.md |
| G88-F9 | LOW | 5.9 (side observation) | product bug (UI; U21 polish class) — disbursement detail shows the linked trust transaction as a raw UUID (`legal/disbursements/[id]/detail-client.tsx:270-284`) instead of its reference | new | days-01-07.md |
| G88-F10 | LOW | 5.12 (side observation) | product bug (UI; U21 polish class) — matter Fees › Disbursements keeps a MATTER column reading "—" (`project-disbursements-tab.tsx:82-88` passes no `projectNames`) | new | days-01-07.md |
| G88-F11 | LOW | 12.3a | scenario mismatch needing authorisation — account balance R 53 750,00 ≠ doc R50,000 because of §5 row 5 (Sipho keeps R 3 750); Moroka ledger R 50 000,00 (ruling C-11). Product correct: cashbook = one sum over all the account's transactions (`TrustTransactionRepository.java:106-126`), client-ledger total = sum of ledger cards (`ClientLedgerCardRepository.java:28-31`) | new — founder §6 (G88-M16 family) | days-08-21.md |
| G88-F13 | LOW | 15.1 (side observation) | product bug (copy; U21 copy class) — fee-estimate empty state / help promise a fixed vs T&M choice the dialog does not offer (`empty-states.json:70`, `help.json:26-27`) | new | days-08-21.md |
| G88-F14 | LOW | 15.3b | scenario mismatch needing authorisation — "~R11,000" vs R 9 850,00 = Σ(hours × the scenario's own rates) (ruling C-12). Product correct: `TimeEntryRepository.java:372-387` `budgetAmountConsumed` = `SUM(billing_rate_snapshot × duration_minutes / 60)`, via `ProjectBudgetService.java:168-179` | new — founder §6 | days-08-21.md |
| G88-F15 | LOW | 33.2 (side observation) | product bug (rounding) — Matter Profitability Margin % double-rounded (`ReportService.java:437-441` 2 dp HALF_UP + `toFixed(1)`): RAF 59.0% vs 58.9% in the Client table for the same figures | new | days-22-35.md |
| G88-F16 | MEDIUM | 30.4-soa / 30.6-soa (side observation; C-3 letterhead check) | product bug (document content) — the legal-za Statement of Account has no firm letterhead block (no address / phone / email / website) and prints unconditional blank "VAT Reg:" / "File reference:" labels on the client-facing, portal-shared PDF (`template-packs/legal-za/statement-of-account.json:16,78`; contrast `fee-note-za.json:25-30`) | new — follow-up phase (attribution: 626D (626.8) reconciled fee-note + engagement-letter letterheads, not the SoA) | days-36-60.md |
| G88-F17 | LOW | 38.2 (side observation) | product bug (UI copy) — fee-note Audit trail rows for created / approved / sent all read just "Fee note" (registry wildcard `invoice.*`, `AuditEventTypeRegistry.java:124-128`); the row does not say which action happened | new — follow-up phase (attribution: 625C (D12) relabelled Invoice → Fee note, not per-action labels); 629B W5b: also the Firm › Audit trail Event type list / rows for `invoice.updated` / `invoice.paid` (45.9, 58.1) | days-36-60.md |
| G88-F18 | LOW | 45.6 (side observation) | product bug (UI layout) — the Write off dialog's close "×" is drawn outside the visible card: shared `DialogContent` places the `absolute top-4 right-4` close button in an inner div without `relative`, so it positions against the outer fixed `sm:max-w-lg` box while the caller's `sm:max-w-md` narrows only the inner card (`frontend/components/ui/dialog.tsx:56,69-76`; `disbursement-write-off-dialog.tsx:104-105`) | new — follow-up phase (attribution: none identified — shared dialog wrapper) | days-36-60.md |
| G88-F19 | MEDIUM | 68.2 (side observation) | product bug (portal read model) — the portal matter Tasks section lists only tasks someone has edited since creation (2 of Sipho's 9): template instantiation saves tasks without publishing `PortalTaskCreatedEvent` (`ProjectTemplateService.java:589-609`; the projection is fed only from `TaskService.publishPortalTaskEventIfLinked`, `TaskService.java:1082`), so `portal.portal_tasks` misses every untouched template task | new — follow-up phase (attribution: none identified — template path predates Phase 88) | days-61-75.md |
| G88-F20 | LOW | 68.2 (side observation) | product bug (client-facing copy) — portal task Status badge prints the raw enum "OPEN" (`portal/components/status-badge.tsx:47-49,65-67` humanises only the project variant) | new — follow-up phase (attribution: 624H (#1677) humanised the matter badge, not the task variant) | days-61-75.md |
| G88-F21 | LOW | APPROVE-72 (unscored row between 72.1 and 72.2) | scenario mismatch needing authorisation — design proven: the 90-day file has no second-member approval for the 72.1 trust PAYMENT; the recorder cannot be the sole approver (`TrustTransactionService.java:784-790`, observed at 72.1) and 72.2's balance moves only on approval (ruling O-6) | new — founder §6 | days-61-75.md |
| §5a rows 2, 4, 5, 6 (existing) | — | 3.3a, 6.2a, 33.1-aa, 6.3a, 14.13a | scenario mismatch — §5a rows pending founder authorisation (product-path rows PASS — 3.3b, 6.2b, 33.1-ab, 14.13b — except 6.3b → G88-M6). @mentions: no gap — mention parsing exists (`NotificationService.java:301-306,817-850`) and was observed at 6.2b | founder (§5a, routed by 628H) | days-01-07.md, days-08-21.md, days-22-35.md |
| **Cited still-open (existing ids, not re-filed)** | | | | | |
| G-B8 (617C, incomplete fix) | MEDIUM (617C) | 4.6b (FAIL); events half seen fixed at 4.6a, 6.6, 23.5 | product bug — feed shows the acceptance events but no source-PORTAL marker (DTO `actorName` only, `activity-feed-client.tsx:38-39`); 617C `phase87-capstone/gaps.md:21` expects "(actor Sipho, source PORTAL)" | new — follow-up phase (attribution: U5 625A/B, merged, fixed the events and did not cover the source half) | days-01-07.md |
| G88-M2 (existing) | MEDIUM | TEAM-sipho, TEAM-estate, TEAM-raf; 629B: still open at TEAM-nomvula (W6a) | product bug — matter Team tab shows org roles only; no lead marker | new — follow-up phase (attribution: 628A did not cover it; mid `gaps.md:63` had routed it to 628A, and Epic 628 is Done) | days-01-07.md, days-08-21.md, days-22-35.md |
| G88-M4 (existing, residual) | MEDIUM | 5.12, 5.14 | product bug — page no longer overflows (scrollWidth 1440) but disbursement tables (1249/1257 px) hide BILLING/ACTIONS behind an inner scroll at 1440 | new — follow-up phase (attribution: 628B (628.5) fixed the page overflow and did not cover the inner-scroll residual); 629B: still open at 45.5 (Fees › Disbursements BILLING column clipped) | days-01-07.md, days-36-60.md |
| G88-M5 (existing) | LOW | 6.1, 13.1; 629B: still open at 62.1 (W6a — 4 × `/time-summary/by-member` on Carol's Nomvula visits) and 70.1 (W6b — 2 × on Carol's estate visits) | product bug — Member visit writes `security.access_denied` noise (`/time-summary/by-member`) | new unit — founder (mid `gaps.md:66`) | days-01-07.md, days-08-21.md |
| G88-M6 (existing) | MEDIUM | 6.3b (FAIL) | product bug — comment notification lands on the task list, not the comment | new unit — founder (mid `gaps.md:67`) | days-01-07.md |
| G88-M7 (existing) | LOW | 3.5 | product bug — client Matters list STATUS cell blank for an Active matter | new unit — founder (mid `gaps.md:68`) | days-01-07.md |
| G88-M13 (existing) | MEDIUM | 5.4 (side observation, not scored) | product bug (pre-existing stub) — matter Overview "OUTSTANDING FEE NOTES" tile reads "--" (hard-coded, `frontend/components/projects/kpi-dashboard.tsx:207-213`; sibling "Trust balance" tile `:199-205` likewise); on Day 5 no fee note exists yet, so this walk only re-sees the stub; 629B: still open at 45.10a and 61.1 (W6a, new Nomvula matter) — tile "--" while INV-0001 R 4 585,00 is SENT and unpaid | new unit — founder (mid `gaps.md:53,73,189`) | days-01-07.md, days-36-60.md |
| G88-M9 (existing, 623B) | LOW | 38.1/38.2 (W5a), 45.4 (W5b) side observations | product bug — fee-note Generated Documents / Audit trail panels stale after Send until a re-render / reload; 629B: still open | per mid `gaps.md:70` | days-36-60.md |
| G88-M10 (existing, 623B) | LOW | 45.3 (SELECT), 47.2 side observations | product bug — tariff line stored with `invoice_lines.sort_order` 0 → prints second, between time lines, on the PDF; 629B: still open | per mid `gaps.md:71` (new unit — founder) | days-36-60.md |
| G88-M11 (existing, 623B) | LOW | 55.1 side observation | known follow-up — manual payment flips the open mock payment session to CANCELLED; 629B: still open | per mid `gaps.md:72` | days-36-60.md |
| G-D17 (617C, residual) | LOW | 32.1 (N/A row; cited) | matter/client Fees › sub-tab still labelled "Tariffs" for member rate overrides (`frontend/lib/constants/matter-tab-groups.ts:57`, `client-tab-groups.ts:51`); settings half closed by 628B ("Rates") | new — follow-up phase (attribution: 628B renamed the settings tab and did not cover the matter/client sub-tab) | day-00.md, days-22-35.md |
| G-D20 (617C, residual) | LOW | 23.6 | Diary week card title still truncated ("Gauteng High…", was "G…") | new — follow-up phase (attribution: U21 628B improved the card and did not cover the truncation) | days-22-35.md |

## Stack state left for 629B
Written by W4 at 2026-10-29T15:40Z (full detail: `days-22-35.md` § Handoff).

- **Services (running, from `$MAIN` @ `138119b81`):** backend 1293 / gateway 207 / frontend **33245** (restarted 15:21:31Z, INFRA-5) / portal **33055** (svc `next dev`; the 14.12 prod build was torn down and `portal/next-env.d.ts` restored, INFRA-4) — all `yes yes`; health 200 ×5. caffeinate 99323 + phase runner 6817 alive. Tenant `tenant_5039f2d497cf` at flyway V9, global V2.
- **Browser:** signed in as Thandi (owner) on `/reports/profitability`, 1440×900 light — clear cookies + storage before 629B's first actor.
- **Clients (4, ACTIVE, each with a portal contact + directory row):** Sipho Dlamini (`sipho.dlamini@email.co.za`), Moroka Family Trust `acd36ca9-…` (`moroka.trust@email.co.za`), Khumalo Property Holdings `30aa3450-…` (`khumalo.holdings@email.co.za`), Lerato Mthembu `d3f6be49-…` (`lerato.mthembu@email.co.za`).
- **Matters (4, ACTIVE), unbilled time (no fee notes yet):** Sipho `d42337ad-…` 3.5 h / R 2 900,00 + 2 approved unbilled disbursements; Estate `028d3901-…` 18.0 h / R 31 025,00 (fee estimate 40 h / R 80 000,00); Khumalo `aa61195f-…` 0 h (OTP acceptance `ad215a7c-…` VIEWED); RAF `5c42e002-…` 9 h / R 9 500,00 (court date 2026-12-13, adverse party Road Accident Fund).
- **Trust:** account R 53 750,00 = Moroka R 50 000,00 + Sipho R 3 750,00.
- **Mailpit:** 10 messages (last `2eLfLXG6siubpr6BZYZmEC`, Khumalo OTP acceptance request).
- **Gap ids:** G88-F1 … G88-F15 used (G88-F8 retired as an alias of G-B8); **next free G88-F16** (review pass 3 filed no new id: the @mention half of 33.1 is not a gap). **Cited still-open (not re-filed):** G-B8 (4.6b), G88-M2, G88-M4 (residual), G88-M5, G88-M6, G88-M7, **G88-M13** (5.4 tile "--"; new unit — founder), G-D17 (matter/client half), G-D20. **HIGH: G88-F12** (re-rated by the PR #1711 review; the only HIGH so far; scored as the 14.8b FAIL — 14.8a PASS).
- **629B starts at:** the Day 30 Statement of Account block (scenario lines 414–424, ids `30.1`–`30.7`, ruling C-8), then Day 36. Wow 5 candidate is `screens/wow-5-candidate.png` (see caveats in the wow table).

## 629B (Day 30 SoA, Days 36–90, portal 90-day, E.1–E.11) — branch `phase88/12B-full-rewalk-days-36-90`

Walkers W5a … W9, 2026-10-29T18:48Z → (in progress), same KC stack (no backend/gateway restart since 629A W1a; HEAD 45dcc7a6f = 138119b81 code). Brief `.epic-brief.md` (not committed). Rulings: §R + §11 of the brief.

| Run | Block | File | Rows (scored + NOTE) | P / F / PARTIAL / D / N/A | Browser calls | Commit |
|---|---|---|---|---|---|---|
| W5a | Day 30 SoA 30.1-soa … 30.7-soa; Days 36 + 38 (36.1 … 38.4) | days-36-60.md (part 1) | 19 + 0 | 17 / 2 / 0 / 0 / 0 | ~62 | d4b6a6d9a |
| W5b | Days 45, 47, 50–58 (45.1 … 58.1); wow 6 | days-36-60.md (part 2, file totals) | 19 + 0 | 18 / 1 / 0 / 0 / 0 | ~68 | 1c71af942 |
| W6a | 61.1, TEAM-nomvula, 62.1 | days-61-75.md (part 1) | 2 + 1 | 2 / 0 / 0 / 0 / 0 | ~55 | 35265feda |
| W6b | 65.1, INFRA-6, 68.1, 68.2 (portal prod build), INFRA-7, 70.1, 72.1, APPROVE-72, 72.2 | days-61-75.md (part 2) | 6 + 3 | 6 / 0 / 0 / 0 / 0 | ~62 | (this commit) |

Power at W5a start: battery 78 %, clamshell open (ruling O-13 — proceed); 76 % at 18:55Z.
W5a re-observations of existing ids: **closed — observed PASS** G-C4 (30.4/30.6 SoA preview + Download PDF), G-C10 (36.4 narrative lines), G-B5 / G-C7 + 623B 38.3b (38.3 fee-note letterhead / brand / banking), G88-M8 (38.3 emailed-PDF VAT column), G-C3 terminology half (38.2 "Fee note"); **still open** G88-M9 (38.1/38.2 panels stale after Send until reload).
Power at W5b start (19:00Z): battery 74 %, clamshell open (O-13 — proceed); re-checked per day (70 % / 70 % / 69 %); **AC Power** from ≈ 19:13Z.
W5b re-observations of existing ids: **closed — observed PASS** G-C9 (45.6–45.11 write-off UI end to end), G-D6 filter half + 623B 45.9b (45.9 Entity type + Event type filters), G-B5 / G-C7 + 623B 47.2b (47.2 Moroka fee-note letterhead / brand / banking), G-C11 (55.1 amount + method fields); **still open** G88-M4 (45.5), G88-M9 (45.4), G88-M10 (45.3 / 47.2), G88-M11 (55.1), G88-M13 (45.10a), G88-F17 (45.9 / 58.1). New: **G88-F18** (LOW). No HIGH in W5a/W5b. Next free id **G88-F19**.
`days-36-60.md` totals: 38 scored rows — **35 / 3 / 0 / 0 / 0** (+0 NOTE); all 3 FAILs are G88-F1 wording (30.3-soa-a, 30.5-soa-a, 45.10a).
Power at W6a start (19:16Z): **AC Power**, 72 %, clamshell open; 79 % at end (19:21Z).
W6a re-observations of existing ids: **still open** G88-M2 (TEAM-nomvula), G88-M5 (62.1), G88-M13 (61.1); G88-F7 appended (TEAM-nomvula). No new id, no HIGH. Next free id **G88-F19**.
Power at W6b start (19:24Z): **AC Power**, 81 %, clamshell open; 87 % before Day 72 (19:31Z); 88 % at end (19:34Z).
W6b re-observations of existing ids: **closed — observed PASS** **G-D1** (68.1 bare portal `/login` → Mailpit → Access Portal → `/matters`; one `POST /portal/auth/exchange` on the prod build), **G-D19** (68.2 portal matter status "Active"); **still open** G88-M5 (70.1). New: **G88-F19** (MEDIUM, 68.2 — portal matter Tasks shows 2 of 9; template-instantiated tasks are never projected to `portal.portal_tasks`), **G88-F20** (LOW, 68.2 — portal task status raw "OPEN"), **G88-F21** (LOW, APPROVE-72 — scenario lacks the second-member trust approval, ruling O-6). No HIGH. Next free id **G88-F22**.
Portal prod-build swaps: **W6b** INFRA-6 (19:26:37Z swap-in, build exit 0, `:8080` in 4 / `:8081` in 0 files of `.next/static`, next-server 28580) → INFRA-7 (19:29:41Z restore: PIDs 28580/28542 killed, `svc.sh start portal` → portal 30035 `yes yes`, `portal/next-env.d.ts` checked out, `$MAIN` portal/frontend clean) — `logs/portal-build-W6b.log`, `logs/portal-next-start-W6b.log`, `logs/days61-75-portal-restore-W6b.txt`. W8a, W8b to come.
Magic-link requests: `sipho.dlamini@email.co.za` → 2026-10-29T19:27:09Z → `bdm3MjpDgj9RNXdRwqmHkT` (W6b 68.1; `logs/days61-75-magiclink.txt`).
