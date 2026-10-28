# 628B AFTER-FIX re-observation — e2e mock-auth stack :3001 built fresh from bd9d01fcd, 2026-10-29 ~00:12–00:25 SAST (22:12–22:25 UTC)

Browser: `mcp__playwright`, Alice (owner), org e2e-test-org. Measurements via `page.evaluate` (scrollWidth/innerWidth, `scrollTo(9999,0)` → scrollX, getBoundingClientRect).

| # | Check | Route | Viewport | Value | Verdict | Screenshot |
|---|---|---|---|---|---|---|
| R1a | G88-M4 | /legal/disbursements (4 rows) | 1024 / 1440 / 1920 | sw 1024 / 1440 / 1920 = iw; scrollX 0 / 0 / 0; wrapper `relative overflow-x-auto` (position relative) | PASS | 01, 02, 03 |
| R1b | G88-M4 | /matters/fd626c60… (Ndlovu v RAF — Depth II, 2 disb.) ?tab=disbursements ("Fees · Disbursements") | 1024 / 1440 / 1920 | sw = iw at all 3; scrollX 0; table scrolls in own box (wrapper sw 1263 / cw 656·1072·1152) | PASS | 04, 05, 06 |
| R2 | G-D15 | /settings/documents?tab=templates | 1024 / 1440 / 1920 | sw 1024 / 1440 / 1920; scrollX 0; New Template l748–r888 (main right 1024) wraps under format select at 1024, l1260–r1400 @1440, l1540–r1680 @1920; no clipping ancestor | PASS | 07, 08, 09 |
| R3a | F3 crumb | /matters/4da58b2a… (130-char "628B-LONG …") | 1440 | sw 1440; header h 56; last crumb span w 448 (max-w-md), text-overflow ellipsis, nowrap, truncated (sw>cw), `title` === full name; avatar 1340–1372, bell 1384–1416 | PASS | 10 |
| R3b | F3 crumb | same | 1024 | sw 1024; header h 56; last crumb w 324, ellipsis, title = full name; avatar 924–956, bell 968–1000. Nit: first crumb "E2E Test Organization" wraps to 2 lines (header still 56) | PASS (nit) | 11 |
| R3c | F3 @390 | same matter / short-name matter fd626c60 / dashboard | 390 | header sw 390 on all (avatar 298–330, bell 342–374 in place). Page sw 617 on BOTH long- and short-name matter → from the matter action row (Generate Document / Generate Statement of Account / More actions, right 617), not the header. Dashboard sw 390 (prev obs at 35178f841: 428). BUT nav shrinks to 91 px (min-w-0) while non-last crumbs don't truncate: last crumb collapses to 0 px, "Matters ›" paints under the e2e-test-org switcher (visual overlap) | header no worse (page overflow gone); NOTE visual overlap | 12, 13, 14, 15 |
| R4a | F7 | /diary?view=week (court date created 30 Oct) | 768 / 900 | 1 column (gridTemplateColumns count 1), 7 stacked day cells; event text width 395 / 527 px; time 09:30 line above 2-line clamped title; sw = iw | PASS | 16, 17 |
| R4b | F7 + G-D20 | same | 1440 | 7 columns, grid 1120; text width 68 px; time above title, title line-clamp-2 "628B / Gauteng…" (h 40); `title` attr = full court name | PASS | 19 |
| R4c | F7 + G-D20 | same | 1024 | 7 columns, grid 704 → cell 92 px, event link 52 px, **text column 9 px**: title renders "6 / 2", time "09:30" clipped (sw 36 / cw 9), matter line 9 px. `title` attr full. sw 1024 | **FAIL** (unreadable) | 18 |
| R4d | grid keyboard (info) | same | 900 | focus Mon → ArrowDown ×2 / ArrowUp: no movement; ArrowRight → Tue → Wed; ArrowLeft → Tue. In single-column layout Down/Up are no-ops, Left/Right step days (visually up/down) | INFO | — |
| R5a | F8 stale label | 628B-LONG matter → crumb "Matters" → "Ndlovu — transition repro" (client-side) | 1440 | sequence: A-name → "Matters" → "Matter" (~300 ms) → B-name; A's name never on B | PASS | 20 |
| R5b | client crumb | /clients/c5c49374… | 1440 | "… › Clients › Sipho Ndlovu mvso1vzf2585" | PASS | 21 |
| R5c | fee note crumb | /invoices/157addff… (SENT) | 1440 | "… › Fee notes › INV-0001" | PASS | 22 |
| R5d | draft crumb | /invoices/69a24a1a… (DRAFT, created by me) | 1440 | "… › Fee notes › Fee note" (h1 "Draft fee note"); client-side INV-0001 → list → draft: "INV-0001" → (list) → "Fee note", no leak | PASS | 23 |
| R6a | shell width | /settings/documents, /settings/firm, /matters, /clients | 1440 | 1440 each | PASS | 24–27 |
| R6b | labels | /settings/fees?tab=tariffs, /settings/connections?tab=xero | 1440 | tabs "Rates*" …; "Integrations*" …, h1 "Integrations" | PASS | 28, 29 |
| R6c | h1 | /invoices/billing-runs, /invoices/collections | 1440 | "Fee runs", "Debtors" | PASS | 30, 31 |
| R6d | sidebar org | aside | 1440 | `truncate text-xs font-medium text-white/80`, colour white/0.8, opacity 1; bar `h-4 w-[3px] rounded-full` 3×16 bg rgb(26,143,110) | PASS | any |
| R6e | client badge | Acme Corp (ACTIVE/ACTIVE) | 1440 | badges ["Active"] (single); Apex mvso (ACTIVE/ONBOARDING) → ["Onboarding"] | PASS | 32 |
| R6f | console | all pages above (19 routes re-walked with page.on listeners) | — | 0 errors EXCEPT /settings/fees (?tab=tariffs and bare): React #418 hydration text mismatch, 100% repro (5/5) | FAIL-unrelated (pre-existing, time-window) | console.log |

