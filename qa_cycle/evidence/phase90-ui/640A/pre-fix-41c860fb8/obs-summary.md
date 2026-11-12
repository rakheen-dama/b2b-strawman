# 640A after-OBS: Home KPI row + audience regrid (PR #1767, head 41c860fb8)

Stack: :3001 mock-auth e2e, built from 41c860fb8 and seeded by E2E-LEGAL (legal-lifecycle + legal-depth-ii). Captured 2026-11-12 10:21–10:22Z with the Playwright library (`obs.cjs`). Raw data is in `obs-report.json`. The supplementary synthetic diary probe is `diary-probe.cjs` / `diary-probe-report.json`.

## Matrix (16 captures; every capture had 0 console errors/warnings, 0 hydration messages, no horizontal overflow, 0 browser `dashboard/kpis` requests, one h1 "Home")

| Persona | Audience | Cells (order) | Count (before) | KPI tiles | KPI grid @1024 / @1440 |
|---|---|---|---|---|---|
| Alice (owner) | owner | kpis, diary, unbilledWip, debtors, trustPosition, ficaOutstanding, matterHealth, sensitive, activity | 9 (8) | Active matters 10 · Hours logged 15,8h + sparkline · Billable % 100,0% · Overdue tasks 0; no deltas | 2+2 (2×352px) / 4×272px |
| Bob (admin) | firm | kpis, diary, unbilledWip, debtors, trustPosition, ficaOutstanding, sensitive, activity | 8 (7) | same as Alice | 2+2 / 4 |
| Carol + FV custom role | firm | kpis, diary, unbilledWip, activity | 4 (3) | same, except Billable % = "—" (sr "Not available") + hint "Visible to admins and owners", state=redacted, no delta | 2+2 / 4 |
| Carol (member) | feeEarner | diary, tasks, unbilledWip, matterHealth, activity (no kpis) | 5 (5) | — (no cell) | — |

The light and dark captures are identical in structure, and every value matches across the theme × width variants.

Rows: owner `[kpis][diary][unbilledWip,debtors,trustPosition (span-4)][fica,matterHealth,sensitive (span-4)][activity]`; firm `[kpis][diary][unbilledWip,debtors,trust (span-4)][fica,sensitive (span-6)][activity]`; Carol-FV `[kpis][diary][unbilledWip][activity]`, all span-12. Carol-FV goes from 3 cells to 4: the only change is the new kpis cell. Her role holds only FINANCIAL_VISIBILITY (no VIEW_TRUST), so the trust, debtors and FICA widgets stay gated exactly as they were before.

**Deltas:** in the seed data no delta row rendered on any tile. The previous 30-day window is empty: the seed was created today and the sparkline shows weeks 1–5 = 0h. So `computeDelta` returns null and no delta appears. Neither the up nor the down direction could be observed. Both are covered only by vitest.

**Server-side KPI request count:** I could not observe it. `docker compose ... logs` for the OBS window (10:21–10:23Z) has 0 frontend lines and only scheduler INFO lines from the backend (`grep -ci kpi` = 0). The backend has no access log, and `/actuator/metrics` is not exposed (404). The evidence is indirect: tiles render real values for firm/owner/Carol-FV, the member gets no `home-cell-kpis`, and the browser made 0 kpis requests.

## Sparkline a11y: PASS
The CDP AX tree has role `image`, name "Hours logged per week, last 6 weeks", description "First week 0,0h, latest week 15,8h, peak 15,8h in week 6 of 6.", and it is not ignored. No aria-hidden ancestor was found. The `ariaSnapshot()` of the kpis cell lists `img "Hours logged per week, last 6 weeks"` inside `group "Hours logged"`, followed by the sr summary text. Its description element is `.sr-only`.

