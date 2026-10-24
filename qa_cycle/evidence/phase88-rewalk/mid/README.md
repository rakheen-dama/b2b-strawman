# Phase 88 mid-phase re-walk (623) — Keycloak — evidence index

Scenario `qa/testplan/demos/legal-za-90day-keycloak.md`. Stack: Keycloak dev stack (frontend :3000, gateway :8443, backend :8080, Keycloak :8180, Mailpit :8025, portal :3002) served from the main checkout at `6cc2b525e7a3f9d17a277f7dcffd4dadb6a46499` (= origin/main = worktree base `phase88/06A-mid-rewalk-days-01-07`) after `./mvnw -q clean compile`. Walk date 2026-10-24 (UTC). Walkers: 623A (KC-STACK, Session 0, Days 1–7; branch `phase88/06A-mid-rewalk-days-01-07`); 623B (runs 1–2: Days 36–47 + 61–75, deck re-shoots; branch `phase88/06B-mid-rewalk-days-36-75`, stack not restarted — see § Stack state left for 623C).

## KC-STACK (623.1)

Start time 2026-10-24T07:00:30Z. Host clock is SAST (UTC+2); Postgres `installed_on` values below are local time.

| # | Command | Result |
|---|---|---|
| 1 | `nohup caffeinate -dimsu -t 43200 &` | PID **67665** (a separate `caffeinate -is ./scripts/run-phase.sh 88 619E` PID 65878 was also present, left alone). `pmset -g batt`: "Now drawing from 'AC Power'", 100% charged |
| 2a | `git -C $MAIN fetch -q origin && git rev-parse HEAD origin/main` | both `6cc2b525e7a3f9d17a277f7dcffd4dadb6a46499`; worktree HEAD same |
| 2b | `git status --short -- backend frontend portal gateway packages` | empty |
| 2c | `svc.sh status` | all four `no` (backend/gateway/frontend/portal) — **backend was DOWN on arrival** |
| 2d | `lsof … :(8080\|8443\|3000\|3002)` | nothing listening |
| 2e | `pgrep -fl 'maven\|ForkedBooter\|embedded-pg…'` | PID 4649 orphaned embedded-pg postgres (port 60701) → `kill 4649` → gone. No maven/ForkedBooter |
| 2f | `docker ps … b2b-` | b2b-keycloak Up 40 h (healthy), b2b-localstack / b2b-postgres / b2b-mailpit Up 6 days (healthy) |
| 2g | `curl -sf :8180/realms/docteams` | KC-OK |
| 2h | `touch /tmp/.e2e-keycloak-mode` | created (did not exist) |
| 3 | V4 BEFORE snapshot (stack down) | `logs/v4-before.txt` — both histories end at V3; matches scout expectation exactly |
| 4a | `./mvnw -q clean compile -DskipTests` | **EXIT 0**, wall 17 s (`logs/kc-stack-compile.txt`). `target/classes/db/migration/tenant/` = V1__baseline.sql, V2__add_project_template_work_type.sql, V3__normalise_work_types.sql, **V4__phase88_demo_ready.sql** |
| 4b | `docker builder prune -f` | "Total: 8.005GB" reclaimed |
| 4c | `pnpm install --frozen-lockfile` (root) | "Lockfile is up to date… Packages: +9 -8 … Done in 5.3s" (`logs/kc-stack-pnpm-install.txt`) |
| 4d | next versions | frontend **16.3.8**, portal **16.3.8** |
| 5 | `svc.sh start all` (07:01:30Z) | backend ready 24 s, gateway 6 s, frontend 3 s, portal 3 s |
| 5b | health | 8080 `{"status":"UP"}`, 8443 `{"status":"UP"}`, 3000 → 200, 3002/login → 200 |

Infra note: `svc.sh start` **truncates** the service log (`compose/scripts/svc.sh:124` `: > "$log"`), so the brief's "record `wc -l` and read only new lines" offset (948) does not apply — the whole `backend.log` is this start's output. Not a product gap.

## `svc.sh status` (verbatim)