## Diagnoses
- **R4c (G-D20 @1024)**: `frontend/components/diary/diary-week-view.tsx:31` `WEEK_GRID = "grid grid-cols-1 gap-2 lg:grid-cols-7"` — `lg` is a viewport breakpoint (1024), but the main column at 1024 is only 784 px (240 px sidebar) → 7×92 px cells; after the 24 px row-actions button, icon, gaps and padding the text column is 9 px. F7 moved the switch from md→lg, which fixed 768–1023 but 1024–~1279 is still unreadable. Needs `xl:` (1280 → ~1040 px main, ~130 px cells ≈ 1440's 68 px text… still tight) or a container query on the grid's own width (e.g. `@container` + `@4xl:grid-cols-7`). Pre-F7 (md) 1024 was the same 7-col layout, so not a regression — just not closed at 1024.
- **R6f #418**: `frontend/components/rates/member-rates-table.tsx:30` `const today = new Date().toLocaleDateString("en-CA")` used to pick the current rate. Server (container UTC, 28 Oct 22:2x) picks Alice's 2026-07-31 rate; browser (SAST, 29 Oct 00:2x) picks the 2026-10-29 rate → SSR "31 Jul 2026" vs client "29 Oct 2026" in EFFECTIVE FROM. Only manifests while UTC date ≠ local date (00:00–02:00 SAST) and a rate is effective "today". File not touched by PR #1704 (last change 4f1886c24 / 628A on main; PR only edits a comment in `settings/fees/_tabs/tariffs.tsx`). Previous observation (~23:30 SAST, same date both sides) saw 0 errors — consistent.
- **R3c overlap @390**: `frontend/components/breadcrumbs.tsx` nav `min-w-0` lets the nav shrink to 91 px; only the last crumb can truncate, so earlier crumbs overflow visibly under the org switcher. Pre-fix main overflowed the page by ~36 px instead. Mobile breadcrumb collapse (hide middle crumbs < sm) would close it.

## Data created (via UI)
- Matter "628B-LONG Khumalo Property Holdings — extended conveyancing transfer of Erf 98765 Sandton Extension 12 with servitude registration" (4da58b2a-5f3f-4a79-af21-ab327c130dfd), client Khumalo Property Holdings mvso1vzf2585, ACTIVE.
- Court date on that matter: Hearing, 2026-10-30 09:30, court "628B Gauteng Division of the High Court, Johannesburg — Motion Court Room 4B".
- Draft fee note 69a24a1a-f0f4-478b-bd04-05f74912f699 for Sipho Ndlovu mvso1vzf2585: 2h "Letter of demand" (R5 000) + disbursements Deeds office search fee (R1 250) + Sheriff re-service (R150); total R6 400. These items are now attached to the draft (no longer "unbilled").