## Diary two-column: not observable on seed data
Every persona sees "Nothing in the diary today" (0 rows). The CardContent is `container-type: inline-size`, with a content box of 1086px at 1440 (≥ 56rem = 896px, so the two-column rule is eligible) and 670px at 1024 (one column). I ran a **synthetic** DOM probe (`diary-probe.cjs`) that injects placeholder `<li>`s client-side only:
- At 1440: `column-count: 2`, 32px gap. One row sits alone in column 1 with the right half empty. Two rows sit side by side, row 1 left and row 2 right, at the same y. Five rows split 3 + 2.
- At 1024: `auto`, one column.

So the reviewer's concern holds: with 1–2 rows, the items are split across a ~1086px card, as `diary-synthetic-2rows-1440.png` shows. This is not real DiaryEventRow markup.

## Visual defects seen in the screenshots
1. **Owner @1024, row 4 at span-4 (about 230px per card), a regression from before (span-6).** FICA outstanding drops every client name (only the badges and "0 days" remain). Matter Health drops the matter-name column: the "Matter" and "Status" headers overprint, the TASKS header is clipped at the card edge, and the "Over estimate" tab touches the edge. The Sensitive events title is truncated to "M…". Compare `before/home-alice-1024-light.png`, where the names are visible.
2. **Owner @1024, row 3 (unbilledWip at span-4), a regression for the owner.** In the Unbilled WIP member row, the name "Alice Owner" is missing and "8h 30m" overprints "Oldest from 12 Nov 2026". "Disbursements" collides with "R 1 400,00". Bob @1024 shows the same thing. Bob already had these three widgets at span-4 before 640A, so for Bob it is probably pre-existing, but before.cjs captured Bob only at 1440, so this is unverified.
3. **Owner @1440, Matter Health at span-4.** Matter names are cut to 3–5 characters ("Khu…", "Tea…", "QC …"), where before (span-6) they were readable. FICA names are more truncated too.
4. Minor: at 1440 the three non-sparkline KPI tiles stretch to the hours tile's height (h-full), which leaves about 50px of empty space at the bottom of each. At 1024 the second row is shorter (98px) than the first (150px).
5. Dark mode: no contrast problems in the KPI row. The teal sparkline, the labels and the "—" hint all read clearly. The Matter Health progress track is very dark red on dark, but that predates this slice.

There is no page-level horizontal overflow anywhere. The defects are clipping and overprint inside cards.

## Carol role flow + restore: PASS
- The custom role did not exist on the fresh stack. I created it via Settings → Firm → Roles → New Role (Financial Visibility only).
- I assigned it via the Team sheet. The badge and the sheet both showed the role, with caps [Financial Visibility].
- I captured the four Carol-FV views, then restored Carol to Member via the sheet. After the restore the badge was "Member" and stayed "Member" after a reload. The sheet showed role Member with caps [View Trust, AI Execute], matching the originals, and no overrides.
- A post-restore Home check at 1440 light gave feeEarner with 5 cells, the same as the member captures.
- The custom role itself was left in place, as in the before run.

## Primitive observation (639A, 639D)
- **PageHeader (639A):** exactly one h1 "Home" (`data-slot=page-header-title`) inside one `[data-testid=page-header]` in `#main-content`, with the description "What needs you today". The box is x280/y88, 720×56 @1024 and 1136×56 @1440. The title and description stack with no actions row. In light the h1 is near-black and the description muted; in dark the h1 is white and the description light grey, both legible.
- **StatCard ready:** observed (four tiles, firm/owner). **Redacted:** observed for Carol-FV on Billable %: data-state=redacted, "—" plus sr "Not available", the hint "Visible to admins and owners", and no delta. The ARIA group reads "Billable % Not available Visible to admins and owners".
- **StatCard delta up/down:** not observable (no previous-period data in the seed). Covered only by vitest.
- **Compact variant:** not on Home. I will observe it with the 641B hero later.
- **SparklineTrend (639D):** reads without hover (a flat 0h line for weeks 1–5 rising to 15,8h in week 6, teal stroke, light area fill). The sr summary is in the accessibility tree as its description (see above).
- **StatCard loading:** handled by 640C. **Error:** covered only by vitest (OQ #27). I did not observe either here.