```
  SERVICE      PID      RUNNING  HEALTHY  PORT
  -------      ---      -------  -------  ----
  backend      68172    yes      yes      8080
  gateway      68353    yes      yes      8443
  frontend     68497    yes      yes      3000
  portal       68555    yes      yes      3002
```
Port holders (fresh): 8080 pid 68329 etime 00:43 · 8443 pid 68482 etime 00:28 · 3000 pid 68542 etime 00:23 · 3002 pid 68598 etime 00:16 (`logs/kc-stack-svc-status.txt`).

## V4 — first application to a persistent stack (OQ #9)

### Startup log lines (verbatim, `logs/backend-startup-v4.txt`)

```
2026-10-24T07:01:45.646619Z  DbMigrate  Schema "public" is up to date. No migration necessary.
2026-10-24T07:01:53.737581Z  BackendApplication  Started BackendApplication in 10.553 seconds (process running for 10.751)
2026-10-24T07:01:53.849673Z  TenantMigrationRunner  Running tenant migrations for 2 schemas
2026-10-24T07:01:53.874149Z  DbMigrate  Current version of schema "tenant_56399d06a773": 3
2026-10-24T07:01:53.887484Z  DbMigrate  Migrating schema "tenant_56399d06a773" to version "4 - phase88 demo ready"
2026-10-24T07:01:53.928979Z  DbMigrate  Successfully applied 1 migration to schema "tenant_56399d06a773", now at version v4 (execution time 00:00.035s)
2026-10-24T07:01:53.930849Z  TenantMigrationRunner  Migrated schema tenant_56399d06a773 — 1 migrations applied
2026-10-24T07:01:53.951186Z  DbMigrate  Current version of schema "tenant_5039f2d497cf": 3
2026-10-24T07:01:53.954677Z  DbMigrate  Migrating schema "tenant_5039f2d497cf" to version "4 - phase88 demo ready"
2026-10-24T07:01:53.978351Z  DbMigrate  Successfully applied 1 migration to schema "tenant_5039f2d497cf", now at version v4 (execution time 00:00.018s)
2026-10-24T07:01:53.980204Z  TenantMigrationRunner  Migrated schema tenant_5039f2d497cf — 1 migrations applied
2026-10-24T07:01:53.980301Z  TenantMigrationRunner  Tenant migration runner completed
```
No ERROR-level lines in backend.log.

### `flyway_schema_history` after start (`logs/v4-after.txt`)

| Tenant | V4 row |
|---|---|
| `tenant_5039f2d497cf` (mathebula-partners, 617C tenant) | `4 \| phase88 demo ready \| 2026-10-24 09:01:53.948575 (SAST) \| t` |
| `tenant_56399d06a773` (org_phase86_squash_smoke) | `4 \| phase88 demo ready \| 2026-10-24 09:01:53.871463 (SAST) \| t` |

### BEFORE → AFTER (`logs/v4-before.txt` → `logs/v4-after.txt`), `tenant_5039f2d497cf`

| Row | BEFORE (status / due / amount_paid) | AFTER (status / due / amount_paid) | V4 § |
|---|---|---|---|
| INV-0001 PAID R2 070,00 | PAID / NULL / (no column) | PAID / NULL / **2070.00** | §3 |
| INV-0002 SENT R42 182,00 | SENT / NULL / — | SENT / **2026-11-21** / 0.00 | §2 (issue 2026-10-22 + 30) |
| INV-0003 SENT R7 058,13 | SENT / NULL / — | SENT / **2026-11-21** / 0.00 | §2 |
| INV-0004 PAID R750,00 | PAID / NULL / — | PAID / NULL / **750.00** | §3 |
| DRAFT R9 660,00 (no number) | DRAFT / NULL / — | DRAFT / NULL / 0.00 | untouched |
| project_members | `LEAD` × 4 | `lead` × 4 | §1 |
| columns amount_paid / default_payment_terms_days / payment_method | absent | present (all 3) | |
| constraints | `chk_invoice_status` only (DRAFT, APPROVED, SENT, PAID, VOID) | all 5 (`chk_invoice_status`, `chk_invoices_amount_paid`, `chk_org_settings_payment_terms`, `chk_payment_events_method`, `chk_project_members_role`); `chk_invoice_status` now includes **PARTIALLY_PAID** | |
| `org_settings.default_payment_terms_days` | (no column) | 30 | |

