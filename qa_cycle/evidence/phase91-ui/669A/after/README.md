# 669A OBS-after: compliance, conflict check, legal registers, information requests (task 669.3)

PR #1881, head **`76c16e169`**. Compare with `../before/README.md` (OBS-before @ `2bbab3168`, main `ed22a7bfa`).

## Environment

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. Only the frontend was rebuilt; nothing was reseeded, restarted or torn down by this run.
- **Frontend:** container `e2e-frontend`, image **`sha256:28196429688c`**.
  - Image created **2026-11-27T09:11:42Z**; container created 09:11:44Z.
  - Compose `project.working_dir` = `/Users/rakheendama/Projects/2026/worktree-epic-669A/compose`.
  - Commit `76c16e169` is 09:07:54Z, so the image post-dates it by 3 min 48 s.
  - The image contains 669A code: `"Firm default"` is in `.next/server/chunks/ssr/frontend_components_information-requests_request-detail-client_tsx_*.js` and in a client chunk.
- **Backend / Postgres / IDP:** unchanged from OBS-before (backend `sha256:e7fd89e47741`, containers created 08:10:29Z). The OBS-before seed data (DSAR `6a41ee33-…`) is still present.

**Browser:** the same as before: Playwright library, Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded, theme via `kazi-theme`, logins through `/mock-login`. Personas: Alice (owner) and Carol (member).

## Replay (unedited before scripts)

```
cd ../before/scripts
PHASE=after node 01-capture.cjs                     # 09:18:17Z → 09:23:04Z: 91 page loads, console 4, writes 1
PHASE=after STACK="e2e-frontend image sha256:28196429688c (created 2026-11-27T09:11:42Z) built from worktree-epic-669A/compose @ 76c16e169 (commit 09:07:54Z); backend sha256:e7fd89e47741 unchanged" node 02-assemble.cjs
```

- Both scripts ran **unedited**. They produced the 19 contact sheets and 8 crops (same names as `before/`), plus `measurements.json`, `sel-compat.json`, `console.json` and `discover.json`.
- **Writes:** one conflict check (Alice 1440 light, "Pillay Holdings", NEW_MATTER, 09:21:22Z), as designed. Conflict checks now total **10**: page loads read `History (9)` and the interactions `History (10)`, exactly the +1 predicted in the before README.

**Supplementary checks.** These are new read-only scripts, not edits of the before scripts. They measure what the before scripts did not:
- `scripts/03-verify.cjs` → `verify.json`:
  - disbursement tables (firm list + matter tab) at all 4 variants: frame vs `scrollWidth`, min-content headroom, clipped cells;
  - a viewport sweep from 1024 to 1440 in 16 px steps;
  - row click, the "…" menu, sort + `aria-sort`;
  - element reads for K-39/K-79/K-80(c)/K-85, the empty states and the "Data Requests" h1.
  - Console during this run: **0 messages**.
- `scripts/04-header-case.cjs` → `header-case.json` and `crop-defect-disbursement-header-case-1440-light.png`: header `text-transform` (defect D-1).

## PASS/FAIL

### 1. Every page (91 loads; `measurements.json › summary`)

