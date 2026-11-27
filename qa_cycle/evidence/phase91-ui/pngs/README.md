# 670A: docs and marketing images, stale list (task 670.8, F-7, K-46)

**Decision (orchestrator ruling, D-102): list, do not re-shoot, in 670A.** This follows the Phase 90 precedent (ruling R2, [`../../phase90-ui/pngs/README.md`](../../phase90-ui/pngs/README.md)). Re-shooting any `frontend/public/screenshots/*.png` makes the exit PR a frontend PR: the merge hook would then need a frontend verify marker, FE-GATE on the PR head and the CI Frontend job. The replacement is a separate frontend PR, follow-up **FU-33** (closure C.6 (b)), which also closes register row K-46.

**Tracked set** (`git ls-files documentation/screenshots frontend/public/screenshots`, README/.gitkeep excluded): **12 PNG + 1 WebM** at the `documentation/screenshots/` root and **12 PNG** under `frontend/public/screenshots/` = 25 files, the same 25 as Phase 90. None changed since: `git log --oneline 551778100..HEAD -- documentation/screenshots frontend/public/screenshots` prints nothing; last touched `bcfb0f539` (2026-06-11, docs) and `e4e4e0d1f` (2026-05-23, marketing), both before Phase 86.

**How classified:** every PNG was opened with the Read tool on 2026-11-27 (a 700 px-wide `sips` copy in `/tmp/670A-D/png/`, not committed). The WebM was classified by its name only, as in Phase 90.

**What Phase 91 changed about them:** nothing in the images; what changed is the product. FD-1 (A1, ADR-392) made the firm shell **light** (light rail, inset canvas, slate-900 primary buttons). Every image below shows the **dark** pre-Phase-86 rail, so each is now stale twice: the Phase 86–90 shell changes Phase 90 already listed, plus the light scheme. The marketing site itself stays on its own light theme (FD-2); only the product screenshots inside it are affected.

## Marketing images (`frontend/public/screenshots/`, 1440×900)

All 12 show the same pre-Phase-86 frame: a dark slate rail with the zones Work / Matters / Clients / Finance / Team / AI, the removed "Calendar", "Court Calendar" and "Recurring Schedules" items, the old header with breadcrumbs and avatar, and the Next.js dev "N" badge bottom-left. **All 12 are stale.** "Phase 91 surface" below means the page body itself was also changed by a Phase 91 slice.