Smoke tenant `tenant_56399d06a773`: V4 row, 0 invoices, 0 project_members, all 5 constraints, terms 30 — nothing to backfill. **All observed values match the brief's expectation.**

### Step 8 — browser observation of the backfill on the old tenant (closes 620A "live backfill observation DEFERRED → 623")

Playwright MCP, viewport 1440×900, cookies cleared → `http://localhost:3000/dashboard` → KC two-step login `thandi@mathebula-test.local` / `SecureP@ss1` → `/org/mathebula-partners/dashboard` (2026-10-24 ~07:06Z).

| Check | Observed | Result | Screenshot |
|---|---|---|---|
| Home › Debtors widget | "**2** clients owe fees"; Largest debtors: **Moroka Family Trust R 42 182,00** "Not yet overdue" (Current R 42 182,00, 30/60/90+ R 0,00) and **Nomvula Khoza R 7 058,13** "Not yet overdue" (Current). Not "No client owes fees." | PASS | `screens/v4-after-home-debtors.png` |
| Fees › Fee notes list | header tiles "Total outstanding R 49 240,13", "Overdue R 0,00"; column "Due Date" present | PASS (supporting) | DOM snapshot only, no screenshot (tenant since dropped). Supporting only: rows 1 and 3 carry the 620A closure |
| INV-0002 detail (`/invoices/3f8888c9-4b2a-4891-af9c-eb1a2a13b858`) | header "Issued: 22 Oct 2026 · **Due: 21 Nov 2026**"; Fee note details › Due Date **21 Nov 2026**; status Sent; Record Payment button present | PASS | `screens/v4-after-inv0002-due.png` |

Then cookies + storage cleared (before Session 0 reset).

### Re-provisioned Mathebula tenant (V4 evidence #2, Day 0 Phase B)

After the Session 0 reset, approval of the new access request (2026-10-24 07:13:24Z) provisioned `tenant_5039f2d497cf` from empty: `Current version … << Empty Schema >>` → 1 baseline → 2 → 3 → **4 - phase88 demo ready** → "Successfully applied 4 migrations to schema "tenant_5039f2d497cf", now at version v4" → "Successfully provisioned tenant tenant_5039f2d497cf for org mathebula-partners" (`logs/day0-provisioning.txt`). `org_schema_mapping`: `mathebula-partners → tenant_5039f2d497cf`, `org_phase86_squash_smoke → tenant_56399d06a773`. History of the new schema: V1 09:13:24.320, V2 09:13:25.381, V3 09:13:25.398, V4 09:13:25.411 (SAST), all success; `org_settings`: legal-za, ZAR, terms 30.

### Statement (for 623C `migrations.md`)

**V4 (`V4__phase88_demo_ready.sql`, §1–§3) was first applied to a persistent stack (Keycloak dev DB `docteams`) at 2026-10-24 09:01:53 SAST (07:01:53Z). Durable record: `tenant_56399d06a773` (`org_phase86_squash_smoke`, still present) `flyway_schema_history` row `4 | phase88 demo ready | installed_on 2026-10-24 09:01:53.871463 | success t`, on top of V1 (2026-10-18) / V2 / V3 (2026-10-22) (`logs/v4-durable-record.txt`, fresh read-only SELECT 2026-10-24T07:49:57Z). Every later tenant migration takes a new file (V5+).**

Supporting history: the same start also upgraded `tenant_5039f2d497cf` (617C Mathebula tenant) in place, `installed_on` 2026-10-24 09:01:53.948575 (`logs/v4-after.txt`, `logs/backend-startup-v4.txt`). That schema was then dropped by the slice-authorised Session 0 reset and re-provisioned from empty (V1–V4 at 09:13:25 SAST), so its row is history only and must not be cited as the durable record.

623C fresh read (2026-10-24T10:53:02Z): both tenant schemas still at max V4, `public` at max V1, no V5+ anywhere (`logs/623c-flyway-history.txt`); decision record in [migrations.md](migrations.md).

## Sections

