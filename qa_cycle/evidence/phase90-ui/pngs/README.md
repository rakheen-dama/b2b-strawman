# 644B: docs and marketing images, stale list (task 644.8, OQ #16)

**Decision (orchestrator ruling R2, 2026-11-13): list, do not re-shoot, in 644B.** Re-shooting any `frontend/public/screenshots/*.png` makes the PR a frontend PR. The merge hook would then require a frontend verify marker and FE-GATE, and CI would run the Frontend job. So the replacement is a **separate frontend PR**. Nothing under `frontend/public/` or `documentation/screenshots/` is modified here.

**Real count:** 644.8 and OQ #16 say "14 under `documentation/screenshots/`". The tracked set is in fact:
- **12 PNG + 1 WebM** at `documentation/screenshots/` root (`git ls-files documentation/screenshots`, excluding README/.gitkeep in subfolders);
- **12 PNG** under `frontend/public/screenshots/`.

That makes 25 files. Every PNG was opened with the Read tool (downscaled copies for the 21 not read at full size) before it was classified. The WebM was classified by its name only.

Last-touched commits: all `documentation/screenshots/*` in `bcfb0f539` (2026-06-11, a file move); all marketing PNGs in `8ee57c64e` / `e4e4e0d1f` (2026-05-23). **Every one predates Phase 86, 87 and 90.**

## Marketing images (`frontend/public/screenshots/`, 1440×900)

The marketing PNGs all show the **pre-Phase-86 shell**:
- the dark rail has the zones Work / Matters / Clients / Finance / Team / AI, with "Calendar", "Court Calendar" and "Recurring Schedules" (all removed or merged in Phases 86–87);
- the old 56px header has breadcrumbs and avatar;
- the Next.js dev "N" badge is bottom-left.

So **all 12 are stale** regardless of the page shown, because 638A/638B replaced the shell. "In-scope" below means the page body is a Phase 90 surface.

| File | Referenced by (code) | Shows (observed) | In-scope surface? | Why stale | Replacement plan |
|---|---|---|---|---|---|
| `dashboard.png` | **`components/marketing/hero-section.tsx:78`** (landing hero) | Old "Dashboard": period tabs, 5 KPI cards (AVG. MARGIN "--", BUDGET HEALTH), Matter Health, Team Time donut, Sensitive events | **yes**: Home | Old shell; Home was rebuilt in 640A–640C (KPI row, audience regrid, kit widgets); "Dashboard" is now "Home" | Follow-up frontend PR: production build on the KC stack, 1440×900 light, owner, `nextjs-portal` null (636C recipe). The observed reference is `../matrix/home-1440-light.png` |
| `matter-detail.png` | **`marketing/features-section.tsx:25`** | Old matter Overview (no hero tiles; tabs "Details · Overview · Work · Finance · Client · Schedule · Activity") | **yes**: matter detail | Old shell; 641B DetailHero + KPI strip; 642C tab bar | Same PR, from `../matrix/matter-detail-1440-light.png` |
| `trust-accounting.png` | **`marketing/features-section.tsx:55`** | Old trust dashboard: 4 cards, raw Recent Transactions table | **yes**: trust dashboard | Old shell; 643A PageHeader, StatCards, balance-trend chart, DataTable | Same PR, from `../matrix/trust-1440-light.png` |
| `invoices.png` | **`marketing/features-section.tsx:40`** | Fee Notes list (3 summary cards, raw table) | no (long tail) | Old shell only; the page body is unchanged by Phase 90 | Same PR (shell); or wait for Phase 91 §6 (fee notes onto the primitives) and shoot once |
| `compliance.png` | **`marketing/features-section.tsx:70`** | Compliance: lifecycle distribution cards | no (long tail) | Old shell only | Same PR (shell); Phase 91 §9 light pass changes the body again |
| `clients.png` | not referenced by code (QA docs only) | Old clients list with filter chips | yes: clients list | Old shell; 642A DataTable | Unreferenced asset: delete or re-shoot in the same PR |
| `matters-list.png` | not referenced by code | Old matters list as **cards** | yes: matters list | Old shell; 641A DataTable | Same |
| `profitability.png` | not referenced by code | **Not the profitability report:** a matter's Finance · Financials tab, empty state "No financial data yet" | no (name misleading) | Old shell | Delete as unreferenced, or rename and re-shoot from `../matrix/profitability-1440-light.png` |
| `budget.png` | not referenced by code | Matter Finance · Fee Estimate tab, empty "No budget configured" | no | Old shell | Unreferenced asset: delete or keep stale |
| `court-calendar.png` | not referenced by code | Old "Court Calendar" page (merged into Diary in Phase 87) | no (route gone) | Old shell **and** the page no longer exists | Delete |
| `team.png` | not referenced by code | Team page with invite card | no | Old shell | Unreferenced asset: delete or keep stale |
| `time-tracking.png` | not referenced by code | Matter Finance · Time tab, empty "No time tracked yet" | no | Old shell | Unreferenced asset: delete or keep stale |