| File | Referenced by (code) | Shows (observed) | Phase 91 surface? | Why stale | Replacement plan (FU-33) |
|---|---|---|---|---|---|
| `dashboard.png` | **`components/marketing/hero-section.tsx:78`** (landing hero) | Old "Dashboard": period tabs, 5 KPI cards (AVG. MARGIN "--", BUDGET HEALTH), Matter Health list, Team Time donut, Sensitive events | yes (shell; Home h1 "Home", 663C density) | dark rail; pre-Phase-90 Home | Re-shoot `/dashboard` at 1440 light on the A1 shell (current state: [`../exit/shell/dashboard-expanded.png`](../exit/shell/dashboard-expanded.png), Alice 1440-L tile) |
| `matter-detail.png` | **`marketing/features-section.tsx:25`** | Old matter Overview: no DetailHero tiles, tabs "Details · Overview · Work · Finance · Client · Schedule · Activity" | yes (shell; 663A/663B matter tabs) | dark rail; pre-641B hero | Re-shoot a seeded matter Overview at 1440 light (tab sheets: [`../exit/sites/matter-fields.png`](../exit/sites/matter-fields.png), [`../exit/sites/matter-activity.png`](../exit/sites/matter-activity.png)) |
| `invoices.png` | **`marketing/features-section.tsx:40`** | "Fee Notes" list: 3 summary cards, filter chips, raw table, "Billing Runs" tab | **yes** (666A: DataTable list, status filter, "Fee runs") | dark rail; 666A rebuilt the body and renamed "Billing Runs" (K-77) | Re-shoot `/invoices` at 1440 light ([`../exit/fees/invoices.png`](../exit/fees/invoices.png)) |
| `trust-accounting.png` | **`marketing/features-section.tsx:55`** | Old trust dashboard: 4 cards, raw Recent Transactions table | yes (shell; 667A/667B trust sub-pages) | dark rail; pre-643A dashboard | Re-shoot `/trust-accounting` at 1440 light ([`../exit/trust/trust-home.png`](../exit/trust/trust-home.png)); K-94's approved wording (FU-31) should land first or the image goes stale again |
| `compliance.png` | **`marketing/features-section.tsx:70`** | "Compliance": lifecycle distribution cards, Onboarding Pipeline, Data Requests, Dormancy Check | **yes** (669A: h1 "FICA & compliance" K-84, PageHeader) | dark rail; h1 renamed in 669A | Re-shoot `/compliance` at 1440 light ([`../exit/sec9/compliance.png`](../exit/sec9/compliance.png)) |
| `clients.png` | not referenced by code | Clients list with lifecycle filter chips (raw table) | shell only | dark rail; 642A DataTable | Unreferenced: delete, or re-shoot in the same PR (founder's call, as in Phase 90) |
| `matters-list.png` | not referenced by code | Matters list as **cards** | shell only | dark rail; 641A DataTable | Same |
| `profitability.png` | not referenced by code | **Not the profitability report:** matter "Finance · Financials" tab, empty "No financial data yet" | no (name misleading) | dark rail | Delete, or rename and re-shoot the profitability report |
| `budget.png` | not referenced by code | Matter "Finance · Fee Estimate" tab, empty "No budget configured" | yes (663B matter Budget empty) | dark rail | Unreferenced: delete or keep stale ([`../exit/sites/matter-budget.png`](../exit/sites/matter-budget.png) is the current state) |
| `court-calendar.png` | not referenced by code | Old "Court Calendar" page (merged into Diary in Phase 87) | no (route gone) | dark rail **and** the page no longer exists | Delete |
| `team.png` | not referenced by code | Team page with the invite card, Members / Pending Invitations | **yes** (669B: PageHeader, shared member table) | dark rail; 669B restyled the page | Unreferenced: delete or re-shoot ([`../exit/sec9/team.png`](../exit/sec9/team.png)) |
| `time-tracking.png` | not referenced by code | Matter "Finance · Time" tab, empty "No time tracked yet" | yes (663B matter Time empty) | dark rail | Unreferenced: delete or keep stale ([`../exit/sites/matter-time.png`](../exit/sites/matter-time.png)) |

**Recommended follow-up PR (FU-33, frontend, outside Phase 91):**
- Re-shoot the **5 code-referenced** images (`dashboard`, `matter-detail`, `invoices`, `trust-accounting`, `compliance`) at 1440×900 **light** on a seeded tenant, without the Next dev badge (production build or the e2e stack's prod frontend).
- Delete or keep the 7 unreferenced ones per the founder's call; `court-calendar.png` should go (route removed).
- Gates: FE-GATE + frontend verify marker + Frontend CI; observe marketing `/` at 1440 light after the swap.
- Order: after FU-31 (K-94 trust landing wording) if the trust image is to stay current.

## Documentation images (`documentation/screenshots/`)

These are **dated QA evidence**, referenced from checkpoint results and archived cycle reports (`git grep -l <name>`). They record what a past cycle saw; re-shooting them would falsify those records. They are **historical, not stale product documentation**, so no re-shoot is planned; the replacement for "what it looks like now" is the 670A exit evidence linked below. This matches Phase 90's classification, re-confirmed by opening each file.

| File | Dims | Shows (observed 2026-11-27) | Status | Current equivalent |
|---|---|---|---|---|
| `day-00-firm-dashboard-legal.png` | 1728×945 | Empty-firm "Dashboard" with the "Getting started" banner; dark Phase-86-era rail | Historical (stale vs Phase 90 + 91) | [`../exit/shell/dashboard-expanded.png`](../exit/shell/dashboard-expanded.png) |
| `day-34-profitability-dashboard.png` | 1728×1140 | **Accounting vertical** profitability ("Thornton & Associates", Engagement Profitability) | Historical; vertical removed in Phase 86 | — |
| `day-60-matter-closed.png` | 1728×945 | Closed matter Overview (no hero), dark rail | Historical | [`../exit/sites/matter-fields.png`](../exit/sites/matter-fields.png) (matter page) |
| `day-60-closure-gates-all-green.png` | 1728×945 | Close-matter dialog, all gates green, over a dimmed dark-rail page | Historical | — |
| `day-60-gate-report-initial.png` | 1728×945 | Close-matter dialog with 4 failing gates ("Fix this" links) | Historical (unreferenced) | — |
| `day-04-portal-home-first-login.png` | 1728×945 | Portal Home, old portal nav incl. "Proposals" | Historical; the portal does not follow the scheme (FD-2) | [`../suites/portal/portal-home-1024-light.png`](../suites/portal/portal-home-1024-light.png) |
| `day-04-fica-submitted.png` | 1728×945 | Portal request REQ-0001, 3 items submitted | Historical; not a Phase 91 surface | — |
| `day-08-proposal-accepted.png` | 1728×1042 | Portal engagement letter, ACCEPTED | Obsolete: proposals removed in Phase 86 | — |
| `day-08-proposal-review.png` | 1728×1054 | Portal engagement letter, SENT, Accept / Decline | Obsolete (Phase 86) | — |
| `bug-reg-001-rates-page.png` | 1200×953 | "DocTeams" e2e shell, "Something went wrong" on "Rates & Currency" | Historical bug evidence (the h1 is now "Rates", K-124) | [`../exit/settings/fees-tariffs.png`](../exit/settings/fees-tariffs.png) |
| `schedule-dialog-open.png` | 1507×901 | "New Recurring Schedule" dialog | Obsolete: schedules removed in Phase 86 | — |
| `schedules-fixed.png` | 1507×901 | Recurring Schedules list, empty | Obsolete (Phase 86) | — |
| `demo-legal-za-walkthrough.webm` | — | Legal-ZA demo walkthrough video (not opened) | Stale by name/date (pre-Phase 86 recording) | Re-record only if the founder wants a current demo video (not planned) |

No `documentation/screenshots/` file was re-shot (re-shoots there are allowed without a marker, but only on the orchestrator's request; none was made).