| Section | Doc lines | File | Rows | P / F / D / N/A |
|---|---|---|---|---|
| KC-STACK + V4 (623.1) | — | README.md | — | V4 applied on both tenants, backfill as expected; step-8 browser observation 3/3 PASS (rows 1 + 3 screenshot-backed carry the 620A closure; row 2 supporting, DOM snapshot only); V4 durable record `logs/v4-durable-record.txt` |
| Session 0 (623.2) | 43–49 + master 115–152 | session-0.md | 16 (0.A, 0.B, M.1–M.9, 5 checkpoints) | 15 / 0 / 0 / 1 (M.2) |
| Day 0 seeding (not scored) | 52–188 (phases A–F, J) | session-0.md | 7 phases run, 4 skipped (G, H, I, K) | — |
| Days 1–7 (623.3) | 209–298 | days-01-07.md | 72 (60 doc [59 steps, 4.3b split] + 3 pre + 9 target [T2 split]) | 61 / 10 / 1 / 0 |
| Setup Days 8–21 (not scored, 623B run 1) | 307–360 | days-36-47.md | 8 setup rows run (S8, S9, S9b, S10, S11, S12, S13, S16 — S9.5 + the other Days 8–35 steps not run) | — (all OK) |
| Days 36–47 (623.4, 623B run 1) | 423–464 | days-36-47.md | 40 (24 doc [splits 38.3a/b, 45.9a/b, 47.2a/b → 27] + 13 target T7a–T13d, T10b) | 36 / 4 / 0 / 0 |
| Days 61–75 (623.5, 623B run 2) | 475–504 | days-61-75.md | 23 (19 doc [split 75.2a/b → 20] + 3 target T14–T16) | 11 / 2 / 10 / 0 |
| Gap list (623.7, 623C) | — | gaps.md | 14 gaps + 2 retired/notes (G88-M1…M16) | 0 BLOCKER / 0 HIGH / 5 MEDIUM / 9 LOW |
| Migration-numbering decision (623.8, 623C) | — | migrations.md | — | V4 applied 2026-10-24 09:01:53 SAST; next tenant V5, next global V2 |

Days 1–7 doc rows only (60): 50 / 9 / 1 / 0. Target rows (9): 8 / 1 / 0 / 0. Pre-rows (3): 3 / 0 / 0 / 0. FAIL: 2.9, 3.8, 4.2, 4.3b-ii, 4.6, 5.2, 5.4, 6.3, 6.6, T2b. DEFERRED: 5.14 (→ 629). Split rows count separately. PASS includes 3.3 and 6.2 "PASS (adapted — 617C precedent)" (scenario-wording deviations awaiting founder authorisation, days-01-07.md § Scenario-wording deviations) and T4 (b) "PASS (supporting only — not discriminating)".

UI halves of 4.4 / 4.5 / 6.1 (rates R 1 200,00/hr and R 550,00/hr, entries R 1 800,00 and R 1 100,00) are post-hoc re-observations of the same persisted entries (2026-10-24 07:50–07:51Z, Log Time dialogs Cancelled, no new walk state): `screens/4.5-recap-*.png`, `screens/6.1-recap-*.png`.

617C → 623A headline: G-B3 access chain closed (list → open → log time → reply PASS); notification deep-link precision FAIL (6.3, G88-M6). G-B7 acceptance PDF inline PASS (4.3b-i). No regression of a Phase 88 fix.

623B adapted PASS rows: 45.8, 45.11, 61.1 "PASS (adapted — …)" (scenario-wording deviations awaiting founder authorisation); adapted PASS counts as PASS, as in 623A, so the tallies are unchanged.

Days 36–47 doc rows only (27 split rows): 24 / 3 / 0 / 0 — FAIL 38.3b, 45.9b, 47.2b; target rows (13): 12 / 1 (T12, pointer to 47.2b) / 0 / 0. Days 61–75 doc rows only (20 split rows): 8 / 2 / 10 / 0 — FAIL 68.1, 75.2b; DEFERRED 68.2, 75.3–75.11; target rows (3): 3 / 0 / 0 / 0.

