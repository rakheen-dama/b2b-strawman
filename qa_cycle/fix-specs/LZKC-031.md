# Fix Spec: LZKC-031 — Terminology residual cluster (copy-audit, NOT one mechanism)

## Problem
Day 90 / 90.1 + 90.10 deep innerText scans found residual Project/Customer/Invoice copy on legal-za surfaces beyond the LZKC-009/021 fixed sites, plus portal `/profile` "Role: General Customer". All Low, cosmetic, but they undercut the vertical-terminology story the rest of the app now tells.

## Root Cause (confirmed) — mechanism assessment
The terminology system exists and works: `packages/shared/src/terminology-map.ts` (legal-za maps Project→Matter, Customer→Client, Invoice→Fee Note, lines 60-110), consumed via `useTerminology()` (`frontend/lib/terminology.tsx:48`), `TerminologyText` (`frontend/components/terminology-text.tsx:23`), `TerminologyHeading` (`frontend/components/terminology-heading.tsx`).

**This is NOT one mechanism.** The sites split into four distinct classes — each verified in source:

**Class A — frontend hardcoded copy bypassing `t()`** (the dominant class, same bug-class as LZKC-021; per-site one-word edits routed through the existing hook):
- My Work "PROJECT" table headers: `frontend/components/my-work/assigned-task-list.tsx:87`, `frontend/components/my-work/available-task-list.tsx:79` (also `urgency-task-list.tsx:124` — not in QA's list but same defect, include).
- Compliance: `frontend/components/compliance/OnboardingPipelineSection.tsx:51` ("No customers currently in onboarding"), `frontend/components/compliance/DormancyCheckSection.tsx:44` ("Check for Dormant Customers").
- Billing Runs: `frontend/components/billing-runs/billing-run-summary-cards.tsx:29-30` ("Customers"/"Invoices Generated" stat cards), wizard step label `frontend/components/billing-runs/billing-run-wizard.tsx:14` ("Select Customers"), `send-step.tsx:128,181,199` — dev to locate the exact "CUSTOMERS"/"INVOICES" list-table headers QA saw on `/invoices/billing-runs` with the same scan.
- Settings > General: `frontend/components/settings/org-documents-section.tsx:61` ("shared across all projects") + the two "invoices" strings on that page (currency/label descriptions — locate by grep on the page's components).
- Schedules: `frontend/components/schedules/ScheduleList.tsx:134` ("automate project creation") ×2 sites.
- Profitability: `frontend/components/profitability/customer-profitability-section.tsx:163`, `frontend/components/profitability/customer-financials-tab.tsx:132` ("Customer Profitability") + page blurb.

**Class B — message catalog** — `frontend/lib/messages/en/empty-states.json:28` ("across all projects"), `:42` ("generate accurate invoices"). The catalog is static JSON; consumers must run values through `t()` at render, or the strings need terminology-neutral rewording.

**Class C — backend-seeded report definitions** — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/StandardReportPackSeeder.java:169` ("grouped by member, project…"), `:207` ("Invoice Aging Report"), `:285` ("per project"). These are seeded per-tenant rows — fixing the seeder only helps NEW tenants; existing tenants need either a reconciliation pass or display-layer substitution. Bigger than a copy edit.

**Class D — backend notification titles + portal role label**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java:583` "Invoice %s for %s has been paid" (+ the sibling invoice-sent title nearby) — stored notification text; the backend has no terminology map, so either generate profile-aware copy or reword neutrally ("Fee note" only for legal? No — use the stored-text-neutral fix: "INV-0001 for Sipho Dlamini has been paid" or route through vertical profile at creation).
- Portal `/profile`: `portal/app/(authenticated)/profile/page.tsx:36-38` `formatRole()` renders the PortalContact role enum (`GENERAL` → "General"; QA saw "General Customer") at `:136-138`. Fix: map role codes to client-facing labels (e.g. `GENERAL` → "Contact") instead of prettifying the enum.

## Fix (cluster — requires explicit orchestrator authorization per CLAUDE.md §7)
Recommended split — three PRs, each individually small:
1. **PR-1 (Class A + B, frontend copy)**: route each listed string through `useTerminology()`/`TerminologyText` (components that are already client components) or reword neutrally where a hook is unavailable (RSC contexts can use the profile-aware server path the other fixed pages use — mirror the LZKC-021 fix pattern). For empty-states.json, pass values through `t()` in the consumer (`frontend/lib/messages/index.ts` consumers) or reword ("across all your work", "generate accurate billing").
2. **PR-2 (Class D)**: notification title wording in `NotificationService.java:583` (+sibling) and portal `formatRole` label map. Note: these notification titles are STORED — existing rows keep old copy; only new notifications change (acceptable for Low severity, state in PR).
3. **PR-3 (Class C, report pack)**: seeder copy + decision on existing-tenant reconciliation (the `VerticalProfileReconciliationSeeder` pattern exists — `backend/.../verticals/VerticalProfileReconciliationSeeder.java`). This part is the only >S chunk; if the orchestrator wants to defer it (like LZKC-009 sites 3/4), PR-1+PR-2 still clear the bulk of QA's list.

## Scope
PR-1: Frontend only (~10 files). PR-2: Backend (1 file) + Portal (1 file). PR-3: Backend seeder + reconciliation. Migration needed: no (PR-3 reconciliation is a startup seeder, not Flyway).

## Verification
Re-run the Day-90 programmatic innerText scan (`\b(project|customer|invoice)\b`) across the 25 firm routes + portal `/profile` on a legal-za tenant; the enumerated sites must return zero hits (known-deferred LZKC-009 sites 3/4 excluded). Frontend gate: `pnpm lint && pnpm build && pnpm test` + prettier. PR-2/3: full backend verify.

## Estimated Effort
PR-1: M · PR-2: S · PR-3: M (or DEFER by authorization)

## Status requested
SPEC_READY, but **cluster execution needs orchestrator authorization** for (i) the 3-PR split vs one-per-site, and (ii) whether PR-3 (report pack) ships now or joins the LZKC-009-sites-3/4 deferred bucket.
