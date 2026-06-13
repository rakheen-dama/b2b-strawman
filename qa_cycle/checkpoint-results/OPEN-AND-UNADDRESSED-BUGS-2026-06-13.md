# Observed Bugs Not Addressed — Legal-ZA Full-Lifecycle Regression (Cycle 2026-06-13)

Scope: the full 90-day legal-ZA lifecycle walk (Days 0→90, ALL_DAYS_COMPLETE) on the Keycloak dev stack.
This document lists everything **observed but not fixed** in this cycle, with the disposition and rationale for each.

**Fixed this cycle (NOT in this list, for reference):**
- **VERIFIED on main (QA re-walked E2E):** OBS-503, OBS-504, OBS-505 (incl. scheduled fan-out + tx-isolation cascade), OBS-3001.
- **FIXED + MERGED, awaiting E2E re-verify:** OBS-8801 (PR #1440 — `project_id` across 12 audit emit sites; integration-tested + CI green; the firm/portal activity-feed E2E re-verify is deferred because it needs fresh proposal/invoice/closure events).
- **No-regression (prior-cycle fixes re-checked):** OBS-2801/2801b/2802/2803.

---

## A. New genuine gaps left open (real bugs, deliberately not fixed)

### OBS-506 — AI specialist launcher buttons 404 (uppercase IDs)  ·  Severity LOW  ·  Status: DEFERRED (AI-infra exemption class)
- **What:** 5 interactive `SpecialistLauncherButton` call sites pass uppercase specialist IDs (`INTAKE`/`BILLING`/`INBOX`) → `startSession` → `POST /api/assistant/specialists/{ID}/sessions` → `specialistRegistry.requireById` **404**. Sites: `create-customer-dialog.tsx:303`, `invoice-detail-client.tsx:215`, `invoice-generation-dialog.tsx:233`, `projects/[id]/page.tsx:686`, `information-requests/[id]/page.tsx:56`.
- **Why not fixed:** real bug (wrong IDs — would 404 even in prod), but in Keycloak mode the *entire* `/api/assistant/*` path is unwired (the pre-existing **OBS-201** exemption), so fixing the IDs alone produces no observable improvement and can't be E2E-verified until the AI proxy is wired. Same "AI infrastructure not plumbed end-to-end in KC mode" class as OBS-201.
- **Recommended follow-up:** correct the 5 uppercase IDs to the registered `intake-za`/`billing-za`/`inbox-za` **together with** the OBS-201 assistant-proxy wiring, then verify the launcher buttons E2E. Trivial (same correction already shipped for the automation packs + dropdown in OBS-505).

### OBS-6002 — interactive buttons don't fire under Playwright real-pointer clicks  ·  Severity MEDIUM (candidate)  ·  Status: OPEN — pending quiescent-build repro (most likely TOOLING, not a product defect)
- **What:** trust **Approve** (`ApprovalBadge`) + **Close Matter** (`close-matter-btn`) on firm :3000, and later the portal login submit / Download / tab-switch buttons on :3002, did not fire their bound React `onClick` under a Playwright *real pointer* click (no backend call). Invoking the bound handler directly / `form.requestSubmit()` / URL searchParam always worked, and the backend always processed correctly.
- **Why not fixed:** strong evidence it is **Playwright-pointer + dev-HMR friction, not a Radix pointer-interception product bug**: it reproduced on *every* interactive element across *both* apps and *every* component type, and the handler always works when invoked directly. A genuine OBS-2103/OBS-1001-class interception would be component-specific. The frontend dev server was doing frequent Fast-Refresh rebuilds during the runs (a known cause of synthetic-pointer misses against a transiently-replaced DOM).
- **Recommended follow-up:** repro on a **quiescent / production frontend build** (`pnpm build` + `pnpm start`, no HMR). If clicks fire there → confirmed tooling/environmental, close as NOT_A_BUG. If it still reproduces on a built frontend → real, file SPEC_READY (OBS-2103 family). Did **not** block any checkpoint (all actions completed via the handler-invocation workaround).

---

## B. Carry-over exemptions (pre-existing WONT_FIX — observed again, intentionally not re-filed)

| ID | What | Disposition |
|----|------|-------------|
| **OBS-201** | `/api/assistant/invocations` (and all `/api/assistant/*`) 404 on firm pages in KC mode — AI client-side proxy not wired | WONT_FIX-EXEMPT (AI infra not plumbed end-to-end in KC; backend controllers exist). Parent class of OBS-506. |
| **OBS-6001** | No separate **Statement of Account** email after matter closure | WONT_FIX (by design): `PortalDocumentNotificationHandler` 5-min Caffeine dedup coalesces closure-pack docs into one email (closure letter wins). SoA PDF still generated, attached, and portal-downloadable (verified Day 61). |
| **OBS-2101** | No tariff dropdown on time entry; non-tariff entries book at R 0,00 with a "No rate card found" warning | WONT_FIX (prior cycle). Behaved exactly as the scenario amendment expects. |
| **OBS-701** | Portal proposal/engagement-letter view shows no structured fee-estimate table / VAT line (Fee column "-") | WONT_FIX (prior cycle). Hourly fee model surfaces the rate note, not a line table. |

---

## C. Mandate exemptions (out of scope by user mandate — observed as expected)

| Area | Observation | Disposition |
|------|-------------|-------------|
| **KYC / FICA** | "Verify with AI" disabled; FICA adapter not configured (Day 2) | Expected PARTIAL per mandate — only KYC + Payments are acceptable open gaps. |
| **Payments** | Mock payment gateway only ("Mock Payment Checkout — DEV ONLY"); no real PSP | Expected per mandate. Webhook reconciliation SENT→PAID verified end-to-end on the mock path (Day 30). |

---

## D. Cosmetic / below-gap-threshold observations (noted, not filed)

- **Recharts `referenceLine` degenerate-path SVG warning** — firm Dashboard "Team Time" chart emits a library-internal `Expected moveto … "L 2,20 L 2,20 Z"` console warning. Chart renders correctly. Pre-existing, no functional impact (Day 21, Day 90).
- **Dashboard sparkline `<path> d` SVG glitch** — similar cosmetic recharts artifact (Day 90). No functional impact.
- **Portal `:3002/favicon.ico` 404** — cosmetic, every portal page.
- **Next.js dev `scroll-behavior: smooth` advisory** — benign dev-only console warning (classified INFO in prior cycles).

---

## E. Investigated and confirmed NOT bugs (by-design — recorded so they aren't re-raised)

- **Proposal acceptance auto-creates a new matter** (distinct from the originating RAF matter) — by design, `ProposalOrchestrationService.createProject` unconditionally creates a project on accept (ADR-125). Identical to the prior VERIFIED cycle (showed "2 Active Matters"). *Product-enhancement candidate (not a bug): reuse the originating matter when a proposal was authored from one.*
- **Retention card "State B / unconfigured"** (Day 85) — the per-matter retention end-date derives from `OrgSettings.legalMatterRetentionYears`, which was never set in Day-1 setup; `ProjectService.computeRetentionEndsOn` deliberately returns null (no default) to distinguish configured vs unconfigured tenants. The sweep engine itself is configured.
- **No `recoverable` toggle on disbursements** (Day 21) — every disbursement is rebillable-by-design via UNBILLED→APPROVED→BILLED.
- **Fee-note email "Amount Due" shows net** (ZAR 1250.00) vs document total R 1 437,50 incl. VAT (Day 28) — identical to the prior VERIFIED cycle; copy choice, not a calculation error.
- **`project.deleted` audit event carries `project_id` but the live UI feed 404s for a deleted matter** (OBS-8801 fix) — the feed endpoint guards on `requireViewAccess`; the event is queryable, surfacing deleted-matter history in the UI is a separate product decision.
- **`"System"`-labelled audit row** in the actor filter (Day 85) — a `payment.session.created` event with `actor_type=SYSTEM` carrying the user's actor_id; correct §12.3.4 label resolution.
- **Close Matter button in the page header** (`close-matter-btn`) rather than the scenario's described sidebar footer — UI-location scenario/build mismatch, not a defect.
- **Weekly-digest copy** surfaces invoices/trust/requests/deadlines, not literal "SoA-downloaded"/"matter-closed" lines (Day 75) — designed content model.
- **Trust balance R 70,000 (two deposits)** vs the scenario's R 71,000 (three) — cycle-correct; the scenario's third R 1,000 deposit came from a prior-cycle Day-14 step (OBS-1101) not performed this clean-slate cycle.
- **`document.generated_with_clauses`** intentionally left without `project_id` in the OBS-8801 fix — internal companion event, no formatter label, never surfaced; forcing it would surface a duplicate-ish internal row.

---

## Summary

- **Open after this cycle:** 2 items — **OBS-506** (LOW, deferred to AI-proxy wiring) and **OBS-6002** (MEDIUM candidate, pending quiescent-build repro; most likely tooling). Neither blocked any lifecycle checkpoint.
- **Everything else** is either fixed-and-verified, a documented pre-existing WONT_FIX, a mandate exemption (KYC/Payments), cosmetic, or confirmed by-design.
- No tenant-isolation, data-integrity, or transaction-safety bug was left unaddressed.