617C → 623B headline: the four HIGH chains of Epic 623 pass in the browser — **G-C9 write-off PASS** (write off → reason → audit → excluded from next draft → Undo → re-write-off, 45.6–45.11 + T11); **G-C10 narrative lines PASS** (draft, preview, saved + emailed PDF, INV-0003); **G-C11 part payments PASS** (R 5 000 EFT → PARTIALLY_PAID on fee note, client row, Home Debtors, aging report; tile drops by exactly R 5 000); **G-D21 debtors PASS on the firm-level views** (Home Debtors 1 → 2 → 3 clients owe fees, Clients "Outstanding fee notes", Fees tiles / Debtor book — all on balance; the matter Overview "Outstanding fee notes" tile still reads "--", G88-M13). Closure gates: a written-off disbursement counts as settled (T14 PASS), a PARTIALLY_PAID fee note satisfies FINAL_BILL_ISSUED (T15 PASS), Admin gets Step 3 "Cannot close — resolve gates" (618C, T16 PASS). G-B3 also closed on 62.1 / 70.1. Still FAIL with unbuilt owners: letterhead/brand/banking (626), audit filter by plain words (625), portal login from the bare URL (G-D1 → 624), close a COMPLETED matter (G-D2 → 627), URL tab wins over click (G-B14 → 627A, observed still open). D-1 (a) left the Day-75 close DEFERRED (G88-M16 → 628H). No regression of a Phase 88 fix.

## Candidate gaps raised by 623A

See `days-01-07.md` § Candidate gaps (G88-M1 … G88-M7; 0 BLOCKER, 0 HIGH, 3 MEDIUM, 4 LOW — caveat: G-B3 access chain closed (list → open → log time → reply PASS); notification deep-link precision FAIL (6.3, G88-M6, MEDIUM)). 623C consolidates into `gaps.md`. No regression of a Phase 88 fix was observed.

| ID | Severity | Owner |
|---|---|---|
| G88-M1 scenario doc paths (0.A / M.6) | LOW | 628H (needs founder §6 authorisation) |
| G88-M2 Team tab does not identify the matter lead (T2b) | MEDIUM | new unit for the founder (619 follow-up — Team panel lead indicator) |
| G88-M3 portal accepted state date-only (4.3b-ii) | LOW | 624 |
| G88-M4 disbursement tables overflow 1440 px | MEDIUM | 627 |
| G88-M5 Member matter visits write `security.access_denied` audit noise | LOW | 627 (619D N3 follow-up) |
| G88-M6 comment notification lands on task list, not the comment (6.3) | MEDIUM | new unit for the founder (notification deep-link to comment) |
| G88-M7 client Matters table STATUS cell blank | LOW | 627 |

Scenario-wording deviations needing founder authorisation (CLAUDE.md §6, route to 628H): 3.3, 6.2, 6.3 unread count, G88-M1 doc paths — listed in `days-01-07.md` § Scenario-wording deviations.

Consolidated and re-routed in [gaps.md](gaps.md) (623C); where owners differ, gaps.md supersedes the table above.

## Candidate gaps raised by 623B

See `days-36-47.md` (G88-M8 … G88-M11) and `days-61-75.md` (G88-M12 … G88-M16) § Candidate gaps: **7 new gaps — 0 BLOCKER, 0 HIGH, 2 MEDIUM (M13, M16), 5 LOW (M8, M9, M10, M11, M14)**. Not new: G88-M12 is a retired id (merged into register gap G-B14); G88-M15 is a note on G-D3 (already specified by 627.5 / 627.8). Ids are not renumbered. No regression of a closed Phase 88 gap. 623C consolidates into `gaps.md`.