**Recommended follow-up PR (frontend, outside Phase 90):**
- Re-shoot the 5 code-referenced images. They are the ones a visitor sees.
- Delete or keep the 7 unreferenced ones per the founder's call.
- Gates: FE-GATE + frontend verify marker + Frontend CI.
- Do it **after** Phase 91 settles the light-mode scheme (Phase 91 founder decision 2), or the images go stale again.

## Documentation images (`documentation/screenshots/`)

These are **dated QA evidence**, referenced from checkpoint-result files and archived cycle reports (`git grep -l <name>`). They are records of what a past cycle saw, not product documentation. Re-shooting would falsify those records, and `captureScreenshot(…, { curated: true })` cannot write here anyway: it writes only `documentation/screenshots/legal-vertical/` (untracked), from e2e specs. **Decision: list as historical; no replacement.** A new demo set belongs in the next QA cycle's own evidence folder.

| File | Dims | Referenced by | Shows (observed) | In-scope surface? | Status |
|---|---|---|---|---|---|
| `day-00-firm-dashboard-legal.png` | 1728×945 | `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`, `qa_cycle/checkpoint-results/day-00.md` + 4 archives | Empty-firm "Dashboard" with "Getting started" banner, old Phase-86-era rail | yes: Home | Historical (stale vs Phase 90). Current equivalent: `../matrix/home-1440-light.png` |
| `day-34-profitability-dashboard.png` | 1728×1140 | accounting-za archive only | **Accounting-vertical** profitability ("Thornton & Associates", Engagement Profitability) | yes: profitability | Historical; the accounting vertical was removed in Phase 86 |
| `day-60-matter-closed.png` | 1728×945 | `qa_cycle/checkpoint-results/day-60.md` | Closed matter Overview (no hero) | yes: matter detail | Historical. Current: `../matrix/matter-detail-1440-light.png` |
| `day-60-closure-gates-all-green.png` | 1728×945 | `_archive_2026-06-13…/day-60.md` | Close-matter dialog over the matter page | partly | Historical |
| `day-60-gate-report-initial.png` | 1728×945 | none | Close-matter dialog with failing gates | partly | Historical (unreferenced) |
| `day-04-portal-home-first-login.png` | 1728×945 | testplan + `checkpoint-results/day-04.md` | Portal Home (old portal nav incl. "Proposals") | portal (token retune only) | Historical. Current: `../matrix/portal-home-1024-light.png` |
| `day-04-fica-submitted.png` | 1728×945 | testplan + `checkpoint-results/day-04.md` | Portal request REQ-0001, 3 items submitted | no | Historical; not a Phase 90 surface |
| `day-08-proposal-accepted.png` | 1728×1042 | `checkpoint-results/day-08.md` + archives | Portal engagement letter, ACCEPTED | no | Obsolete: proposals removed in Phase 86 |
| `day-08-proposal-review.png` | 1728×1054 | same | Portal engagement letter, SENT, Accept/Decline | no | Obsolete (Phase 86) |
| `bug-reg-001-rates-page.png` | 1200×953 | `_archive_2026-04-06_e2e/…/regression-cycle2-verification.md` | "DocTeams" e2e shell, "Something went wrong" on Rates & Currency | no | Historical bug evidence |
| `schedule-dialog-open.png` | 1507×901 | none | "New Recurring Schedule" dialog | no | Obsolete: schedules removed in Phase 86 |
| `schedules-fixed.png` | 1507×901 | none | Recurring Schedules list, empty | no | Obsolete (Phase 86) |
| `demo-legal-za-walkthrough.webm` | video | none in code/docs (stats JSON only) | full walkthrough (by name; not played) | all | Stale (pre-Phase-86 product). Re-record only with a new demo script; not Phase 90 scope |

## OQ #16 final state

**Closed by 644B, "list" branch (ruling R2).**
- 25 tracked files classified: 12 marketing + 12 docs PNG + 1 WebM. The spec's "14" was a miscount.
- All 12 marketing PNGs are stale (old shell). The 5 code-referenced ones go to one follow-up frontend PR, preferably after Phase 91's colour-scheme decision.
- The 13 documentation files are historical QA evidence and are kept as records.
