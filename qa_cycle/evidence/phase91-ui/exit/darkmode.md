# 670A — dark walk, dark-mode debt list and FD-8 evidence (task 670.6, Stage M2)

**Result: every in-scope `/org/` surface was walked in dark at 1440 as the owner. 99 / 99 FAIL the strict rule (any text below WCAG AA, or a light slab, faint field or lost-colour control, counts as a fail), every one at least *degraded*.** The reason is a single shell element. The header notification badge is white on the dark accent teal-600, **3.16:1**. It sits in the header of every page (DD-1 = K-21 / K-31 dark). Judged on the page body alone (the shell badge set aside), **76 / 99 pages pass and 23 fail**. Of those 23, 1 is *illegible*: a focused row-menu item at **2.25:1** (DD-2, K-21). The other 22 are *degraded*. Phase 90's D-1…D-5 stay fixed. **FD-8: not passed** (see the end of this file).

## Method

- **When / where:** 2026-11-27, walk ≈13:39Z–13:44:33Z (`darkwalk.json` capturedAt), targeted re-verification → 13:49:14Z. Stack A (e2e mock-auth :3001, provenance in `README.md` § Run and environment). Persona **Alice** (owner). The 1440×900 viewport, DPR 1, `kazi-theme=dark` stored by the init script, `html.dark` confirmed on **102 / 102** loads, reduced motion, timezone Africa/Johannesburg.
- **Pages:** every target of the exit matrix that has an Alice 1440-dark tile and an `/org/` URL. That is M1's shell and settings groups (32 tabs, 11 sub-pages + the custom-checklist edit form, dashboard expanded / collapsed / after Ctrl+B) plus M2's fee notes, fee-note detail per status, trust, the 663A / 663B sites, Section 8 and Section 9. Each page is opened with the same tab click or scroll as its matrix tile: **99 pages**. Three re-verification pages for carried items were added that are not matrix surfaces: matter Overview (K-57), matter Team tab (K-58) and `/matters/new` (Phase 90 D-3). **102 loads.** Platform admin is light-only by design, the portal is never dark, and marketing is light-only (ADR-371), so none of these are in scope.
- **Screenshots:** the walk **reuses the matrix contact sheets** (the Alice row's 1440-D tile; "Sheet" column below). No new full-page PNGs were made. The raw viewport tiles of the walk itself are in `/tmp/670A-exit-raw/darkwalk/`, which is not committed. Defect evidence is one crop sheet, [`darkmode-crops.png`](darkmode-crops.png), from `scripts/m2-dark-reverify.cjs`. Each crop caption carries the measured colours and ratios.
- **Automated audit** (`scripts/m2-dark-audit.cjs` `darkAudit()`, an adapted copy of Phase 90's `pageAudit()`). Results per page are in `darkwalk.json` → `walk[].audit`. Every colour is alpha-composited over its ancestor backgrounds on a 1×1 sRGB canvas (S7 gotcha 14). Only measured ratios are published. The audit checks:
  - **Text:** every visible text owner. Below **4.5:1** fails (**3:1** for large text: ≥ 24 px, or ≥ 18.66 px bold). Below 3:1 = **illegible**; from 3:1 up to the AA threshold = **degraded**. Disabled controls are excluded (14 such text nodes over the walk, all disabled controls).
  - **Light surfaces:** composited luminance ≥ 0.45 and area ≥ 1500 px².
  - **Light discs or chips:** 256–1500 px², luminance ≥ 0.6, not a control. This catches K-58 avatars.
  - **Faint form fields:** Phase 90 rule, border < 1.3:1 **and** fill < 1.15:1 against the surround.
  - **Destructive buttons:** the computed icon colour (`currentColor`) and its hue, for buttons or links whose class contains `text-destructive`.
  - **Accent-filled controls:** teal fills, measured for their text ratio.
  - **Kit card borders.**
- **Rubric (brief M-5):** **illegible** = text < 3:1 or an invisible control. **degraded** = below AA for text (4.5:1) or 3:1 for non-text, or a light slab on a dark page. A page **PASSes** only with no illegible and no degraded item.
- **Not counted (Phase 90 precedent, kept so the two walks compare):**
  - Inverted primary buttons and selected filter chips. They are white or `#eff1f5` with dark text, every one with text ≥ AA: 64 "light surface" hits, all primary buttons, the selected "Open" / "In Progress" chips and the compliance "Check for Dormant Clients" pill.
  - `border-input` field borders at **1.25:1**. Phase 90 ruled this the kit baseline token. It is still below the 3:1 non-text guideline. That is a token-level caveat carried with K-13, not hidden.
  - Outline buttons with a text label.
- **Console:** 102 loads, **0 errors, 0 page errors, 0 hydration**. 2 warnings, both `[tiptap warn]: Duplicate extension names` on the two template-editor pages (K-17, known).
- **Commands** (from `scripts/`):
  - `nohup caffeinate -dimsu bash -c 'node m2-darkwalk.cjs …'` → `../darkwalk.json`, exit 0, `DONE 102 pages`.
  - `node m2-dark-reverify.cjs` → `../darkmode-crops.png` + `darkwalk.json → reverify`, exit 0. It was run twice; the committed output is the second run, after the DD-3b selector was narrowed to `.line-through`.

## Summary

| Measure | Value |
|---|---|
| Pages walked (in scope) | **99**, plus 3 re-verification pages |
| Strict verdict (header badge included) | **0 PASS / 99 FAIL**: 98 degraded, 1 illegible (`/legal/adverse-parties`, focused menu item) |
| Page body (header badge set aside) | **76 PASS / 23 FAIL**: DD-3 on 14 matrix pages, DD-1 body accent controls on 11, DD-5 on 2, DD-7 on 2, DD-2/DD-8 on 1 (some pages carry two). Plus the 3 re-verification pages (DD-3, DD-4, DD-3) |
| Lowest measured text ratio in the walk | 3.16:1 (accent fills; no text < 3:1 on any loaded page). The 2.25:1 focused menu item appears only after opening a menu (probe) |
| White-card-on-dark / light slab | **0** (no unexpected light surface; the 64 hits are inverted primaries, by design) |
| Faint fields below the 1.25 baseline | 2 pages (DD-7) |
| Phase 90 D-1…D-5 | **still fixed** (details below) |

## Dark-mode debt list (§15.6.5 table shape; ids DD-n)

| # | Page (route) | Symptom | Element / file:line | Severity | Proposed token fix | Screenshot |
|---|---|---|---|---|---|---|
| **DD-1** (= **K-21** accent fill, + K-31 dark / F-668A-T3, F-666A-9) | **Every `/org/` page** (header badge, 99/99). Accent controls on 11 pages: `/invoices` "New fee note"; fee-note DRAFT "Approve", "Save Changes"; APPROVED "Send fee note"; SENT / SENT-overdue / PARTIALLY_PAID "Record Payment"; `/settings/matters?tab=take-on` "Start" ×4; `/settings/fees?tab=trust-accounting` "Add Account"; `/invoices/billing-runs/new` and `/trust-accounting/reconciliation/new` active step "1" | White on the dark `--button-accent` teal-600 `#00a38f`, **3.16:1** (badge 10 px text, buttons 14 px). Measured identically in the walk and the crops | Badge `frontend/components/notifications/notification-bell.tsx:69` (`bg-(--button-accent) text-(--button-accent-foreground)`); token: dark `--button-accent` (`frontend/app/globals.css`, F-668A-T3) / `packages/ui/styles/globals.css:232-233` (K-21) | **degraded** | Dark `--button-accent` → a shade that gives ≥ 4.5:1 with white (e.g. teal-700 `#00776a`, which the light scheme already uses at 5.46:1), or keep teal-500/600 and switch `--button-accent-foreground` to near-black in dark | [`darkmode-crops.png`](darkmode-crops.png) DD-1a, DD-1b, DD-1c; every Alice 1440-D tile |
| **DD-2** (= **K-21** focused item) | Every row / overflow menu. Measured on `/legal/adverse-parties` row "Actions" → ArrowDown | Focused (highlighted) menu item white on the dark `--accent` teal-500 `#00c2b1`, **2.25:1** | `packages/ui/styles/globals.css:232-233` (dark `--accent`); item classes `focus:bg-accent focus:text-accent-foreground` (`packages/ui` dropdown-menu) | **illegible** | Dark `--accent` → a dark-surface tint (e.g. teal-950 / `bg-muted`) with `--accent-foreground` light, ≥ 4.5:1 | DD-2 |
| **DD-3** (= **K-57**, incl. **F-668A-A-2**) | 14 matrix pages: `/my-work` (agenda labels "Today's diary", "Time Today", "Next Deadline", "Upcoming · next 14 days", "Open diary"); all 7 fee-note details + the custom-fields tile ("No documents generated yet" 4.24:1, audit "1 hour ago" 4.10/4.24:1); `/settings/firm?tab=audit-trail` ("766 total", entity short ids); `/settings/connections?tab=ai` (currency prefix "R"); `/settings/matters/templates/<id>` "(optional)"; task Sheet Time entries + Comments tiles "No sub-items yet" 4.24:1. Re-verification: matter Overview struck-through setup steps; `/matters/new` "(optional)" | `text-slate-500` with no dark pair (or `dark:text-slate-500`): `#6b7482` on `#14171c` **3.80:1**, on `#0c0e11` 4.10:1, on `#07080b` 4.24:1 | `components/dashboard/todays-agenda.tsx` (labels), `components/templates/GeneratedDocumentsList.tsx:275`, `components/audit/audit-timeline.tsx:233`, `app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:944`, `components/tasks/task-sub-items.tsx:147`, `components/projects/kpi-dashboard.tsx:310`, `app/(app)/org/[slug]/matters/new/new-matter-page-client.tsx:211,224` (all pre-existing; `git blame` 2026-02 … 2026-10) | **degraded** | `text-slate-500` → `text-muted-foreground` (as K-57 proposes) | DD-3a, DD-3b, DD-3c |
| **DD-4** (= **K-58**) | `/matters/<id>?tab=team` (re-verification page) | Member avatars keep light pastel discs in dark: `#dbfce7` and `#ffe4e6` 32 px on `#07080b`. Initials "AO" `#008236` on `#dbfce7` **4.50:1** at two decimals, just under 4.5 unrounded (flagged by the audit; register value 4.496) | `components/projects/project-members-panel.tsx:39-45` (`AVATAR_COLORS`) | **degraded** | Dark pairs in `AVATAR_COLORS` (e.g. `dark:bg-green-950 dark:text-green-300`) or reuse the `/team` avatar | DD-4 |
| **DD-5** (= **F-665C-14 / F-665D-12**) | `/settings/firm?tab=data-protection` (processing register, 7 rows), `/settings/firm?tab=compliance` (retention table, 5 rows). Roles `role-card.tsx:52`: **not observable** (no custom role on stack A) | Ghost destructive Delete icons render **grey** `#989faa` on `#14171c` (6.74:1, legible, hue sat 0.10). The destructive colour cue is lost in dark: the ghost variant's `dark:text-slate-400` outranks `text-destructive`. The consumer workaround copies (Matter templates, Tags, Tax `DESTRUCTIVE_GHOST`) render red `#ff6467` 6.22:1 (DD-5c, for comparison) | `components/data-protection/processing-register-table.tsx:384`, `components/compliance/RetentionPolicyTable.tsx:301`, `components/roles/role-card.tsx:52`; kit `packages/ui/src/button.tsx:22` (ghost dark override) | **degraded** (semantic colour lost, not contrast) | A kit `ghost-destructive` variant (or `dark:text-destructive` honoured by the ghost variant); then remove the three workaround copies (D-55) | DD-5a, DD-5b, DD-5c |
| **DD-6** (= **K-13**) | Every page with a kit `Card` (shared `@b2mash/ui`) | Kit Card border `#1b1e24` on the page `#07080b` **1.20:1** (card fill vs page 1.11:1). My Work's own card 1.25:1. Badge status pairs: **no badge text below AA** on any of the 102 dark loads. Skeleton: not observable (pages measured after load). The `border-input` 1.25:1 field baseline (Method) belongs here too | `packages/ui/src/card.tsx:10`, `badge.tsx:13-29`, `skeleton.tsx:7` (unchanged in Phase 91) | **degraded** (LOW; decorative border) | Token pairs for Card border / Badge / Skeleton in `packages/ui` (PORTAL-GATE), as K-13 proposes | DD-6 |
| **DD-7** (= **F-670A-4**, new) | `/settings/fees?tab=tariffs` (Default Currency selector), `/settings/firm?tab=audit-trail` (Event type filter) | Hand-rolled combobox triggers `border-slate-200 bg-white … dark:border-slate-800 dark:bg-slate-950` inside a `SettingsSection` card: border `#20242b` **1.15:1** and fill **1.11:1** against the `#14171c` card. Below the Phase 90 1.25 baseline; the label text itself is 7.51:1 | `components/rates/currency-selector.tsx:80`; `app/(app)/org/[slug]/settings/firm/_tabs/audit-log-client.tsx:354` | **degraded** | Kit field tokens: `border-input bg-transparent dark:bg-input/30` (as `SelectTrigger`) | DD-7, DD-7b |
| **DD-8** (= **F-670A-3**, new) | `/legal/adverse-parties` row menu | Unfocused "Delete" item `#e7000b` on the dark popover `#1b1e24`, **3.50:1**. Its `className="text-red-600 focus:text-red-600"` has no dark pair; the client / matter overflow menus have `dark:text-red-400` | `app/(app)/org/[slug]/legal/adverse-parties/adverse-party-registry-client.tsx:107` (moved verbatim by 669A; pre-existing) | **degraded** | `DropdownMenuItem variant="destructive"` (the K-25 fix pattern from 665C) | DD-2 crop (the "Delete" row) |

## Carried items and Phase 90 pages — re-verification (measured)

| Item | Result on stack A, Alice 1440 dark | Status |
|---|---|---|
| **K-13** (kit Card / Badge / Skeleton) | Card border 1.20:1 (decorative), Badge pairs ≥ AA everywhere walked, Skeleton not observable | **carried** → DD-6 (not fixed; `packages/ui` card/badge/skeleton untouched in Phase 91) |
| **K-21** (dark accent) | Accent fills 3.16:1 (DD-1); focused menu item 2.25:1 (DD-2) | **carried** → DD-1, DD-2 (not fixed) |
| **K-31 dark / F-668A-T3** (bell badge) | 3.16:1 on every page | still open, folded into DD-1 |
| **K-57** (`text-slate-500`, 3.80:1) | 3.80 / 4.10 / 4.24:1 on 14 matrix pages + 2 re-verification pages | still open → DD-3 |
| **K-58** (matter Team pastel avatars) | `#dbfce7` / `#ffe4e6` discs on `#07080b`; "AO" 4.50:1 (< 4.5 unrounded) | still open → DD-4 |
| **F-665C-14 / F-665D-12** (ghost destructive grey in dark) | Data protection + Compliance Delete icons `#989faa` (grey); workaround copies red `#ff6467` | still open → DD-5 (roles site not observable: no custom role) |
| **F-666A-9** (dark wizard step badge / "New fee note" 3.16:1) | Step "1" 3.16:1 on fee-run new and reconciliation new; "New fee note" 3.16:1 | still open, folded into DD-1 |
| **F-668A-A-2** (Today's agenda labels 3.80:1) | "Today's diary", "Time Today", "Next Deadline", "Upcoming · next 14 days", "Open diary" 3.80:1 on `/my-work` | still open, folded into DD-3 |
| Phase 90 **D-1** (`/my-work` black-on-black time today / weekly total) | "Time Today 13h / 8h" figure `#ffffff` on `#14171c` 17.96:1; no text < 3.80 on `/my-work` | **still fixed** (#1782) |
| Phase 90 **D-2** (`/my-work` light slabs) | 0 light surfaces on `/my-work` | **still fixed** |
| Phase 90 **D-3** (native selects on 7 pages) | `/matters/new`, `/conflict-check`, `/legal/disbursements`, `/legal/adverse-parties`, `/settings/documents/templates/new`, `/settings/matters/checklists/new`, `/settings/matters/templates/<id>`: no field below the 1.25 baseline (the templates detail page's 12 hits are the 1.25 `border-input` baseline) | **still fixed** (#1785) |
| Phase 90 **D-4** (`dark:text-slate-600` 2.62:1 on 4 pages) | Checklists tab, Rates tab, Firm › General, disbursement detail: no text < 3.80 on any of them; Rates' only hit is DD-7 (a field, not text) | **still fixed** (#1785) |
| Phase 90 **D-5** (Xero button 2.38:1) | The stack's Xero card shows "Configure Xero" (outline, 20.03:1), not "Connect to Xero". The code carries the fix (`XeroConnectionCard.tsx:144` `bg-[#13B5EA] text-slate-950`); the button state itself was not rendered on stack A | **fix present in code; button not observable on this data** |

## FD-8 statement (for closure C.5)

> **FD-8: not passed — dark mode stays available but unadvertised.**
>
> The 670.6 dark walk (Alice, 1440×900, dark, 99 in-scope `/org/` surfaces, 2026-11-27, stack A at `c4595f924`) did not pass for every in-scope surface. **All 99 pages fail** on the shell header notification badge (white on the dark accent teal `#00a38f`, 3.16:1; DD-1 = K-21 / K-31). That badge is present on every page. On the page bodies, **23 of 99 pages fail**. `/legal/adverse-parties` is **illegible** because its focused menu item is white on `#00c2b1`, 2.25:1 (DD-2 = K-21, and the same token applies to every row menu). The other 22 are **degraded**:
>
> - DD-1 accent buttons and wizard steps: `/invoices`, 5 fee-note details + the custom-fields tile, `/invoices/billing-runs/new`, `/trust-accounting/reconciliation/new`, `/settings/matters?tab=take-on`, `/settings/fees?tab=trust-accounting`.
> - DD-3 `text-slate-500` 3.80–4.24:1: `/my-work`, all 7 fee-note details, `/settings/firm?tab=audit-trail`, `/settings/connections?tab=ai`, `/settings/matters/templates/<id>`, the task Sheet.
> - DD-5 grey ghost-destructive icons: `/settings/firm?tab=data-protection`, `/settings/firm?tab=compliance`.
> - DD-7 faint combobox fields 1.15:1: `/settings/fees?tab=tariffs`, `/settings/firm?tab=audit-trail`.
>
> No page has a white slab, black-on-black text or text below 3:1 in its loaded state, and Phase 90's D-1…D-5 remain fixed.
>
> Evidence:
> - `qa_cycle/evidence/phase91-ui/exit/darkmode.md` (this file, walk index below)
> - `qa_cycle/evidence/phase91-ui/exit/darkwalk.json` (per-page audit, menu probe, re-verification numbers)
> - `qa_cycle/evidence/phase91-ui/exit/darkmode-crops.png` (measured crops)
> - the Alice 1440-D tiles of the sheets listed below.
>
> The minimum fix set that would flip the walk is DD-1 + DD-2 (the two K-21 accent tokens: one change in the dark token block) plus DD-3 (K-57 `text-slate-500` → `text-muted-foreground` at the listed sites), DD-5 (kit ghost-destructive), DD-7 and DD-8. DD-4 (K-58) and DD-6 (K-13) are on non-matrix or decorative elements.

## Walk index (every page; Alice, 1440×900, dark)

Columns:

- **Text / below AA (min):** visible text owners measured, and how many fall below their AA threshold (with the lowest ratio).
- **Discs / faint:** light discs, and fields below the 1.25 baseline.
- **Strict:** the rubric applied to everything, including the header badge.
- **Body:** the same rubric with the header badge set aside.
- **Sheet:** the matrix contact sheet that holds this page's Alice 1440-D tile. The three re-verification pages (matter Overview, matter Team, `/matters/new`; rows 99–101) have no matrix sheet and are marked "crop sheet".

| # | Route (as landed) | h1 | `html.dark` | Text | Below AA (min) | Discs / faint | Issues | Strict | Body | Sheet |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `/dashboard` | Home | true | 232 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](shell/dashboard-expanded.png) |
| 2 | `/dashboard` | Home | true | 207 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](shell/dashboard-collapsed.png) |
| 3 | `/settings/firm?tab=general` | General | true | 90 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/firm-general.png) |
| 4 | `/settings/firm?tab=roles` | Roles & Permissions | true | 55 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/firm-roles.png) |
| 5 | `/settings/firm?tab=audit-trail` | Audit log | true | 360 | 52 (min 3.16) | 0/1 | DD-1 header badge + **DD-3, DD-7** | **FAIL** — degraded | FAIL | [sheet](settings/firm-audit-trail.png) |
| 6 | `/settings/firm?tab=data-protection` | Data Protection | true | 116 | 1 (min 3.16) | 0/0 | DD-1 header badge + **DD-5** | **FAIL** — degraded | FAIL | [sheet](settings/firm-data-protection.png) |
| 7 | `/settings/firm?tab=data-requests` | DSAR Requests | true | 45 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/firm-data-requests.png) |
| 8 | `/settings/firm?tab=compliance` | Compliance | true | 78 | 1 (min 3.16) | 0/0 | DD-1 header badge + **DD-5** | **FAIL** — degraded | FAIL | [sheet](settings/firm-compliance.png) |
| 9 | `/settings/firm?tab=features` | Features | true | 36 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/firm-features.png) |
| 10 | `/settings/firm?tab=subscription` | Subscription | true | 35 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/firm-subscription.png) |
| 11 | `/settings/matters?tab=templates` | Matter Templates | true | 75 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-templates.png) |
| 12 | `/settings/matters?tab=naming` | Matter Naming | true | 44 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-naming.png) |
| 13 | `/settings/matters?tab=custom-fields` | Custom Fields | true | 251 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-custom-fields.png) |
| 14 | `/settings/matters?tab=tags` | Tags | true | 43 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-tags.png) |
| 15 | `/settings/matters?tab=checklists` | Checklists | true | 74 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-checklists.png) |
| 16 | `/settings/matters?tab=request-templates` | Request Templates | true | 53 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-request-templates.png) |
| 17 | `/settings/matters?tab=request-settings` | Request Settings | true | 37 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-request-settings.png) |
| 18 | `/settings/matters?tab=take-on` | Take-on | true | 73 | 5 (min 3.16) | 0/0 | DD-1 header badge + **DD-1** | **FAIL** — degraded | FAIL | [sheet](settings/matters-take-on.png) |
| 19 | `/settings/documents?tab=templates` | Templates | true | 223 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-templates.png) |
| 20 | `/settings/documents?tab=clauses` | Clause Library | true | 174 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-clauses.png) |
| 21 | `/settings/documents?tab=acceptance` | Acceptance | true | 29 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-acceptance.png) |
| 22 | `/settings/documents?tab=email` | Email | true | 38 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-email.png) |
| 23 | `/settings/fees?tab=tariffs` | Rates | true | 61 | 1 (min 3.16) | 0/1 | DD-1 header badge + **DD-7** | **FAIL** — degraded | FAIL | [sheet](settings/fees-tariffs.png) |
| 24 | `/settings/fees?tab=tax` | Tax | true | 58 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/fees-tax.png) |
| 25 | `/settings/fees?tab=time-recording` | Time recording | true | 48 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/fees-time-recording.png) |
| 26 | `/settings/fees?tab=fee-runs` | Fee runs | true | 41 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/fees-fee-runs.png) |
| 27 | `/settings/fees?tab=collections` | Collections | true | 37 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/fees-collections.png) |
| 28 | `/settings/fees?tab=trust-accounting` | Trust accounting | true | 69 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-1** | **FAIL** — degraded | FAIL | [sheet](settings/fees-trust-accounting.png) |
| 29 | `/settings/connections?tab=xero` | Integrations | true | 76 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-xero.png) |
| 30 | `/settings/connections?tab=mcp` | MCP | true | 33 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-mcp.png) |
| 31 | `/settings/connections?tab=ai` | Set Up AI | true | 88 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](settings/connections-ai.png) |
| 32 | `/settings/connections?tab=notifications` | Notifications | true | 85 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-notifications.png) |
| 33 | `/settings/connections?tab=packs` | Packs | true | 64 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-packs.png) |
| 34 | `/settings/connections?tab=automations` | Automations | true | 85 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-automations.png) |
| 35 | `/settings/connections/ai/history` | Execution History | true | 27 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-ai-history.png) |
| 36 | `/settings/connections/automations/executions` | Execution Log | true | 131 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-automations-executions.png) |
| 37 | `/settings/connections/xero/sync-log` | Sync Log | true | 30 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/connections-xero-sync-log.png) |
| 38 | `/settings/documents/templates/new` | New Template | true | 33 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-templates-new.png) |
| 39 | `/settings/documents/templates/d0e0ddea…/edit` | Matter Closure Letter | true | 78 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/documents-templates-edit.png) |
| 40 | `/settings/matters/checklists/new` | New Checklist Template | true | 38 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-checklists-new.png) |
| 41 | `/settings/matters/checklists/38030952…` | Generic Client Onboarding | true | 58 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-checklists-detail.png) |
| 42 | `/settings/matters/checklists/38030952…` | Generic Client Onboarding | true | 58 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-checklists-edit.png) |
| 43 | `/settings/matters/checklists/9c089f62…/edit` | Edit Checklist Template | true | 39 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-checklists-edit.png) |
| 44 | `/settings/matters/request-templates/new` | New Request Template | true | 31 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-request-templates-new.png) |
| 45 | `/settings/matters/request-templates/bca7f057…` | Liquidation and Distribution Account Pac | true | 64 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](settings/matters-request-templates-detail.png) |
| 46 | `/settings/matters/templates/2ef62c4b…` | Collections (Debt Recovery) | true | 86 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](settings/matters-templates-detail.png) |
| 47 | `/invoices` | Fee notes | true | 163 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-1** | **FAIL** — degraded | FAIL | [sheet](fees/invoices.png) |
| 48 | `/invoices/billing-runs` | Fee runs | true | 30 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](fees/billing-runs.png) |
| 49 | `/invoices/billing-runs/b20ef2cd…` | Fee run · 01 Nov 2026 – 30 Nov 2026 | true | 47 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](fees/billing-runs-detail.png) |
| 50 | `/invoices/billing-runs/new` | New fee run | true | 28 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-1** | **FAIL** — degraded | FAIL | [sheet](fees/billing-runs-new.png) |
| 51 | `/invoices/collections` | Debtors | true | 76 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](fees/collections.png) |
| 52 | `/invoices/unbilled` | Unbilled work | true | 37 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](fees/unbilled.png) |
| 53 | `/invoices/56a60a59…` | Draft fee note | true | 69 | 6 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-draft.png) |
| 54 | `/invoices/28770397…` | INV-0009 | true | 68 | 6 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-approved.png) |
| 55 | `/invoices/77476a6c…` | INV-0005 | true | 84 | 5 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-sent.png) |
| 56 | `/invoices/39452bc6…` | INV-0010 | true | 94 | 7 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-sent-overdue.png) |
| 57 | `/invoices/e2f1131b…` | INV-0002 | true | 128 | 8 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-partially-paid.png) |
| 58 | `/invoices/400c8a1d…` | INV-0006 | true | 120 | 7 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-paid.png) |
| 59 | `/invoices/17730378…` | INV-0008 | true | 80 | 5 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](feenote/feenote-void.png) |
| 60 | `/trust-accounting` | Trust Accounting | true | 97 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-home.png) |
| 61 | `/trust-accounting/transactions` | Transactions | true | 107 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-transactions.png) |
| 62 | `/trust-accounting/client-ledgers` | Client Ledgers | true | 51 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-client-ledgers.png) |
| 63 | `/trust-accounting/client-ledgers/e01fc5f2…` | Moroka Family Trust mwyxjsvmd96f | true | 73 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-client-ledger-detail.png) |
| 64 | `/trust-accounting/investments` | Investments | true | 57 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-investments.png) |
| 65 | `/trust-accounting/other-creditors` | Other trust creditors | true | 29 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-other-creditors.png) |
| 66 | `/trust-accounting/reconciliation` | Reconciliation | true | 15 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-reconciliation.png) |
| 67 | `/trust-accounting/reconciliation/new` | New Reconciliation | true | 28 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-1** | **FAIL** — degraded | FAIL | [sheet](trust/trust-reconciliation-new.png) |
| 68 | `/trust-accounting/reports` | Trust Reports | true | 38 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](trust/trust-reports.png) |
| 69 | `/matters/b758e5be…?tab=fields` | Ndlovu v RAF mwyxjsvmd96f | true | 62 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-fields.png) |
| 70 | `/clients/56b8af3c…?tab=fields` | Acme Corp | true | 43 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/client-fields.png) |
| 71 | `/invoices/56a60a59…` | Draft fee note | true | 69 | 6 (min 3.16) | 0/0 | DD-1 header badge + **DD-1, DD-3** | **FAIL** — degraded | FAIL | [sheet](sites/feenote-custom-fields.png) |
| 72 | `/matters/b758e5be…?tab=tasks&taskId=dc8acba9…` | Ndlovu v RAF mwyxjsvmd96f | true | 156 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](sites/task-sheet.png) |
| 73 | `/matters/b758e5be…?tab=tasks&taskId=dc8acba9…` | Ndlovu v RAF mwyxjsvmd96f | true | 161 | 2 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](sites/task-sheet.png) |
| 74 | `/dashboard` | Home | true | 207 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/dashboard-my-tasks.png) |
| 75 | `/matters/b758e5be…?tab=activity` | Ndlovu v RAF mwyxjsvmd96f | true | 86 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-activity.png) |
| 76 | `/matters/b758e5be…?tab=budget` | Ndlovu v RAF mwyxjsvmd96f | true | 49 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-budget.png) |
| 77 | `/matters/b758e5be…?tab=clients` | Ndlovu v RAF mwyxjsvmd96f | true | 55 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-clients.png) |
| 78 | `/clients/56b8af3c…?tab=rates` | Acme Corp | true | 37 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/client-rates.png) |
| 79 | `/matters/b758e5be…?tab=documents` | Ndlovu v RAF mwyxjsvmd96f | true | 58 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-documents.png) |
| 80 | `/matters/b758e5be…?tab=time` | Ndlovu v RAF mwyxjsvmd96f | true | 92 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sites/matter-time.png) |
| 81 | `/diary` | Diary | true | 27 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec8/diary.png) |
| 82 | `/my-work` | My Work | true | 161 | 7 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | [sheet](sec8/my-work.png) |
| 83 | `/my-work/timesheet` | Timesheet | true | 34 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec8/my-work-timesheet.png) |
| 84 | `/notifications` | Notifications | true | 33 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec8/notifications.png) |
| 85 | `/compliance` | FICA & compliance | true | 55 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/compliance.png) |
| 86 | `/compliance` (AI Audit tab) | FICA & compliance | true | 15 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/compliance.png) |
| 87 | `/compliance/requests` | Data Requests | true | 28 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/compliance-requests.png) |
| 88 | `/compliance/requests/e6826b64…` | DSAR Client qo0b | true | 26 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/compliance-request-detail.png) |
| 89 | `/conflict-check` | Conflict Check | true | 19 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/conflict-check.png) |
| 90 | `/legal/adverse-parties` | Adverse Parties | true | 34 | 1 (min 3.16) | 0/0 | DD-1 header badge + **DD-2, DD-8 (menu probe)** | **FAIL** — illegible (menu focus 2.25) | FAIL | [sheet](sec9/adverse-parties.png) |
| 91 | `/legal/disbursements` | Disbursements | true | 55 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/disbursements.png) |
| 92 | `/legal/disbursements/a7787ace…` | Deeds Office Fees | true | 37 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/disbursement-detail.png) |
| 93 | `/legal/tariffs` | Tariff Schedules | true | 15 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/tariffs.png) |
| 94 | `/information-requests/53f5d5cc…` | REQ-0001 | true | 32 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/information-request-detail.png) |
| 95 | `/team` | Team | true | 33 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/team.png) |
| 96 | `/team` (Pending Invitations tab) | Team | true | 20 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/team.png) |
| 97 | `/ai/reviews` | AI Reviews | true | 15 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/ai-reviews.png) |
| 98 | `/ai/reviews` (History tab) | AI Reviews | true | 15 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](sec9/ai-reviews.png) |
| 99 | `/matters/b758e5be…?tab=overview` | Ndlovu v RAF mwyxjsvmd96f | true | 75 | 5 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | crop sheet |
| 100 | `/matters/b758e5be…?tab=team` | Ndlovu v RAF mwyxjsvmd96f | true | 64 | 2 (min 3.16) | 2/0 | DD-1 header badge + **DD-4** | **FAIL** — degraded | FAIL | crop sheet |
| 101 | `/matters/new` | New Matter | true | 27 | 5 (min 3.16) | 0/0 | DD-1 header badge + **DD-3** | **FAIL** — degraded | FAIL | crop sheet |
| 102 | `/dashboard` (rail collapsed) | Home | true | 207 | 1 (min 3.16) | 0/0 | DD-1 header badge | **FAIL** — degraded | PASS | [sheet](shell/dashboard-collapsed.png) |