| ID | Severity | Owner |
|---|---|---|
| G88-M8 fee-note PDF VAT column wraps amounts mid-number (T7c, 38.3a) | LOW | 626 (U8 PDF template) |
| G88-M9 Generated Documents panel stale after Send fee note until reload (38.1, 45.4) | LOW | 626 (U10) |
| G88-M10 tariff line saved with sort_order 0 → prints between time lines on the PDF (45.3, 47.2a) | LOW | new unit for the founder |
| G88-M11 manual part payment cancels the online-payment session; no link for the balance (T13a) | LOW | new unit for the founder (622 follow-up; confirm intended) |
| G88-M12 → **merged into G-B14** "URL tab wins over click" (register item 26, U16), observed still open on 62.1 — not a new gap; id kept as a marker | — | **627A** (627.2 / 627.3) |
| G88-M13 matter Overview "Outstanding fee notes" tile shows "--" despite outstanding notes (T15, 75.1) | MEDIUM | new unit for the founder (620B/622D follow-up) |
| G88-M14 Tasks tab says "No tasks yet" and hides filters when all tasks are Done (75.1) | LOW | new unit for the founder; "project" copy → 628 |
| G88-M15 Reopen keeps `retention_clock_started_at` from the Complete (75.2b) — observed current behaviour, **already specified by 627.5 / 627.8** (G-D3, ADR-354); a note, not a new gap | — | **627** (627.5 / 627.8) |
| G88-M16 Day-75 closure pre-state (trust R0, all PAID) unreachable after the §5 5.9 reorder (75.3–75.11) | MEDIUM | 628H (needs founder §6 authorisation) |

Scenario-wording deviations raised by 623B (route to 628H): 36.1/45.1, 38.1/45.4/65.1, 45.7, 75.2 (§5-authorised, prose pending 628H); 45.8 "Written off" vs WRITTEN_OFF, 45.11 exact literal, 61.1 "4th client" (3rd here), 68.1 landing page, 75.3 pre-state, 75.7 "Close matter" vs "Confirm Close" (need founder authorisation) — see each day file.

Consolidated and re-routed in [gaps.md](gaps.md) (623C); where owners differ, gaps.md supersedes the table above.

## Deck re-shoots (623.6)