| Check | Before → after | Result |
|---|---|---|
| `main#main-content` = 1, `<main>` = 1 | 91/91 → 91/91 | PASS |
| Level-1 headings in main = 1 | 1 on every page except Carol on a matter she is not on (0) → **identical** | PASS. The 0 is F-668A-B-4, pre-existing and routed. |
| h1 `/compliance` (Alice ×4 variants and `?tab=`) | "Compliance" 30/400 → **"FICA & compliance" 24/600** = breadcrumb = active nav (K-84) | PASS |
| Carol `/compliance` | HTTP 404, "Page not found" → same | PASS |
| `/compliance/requests` h1 | "Data Requests" → "Data Requests". The h1 `textContent` is exactly "Data Requests" (no span inside). The count pill "1" is **outside** the h1, in the actions slot before "New Request" (`bg-muted text-muted-foreground`, 6.76 light / 5.84 dark). | PASS |
| Carol gate h1s | "Compliance" / "Data Request" / "Information Request" + the unchanged sentence. 30/400 → 24/600 (PageHeader). | PASS |
| Detail h1s | "Acme Corp" 30/**700** → 24/**600** (K-82). "REQ-0001" / "REQ-0002" 30/400 → 24/600. "Deeds Office Fees" / "Sheriff Fees" 24/400 → 24/600. | PASS |
| Hand-rolled → PageHeader h1 (conflict check, adverse parties, disbursements, tariffs) | 30/400 → 24/600, same text | PASS |
| Document horizontal overflow | 0 everywhere → **0 everywhere** (91 loads + `verify.json`) | PASS |
| Console | 4 × "Failed to load resource: 404" on Carol `/compliance` → **the same 4, nothing else**. **0 hydration** messages; `/compliance` no longer carries `suppressHydrationWarning`. `verify.json`: 0. | PASS |

### 2. Tables (kit DataTable; frame = the bordered card, 2 px less than the old container)

| Table | 1024 light/dark: scroll/client | 1440 light/dark: scroll/client | Columns in view vs before | Result |
|---|---|---|---|---|
| Compliance › Onboarding Pipeline (5 rows) | 710/710, 718/718 (was 710/710, 718/718) | 1126/1126, 1134/1134 | Client · Duration · Progress (same) | PASS |
| Compliance › open data requests (1) | 710/710, 718/718 | 1126/1126, 1134/1134 | Client · Type · Deadline · Status (same) | PASS |
| `/compliance/requests` (1) | 710/710, 718/718 (was 712, 720) | 1126/1126, 1134/1134 | all 5 (same) | PASS |
| Conflict history (10 rows) | 718/718 (1024 dark) | 1126/1126 (Alice and Carol) | all 6 (same) | PASS |
| Conflict result panel | 662/662 | **718/718** (was 1078). The table now sits inside the 768 px form cap (DA-10) and "Pillay Holdings (Pty) Ltd" wraps to 2 lines. No overflow; noted, not a defect. | same 5 | PASS |
| `adverse-party-table` (3) | 710/710, 718/718 | 1126/1126, 1134/1134 | all 6 (same) | PASS |
| `disbursements-list-page` / `disbursement-list` (4 × `disbursement-row-<id>`, unchanged) | 710/710, 718/718. Min-content **551**, headroom 159/167 px. | 1126/1126, 1134/1134. Min-content 967 (Alice) / 914 (Carol), headroom 159–220. | 1024: Date · Description·Supplier · Amount · Approval · Actions (same). 1440: + Matter, Billing (same; Category/Supplier hidden as before). | PASS |
| Matter › Disbursements tab (1 row) | 662/662, 670/670. Min 528, headroom 134/142. | 1078/1078, 1086/1086. Min 900, headroom 178/186. | same as before at 1440 (before measured 1440 only) | PASS |

- **Sweep, 1024 → 1440 in 16 px steps (Alice light).** No overflow, no clipped cell, and positive headroom at every step.
  - Firm list: 5 → 6 columns at 1248 (frame 934, min 830, headroom **104**) and → 7 at 1376 (frame 1062, min 967, headroom **95**).
  - Matter tab: 6 columns at 1296 (headroom 118) and 7 at 1424 (headroom 162).
- **Rows are ≈8 px shorter** (kit `p-2`, DA-9 judgement call). Pipeline 53 → 45, requests 50 → 39, adverse 57 → 49; the disbursement rows are unchanged (79/95/63).

### 3. Behaviour (`verify.json › behaviour`, `measurements.json › interactions`)

| Check | Observed | Result |
|---|---|---|
| Firm list row click (Date cell, then Description cell) | Navigates to `/legal/disbursements/bf9dedfc-…` and `/legal/disbursements/def3a520-…`; `disbursement-detail` = 1. Alice **and** Carol. Rows `cursor: pointer`. | PASS |
| Matter tab row click | Navigates to `/legal/disbursements/def3a520-…` | PASS |
| Row "…" menu | Firm list and matter tab: URL unchanged, menu open (1 item, "Write off"). Escape closes it and focus returns to `disbursement-actions-bf9dedfc-…`. Carol has 0 triggers, as before. | PASS |
| Sort toggles, local (URL never changes) | Initial `Date=descending`. **Amount:** asc 150 → 750 → 1 250 → 4 025, desc reversed, `aria-sort` ascending/descending. **Supplier:** asc Adv. N. Mokoena → Deeds Office → Sheriff ×2, desc reversed. **Date / Approval:** `aria-sort` toggles asc/desc, but the order cannot change: all 4 rows are 2026-11-27 / Approved (data ties). | PASS, with one note: Supplier `aria-sort` sits on the **hidden** Supplier `<th>` (see D-2). |
| Conflict-check submit | "Run Conflict Check" → result h3 "Potential Conflict" (badge 4.52), URL unchanged, History 9 → 10 | PASS |
| Conflict History Date (DA-8) | "11/27/2026" → **"27 Nov 2026"** (1440 light, 1024 dark, Carol) | PASS. The result line still reads "at 11/27/2026, 11:21:21 AM": F-669A-B-1 remainder, routed. |
| Conflict prefill (4 searchParams), filter "Conflict Found" → 2 rows, Resolve dialog opens and Escapes | identical to before | PASS |
| Adverse search no-match | Before: `<div>` 86 px. After: `EmptyState inline`, 44 px, text in a **`<p>`**, 0 headings inside, `adverse-party-table` 0. "Pillay" → 1 row. | PASS |
| `tariff-item-search` | 0 at load, 1 after expand; 7 sections, 19 items; "summons" → 2. No-match: 125 px block → `EmptyState inline` 44 px `<p>`, 0 headings. | PASS |
| K-81 | Empty badge after the schedule name: 1 (22 px) → **0** on 8/8 variants and on the expanded view | PASS |
| K-67 | REQ-0001 "Every days" → **"Firm default"** (API `reminderIntervalDays: null`); REQ-0002 "Every 5 days" | PASS |
| K-79 | List Date "2026-11-27" mono → **"27 Nov 2026"** in the body font (4/4, both personas). Detail "Incurred 2026-11-27" → "Incurred 27 Nov 2026". Tariffs "Effective from 2024-04-01" → "Effective from 01 Apr 2024". `isoDates` = 0 on every variant. | PASS |
| K-80 (c) | Amount (excl VAT) and Total in mono. VAT: row in the body font, mono only on "R 0,00". Payment Source "Trust Account" / "Office Account" in the body font. "R 0,00 — Zero-rated pass-through" at 1024: **2 lines → 1 line** (20 px). The trust UUID still wraps at 1024 (it did before too; K-80 (b) is backlog). | PASS |
| K-82 | h1 weight 700 → **600** (30 → 24 px) | PASS |
| K-60 | Name to Check 1 128 → **768 px** at 1440 light (dark 1 136 → 768); form `max-width: 768px`. 1024 unchanged (712/720). h1 24/600 via PageHeader. | PASS |
| K-37 (light, composite on canvas) | Info-request "Acme Corp" **3.13 → 5.41** (#00776a on #fdfeff). Disbursement trust-tx link 3.13 → **5.41**. Dark unchanged: 10.74 and 9.63. | PASS (PR expected ≈5.46) |
| K-85 (light) | Labels Contact / Reminder Interval / Sent / Due / Progress, the e-mail and "0/1 accepted": **4.48 → 7.25** (#4d545f on #f8f9fb). Dark unchanged at 6.74. | PASS. "Waiting for client" stays 4.48: not in the K-85 row, F-669A-A-1. |
| K-39 | "\|" separator: light **1.54 → 7.57**, dark 1.92 → 7.51. Tariff "(n)": 7.57 light, 7.51 dark. | PASS |
| K-68 | Header "Requested: 27 Nov 2026, 10:29" and timeline "Request Received 27 Nov 2026" agree, as before | **NOT OBSERVABLE** on this data. The request was created 10:29 SAST, outside the 00:00–01:59 window. Unit-tested only (PR: 2 failing → 11/11). Not claimed as PASS. |

### 4. Empty states (ADR-393)

| Empty | Before | After | Result |
|---|---|---|---|
| `statements-empty` (Alice; Carol on her own matter) | 102 px dashed panel, text 4.48 | **44 px**, `EmptyState inline`, testid kept (count 1), text 17.79 (light) / 20.03 (dark) | PASS |
| Checklist "No checklists yet." | Alice 160 px, Carol 184 px (hand-rolled, no heading) | `EmptyState default` **228 px** (Alice and Carol), icon + h2 title | PASS (default variant). The new h2 collides with no e2e heading query (grep). |
| `/compliance/requests` filtered (In Progress / Completed / Rejected) | `default` 248 px | `default` **248 px** (also 1024 dark) | PASS |
| Disbursements list, From 2030-01-01 | `<div>` 86 px | `EmptyState inline` **44 px**, text `<p>`. The only heading containing "Disbursements" is **h1** (1440 light, 1024 dark, Carol). | PASS |
| Matter › Disbursements tab, empty | 86 px | `EmptyState inline` 44 px `<p>`. The only heading containing "Disbursements" is **h3** (all 4 variants). | PASS |
| "No unbilled disbursements" strip | 38 px, 4.48 | unchanged | (F-669A-B-4, pre-existing) |

### 5. SEL-COMPAT after (`sel-compat.json` vs `../before/sel-compat.json`)

**155 keys identical; 35 differ, all expected:**
- **29** add the new `page-header` testid.
- **3** are the adverse no-match `empties` reading twice: the probe catches the inline EmptyState and its wrapper. The screenshot shows a single line.
- **2** are the checklist page's new h2 "No checklists yet." (EmptyState default title).
- **1** pair is `h1 "Compliance"` **exact** 1 → 0 on `/compliance`. Under Playwright's default matching (substring, case-insensitive), which `day-90-review:351` and `compliance-overview:23` use (no `exact`), it stays **1**.

Unchanged: `adverse-party-table` (1; 0 on no-match), `disbursement-row-*` (4; matter tab 1; Carol 4), `disbursements-list-page` (1), `statements-empty` (1; 0 on Carol's not-on-matter page), `tariff-item-search` (0 → 1 after expand), h1 "Data Requests" (1), h1 "Disbursements" (1).

| Check group | Result |
|---|---|
| 1 Every page | PASS |
| 2 Tables | PASS |
| 3 Behaviour + register rows | PASS. K-68 not observable. D-1 and D-2 below. |
| 4 Empty states | PASS |
| 5 SEL-COMPAT | PASS |

## Defects

- **D-1 — INTRODUCED (LOW, visual): mixed header casing in the disbursement table** (firm list and matter tab, all variants; `header-case.json`, `crop-defect-disbursement-header-case-1440-light.png`).
  - **Before:** all five visible headers were uppercase: DATE · DESCRIPTION · SUPPLIER · AMOUNT (INCL VAT) · APPROVAL · ACTIONS.
  - **After:** the kit sort buttons render "Date", "Amount (incl VAT)" and "Approval" with `text-transform: none`, while "DESCRIPTION · SUPPLIER", "MATTER", "BILLING" and "ACTIONS" are uppercase.
  - **Hypothesis:** `frontend/components/data-table/data-table-column-header.tsx:62-65`.
    - The kit `<button>` class list has no `uppercase`, and Tailwind preflight resets `text-transform` on buttons, so the `<th>`'s `uppercase` (`components/ui/table.tsx:71`) is not inherited.
    - 669A's own `SORT_CONTROL` (`disbursement-list-view.tsx:127-128`) adds `uppercase` to the Description header's supplier button, which makes the mix visible inside one table.
  - **The kit root cause is pre-existing.** `/reports/profitability` shows the same mix; routed as F-669A-A-2.
  - **Fix options:**
    - (a) The one-line kit fix: add `uppercase tracking-wider` to the kit sort button/link class. This is a kit edit, outside 669A's "no kit edits" scope, so it needs a ruling.
    - (b) Accept for 669A and land (a) in 670A.
- **D-2 — INTRODUCED (LOW, a11y, not a regression: before had no `aria-sort` at all): the Supplier sort state is not exposed.**
  - When sorted by supplier, `aria-sort="ascending|descending"` lands on the Supplier `<th>`, which is `display: none` at 1024 and at 1440. It is therefore not in the accessibility tree.
  - The visible Description `<th>` carries no `aria-sort`, and the "Sort by supplier" button exposes no state.
  - **Hypothesis:** `disbursement-list-view.tsx:212-237` (`DescriptionHeader`) together with the kit's `ariaSort(meta)` (`data-table.tsx:382-386`), which only knows the column's own `sortKey`.
  - **Suggested fix:** while Supplier is hidden, give the button an `aria-label` with the state (e.g. "Sort by supplier, ascending") or `aria-pressed`. Alternatively, a kit hook to put `aria-sort` on the Description `th`. Candidate for the 670A list.

No other check failed.

## Pre-existing items re-seen / routed

- **New in `../found.md`:**
  - F-669A-A-1: "Waiting for client" 4.48 (`request-detail-client.tsx:391`).
  - F-669A-A-2: kit sort-header casing, pre-existing on profitability.
- **Re-seen, already routed:**
  - F-669A-B-1: result "Checked … at 11/27/2026, 11:21:21 AM".
  - F-669A-B-2: Overview "ACCESS"/"RECEIVED".
  - F-669A-B-3: `?tab=` ignored; no `aria-current`.
  - F-669A-B-4: "29d remaining" 3.62, "View All Requests" 3.13, History "Resolve" 3.13, matter-header client links 3.13, "No unbilled disbursements" 4.48. Its `statements-empty` part is now resolved: 17.79.
  - F-668A-B-4: Carol not-on-matter page has 0 h1.
  - K-66 (backlog): "—" in 3/4 rows for Alice and 4/4 for Carol, unchanged.

## Files

- 19 `sheet-*.png` + 8 `crop-k*.png` (replay; same names as `before/`).
- 1 defect crop.
- `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`, `verify.json`, `header-case.json` (single-line JSON).
- `scripts/03-verify.cjs`, `scripts/04-header-case.cjs`, and this README.
- **37 files.** Raw tiles are in `/tmp/669A-after-raw/` (not committed).