All five: `marketing/decks/screens/phase88/`, PNG **1440 × 900** (`file` verified), viewport (not full page), light theme (`emulateMedia colorScheme: light`), Mathebula & Partners tenant (`tenant_5039f2d497cf`, brand `#1B3A4B` set in Session 0), actor Thandi (owner), Keycloak dev stack :3000. Each was read back. `exec-deck.html` untouched (reference update is 629D, OQ #19); the 620E `home.png` / `matters.png` / `clients.png` left in place.

| File | Shows | Source URL | Taken (UTC) |
|---|---|---|---|
| `fee-note-lines.png` | INV-0001 (Sent) scrolled to "Fee note details — Due Date 23 Nov 2026" + Line Items with the narrative lines "2026-10-24 · Bob Ndlovu · Drafting particulars of claim" and "2026-10-24 · Carol Mokoena · Legal research — precedent review", the deeds disbursement, Subtotal R 4 150,00 / VAT R 435,00 / **Total R 4 585,00**. The dev Online Payment Link panel (mock-payment URL) is scrolled out of frame | `/org/mathebula-partners/invoices/8878574f-e15f-4936-a8c0-dd05d17b5556` | **re-shot 2026-10-24 09:34Z** (review fix; view only, no state change). The original 08:40Z run-1 shot showed the mock-payment URL panel and was replaced |
| `part-payment.png` | INV-0002 **Partially Paid** badge, "Part payment received — Paid R 5 000,00 · Balance due R 37 814,50", Payment History "Completed · Manual · EFT · EFT/MOROKA-001 · R 5 000,00", Record Payment offered, Void hidden | `/org/mathebula-partners/invoices/4591b2c1-3404-4cb9-8b97-d0e3099bfb64` | 08:48Z (run 1, after T13a) |
| `home-debtors.png` | Home › Debtors "**3** clients owe fees": Moroka Family Trust R 37 814,50 · Nomvula Khoza R 7 058,13 · Sipho Dlamini R 4 585,00 (all "Not yet overdue", Current bars + aging legend); Trust position "Balanced" | `/org/mathebula-partners/dashboard` | 09:02Z (run 2, after 65.1, before 75.1) |
| `clients-outstanding.png` | Clients list, **Outstanding fee notes** column: Moroka "R 37 814,50 · 1", Nomvula "R 7 058,13 · 1", Sipho "R 4 585,00 · 1"; FICA "Done" ×3; Trust balance R 50 000,00 / R 0,00 / R 3 750,00 | `/org/mathebula-partners/clients` | 09:03Z (run 2) |
| `matters-fee-earner.png` | Matters "Open matters", **Fee earner** column: Thandi Mathebula (Moroka estate), **Bob Ndlovu** (Nomvula collections), Thandi Mathebula (Sipho litigation); all Active (Sipho's matter not yet completed) | `/org/mathebula-partners/matters` | 09:03Z (run 2) |

## Deferred items and owners

| Item | Why deferred | Owner |
|---|---|---|
| 5.14 mixed Approved + Pending disbursement list screenshot | Walker missed the capture window between 5.10 and 5.11; re-capture would need extra disbursements (new walk state, forbidden); 623B covers Days 36–47, not Day 5 | **629** (full 90-day re-walk walks Day 5 again) |
| 68.2 portal view of matter status + fee notes (623B) | Blocked by G-D1: the bare `/login` refuses Sipho (68.1 FAIL); the `?orgId=` driver path was observed but is a substituted pre-state (617C review) | **624** (U11), then 629 re-walk |
| 75.3–75.9, 75.11 Day-75 happy-path close (623B) | D-1 (a) Strict: scenario pre-state (trust R0, all fee notes PAID) unreachable after the §5 5.9 reorder; no refund/transfer workaround, no owner override | **628H** (G88-M16 scenario amendment, founder §6), then 629 re-walk |
| 75.10 retention row / retention card after close (623B) | No close under D-1 (a); G-D3 itself unbuilt | **629** (observe), **627** U17 (fix G-D3) |

## Stack state left for 623B (historical — 623A end state)

KC stack running from `$MAIN` (backend PID 70187 on :8080, gateway, frontend, portal), caffeinate PID 67665 (`-dimsu -t 43200`), `/tmp/.e2e-keycloak-mode` present. Dev artefacts in `$MAIN`: `.svc/`, `.playwright-mcp/` (623a-*.png, fixtures), possibly `portal/next-env.d.ts` (portal dev server rewrite) — not committed.

## Stack state left for 623C

623A built and started the stack (§ KC-STACK rows 4a–5 above); 623B (runs 1–2) did not restart, rebuild or pull it. KC stack still running from `$MAIN` (`svc.sh status` 08:54Z: backend / gateway / frontend / portal all `yes yes`; Docker b2b-keycloak, b2b-postgres, b2b-mailpit, b2b-localstack up). `origin/main` = `7d25a8eb4` throughout. caffeinate PID 67665 (`-dimsu -t 43200`, started 07:00:30Z, alive at 09:17Z, expires ≈ 19:00Z); host was on **battery** (91 % → 79 % over the walk). `/tmp/.e2e-keycloak-mode` present. Browser (Playwright MCP) logged in as Thandi, light colour scheme emulated; the separate portal context was closed. Dev artefacts in `$MAIN` (not committed): `.svc/`, `.playwright-mcp/` (`623b-*.png`, `623b-deck-*.png`, `623b-47.1-INV-0002-download.pdf`, FICA fixtures), possibly `portal/next-env.d.ts`.

Tenant `tenant_5039f2d497cf` at end of 623B (09:17Z): 3 clients ACTIVE (Sipho, Moroka Family Trust, Nomvula Khoza); 3 matters **ACTIVE** (Sipho's completed → reopened; 9 tasks DONE; Moroka + Nomvula 9 OPEN each); INV-0001 SENT R 4 585,00, INV-0002 PARTIALLY_PAID balance R 37 814,50, INV-0003 SENT R 7 058,13; Moroka 1 h unbilled (Carol, 70.1); disbursements sheriff WRITTEN_OFF / deeds BILLED; trust Sipho R 3 750,00, Moroka R 38 000,00; no matter closed, `matter_closure_log` empty; Mailpit 14 messages. Created ids per day file § Created state.

623B end state confirmed at review time (2026-10-24 09:32–09:36Z): stack not restarted (`svc.sh status`: backend 70060, gateway 68353, frontend 68497, portal 68555, all `yes yes`; origin/main still `7d25a8eb4`); read-only SELECT `security.access_denied` still 21 rows / max 09:05:59Z (T16 after-state, `logs/backend-access-denied-T16.txt`); `fee-note-lines.png` re-shot as Thandi (view only); INV-0001 PDF re-fetched from Mailpit (`logs/mailpit-inv0001-INV-0001.pdf`). No writes.
